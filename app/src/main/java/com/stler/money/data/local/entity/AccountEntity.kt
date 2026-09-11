package com.stler.money.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.stler.money.domain.model.Account
import com.stler.money.domain.model.AccountType

@Entity(
    tableName = "accounts",
    indices = [Index("type"), Index("archived"), Index("updatedAt")],
)
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String = "",
    val currency: String = "EUR",
    val type: String = "cash",
    val color: String = "#6b7280",
    val balance: Double = 0.0,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: String = "",
    val updatedAt: String = "",
)

fun AccountEntity.toDomain() = Account(
    id = id,
    name = name,
    currency = currency,
    type = AccountType.fromSheetValue(type),
    color = color,
    balance = balance,
    archived = archived,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Account.toEntity() = AccountEntity(
    id = id,
    name = name,
    currency = currency,
    type = type.sheetValue,
    color = color,
    balance = balance,
    archived = archived,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
