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
    /** `StatusBarNotification.postTime` — set by the listener service (not the parsers), part
     * of the permanent "already processed" identity alongside [notificationKey]. */
    val postedAtMillis: Long = 0L,
)

interface TransactionDraftRepository {
    fun observeAll(): Flow<List<TransactionDraft>>
    suspend fun deleteById(id: String)

    /**
     * Records one parsed notification: resolves which Account (if any) recognizes its
     * source package, then either inserts a new draft, merges it into a recent draft for
     * the same real-world event reported by a different source (upgrading that draft's
     * account if this notification resolves it), or drops it as a plain duplicate. Returns
     * true only when a new draft row was actually inserted.
     */
    suspend fun recordNotification(parsed: ParsedDraft): Boolean
}
