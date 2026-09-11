package com.stler.money.data.repository

import androidx.room.withTransaction
import com.google.gson.Gson
import com.stler.money.data.local.MoneyDatabase
import com.stler.money.data.local.dao.CategoryDao
import com.stler.money.data.local.dao.SyncQueueDao
import com.stler.money.data.local.dao.TransactionDao
import com.stler.money.data.local.entity.CategoryEntity
import com.stler.money.data.local.entity.SyncQueueEntity
import com.stler.money.data.local.entity.TransactionCategoryCrossRef
import com.stler.money.data.local.entity.toDomain
import com.stler.money.data.local.entity.toEntity
import com.stler.money.data.remote.SheetsApi
import com.stler.money.data.remote.SheetsMapper
import com.stler.money.domain.model.Category
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepositoryImpl @Inject constructor(
    private val db: MoneyDatabase,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val syncQueueDao: SyncQueueDao,
    private val sheetsApi: SheetsApi,
    private val mapper: SheetsMapper,
    private val gson: Gson,
) : CategoryRepository {

    override fun observeAll(): Flow<List<Category>> =
        categoryDao.observeAll().map { it.map { e -> e.toDomain() } }

    override suspend fun getAll(): List<Category> =
        categoryDao.getAll().map { it.toDomain() }

    override suspend fun getById(id: String): Category? =
        categoryDao.getById(id)?.toDomain()

    override suspend fun createCategory(category: Category) {
        val entity = category.toEntity()
        categoryDao.upsert(entity)
        enqueue("category", "INSERT", category.id, entity)
    }

    override suspend fun updateCategory(category: Category) {
        val entity = category.toEntity()
        categoryDao.upsert(entity)
        enqueue("category", "UPDATE", category.id, entity)
    }

    /**
     * Only touches (writes + enqueues) categories whose `sortOrder` actually changed —
     * without this check, a plain `mapIndexed` rewrites and re-enqueues *every* category
     * in the list on every drag, even ones that didn't move (real bug: swapping the first
     * two categories in a 28-category list enqueued all 28 for sync, and needlessly
     * re-serialized every other field too — e.g. `expense_limit` drifting from "24" to
     * "24.0" purely from being round-tripped through `Double` again for no reason).
     */
    override suspend fun reorder(orderedIds: List<String>) {
        val existing = categoryDao.getAll().associateBy { it.id }
        val now = nowIso()
        val updated = orderedIds.mapIndexedNotNull { index, id ->
            val entity = existing[id] ?: return@mapIndexedNotNull null
            if (entity.sortOrder == index) null else entity.copy(sortOrder = index, updatedAt = now)
        }
        if (updated.isEmpty()) return
        categoryDao.upsertAll(updated)
        updated.forEach { enqueue("category", "UPDATE", it.id, it) }
    }

    /**
     * See tech spec §16.7 — dedup prevents a transaction ending up with the same category ID
     * twice. The category DELETE enqueue happens *inside* the same `withTransaction` as the
     * row deletion (previously after it) — a process death between the two used to leave the
     * category gone locally with the DELETE never queued, so the next pull restored it from
     * Sheets as if nothing happened.
     */
    override suspend fun deleteCategoryWithTransfer(id: String, transferToId: String) {
        val now = nowIso()
        val affected = transactionDao.getByCategoryId(id)

        db.withTransaction {
            affected.forEach { txn ->
                val currentIds = if (txn.categoryIds.isBlank()) emptyList()
                else txn.categoryIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                val newIds = currentIds
                    .map { if (it == id) transferToId else it }
                    .distinct()
                val updated = txn.copy(categoryIds = newIds.joinToString(","), updatedAt = now)
                transactionDao.upsert(updated)
                transactionDao.clearCategoryRefs(txn.id)
                if (newIds.isNotEmpty()) {
                    transactionDao.insertCategoryRefs(
                        newIds.mapIndexed { index, catId -> TransactionCategoryCrossRef(txn.id, catId, index) }
                    )
                }
                enqueueTransaction(txn.id, updated)
            }
            categoryDao.deleteById(id)
            enqueue("category", "DELETE", id, null)
        }
    }

    override suspend fun fetchAllAndSave(spreadsheetId: String) {
        val pendingIds = syncQueueDao.getAll().filter { it.entityType == "category" }.map { it.entityId }.toSet()
        val response = sheetsApi.batchGet(spreadsheetId, listOf("categories"))
        val values = response.valueRanges.firstOrNull()?.values
        // See TransactionRepositoryImpl.fetchAllAndSave — no header row at all means don't
        // trust this response enough to run deleteNotIn.
        if (values.isNullOrEmpty()) return
        val remote = values.drop(1).mapNotNull { mapper.rowToCategory(it) }
        categoryDao.upsertAll(remote.filter { it.id !in pendingIds })
        categoryDao.deleteNotIn(remote.map { it.id } + pendingIds)
    }

    override suspend fun clearAllLocalData() {
        categoryDao.deleteAll()
    }

    private suspend fun enqueue(type: String, op: String, id: String, payload: CategoryEntity?) {
        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = type,
                operation = op,
                entityId = id,
                payloadJson = if (payload != null) gson.toJson(payload) else "",
            )
        )
    }

    private suspend fun enqueueTransaction(id: String, payload: com.stler.money.data.local.entity.TransactionEntity) {
        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = "transaction",
                operation = "UPDATE",
                entityId = id,
                payloadJson = gson.toJson(payload),
            )
        )
    }

    private fun nowIso(): String = Instant.now().toString()
}
