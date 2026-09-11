package com.stler.money.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.stler.money.domain.model.Transaction
import com.stler.money.domain.model.TransactionType

/**
 * `categoryIds` (comma-separated) is the source of truth — it round-trips to
 * Sheets column I verbatim. The `transaction_categories` join table
 * ([TransactionCategoryCrossRef]) is a derived index maintained alongside it
 * by the repository for fast category-filtered queries — see tech spec §5.6
 * / §17 item 3. Never write one without the other.
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index("date"),
        Index("type"),
        Index("accountId"),
        Index("debtRefId"),
        Index("updatedAt"),
    ],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val date: String = "",
    val time: String = "",
    val type: String = "expense",
    val amount: Double = 0.0,
    val currency: String = "",
    val amountBase: Double = 0.0,
    val accountId: String = "",
    val categoryIds: String = "",
    val toAccountId: String = "",
    val toAmount: Double = 0.0,
    val toCurrency: String = "",
    val debtRefId: String = "",
    val comment: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
)

fun TransactionEntity.toDomain() = Transaction(
    id = id,
    date = date,
    time = time,
    type = TransactionType.fromSheetValue(type),
    amount = amount,
    currency = currency,
    amountBase = amountBase,
    accountId = accountId,
    categoryIds = if (categoryIds.isBlank()) emptyList() else categoryIds.split(",").map { it.trim() }.filter { it.isNotEmpty() },
    toAccountId = toAccountId,
    toAmount = toAmount,
    toCurrency = toCurrency,
    debtRefId = debtRefId,
    comment = comment,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Transaction.toEntity() = TransactionEntity(
    id = id,
    date = date,
    time = time,
    type = type.sheetValue,
    amount = amount,
    currency = currency,
    amountBase = amountBase,
    accountId = accountId,
    categoryIds = categoryIds.joinToString(","),
    toAccountId = toAccountId,
    toAmount = toAmount,
    toCurrency = toCurrency,
    debtRefId = debtRefId,
    comment = comment,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
