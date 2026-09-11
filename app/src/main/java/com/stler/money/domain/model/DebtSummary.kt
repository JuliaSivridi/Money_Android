package com.stler.money.domain.model

enum class DebtDirection { LENT, BORROWED }

/** Computed, not persisted — see tech spec §5.5. */
data class DebtSummary(
    val counterpart: String,
    val direction: DebtDirection,
    val totalAmount: Double,
    val currency: String,
    val transactionIds: List<String>,
)
