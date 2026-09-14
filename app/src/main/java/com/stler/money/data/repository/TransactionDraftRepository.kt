package com.stler.money.data.repository

import com.stler.money.domain.model.TransactionDraft
import kotlinx.coroutines.flow.Flow

/** Output of a [com.stler.money.notifications.NotificationParser] — see notifications/. */
data class ParsedDraft(
    val sourcePackage: String,
    val sourceLabel: String,
    val notificationKey: String,
    val textHash: String,
    val amount: Double,
    val currency: String,
    val date: String,
)

interface TransactionDraftRepository {
    fun observeAll(): Flow<List<TransactionDraft>>
    suspend fun deleteById(id: String)

    /** Returns true if a new draft was inserted, false if a recent duplicate suppressed it. */
    suspend fun insertIfNotDuplicate(parsed: ParsedDraft): Boolean
}
