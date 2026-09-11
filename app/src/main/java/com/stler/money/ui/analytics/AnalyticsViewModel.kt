package com.stler.money.ui.analytics

import androidx.lifecycle.viewModelScope
import com.stler.money.data.repository.AccountRepository
import com.stler.money.data.repository.CategoryRepository
import com.stler.money.data.repository.ExchangeRateRepository
import com.stler.money.data.repository.TransactionRepository
import com.stler.money.ui.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Backs [AnalyticsScreen] and its Yearly/Monthly tabs — tech spec §8.4. */
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    transactionRepository: TransactionRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    exchangeRateRepository: ExchangeRateRepository,
    private val analyticsPreferences: AnalyticsPreferences,
) : BaseViewModel() {

    val transactions = transactionRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val accounts = accountRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val categories = categoryRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val baseCurrency = exchangeRateRepository.baseCurrency
    val rates = exchangeRateRepository.rates

    /** Empty set means "all accounts" — persisted across restarts, see [AnalyticsPreferences]. */
    val balanceAccountIds = analyticsPreferences.balanceAccountIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun setBalanceAccountIds(ids: Set<String>) = safeLaunch {
        analyticsPreferences.setBalanceAccountIds(ids)
    }
}
