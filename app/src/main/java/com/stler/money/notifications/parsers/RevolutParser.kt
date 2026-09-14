package com.stler.money.notifications.parsers

import com.stler.money.data.repository.ParsedDraft
import com.stler.money.notifications.MoneyAmountExtractor
import com.stler.money.notifications.NotificationParser
import com.stler.money.notifications.hashNotificationText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** No real Revolut notification sample was available while writing this — same
 * best-effort amount+currency search as the other parsers (see GooglePayParser). */
class RevolutParser : NotificationParser {
    override val packageName = "com.revolut.revolut"
    override val sourceLabel = "Revolut"

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
