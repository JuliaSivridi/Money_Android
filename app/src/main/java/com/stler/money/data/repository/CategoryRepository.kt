package com.stler.money.data.repository

import com.stler.money.domain.model.Category
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun observeAll(): Flow<List<Category>>
    suspend fun getAll(): List<Category>
    suspend fun getById(id: String): Category?

    suspend fun createCategory(category: Category)
    suspend fun updateCategory(category: Category)

    /** Batch sortOrder write after a drag-reorder — tech spec §8.3. */
    suspend fun reorder(orderedIds: List<String>)

    /** Deletes [id], replacing it with [transferToId] on every referencing transaction — §16.7. */
    suspend fun deleteCategoryWithTransfer(id: String, transferToId: String)

    suspend fun fetchAllAndSave(spreadsheetId: String)
    suspend fun clearAllLocalData()
}
