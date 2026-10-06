package com.stler.money.data.local.entity

import androidx.room.Entity

/**
 * Permanent record that one specific notification (identified by its key *and* post time —
 * a source that reuses the same key for a genuinely new post gets a new postTime) has already
 * been turned into a draft. Needed because a draft row is deleted on confirm/discard while
 * the notification itself can stay in the shade for hours, and every rescan (app open) sees it
 * again. Local-only, never synced; old rows are pruned, see TransactionDraftRepositoryImpl.
 */
@Entity(
    tableName = "processed_notifications",
    primaryKeys = ["sourcePackage", "notificationKey", "postedAt"],
)
data class ProcessedNotificationEntity(
    val sourcePackage: String,
    val notificationKey: String,
    val postedAt: Long,
    val processedAt: Long,
)
