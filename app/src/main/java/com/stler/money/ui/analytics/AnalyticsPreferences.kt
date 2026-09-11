package com.stler.money.ui.analytics

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.analyticsDataStore by preferencesDataStore(name = "analytics_prefs")

/**
 * Persists the Yearly chart's "balance trend: accounts" selection across app
 * restarts — the user's own request; real PWA's `usePrefsStore` persists the
 * equivalent `analyticsAccountIds`. Local-only (not synced to `settings!A1`
 * like base currency) — nothing asked for cross-device sync here.
 */
@Singleton
class AnalyticsPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Empty set means "all accounts" — same convention as the real PWA. */
    val balanceAccountIds: Flow<Set<String>> =
        context.analyticsDataStore.data.map { it[BALANCE_ACCOUNT_IDS] ?: emptySet() }

    suspend fun setBalanceAccountIds(ids: Set<String>) {
        context.analyticsDataStore.edit { it[BALANCE_ACCOUNT_IDS] = ids }
    }

    companion object {
        private val BALANCE_ACCOUNT_IDS = stringSetPreferencesKey("balance_account_ids")
    }
}
