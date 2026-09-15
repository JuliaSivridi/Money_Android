package com.stler.money.data.local.entity

import androidx.room.Entity

/**
 * Which whitelisted notification packages (see notifications/NotificationParserRegistry)
 * should be attributed to which [com.stler.money.domain.model.Account] — configured by the
 * user per-account (AccountFormSheet), purely local, never synced to Sheets: this has no
 * meaning on another device or the PWA, and isn't part of the Account's own Sheets-mirrored
 * columns (§5.7) for that reason.
 */
@Entity(tableName = "account_notification_sources", primaryKeys = ["accountId", "packageName"])
data class AccountNotificationSourceEntity(
    val accountId: String,
    val packageName: String,
)
