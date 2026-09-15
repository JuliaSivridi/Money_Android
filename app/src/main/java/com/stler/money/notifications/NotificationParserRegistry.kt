package com.stler.money.notifications

import com.stler.money.notifications.parsers.GooglePayParser
import com.stler.money.notifications.parsers.RevolutParser
import com.stler.money.notifications.parsers.SPankkiParser
import com.stler.money.notifications.parsers.WiseParser
import javax.inject.Inject
import javax.inject.Singleton

/** One whitelisted notification source — surfaced to the account-mapping checkboxes in
 * AccountFormSheet, so the UI never has to know about [NotificationParser] itself. */
data class NotificationSource(val packageName: String, val label: String)

/**
 * The notification-source whitelist — a plain list, not Hilt multibinding, since a
 * handful of parsers doesn't justify that machinery yet. Add a new [NotificationParser]
 * here to extend to another source.
 */
@Singleton
class NotificationParserRegistry @Inject constructor() {
    private val parsers: List<NotificationParser> = listOf(
        GooglePayParser(),
        WiseParser(),
        RevolutParser(),
        SPankkiParser(),
    )
    private val byPackage: Map<String, NotificationParser> = parsers.associateBy { it.packageName }

    fun forPackage(packageName: String): NotificationParser? = byPackage[packageName]

    /** Drives both the listener's own filtering and the Settings "is access granted" check. */
    val whitelistedPackages: Set<String> get() = byPackage.keys

    val allSources: List<NotificationSource> get() = parsers.map { NotificationSource(it.packageName, it.sourceLabel) }
}
