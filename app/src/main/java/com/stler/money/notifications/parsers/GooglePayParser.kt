package com.stler.money.notifications.parsers

import com.stler.money.data.repository.ParsedDraft
import com.stler.money.notifications.MoneyAmountExtractor
import com.stler.money.notifications.NotificationParser
import com.stler.money.notifications.hashNotificationText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * No real Google Wallet notification sample was available while writing this (see tech
 * spec / dev plan) — it only looks for a money amount+currency anywhere in the title/text
 * rather than matching a specific phrase, since merchant is never parsed anyway.
 * MoneyNotificationListenerService logs the raw title/text of every notification from this
 * package regardless of parse success, so a real sample can be captured from Logcat
 * (tag "MoneyNotificationListener") to tighten this once one is seen.
 */
class GooglePayParser : NotificationParser {
    override val packageName = "com.google.android.apps.walletnfcrel"
    override val sourceLabel = "Google Pay"

    override fun parse(title: String, text: String, postedAt: Instant, notificationKey: String): ParsedDraft? {
        val money = MoneyAmountExtractor.extract("$title $text") ?: return null
        return ParsedDraft(
            sourcePackage = packageName,
            sourceLabel = sourceLabel,
            notificationKey = notificationKey,
            textHash = hashNotificationText(title, text),
            amount = money.amount,
            currency = money.currency,
            date = LocalDate.ofInstant(postedAt, ZoneId.systemDefault()).toString(),
        )
    }
}
