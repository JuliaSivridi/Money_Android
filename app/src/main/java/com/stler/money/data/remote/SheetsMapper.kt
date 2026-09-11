package com.stler.money.data.remote

import com.stler.money.data.local.entity.AccountEntity
import com.stler.money.data.local.entity.CategoryEntity
import com.stler.money.data.local.entity.TransactionEntity
import javax.inject.Inject

/**
 * Converts between Google Sheets row arrays and Room entities (bidirectional).
 * Column layout — see tech spec §5.7 (byte-identical to Money PWA's sheet schema,
 * the spreadsheet is shared between both apps).
 *
 * Transaction columns (A–P):
 *   0=id, 1=date, 2=time, 3=type, 4=amount, 5=currency, 6=amount_base,
 *   7=account_id, 8=category_ids, 9=to_account_id, 10=to_amount, 11=to_currency,
 *   12=debt_ref_id, 13=comment, 14=created_at, 15=updated_at
 *
 * Account columns (A–J):
 *   0=id, 1=name, 2=currency, 3=type, 4=balance, 5=archived, 6=sort_order,
 *   7=created_at, 8=updated_at, 9=color
 *
 * Category columns (A–K):
 *   0=id, 1=name, 2=icon, 3=color, 4=is_expense, 5=expense_limit,
 *   6=is_income, 7=income_limit, 8=sort_order, 9=created_at, 10=updated_at
 */
class SheetsMapper @Inject constructor() {

    // ── Transactions ──────────────────────────────────────────────────────

    fun rowToTransaction(row: List<Any?>): TransactionEntity? {
        val id = row.str(0).takeIf { it.isNotBlank() } ?: return null
        return TransactionEntity(
            id = id,
            date = row.dateStr(1),
            time = row.timeStr(2),
            type = row.str(3).ifEmpty { "expense" },
            amount = row.double(4),
            currency = row.str(5),
            amountBase = row.double(6),
            accountId = row.str(7),
            categoryIds = row.str(8),
            toAccountId = row.str(9),
            toAmount = row.double(10),
            toCurrency = row.str(11),
            debtRefId = row.str(12),
            comment = row.str(13),
            createdAt = row.str(14),
            updatedAt = row.str(15),
        )
    }

    fun transactionToRow(t: TransactionEntity): List<Any?> = listOf(
        t.id.text(), t.date.text(), t.time.text(), t.type.text(), t.amount.text(), t.currency.text(),
        t.amountBase.text(), t.accountId.text(), t.categoryIds.text(), t.toAccountId.text(),
        t.toAmount.text(), t.toCurrency.text(), t.debtRefId.text(), t.comment.text(),
        t.createdAt.text(), t.updatedAt.text(),
    )

    // ── Accounts ──────────────────────────────────────────────────────────

    fun rowToAccount(row: List<Any?>): AccountEntity? {
        val id = row.str(0).takeIf { it.isNotBlank() } ?: return null
        return AccountEntity(
            id = id,
            name = row.str(1),
            currency = row.str(2).ifEmpty { "EUR" },
            type = row.str(3).ifEmpty { "cash" },
            balance = row.double(4),
            archived = row.bool(5),
            sortOrder = row.int(6, 0),
            createdAt = row.str(7),
            updatedAt = row.str(8),
            color = row.str(9).ifEmpty { "#6b7280" },
        )
    }

    fun accountToRow(a: AccountEntity): List<Any?> = listOf(
        a.id.text(), a.name.text(), a.currency.text(), a.type.text(), a.balance.text(),
        (if (a.archived) "TRUE" else "FALSE").text(), a.sortOrder.text(),
        a.createdAt.text(), a.updatedAt.text(), a.color.text(),
    )

    // ── Categories ────────────────────────────────────────────────────────

    fun rowToCategory(row: List<Any?>): CategoryEntity? {
        val id = row.str(0).takeIf { it.isNotBlank() } ?: return null
        return CategoryEntity(
            id = id,
            name = row.str(1),
            icon = row.str(2).ifEmpty { "Tag" },
            color = row.str(3).ifEmpty { "#6b7280" },
            // Default TRUE — only an explicit "FALSE" cell is treated as false (spec §5.7 note).
            isExpense = row.boolDefaultTrue(4),
            expenseLimit = row.double(5),
            isIncome = row.bool(6),
            incomeLimit = row.double(7),
            sortOrder = row.int(8, 0),
            createdAt = row.str(9),
            updatedAt = row.str(10),
        )
    }

    fun categoryToRow(c: CategoryEntity): List<Any?> = listOf(
        c.id.text(), c.name.text(), c.icon.text(), c.color.text(),
        (if (c.isExpense) "TRUE" else "FALSE").text(), c.expenseLimit.text(),
        (if (c.isIncome) "TRUE" else "FALSE").text(), c.incomeLimit.text(),
        c.sortOrder.text(), c.createdAt.text(), c.updatedAt.text(),
    )

    // ── Row-number lookup (for UPDATE / DELETE push) ───────────────────────

    /**
     * Returns the 1-based row number (in Sheets notation) for the entity with [id].
     * Skips the header row (index 0 -> row 1 in Sheets, so entity rows start at row 2).
     */
    fun findRowNumber(rows: List<List<Any?>>, id: String): Int? {
        rows.forEachIndexed { index, row ->
            if (index == 0) return@forEachIndexed
            if (row.str(0) == id) return index + 1
        }
        return null
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private fun List<Any?>.str(index: Int): String =
        getOrNull(index)?.toString()?.trim() ?: ""

    /**
     * Reads a date cell at [index] and always returns an ISO "YYYY-MM-DD" string (or "").
     * Mirrors the Tasks Android sibling's dateStr() — Sheets can auto-convert a RAW
     * "2026-01-15" write into a native date cell depending on column formatting;
     * reading it back with UNFORMATTED_VALUE then yields a serial number (days
     * since 1899-12-30) instead of the original string.
     */
    private fun List<Any?>.dateStr(index: Int): String {
        val raw = getOrNull(index) ?: return ""
        if (raw is Number) {
            return try {
                java.time.LocalDate.of(1899, 12, 30)
                    .plusDays(raw.toLong())
                    .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
            } catch (_: Exception) { "" }
        }
        return raw.toString().trim()
    }

    /**
     * Reads a time cell at [index] and always returns an "HH:mm" string (default "00:00").
     * Same root cause as [dateStr]: whether a "22:49"-looking cell round-trips as text or
     * gets auto-converted to a native Sheets time value depends on how it was entered
     * (typed directly into Sheets vs. written by an app, `'`-prefixed or not) — with
     * UNFORMATTED_VALUE, a native time cell comes back as the fractional-day serial
     * (e.g. 0.5166666666666667, not "12:24"), which this converts back.
     */
    private fun List<Any?>.timeStr(index: Int): String {
        val raw = getOrNull(index)
        if (raw is Number) {
            val fractionOfDay = raw.toDouble().let { it - Math.floor(it) }
            val totalMinutes = Math.round(fractionOfDay * 24 * 60) % (24 * 60)
            return "%02d:%02d".format(totalMinutes / 60, totalMinutes % 60)
        }
        return raw?.toString()?.trim().let { if (it.isNullOrEmpty()) "00:00" else it }
    }

    /** Default false — used for is_income (spec §5.7: "default FALSE"). */
    private fun List<Any?>.bool(index: Int): Boolean =
        when (val v = getOrNull(index)) {
            is Boolean -> v
            is String -> v.equals("true", ignoreCase = true)
            else -> false
        }

    /** Default true — used for is_expense (spec §5.7: only an explicit "FALSE" is false). */
    private fun List<Any?>.boolDefaultTrue(index: Int): Boolean =
        when (val v = getOrNull(index)) {
            is Boolean -> v
            is String -> !v.equals("false", ignoreCase = true)
            else -> true
        }

    private fun List<Any?>.int(index: Int, default: Int = 0): Int =
        when (val v = getOrNull(index)) {
            is Number -> v.toInt()
            is String -> v.toIntOrNull() ?: default
            else -> default
        }

    private fun List<Any?>.double(index: Int, default: Double = 0.0): Double =
        when (val v = getOrNull(index)) {
            is Number -> v.toDouble()
            is String -> v.toDoubleOrNull() ?: default
            else -> default
        }

    /** Convert any value to a String for RAW Sheets writes. Null -> "". */
    private fun Any?.text(): String = when {
        this == null -> ""
        this is String -> this
        else -> this.toString()
    }
}
