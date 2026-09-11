package com.stler.money.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.stler.money.domain.model.Category

@Entity(
    tableName = "categories",
    indices = [Index("sortOrder")],
)
data class CategoryEntity(
    @PrimaryKey val id: String,
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

fun CategoryEntity.toDomain() = Category(
    id = id,
    name = name,
    icon = icon,
    color = color,
    isExpense = isExpense,
    expenseLimit = expenseLimit,
    isIncome = isIncome,
    incomeLimit = incomeLimit,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Category.toEntity() = CategoryEntity(
    id = id,
    name = name,
    icon = icon,
    color = color,
    isExpense = isExpense,
    expenseLimit = expenseLimit,
    isIncome = isIncome,
    incomeLimit = incomeLimit,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
