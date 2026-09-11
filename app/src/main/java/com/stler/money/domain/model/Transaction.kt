package com.stler.money.domain.model

enum class TransactionType {
    EXPENSE, INCOME, TRANSFER, DEBT_LENT, DEBT_BORROWED;

    val sheetValue: String get() = name.lowercase()

    companion object {
        fun fromSheetValue(value: String): TransactionType = when (value) {
            "income" -> INCOME
            "transfer" -> TRANSFER
            "debt_lent" -> DEBT_LENT
            "debt_borrowed" -> DEBT_BORROWED
            else -> EXPENSE
        }
    }
}

/**
 * See tech spec §5.1. `categoryIds` holds up to 2 entries — index 0 is the
 * *primary* category (used for analytics aggregation), index 1 is a tag only.
 */
data class Transaction(
    val id: String,
    val date: String = "",           // ISO "YYYY-MM-DD"
    val time: String = "",           // "HH:MM", defaults to "00:00" — Sheets column C
    val type: TransactionType = TransactionType.EXPENSE,
    val amount: Double = 0.0,
    val currency: String = "",
    val amountBase: Double = 0.0,
    val accountId: String = "",
    val categoryIds: List<String> = emptyList(),
    val toAccountId: String = "",
    val toAmount: Double = 0.0,
    val toCurrency: String = "",
    val debtRefId: String = "",
    val comment: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
) {
    /** An open debt has no repayment transaction pointing back at it yet. */
    val isOpenDebt: Boolean get() =
        (type == TransactionType.DEBT_LENT || type == TransactionType.DEBT_BORROWED) && debtRefId.isBlank()
}

/** Balance delta for a mutation — see tech spec §16.1 computeBalanceDelta. */
data class BalanceDelta(
    val accountId: String,
    val delta: Double,
    val toAccountId: String? = null,
    val toDelta: Double? = null,
)

fun computeBalanceDelta(t: Transaction): BalanceDelta = when (t.type) {
    TransactionType.EXPENSE -> BalanceDelta(t.accountId, -t.amount)
    TransactionType.INCOME -> BalanceDelta(t.accountId, +t.amount)
    TransactionType.TRANSFER -> BalanceDelta(t.accountId, -t.amount, t.toAccountId, +t.toAmount)
    TransactionType.DEBT_LENT ->
        if (t.debtRefId.isNotBlank()) BalanceDelta(t.accountId, +t.amount)   // repayment received
        else BalanceDelta(t.accountId, -t.amount)                            // lent out
    TransactionType.DEBT_BORROWED ->
        if (t.debtRefId.isNotBlank()) BalanceDelta(t.accountId, -t.amount)   // repayment made
        else BalanceDelta(t.accountId, +t.amount)                            // received
}
