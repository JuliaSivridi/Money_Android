package com.stler.money.ui.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.stler.money.data.repository.AccountRepository
import com.stler.money.data.repository.CategoryRepository
import com.stler.money.data.repository.TransactionRepository
import com.stler.money.domain.model.Account
import com.stler.money.domain.model.Category
import com.stler.money.domain.model.Transaction
import com.stler.money.domain.model.TransactionType
import com.stler.money.ui.BaseViewModel
import com.stler.money.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class TransactionsUiState(
    val transactions: List<Transaction> = emptyList(),
    val accountsById: Map<String, Account> = emptyMap(),
    val categoriesById: Map<String, Category> = emptyMap(),
    /**
     * True only until the first real Room emission arrives. `SyncState.Syncing` was tried
     * first and was the wrong signal entirely — on a warm install Room already has cached
     * data, so nothing is ever "empty AND syncing" even though there's still a real (if
     * brief) gap, every cold start, between the ViewModel existing and its `StateFlow`
     * moving past this `stateIn`'s own initial value. This flag is `true` in exactly that
     * gap and `false` from the first real `combine` emission onward, forever after —
     * exactly the "no data yet" window the shimmer needs, regardless of sync state.
     */
    val isLoading: Boolean = true,
)

/**
 * Backs [TransactionsScreen] — tech spec §8.1 — plus the real multi-select
 * `FilterMenu` (§8.4a, real PWA's `FilterPanel.tsx`/`FilterBar.tsx`). The
 * "arrive pre-filtered" nav args (Accounts/Categories row tap, Analytics donut
 * drill-down — §12) just seed the initial [filterState] instead of being a
 * separate single-entity concept; from the user's perspective they're now
 * indistinguishable from opening the filter sheet and picking the same
 * account/category/date range by hand.
 *
 * [filterState] and [search] are read directly by `MainScreen` too (via
 * `hiltViewModel(backStackEntry)` on the same Transactions nav entry — see
 * `MainScreen.kt`) so the top-bar filter button and `FilterMenu` sheet can
 * live outside this screen's own composable while still sharing this exact
 * ViewModel instance.
 */
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    transactionRepository: TransactionRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    savedStateHandle: SavedStateHandle,
) : BaseViewModel() {

    private val _filterState = MutableStateFlow(
        TransactionFilterState(
            accountIds = setOfNotNull(savedStateHandle.get<String>(Screen.ARG_ACCOUNT_ID)),
            categoryIds = setOfNotNull(savedStateHandle.get<String>(Screen.ARG_CATEGORY_ID)),
            dateFrom = savedStateHandle.get<String>(Screen.ARG_DATE_FROM),
            dateTo = savedStateHandle.get<String>(Screen.ARG_DATE_TO),
        )
    )
    val filterState: StateFlow<TransactionFilterState> = _filterState.asStateFlow()

    private val _search = MutableStateFlow("")
    val search: StateFlow<String> = _search.asStateFlow()

    val accounts = accountRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val categories = categoryRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleType(type: TransactionType) = _filterState.update {
        it.copy(types = if (type in it.types) it.types - type else it.types + type)
    }

    /** "Debt" virtual chip — maps to both DEBT_LENT and DEBT_BORROWED together, matching `FilterPanel.tsx`'s `toggleDebt`. */
    fun toggleDebtType() = _filterState.update { s ->
        val isDebtActive = TransactionType.DEBT_LENT in s.types || TransactionType.DEBT_BORROWED in s.types
        val without = s.types - TransactionType.DEBT_LENT - TransactionType.DEBT_BORROWED
        s.copy(types = if (isDebtActive) without else without + TransactionType.DEBT_LENT + TransactionType.DEBT_BORROWED)
    }

    fun toggleAccount(id: String) = _filterState.update {
        it.copy(accountIds = if (id in it.accountIds) it.accountIds - id else it.accountIds + id)
    }

    fun toggleCategory(id: String) = _filterState.update {
        it.copy(categoryIds = if (id in it.categoryIds) it.categoryIds - id else it.categoryIds + id)
    }

    fun setDateFrom(value: String?) = _filterState.update { it.copy(dateFrom = value) }
    fun setDateTo(value: String?) = _filterState.update { it.copy(dateTo = value) }
    fun setAmountMin(value: String) = _filterState.update { it.copy(amountMin = value) }
    fun setAmountMax(value: String) = _filterState.update { it.copy(amountMax = value) }
    fun setSearch(value: String) { _search.value = value }

    fun clearFilter() {
        _filterState.value = TransactionFilterState()
        _search.value = ""
    }

    val uiState = combine(
        transactionRepository.observeAll(),
        accountRepository.observeAll(),
        categoryRepository.observeAll(),
        filterState,
        search,
    ) { transactions, accounts, categories, filter, searchQuery ->
        TransactionsUiState(
            transactions = transactions.filter { it.matchesFilter(filter, searchQuery) },
            accountsById = accounts.associateBy { it.id },
            categoriesById = categories.associateBy { it.id },
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TransactionsUiState())
}
