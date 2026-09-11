package com.stler.money.data.local.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * Derived join table for `Transaction.categoryIds` — additive, not a
 * redesign of [TransactionEntity]. Written in the same Room `@Transaction`
 * as the owning transaction whenever `categoryIds` changes; never read or
 * written independently. See tech spec §5.6 / §17 item 3.
 */
@Entity(
    tableName = "transaction_categories",
    primaryKeys = ["transactionId", "categoryId"],
    indices = [Index("categoryId")],
)
data class TransactionCategoryCrossRef(
    val transactionId: String,
    val categoryId: String,
    /** 0 = primary category (analytics), 1 = secondary tag only. */
    val position: Int,
)
