package com.stler.money.notifications

import com.stler.money.notifications.parsers.GooglePayParser
import com.stler.money.notifications.parsers.RevolutParser
import com.stler.money.notifications.parsers.SPankkiParser
import com.stler.money.notifications.parsers.WiseParser
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The notification-source whitelist — a plain list, not Hilt multibinding, since a
 * handful of parsers doesn't justify that machinery yet. Add a new [NotificationParser]
 * here to extend to another source (Julia mentioned other Finnish banks may follow).
 */
@Singleton
class NotificationParserRegistry @Inject constructor() {
    private val byPackage: Map<String, NotificationParser> = listOf(
        GooglePayParser(),
        WiseParser(),
        RevolutParser(),
        SPankkiParser(),
    ).associateBy { it.packageName }

    fun forPackage(packageName: String): NotificationParser? = byPackage[packageName]

    /** Drives both the listener's own filtering and the Settings "is access granted" check. */
    val whitelistedPackages: Set<String> get() = byPackage.keys
}
