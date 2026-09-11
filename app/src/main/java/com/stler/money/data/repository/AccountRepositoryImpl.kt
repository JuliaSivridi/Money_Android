package com.stler.money.data.repository

import com.google.gson.Gson
import com.stler.money.data.local.dao.AccountDao
import com.stler.money.data.local.dao.SyncQueueDao
import com.stler.money.data.local.entity.AccountEntity
import com.stler.money.data.local.entity.SyncQueueEntity
import com.stler.money.data.local.entity.toDomain
import com.stler.money.data.local.entity.toEntity
import com.stler.money.data.remote.SheetsApi
import com.stler.money.data.remote.SheetsMapper
import com.stler.money.domain.model.Account
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepositoryImpl @Inject constructor(
    private val accountDao: AccountDao,
    private val syncQueueDao: SyncQueueDao,
    private val sheetsApi: SheetsApi,
    private val mapper: SheetsMapper,
    private val gson: Gson,
) : AccountRepository {

    override fun observeAll(): Flow<List<Account>> =
        accountDao.observeAll().map { it.map { e -> e.toDomain() } }

    override suspend fun getAll(): List<Account> =
        accountDao.getAll().map { it.toDomain() }

    override suspend fun getById(id: String): Account? =
        accountDao.getById(id)?.toDomain()

    override suspend fun createAccount(account: Account) {
        val entity = account.toEntity()
        accountDao.upsert(entity)
        enqueue("account", "INSERT", account.id, entity)
    }

    override suspend fun updateAccount(account: Account) {
        val entity = account.toEntity()
        accountDao.upsert(entity)
        enqueue("account", "UPDATE", account.id, entity)
    }

    override suspend fun deleteAccount(id: String) {
        accountDao.deleteById(id)
        enqueue("account", "DELETE", id, null)
    }

    override suspend fun adjustBalance(accountId: String, delta: Double) {
        val entity = accountDao.getById(accountId) ?: return
        val updated = entity.copy(balance = entity.balance + delta, updatedAt = nowIso())
        accountDao.upsert(updated)
        enqueue("account", "UPDATE", accountId, updated)
    }

    override suspend fun fetchAllAndSave(spreadsheetId: String) {
        val pendingIds = syncQueueDao.getAll().filter { it.entityType == "account" }.map { it.entityId }.toSet()
        val response = sheetsApi.batchGet(spreadsheetId, listOf("accounts"))
        val remote = response.valueRanges.firstOrNull()?.values?.drop(1)
            ?.mapNotNull { mapper.rowToAccount(it) } ?: return
        accountDao.upsertAll(remote.filter { it.id !in pendingIds })
        accountDao.deleteNotIn(remote.map { it.id } + pendingIds)
    }

    override suspend fun clearAllLocalData() {
        accountDao.deleteAll()
    }

    private suspend fun enqueue(type: String, op: String, id: String, payload: AccountEntity?) {
        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = type,
                operation = op,
                entityId = id,
                payloadJson = if (payload != null) gson.toJson(payload) else "",
            )
        )
    }

    private fun nowIso(): String = Instant.now().toString()
}
