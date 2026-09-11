package com.stler.money.ui.accounts

import androidx.lifecycle.viewModelScope
import com.stler.money.data.repository.AccountRepository
import com.stler.money.data.repository.TransactionRepository
import com.stler.money.domain.model.AccountType
import com.stler.money.ui.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Backs [AccountsScreen] — tech spec §8.2. */
@HiltViewModel
class AccountsViewModel @Inject constructor(
    accountRepository: AccountRepository,
    transactionRepository: TransactionRepository,
    private val accountsPreferences: AccountsPreferences,
) : BaseViewModel() {

    val accounts = accountRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Unresolved debt_lent/debt_borrowed transactions — real `AccountsPage.tsx`'s Debts section. */
    val openDebts = transactionRepository.observeOpenDebts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * True only until the first real Room emission arrives — drives the shimmer-vs-real-
     * empty-state choice. `SyncState.Syncing` was tried first and was the wrong signal: on
     * a warm install Room already has cached data, so nothing is ever "empty AND syncing"
     * even though there's still a real (if brief) gap, every cold start, before this
     * `StateFlow` moves past its own initial value.
     */
    val isLoading = accountRepository.observeAll()
        .map { false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** Collapsed account-type sections (cash/card/savings/investment) — real `collapsedAccountGroups`. */
    val collapsedGroups = accountsPreferences.collapsedGroups
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun toggleGroup(type: AccountType) = safeLaunch {
        val current = collapsedGroups.value
        val next = if (type.name in current) current - type.name else current + type.name
        accountsPreferences.setCollapsedGroups(next)
    }
}
