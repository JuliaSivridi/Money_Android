package com.stler.money.ui.transaction

import androidx.lifecycle.viewModelScope
import com.stler.money.data.repository.AccountRepository
import com.stler.money.data.repository.CategoryRepository
import com.stler.money.data.repository.ExchangeRateRepository
import com.stler.money.data.repository.TransactionRepository
import com.stler.money.data.repository.convertToBase
import com.stler.money.domain.model.Transaction
import com.stler.money.ui.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Backs [TransactionFormSheet] — tech spec §10. Currency conversion
 * (`amount_base`, §16.5) is centralized here rather than in the composable,
 * matching the write-path split described in §3.
 */
@HiltViewModel
class TransactionFormViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    private val exchangeRateRepository: ExchangeRateRepository,
) : BaseViewModel() {

    val accounts = accountRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories = categoryRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(transaction: Transaction, isEdit: Boolean, onDone: () -> Unit) = safeLaunch {
        val withBase = transaction.copy(amountBase = computeAmountBase(transaction.amount, transaction.currency))
        if (isEdit) transactionRepository.updateTransaction(withBase) else transactionRepository.createTransaction(withBase)
        onDone()
    }

    /**
     * Only fetches the same-currency history fallback (a real Room query) when there's no live
     * rate to begin with — the common case (a rate is cached) stays exactly one Map lookup.
     */
    private suspend fun computeAmountBase(amount: Double, currency: String): Double {
        val baseCurrency = exchangeRateRepository.baseCurrency.value
        val rates = exchangeRateRepository.rates.value
        if (currency == baseCurrency || rates.containsKey(currency)) {
            return convertToBase(amount, currency, baseCurrency, rates)
        }
        val history = transactionRepository.observeAll().first()
            .filter { it.currency == currency }
            .sortedByDescending { it.createdAt }
            .take(HISTORY_SAMPLE_SIZE)
        return convertToBase(amount, currency, baseCurrency, rates, sameCurrencyHistory = history)
    }

    /**
     * Simplified "Mark as repaid" (§5.1 debt invariant): creates a repayment
     * transaction on the same account, for the same amount, dated today.
     * A picker for a different account/amount is follow-up work.
     */
    fun markAsRepaid(original: Transaction, onDone: () -> Unit) = safeLaunch {
        val now = java.time.Instant.now().toString()
        val today = java.time.LocalDate.now().toString()
        val repayment = original.copy(
            id = com.stler.money.util.generateId("txn"),
            date = today,
            debtRefId = original.id,
            comment = "Repaid — ${original.comment}".trim(' ', '—'),
            createdAt = now,
            updatedAt = now,
        )
        val withBase = repayment.copy(amountBase = computeAmountBase(repayment.amount, repayment.currency))
        transactionRepository.createTransaction(withBase)
        onDone()
    }

    fun delete(id: String, onDone: () -> Unit) = safeLaunch {
        transactionRepository.deleteTransaction(id)
        onDone()
    }

    private companion object {
        /** Sample size for the same-currency history fallback in [computeAmountBase]. */
        const val HISTORY_SAMPLE_SIZE = 8
    }
}
