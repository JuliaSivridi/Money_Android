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

    /**
     * Cross-source correlation — different apps (e.g. a wallet app and the bank's own
     * app) can each post their own notification for the *same* real-world transaction.
     * Matching purely on amount+currency within [sinceIso] is the only signal available
     * without understanding each source's specific text; scoped to a tighter window than
     * [findRecentDuplicate] since it's a fuzzier match.
     */
    @Query(
        """
        SELECT * FROM transaction_drafts
        WHERE amount = :amount AND currency = :currency AND createdAt >= :sinceIso
        ORDER BY createdAt DESC
        LIMIT 1
        """,
    )
    suspend fun findRecentSameAmount(amount: Double, currency: String, sinceIso: String): TransactionDraftEntity?

    @Query("UPDATE transaction_drafts SET accountId = :accountId WHERE id = :id")
    suspend fun updateAccountId(id: String, accountId: String)

    @Insert
    suspend fun insert(draft: TransactionDraftEntity)

    @Query("DELETE FROM transaction_drafts WHERE id = :id")
    suspend fun deleteById(id: String)
}
