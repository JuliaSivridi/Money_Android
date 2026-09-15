package com.stler.money.domain.model

/**
 * A locally-captured, unconfirmed transaction suggestion parsed from a bank/payment
 * notification (see notifications/) — never synced to Sheets. Confirming one creates a
 * real [Transaction] through the normal path; the draft is then deleted.
 */
data class TransactionDraft(
    val id: String,
    val sourceLabel: String,
    val amount: Double,
    val currency: String,
    val date: String, // ISO "YYYY-MM-DD"
    /** Resolved Account id, or blank if none/ambiguous — see TransactionDraftRepository. */
    val accountId: String = "",
)

/** Prefill for [TransactionFormSheet] when opening a draft — merchant is never captured,
 * and comment is left blank (not the source label) per the user's explicit request. */
fun TransactionDraft.toPrefillTransaction(suggestedCategoryId: String?) = Transaction(
    id = "",
    date = date,
    amount = amount,
    accountId = accountId,
    categoryIds = suggestedCategoryId?.let { listOf(it) } ?: emptyList(),
)
