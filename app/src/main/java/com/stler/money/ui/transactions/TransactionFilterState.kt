package com.stler.money.ui.transactions

import com.stler.money.domain.model.Transaction
import com.stler.money.domain.model.TransactionType

/**
 * Mirrors the real PWA's `FilterState` (`store/uiStore.ts`) field-for-field so
 * [matchesFilter] below is a direct port of `useFilteredTransactions` in
 * `hooks/useTransactions.ts`. `search` is deliberately kept separate from this
 * state (own `StateFlow` on the ViewModel), matching the PWA's `searchQuery`.
 */
data class TransactionFilterState(
    val types: Set<TransactionType> = emptySet(),
    val accountIds: Set<String> = emptySet(),
    val categoryIds: Set<String> = emptySet(),
    val dateFrom: String? = null,
    val dateTo: String? = null,
    val amountMin: String = "",
    val amountMax: String = "",
) {
    /** Drives the top-bar filter button's highlighted state — matches web `Header.tsx`'s `hasFilters` (search excluded there too). */
    val isActive: Boolean get() =
        types.isNotEmpty() || accountIds.isNotEmpty() || categoryIds.isNotEmpty() ||
            dateFrom != null || dateTo != null || amountMin.isNotBlank() || amountMax.isNotBlank()
}

fun Transaction.matchesFilter(filter: TransactionFilterState, search: String): Boolean {
    if (filter.accountIds.isNotEmpty() && accountId !in filter.accountIds && toAccountId !in filter.accountIds) return false
    if (filter.types.isNotEmpty() && type !in filter.types) return false
    if (filter.categoryIds.isNotEmpty() && categoryIds.none { it in filter.categoryIds }) return false
    if (filter.dateFrom != null && date < filter.dateFrom) return false
    if (filter.dateTo != null && date > filter.dateTo) return false
    filter.amountMin.toDoubleOrNull()?.let { if (amount < it) return false }
    filter.amountMax.toDoubleOrNull()?.let { if (amount > it) return false }
    val q = search.trim()
    if (q.isNotEmpty() && !comment.contains(q, ignoreCase = true)) return false
    return true
}
