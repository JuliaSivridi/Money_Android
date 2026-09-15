package com.stler.money.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.stler.money.data.repository.TransactionDraftRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/**
 * Listens only to whitelisted packages (see NotificationParserRegistry) — never reads
 * content from anything else. Enabled via the system Notification Access setting, see the
 * card in SettingsScreen. A field-injected Service (like the Activity), not constructor
 * injection — the OS instantiates it directly.
 */
@AndroidEntryPoint
class MoneyNotificationListenerService : NotificationListenerService() {

    @Inject lateinit var draftRepository: TransactionDraftRepository
    @Inject lateinit var parserRegistry: NotificationParserRegistry

    // Not suspend-friendly itself — same fire-and-forget pattern SyncWorker/
    // ExchangeRateRepositoryImpl already use elsewhere in this app.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        handle(sbn)
    }

    /** Re-processes notifications still in the shade (not yet swiped away) — for a real
     * payment notification the listener missed live (app not running yet at the time, a
     * brief listener hiccup) that the user wants to pick up retroactively. Shares [handle]
     * with the live path, so dedup/logging behave identically either way. */
    private fun rescan() {
        val active = activeNotifications ?: return
        Log.d(TAG, "Rescan: ${active.size} active notification(s)")
        active.forEach { handle(it) }
    }

    private fun handle(sbn: StatusBarNotification) {
        val parser = parserRegistry.forPackage(sbn.packageName) ?: return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        // Logged unconditionally (not just on parse failure) so a real sample can be
        // captured from Logcat to tighten a parser once one is actually seen.
        Log.d(TAG, "${sbn.packageName}: title=\"$title\" text=\"$text\"")

        val parsed = runCatching {
            parser.parse(title, text, Instant.ofEpochMilli(sbn.postTime), sbn.key)
        }.getOrNull()

        if (parsed == null) {
            Log.d(TAG, "Could not parse notification from ${sbn.packageName} — skipping")
            return
        }

        scope.launch {
            val inserted = draftRepository.recordNotification(parsed)
            Log.d(TAG, if (inserted) "Draft created: ${parsed.amount} ${parsed.currency}" else "Duplicate — skipped")
        }
    }

    companion object {
        private const val TAG = "MoneyNotificationListener"

        // The OS owns this Service's lifecycle; a raw reference here (cleared on
        // disconnect/destroy) is the standard way other code reaches a running
        // NotificationListenerService — there's no other handle to it once bound.
        @Volatile private var instance: MoneyNotificationListenerService? = null

        /** Returns false (nothing to trigger) when notification access isn't currently
         * connected — not granted yet, or the OS hasn't bound the service in this process. */
        fun rescanActiveNotifications(): Boolean {
            val service = instance ?: return false
            service.rescan()
            return true
        }
    }
}
