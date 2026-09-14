package com.stler.money.notifications

import com.stler.money.data.repository.ParsedDraft
import java.time.Instant

/**
 * Strategy interface — one implementation per whitelisted notification source (see
 * NotificationParserRegistry). Merchant/payee is deliberately never extracted: the app
 * doesn't store that for real transactions either, so parsing only needs to locate a
 * money amount+currency in the notification text (see [MoneyAmountExtractor]), not
 * understand its full phrasing.
 */
interface NotificationParser {
    val packageName: String
    val sourceLabel: String

    /** Returns null (caller logs+skips) when no amount could be found. */
    fun parse(title: String, text: String, postedAt: Instant, notificationKey: String): ParsedDraft?
}
