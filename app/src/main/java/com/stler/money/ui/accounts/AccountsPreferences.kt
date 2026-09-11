package com.stler.money.ui.accounts

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.accountsDataStore by preferencesDataStore(name = "accounts_prefs")

/**
 * Persists which account-type sections (cash/card/savings/investment) are
 * collapsed on AccountsScreen — real PWA's `usePrefsStore.collapsedAccountGroups`.
 * Local-only (not synced to `settings!A1`), same scope decision as
 * `AnalyticsPreferences` — nothing asked for cross-device sync of this either.
 */
@Singleton
class AccountsPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** [AccountType.name] values of the collapsed sections. */
    val collapsedGroups: Flow<Set<String>> =
        context.accountsDataStore.data.map { it[COLLAPSED_GROUPS] ?: emptySet() }

    suspend fun setCollapsedGroups(groups: Set<String>) {
        context.accountsDataStore.edit { it[COLLAPSED_GROUPS] = groups }
    }

    companion object {
        private val COLLAPSED_GROUPS = stringSetPreferencesKey("collapsed_groups")
    }
}
