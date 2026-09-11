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
        val withBase = transaction.copy(
            amountBase = convertToBase(
                amount = transaction.amount,
                currency = transaction.currency,
                baseCurrency = exchangeRateRepository.baseCurrency.value,
                rates = exchangeRateRepository.rates.value,
            )
        )
        if (isEdit) transactionRepository.updateTransaction(withBase) else transactionRepository.createTransaction(withBase)
        onDone()
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
        val withBase = repayment.copy(
            amountBase = convertToBase(
                amount = repayment.amount,
                currency = repayment.currency,
                baseCurrency = exchangeRateRepository.baseCurrency.value,
                rates = exchangeRateRepository.rates.value,
            )
        )
        transactionRepository.createTransaction(withBase)
        onDone()
    }

    fun delete(id: String, onDone: () -> Unit) = safeLaunch {
        transactionRepository.deleteTransaction(id)
        onDone()
    }
}
