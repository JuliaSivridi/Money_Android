package com.stler.money.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.stler.money.data.local.entity.TransactionCategoryCrossRef
import com.stler.money.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    // --- Observation (for UI) ---

    @Query("SELECT * FROM transactions ORDER BY date DESC, time DESC, createdAt DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE accountId = :accountId OR toAccountId = :accountId ORDER BY date DESC, time DESC, createdAt DESC")
    fun observeByAccount(accountId: String): Flow<List<TransactionEntity>>

    /** Joins through [TransactionCategoryCrossRef] instead of a `LIKE` scan — see tech spec §5.6. */
    @Query("""
        SELECT t.* FROM transactions t
        INNER JOIN transaction_categories tc ON t.id = tc.transactionId
        WHERE tc.categoryId = :categoryId
        ORDER BY t.date DESC, t.time DESC, t.createdAt DESC
    """)
    fun observeByCategory(categoryId: String): Flow<List<TransactionEntity>>

    /** Open debts: unresolved debt transactions (debtRefId empty) not yet referenced as a repayment's debtRefId. */
    @Query("""
        SELECT * FROM transactions
        WHERE (type = 'debt_lent' OR type = 'debt_borrowed')
          AND debtRefId = ''
          AND id NOT IN (SELECT debtRefId FROM transactions WHERE debtRefId != '')
        ORDER BY date DESC, time DESC
    """)
    fun observeOpenDebts(): Flow<List<TransactionEntity>>

    // --- Suspend reads (for SyncWorker / repo logic) ---

    @Query("SELECT * FROM transactions")
    suspend fun getAll(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: String): TransactionEntity?

    @Query("""
        SELECT t.* FROM transactions t
        INNER JOIN transaction_categories tc ON t.id = tc.transactionId
        WHERE tc.categoryId = :categoryId
    """)
    suspend fun getByCategoryId(categoryId: String): List<TransactionEntity>

    // --- Writes ---

    @Upsert
    suspend fun upsert(transaction: TransactionEntity)

    @Upsert
    suspend fun upsertAll(transactions: List<TransactionEntity>)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query("DELETE FROM transactions WHERE id NOT IN (:ids)")
    suspend fun deleteNotIn(ids: List<String>)

    // --- transaction_categories join table maintenance (see TransactionEntity.kt) ---

    @Query("DELETE FROM transaction_categories WHERE transactionId = :transactionId")
    suspend fun clearCategoryRefs(transactionId: String)

    @Insert
    suspend fun insertCategoryRefs(refs: List<TransactionCategoryCrossRef>)

    @Query("DELETE FROM transaction_categories")
    suspend fun deleteAllCategoryRefs()
}
