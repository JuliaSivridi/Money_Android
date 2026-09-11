package com.stler.money.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Pending Sheets API operations queued while offline or before push.
 * Drained by SyncWorker during the push phase — see tech spec §7.
 *
 * The auto-increment [id] doubles as the "createdAt" ordering signal for the
 * queue's latest-per-entity dedup step (§16.2): SQLite ROWID insertion order
 * is exactly the temporal order the pseudocode's `createdAt` field describes,
 * so a separate timestamp column isn't needed.
 */
@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityType: String,   // "transaction" | "account" | "category"
    val operation: String,    // "INSERT" | "UPDATE" | "DELETE"
    val entityId: String,
    val payloadJson: String,  // serialized entity (empty for DELETE)
    val retryCount: Int = 0,
)
