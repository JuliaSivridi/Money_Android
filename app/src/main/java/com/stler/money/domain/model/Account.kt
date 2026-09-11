package com.stler.money.domain.model

enum class AccountType {
    CASH, CARD, SAVINGS, INVESTMENT;

    val sheetValue: String get() = name.lowercase()

    companion object {
        /** Fixed section order used by AccountsScreen — tech spec §8.2. */
        val SECTION_ORDER = listOf(CASH, CARD, SAVINGS, INVESTMENT)

        fun fromSheetValue(value: String): AccountType = when (value) {
            "card" -> CARD
            "savings" -> SAVINGS
            "investment" -> INVESTMENT
            else -> CASH
        }
    }
}

/** See tech spec §5.2. `balance` is denormalised — mutate only via AccountRepository.adjustBalance(). */
data class Account(
    val id: String,
    val name: String = "",
    val currency: String = "EUR",
    val type: AccountType = AccountType.CASH,
    val color: String = "#6b7280",
    val balance: Double = 0.0,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: String = "",
    val updatedAt: String = "",
)
