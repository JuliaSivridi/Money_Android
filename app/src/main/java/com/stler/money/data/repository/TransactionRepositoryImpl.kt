package com.stler.money.data.repository

import androidx.room.withTransaction
import com.google.gson.Gson
import com.stler.money.data.local.MoneyDatabase
import com.stler.money.data.local.dao.SyncQueueDao
import com.stler.money.data.local.dao.TransactionDao
import com.stler.money.data.local.entity.SyncQueueEntity
import com.stler.money.data.local.entity.TransactionCategoryCrossRef
import com.stler.money.data.local.entity.TransactionEntity
import com.stler.money.data.local.entity.toDomain
import com.stler.money.data.local.entity.toEntity
import com.stler.money.data.remote.SheetsApi
import com.stler.money.data.remote.SheetsMapper
import com.stler.money.domain.model.BalanceDelta
import com.stler.money.domain.model.Transaction
import com.stler.money.domain.model.computeBalanceDelta
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepositoryImpl @Inject constructor(
    private val db: MoneyDatabase,
    private val transactionDao: TransactionDao,
    private val syncQueueDao: SyncQueueDao,
    private val accountRepository: AccountRepository,
    private val sheetsApi: SheetsApi,
    private val mapper: SheetsMapper,
    private val gson: Gson,
) : TransactionRepository {

    override fun observeAll(): Flow<List<Transaction>> =
        transactionDao.observeAll().map { it.map { e -> e.toDomain() } }

    override fun observeByAccount(accountId: String): Flow<List<Transaction>> =
        transactionDao.observeByAccount(accountId).map { it.map { e -> e.toDomain() } }

    override fun observeByCategory(categoryId: String): Flow<List<Transaction>> =
        transactionDao.observeByCategory(categoryId).map { it.map { e -> e.toDomain() } }

    override fun observeOpenDebts(): Flow<List<Transaction>> =
        transactionDao.observeOpenDebts().map { it.map { e -> e.toDomain() } }

    override suspend fun getById(id: String): Transaction? =
        transactionDao.getById(id)?.toDomain()

    // create/update/delete each wrap the entity write, category refs, sync-queue enqueue, AND
    // the account balance adjustment(s) in one `db.withTransaction` — previously these were
    // separate steps, so a crash/process-death between them left `Account.balance` silently
    // wrong forever (balance is denormalised, never recomputed from transaction history) and
    // update's revert-then-reapply could double-apply one side on a partial failure. Now
    // either all of it commits or none of it does; `adjustBalance` throwing on a missing
    // account (AccountRepositoryImpl) rolls back the whole mutation instead of silently
    // dropping a transfer's destination-side delta.

    override suspend fun createTransaction(transaction: Transaction) {
        val entity = transaction.toEntity()
        db.withTransaction {
            transactionDao.upsert(entity)
            writeCategoryRefs(transaction)
            enqueue("transaction", "INSERT", transaction.id, entity)
            applyDelta(transaction)
        }
    }

    override suspend fun updateTransaction(transaction: Transaction) {
        val entity = transaction.toEntity()
        db.withTransaction {
            val old = transactionDao.getById(transaction.id)?.toDomain()
            transactionDao.upsert(entity)
            writeCategoryRefs(transaction)
            enqueue("transaction", "UPDATE", transaction.id, entity)
            if (old != null) reverseDelta(old)
            applyDelta(transaction)
        }
    }

    override suspend fun deleteTransaction(id: String) {
        db.withTransaction {
            val old = transactionDao.getById(id)?.toDomain() ?: return@withTransaction
            transactionDao.deleteById(id)
            transactionDao.clearCategoryRefs(id)
            enqueue("transaction", "DELETE", id, null)
            reverseDelta(old)
        }
    }

    override suspend fun fetchAllAndSave(spreadsheetId: String) {
        val pendingIds = syncQueueDao.getAll().filter { it.entityType == "transaction" }.map { it.entityId }.toSet()
        val response = sheetsApi.batchGet(spreadsheetId, listOf("transactions"))
        val values = response.valueRanges.firstOrNull()?.values
        // No header row at all reads as an untrustworthy/transient response, not "the sheet is
        // really empty" — proceeding would wipe every local row not in the sync queue via
        // deleteNotIn below. A genuinely empty sheet still has its header row (values.size == 1);
        // only the fully-empty-array case (no header either) is refused.
        if (values.isNullOrEmpty()) return
        val remote = values.drop(1).mapNotNull { mapper.rowToTransaction(it) }

        db.withTransaction {
            val toStore = remote.filter { it.id !in pendingIds }
            transactionDao.upsertAll(toStore)
            transactionDao.deleteNotIn(remote.map { it.id } + pendingIds)
            // Rebuild category refs for every upserted row (cheap: id + up to 2 category ids).
            toStore.forEach { entity ->
                transactionDao.clearCategoryRefs(entity.id)
                val ids = if (entity.categoryIds.isBlank()) emptyList()
                else entity.categoryIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (ids.isNotEmpty()) {
                    transactionDao.insertCategoryRefs(ids.mapIndexed { i, catId -> TransactionCategoryCrossRef(entity.id, catId, i) })
                }
            }
        }
    }

    override suspend fun clearAllLocalData() {
        transactionDao.deleteAll()
        transactionDao.deleteAllCategoryRefs()
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private suspend fun writeCategoryRefs(t: Transaction) {
        transactionDao.clearCategoryRefs(t.id)
        if (t.categoryIds.isNotEmpty()) {
            transactionDao.insertCategoryRefs(
                t.categoryIds.mapIndexed { index, categoryId -> TransactionCategoryCrossRef(t.id, categoryId, index) }
            )
        }
    }

    private suspend fun applyDelta(t: Transaction) = applyBalanceDelta(computeBalanceDelta(t))

    private suspend fun reverseDelta(t: Transaction) {
        val d = computeBalanceDelta(t)
        applyBalanceDelta(BalanceDelta(d.accountId, -d.delta, d.toAccountId, d.toDelta?.let { -it }))
    }

    private suspend fun applyBalanceDelta(d: BalanceDelta) {
        accountRepository.adjustBalance(d.accountId, d.delta)
        if (d.toAccountId != null && d.toDelta != null) {
            accountRepository.adjustBalance(d.toAccountId, d.toDelta)
        }
    }

    private suspend fun enqueue(type: String, op: String, id: String, payload: TransactionEntity?) {
        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = type,
                operation = op,
                entityId = id,
                payloadJson = if (payload != null) gson.toJson(payload) else "",
            )
        )
    }
}
