package com.stler.money.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.stler.money.auth.AuthPreferences
import com.stler.money.auth.GoogleAuthRepository
import com.stler.money.data.local.dao.SyncQueueDao
import com.stler.money.data.local.entity.AccountEntity
import com.stler.money.data.local.entity.CategoryEntity
import com.stler.money.data.local.entity.SyncQueueEntity
import com.stler.money.data.local.entity.TransactionEntity
import com.stler.money.data.remote.SheetsApi
import com.stler.money.data.remote.SheetsMapper
import com.stler.money.data.remote.dto.BatchUpdateValuesBody
import com.stler.money.data.remote.dto.ValuesBody
import com.stler.money.data.repository.AccountRepository
import com.stler.money.data.repository.CategoryRepository
import com.stler.money.data.repository.TransactionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * WorkManager worker handling bidirectional sync with Google Sheets — tech spec §7.
 *
 * Execution order:
 *  1. Push — dedup the queue to one item per entity (§16.2, the one place this
 *     diverges from the Tasks Android sibling, which pushes every item as-is),
 *     then drain it to the Sheets API (INSERT / UPDATE / DELETE).
 *  2. Pull — batchGet each sheet -> Room upsert (transactions / accounts / categories).
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val sheetsApi: SheetsApi,
    private val mapper: SheetsMapper,
    private val syncQueueDao: SyncQueueDao,
    private val authPreferences: AuthPreferences,
    private val authRepository: GoogleAuthRepository,
    private val gson: Gson,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        var spreadsheetId = authPreferences.spreadsheetId.first()

        if (spreadsheetId.isBlank()) {
            Log.w(TAG, "spreadsheetId is blank — attempting Drive search")
            spreadsheetId = authRepository.findAndSaveSpreadsheetId()
        }

        if (spreadsheetId.isBlank()) {
            Log.w(TAG, "spreadsheetId still blank after Drive search — aborting")
            return Result.success()
        }

        return try {
            val hadPendingChanges = syncQueueDao.getAll().isNotEmpty()
            push(spreadsheetId)
            // Sheets can sometimes serve a cached (pre-write) response if a batchGet
            // arrives immediately after a batchUpdate — brief pause makes this race
            // condition negligible in practice (same guard as Tasks Android).
            if (hadPendingChanges) delay(1_000L)
            transactionRepository.fetchAllAndSave(spreadsheetId)
            accountRepository.fetchAllAndSave(spreadsheetId)
            categoryRepository.fetchAllAndSave(spreadsheetId)
            Log.d(TAG, "Sync complete")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed (attempt ${runAttemptCount + 1}): ${e.message}", e)
            if (runAttemptCount < 4) Result.retry() else Result.failure()
        }
    }

    // ── Push ──────────────────────────────────────────────────────────────

    /**
     * Dedup step — tech spec §16.2. The queue is already id-ASC ordered
     * (SyncQueueDao.getAll()), so a plain last-write-wins pass over it by key
     * is equivalent to the spec's createdAt-ordered pseudocode. INSERT/UPDATE
     * share one key per entity (a later item — whichever op it is — wins);
     * DELETE gets its own key so it can't be silently dropped by a create/
     * update queued for the same entity earlier in the same push.
     */
    private suspend fun push(spreadsheetId: String) {
        val queue = syncQueueDao.getAll()
        if (queue.isEmpty()) return

        val latestByKey = LinkedHashMap<String, SyncQueueEntity>()
        for (item in queue) {
            val key = if (item.operation == "DELETE") "${item.entityType}:${item.entityId}:delete"
            else "${item.entityType}:${item.entityId}"
            latestByKey[key] = item
        }
        val latestIds = latestByKey.values.map { it.id }.toSet()
        val supersededIds = queue.filter { it.id !in latestIds }.map { it.id }
        if (supersededIds.isNotEmpty()) syncQueueDao.deleteByIds(supersededIds)

        val rowsCache = mutableMapOf<String, List<List<Any?>>>()
        for (item in latestByKey.values) {
            if (item.retryCount >= MAX_RETRIES) continue
            runCatching { executeOperation(item, spreadsheetId, rowsCache) }
                .onSuccess { syncQueueDao.deleteById(item.id) }
                .onFailure { syncQueueDao.incrementRetry(item.id) }
        }
        syncQueueDao.deleteExhausted(MAX_RETRIES)
    }

    private suspend fun executeOperation(
        item: SyncQueueEntity,
        spreadsheetId: String,
        rowsCache: MutableMap<String, List<List<Any?>>>,
    ) {
        val sheetName = sheetOf(item.entityType)

        when (item.operation) {
            "INSERT" -> {
                val appendRange = "$sheetName!A:${lastColOf(item.entityType)}"
                sheetsApi.append(
                    spreadsheetId = spreadsheetId,
                    range = appendRange,
                    body = ValuesBody(range = appendRange, values = listOf(entityRow(item))),
                )
                rowsCache.remove(sheetName)
            }
            "UPDATE" -> {
                val rows = cachedRows(sheetName, spreadsheetId, rowsCache)
                val rowNum = mapper.findRowNumber(rows, item.entityId)
                if (rowNum == null) {
                    // Row absent — INSERT instead (handles an INSERT+UPDATE race within the same push).
                    val appendRange = "$sheetName!A:${lastColOf(item.entityType)}"
                    sheetsApi.append(
                        spreadsheetId = spreadsheetId,
                        range = appendRange,
                        body = ValuesBody(range = appendRange, values = listOf(entityRow(item))),
                    )
                    rowsCache.remove(sheetName)
                } else {
                    val range = "$sheetName!A$rowNum:${lastColOf(item.entityType)}$rowNum"
                    sheetsApi.batchUpdate(
                        spreadsheetId,
                        BatchUpdateValuesBody(data = listOf(ValuesBody(range, values = listOf(entityRow(item))))),
                    )
                }
            }
            "DELETE" -> {
                val rows = cachedRows(sheetName, spreadsheetId, rowsCache)
                val rowNum = mapper.findRowNumber(rows, item.entityId) ?: return // already absent — idempotent
                val range = "$sheetName!A$rowNum:${lastColOf(item.entityType)}$rowNum"
                sheetsApi.clear(spreadsheetId, range)
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private suspend fun cachedRows(
        sheetName: String,
        spreadsheetId: String,
        cache: MutableMap<String, List<List<Any?>>>,
    ): List<List<Any?>> = cache.getOrPut(sheetName) {
        sheetsApi.batchGet(spreadsheetId, listOf(sheetName))
            .valueRanges.firstOrNull()?.values.orEmpty()
    }

    private fun entityRow(item: SyncQueueEntity): List<Any?> = when (item.entityType) {
        "transaction" -> mapper.transactionToRow(gson.fromJson(item.payloadJson, TransactionEntity::class.java))
        "account" -> mapper.accountToRow(gson.fromJson(item.payloadJson, AccountEntity::class.java))
        "category" -> mapper.categoryToRow(gson.fromJson(item.payloadJson, CategoryEntity::class.java))
        else -> emptyList()
    }

    private fun sheetOf(entityType: String) = when (entityType) {
        "transaction" -> "transactions"
        "account" -> "accounts"
        "category" -> "categories"
        else -> entityType
    }

    private fun lastColOf(entityType: String) = when (entityType) {
        "transaction" -> "P"  // 16 columns A-P
        "account" -> "J"      // 10 columns A-J
        "category" -> "K"     // 11 columns A-K
        else -> "Z"
    }

    companion object {
        private const val TAG = "SyncWorker"
        private const val MAX_RETRIES = 5
    }
}
