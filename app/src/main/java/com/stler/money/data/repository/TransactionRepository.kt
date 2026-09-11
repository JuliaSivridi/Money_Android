package com.stler.money.data.repository

import com.stler.money.domain.model.Transaction
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {
    fun observeAll(): Flow<List<Transaction>>
    fun observeByAccount(accountId: String): Flow<List<Transaction>>
    fun observeByCategory(categoryId: String): Flow<List<Transaction>>
    fun observeOpenDebts(): Flow<List<Transaction>>
    suspend fun getById(id: String): Transaction?

    /** Applies the balance delta (§16.1) via AccountRepository.adjustBalance. */
    suspend fun createTransaction(transaction: Transaction)

    /** Reverses the old delta, then applies the new one (§16.1). */
    suspend fun updateTransaction(transaction: Transaction)

    /** Reverses the delta only (§16.1). */
    suspend fun deleteTransaction(id: String)

    suspend fun fetchAllAndSave(spreadsheetId: String)
    suspend fun clearAllLocalData()
}
