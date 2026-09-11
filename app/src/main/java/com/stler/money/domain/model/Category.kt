package com.stler.money.domain.model

/** See tech spec §5.3. `icon` is a Lucide icon name — see §17.2. */
data class Category(
    val id: String,
    val name: String = "",
    val icon: String = "Tag",
    val color: String = "#6b7280",
    val isExpense: Boolean = true,
    val expenseLimit: Double = 0.0,
    val isIncome: Boolean = false,
    val incomeLimit: Double = 0.0,
    val sortOrder: Int = 0,
    val createdAt: String = "",
    val updatedAt: String = "",
)
