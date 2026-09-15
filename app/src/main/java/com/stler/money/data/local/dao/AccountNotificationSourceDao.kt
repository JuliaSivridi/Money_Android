package com.stler.money.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.stler.money.data.local.entity.AccountNotificationSourceEntity

@Dao
interface AccountNotificationSourceDao {

    @Query("SELECT packageName FROM account_notification_sources WHERE accountId = :accountId")
    suspend fun getPackagesForAccount(accountId: String): List<String>

    @Query("SELECT accountId FROM account_notification_sources WHERE packageName = :packageName")
    suspend fun getAccountIdsForPackage(packageName: String): List<String>

    @Query("DELETE FROM account_notification_sources WHERE accountId = :accountId")
    suspend fun deleteForAccount(accountId: String)

    @Insert
    suspend fun insertAll(rows: List<AccountNotificationSourceEntity>)

    /** Replaces the full set of recognized packages for one account. */
    @Transaction
    suspend fun replaceForAccount(accountId: String, packageNames: Set<String>) {
        deleteForAccount(accountId)
        if (packageNames.isNotEmpty()) {
            insertAll(packageNames.map { AccountNotificationSourceEntity(accountId, it) })
        }
    }
}
