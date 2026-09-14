package com.stler.money.notifications.parsers

import com.stler.money.data.repository.ParsedDraft
import com.stler.money.notifications.MoneyAmountExtractor
import com.stler.money.notifications.NotificationParser
import com.stler.money.notifications.hashNotificationText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * No real Wise notification sample was available while writing this (same caveat as
 * GooglePayParser) — best-effort amount+currency search only. Julia's most common real
 * case: her husband pays with his own phone using her Wise-linked card, so this fires on
 * her phone from the Wise app itself, not from a bank/wallet app.
 */
class WiseParser : NotificationParser {
    override val packageName = "com.transferwise.android"
    override val sourceLabel = "Wise"

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
