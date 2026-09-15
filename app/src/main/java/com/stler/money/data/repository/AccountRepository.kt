package com.stler.money.data.repository

import com.stler.money.domain.model.Account
import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    fun observeAll(): Flow<List<Account>>
    suspend fun getAll(): List<Account>
    suspend fun getById(id: String): Account?

    suspend fun createAccount(account: Account)

    /** Edit mode writes `balance` directly (drift-repair affordance) — tech spec §8.2. */
    suspend fun updateAccount(account: Account)

    /** Applies [delta] to the account's balance — never mutate `balance` any other way. */
    suspend fun adjustBalance(accountId: String, delta: Double)

    suspend fun fetchAllAndSave(spreadsheetId: String)
    suspend fun clearAllLocalData()

    // ── Notification-source recognition (local-only, never synced — see notifications/) ──

    suspend fun getNotificationSources(accountId: String): Set<String>
    suspend fun setNotificationSources(accountId: String, packageNames: Set<String>)

    /** Accounts (usually 0 or 1; more than 1 means ambiguous) that recognize [packageName]. */
    suspend fun accountIdsForNotificationPackage(packageName: String): List<String>
}
