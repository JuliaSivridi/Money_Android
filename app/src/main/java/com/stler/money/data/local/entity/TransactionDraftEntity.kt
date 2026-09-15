package com.stler.money.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.stler.money.domain.model.TransactionDraft

/**
 * Pending transaction suggestion captured from a whitelisted notification source —
 * see notifications/. Purely local: never touches sync_queue or Sheets. A row here
 * IS the pending state — confirming (after a real Save) or dismissing it just
 * deletes the row, no status history is kept.
 */
@Entity(tableName = "transaction_drafts")
data class TransactionDraftEntity(
    @PrimaryKey val id: String,
    val sourcePackage: String,
    val sourceLabel: String,
    val notificationKey: String,
    val textHash: String,
    val amount: Double,
    val currency: String,
    val date: String,
    val createdAt: String,
    /** Resolved via AccountNotificationSourceDao at capture time — blank when ambiguous
     * (no account, or more than one, recognizes this notification's package) or when no
     * account recognizes it at all. See TransactionDraftRepositoryImpl.recordNotification. */
    val accountId: String = "",
)

fun TransactionDraftEntity.toDomain() = TransactionDraft(
    id = id,
    sourceLabel = sourceLabel,
    amount = amount,
    currency = currency,
    date = date,
    accountId = accountId,
)
