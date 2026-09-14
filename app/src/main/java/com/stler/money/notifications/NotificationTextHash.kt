package com.stler.money.notifications

import java.security.MessageDigest

/** Shared "same event" hash — the dedup fallback key every [NotificationParser] uses. */
fun hashNotificationText(title: String, text: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest("$title|$text".toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}
