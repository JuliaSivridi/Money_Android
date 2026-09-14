package com.stler.money.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.stler.money.data.local.entity.TransactionDraftEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDraftDao {

    @Query("SELECT * FROM transaction_drafts ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TransactionDraftEntity>>

    /**
     * Dedup lookup — primary key is [notificationKey] (Android reuses the same
     * `sbn.key` when a notification is updated in place); [textHash] is the fallback
     * for the rarer case where the source posts a fresh key for what is really the
     * same event. Both are scoped to [sinceIso] so an old, already-handled draft
     * (deleted long ago) doesn't suppress a genuinely new one with a coincidentally
     * matching hash.
     */
    @Query(
        """
        SELECT * FROM transaction_drafts
        WHERE sourcePackage = :sourcePackage
          AND (notificationKey = :notificationKey OR textHash = :textHash)
          AND createdAt >= :sinceIso
        LIMIT 1
        """,
    )
    suspend fun findRecentDuplicate(
        sourcePackage: String,
        notificationKey: String,
        textHash: String,
        sinceIso: String,
    ): TransactionDraftEntity?

    @Insert
    suspend fun insert(draft: TransactionDraftEntity)

    @Query("DELETE FROM transaction_drafts WHERE id = :id")
    suspend fun deleteById(id: String)
}
