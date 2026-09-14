package com.stler.money.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stler.money.domain.model.Account
import com.stler.money.domain.model.Category
import com.stler.money.domain.model.Transaction
import com.stler.money.domain.model.TransactionDraft
import com.stler.money.domain.model.TransactionType
import com.stler.money.ui.icons.CategoryIconView
import com.stler.money.ui.theme.DebtBorrowedColor
import com.stler.money.ui.theme.DebtLentColor
import com.stler.money.ui.theme.IconSizes
import com.stler.money.ui.util.ErrorSnackbarEffect
import com.stler.money.ui.util.ShimmerListPlaceholder
import com.stler.money.ui.theme.expenseTextColor
import com.stler.money.ui.theme.incomeTextColor
import com.stler.money.util.formatAmount
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Date-grouped transaction list — tech spec §8.1, row layout ported from the
 * real `TransactionItem.tsx` (not the tech spec's shorthand description) —
 * see the icon/title/transfer logic below for exactly what that source does.
 * No balance bar: the live PWA (`TransactionList.tsx`) doesn't have one, the
 * tech spec doc's §9.5 mention of one is stale. The full multi-select
 * `FilterMenu` (§8.4a) lives outside this composable — see `MainScreen.kt`,
 * which hosts it alongside the top-bar filter button so both can share this
 * screen's `TransactionsViewModel` instance. Infinite scroll is still
 * follow-up work; the FAB lives in MainScreen's Scaffold, this screen just
 * exposes [onTransactionClick] for row taps (opens edit mode).
 */
/** One row of the merged transactions+drafts list — see `TransactionsScreen`'s `entries`. */
private sealed interface TransactionListEntry {
    val date: String
    data class Real(val txn: Transaction) : TransactionListEntry {
        override val date get() = txn.date
    }
    data class Draft(val draft: TransactionDraft) : TransactionListEntry {
        override val date get() = draft.date
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    onTransactionClick: (Transaction) -> Unit = {},
    onDraftClick: (TransactionDraft) -> Unit = {},
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    ErrorSnackbarEffect(viewModel)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val showDraftsOnly by viewModel.showDraftsOnly.collectAsStateWithLifecycle()
    val suggestedCategory = state.suggestedCategoryId?.let { state.categoriesById[it] }

    val entries: List<TransactionListEntry> =
        (if (showDraftsOnly) emptyList() else state.transactions.map { TransactionListEntry.Real(it) }) +
            state.drafts.map { TransactionListEntry.Draft(it) }
    val grouped = entries.groupBy { it.date }.toSortedMap(compareByDescending { it })

    Column(modifier = Modifier.fillMaxSize()) {
        if (entries.isEmpty() && state.isLoading) {
            ShimmerListPlaceholder(modifier = Modifier.fillMaxSize())
        } else if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.Wallet,
                        contentDescription = null,
                        modifier = Modifier.padding(bottom = 8.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                    )
                    Text(if (showDraftsOnly) "No drafts" else "No transactions yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                grouped.forEach { (date, dayEntries) ->
                    item {
                        // Same size/weight/color as Tasks Android's Upcoming `DayHeader`
                        // (bodyMedium + Medium + onSurface, not the muted labelMedium
                        // used before) — format matches the real PWA's
                        // `useTransactions.ts#formatGroupLabel`: "d MMM · EEEE" for the
                        // current year, "d MMM yyyy · EEEE" otherwise.
                        Text(
                            text = formatDateHeader(date),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items(dayEntries) { entry ->
                        when (entry) {
                            is TransactionListEntry.Real -> TransactionRow(
                                txn = entry.txn,
                                account = state.accountsById[entry.txn.accountId],
                                toAccount = state.accountsById[entry.txn.toAccountId],
                                categories = entry.txn.categoryIds.mapNotNull { state.categoriesById[it] },
                                onClick = { onTransactionClick(entry.txn) },
                            )
                            is TransactionListEntry.Draft -> DraftRow(
                                draft = entry.draft,
                                suggestedCategory = suggestedCategory,
                                onClick = { onDraftClick(entry.draft) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A pending notification-derived draft, mixed into the same list as real transactions
 * (per Julia's request — not a separate screen). Icon/category name are dimmed (there is
 * no confirmed category yet, just a best-guess suggestion); amount and source ("bank")
 * stay full-brightness, same as a real row.
 */
@Composable
private fun DraftRow(draft: TransactionDraft, suggestedCategory: Category?, onClick: () -> Unit) {
    val containerSize = IconSizes.lg + 12.dp
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (suggestedCategory != null) {
            CategoryIconView(
                iconName = suggestedCategory.icon,
                color = (categoryColorOf(suggestedCategory) ?: Color.Gray).copy(alpha = 0.35f),
                size = IconSizes.lg,
            )
        } else {
            Box(
                modifier = Modifier.size(containerSize).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.HourglassEmpty,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                text = suggestedCategory?.name ?: "Uncategorized",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatAmount(draft.amount, draft.currency), style = MaterialTheme.typography.bodyLarge)
            Text(draft.sourceLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TransactionRow(
    txn: Transaction,
    account: Account?,
    toAccount: Account?,
    categories: List<Category>,
    onClick: () -> Unit,
) {
    val isTransfer = txn.type == TransactionType.TRANSFER
    val currency = account?.currency ?: txn.currency

    val title = when {
        isTransfer -> toAccount?.name ?: "?"
        categories.isNotEmpty() -> {
            val names = categories.joinToString(", ") { it.name }
            if (txn.comment.isNotBlank()) "$names · ${txn.comment}" else names
        }
        else -> txn.comment.ifBlank { txn.type.name.lowercase() }
    }

    val accountColor = account?.color
        ?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
        ?: Color.Gray

    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(isTransfer = isTransfer, categories = categories)
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title)
            Text(
                text = buildAnnotatedString {
                    if (txn.time.isNotBlank() && txn.time != "00:00") {
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                            append("${txn.time} · ")
                        }
                    }
                    withStyle(SpanStyle(color = accountColor)) {
                        append(account?.name ?: "")
                    }
                },
                // bodyMedium, not bodySmall — matches the secondary-line size Tasks
                // Android uses everywhere (deadline/label/folder rows in TaskItem).
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        RowAmount(txn, currency)
    }
}

@Composable
private fun RowIcon(isTransfer: Boolean, categories: List<Category>) {
    val containerSize = IconSizes.lg + 12.dp
    when {
        isTransfer -> Box(
            modifier = Modifier.size(containerSize).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            // Matches the PWA's choice of lucide's "Redo2" (a curved bottom-to-top
            // arrow) for transfers — Material's "Redo" is the same curved-arrow shape,
            // not the straight double-headed SwapHoriz glyph used here before.
            Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        categories.size > 1 -> Box(modifier = Modifier.size(containerSize)) {
            // Secondary icon offset behind the primary — matches TransactionItem.tsx's stacked layout.
            categoryColorOf(categories[1])?.let {
                CategoryIconView(
                    iconName = categories[1].icon,
                    color = it,
                    size = IconSizes.md,
                    modifier = Modifier.offset(x = 8.dp, y = 4.dp).alpha(0.7f),
                )
            }
            CategoryIconView(iconName = categories[0].icon, color = categoryColorOf(categories[0]) ?: Color.Gray, size = IconSizes.lg)
        }
        categories.isNotEmpty() -> CategoryIconView(iconName = categories[0].icon, color = categoryColorOf(categories[0]) ?: Color.Gray, size = IconSizes.lg)
        else -> Box(modifier = Modifier.size(containerSize).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

@Composable
private fun RowAmount(txn: Transaction, currency: String) {
    val isTransfer = txn.type == TransactionType.TRANSFER
    val crossCurrency = isTransfer && txn.toCurrency.isNotBlank() && txn.toCurrency != txn.currency
    when {
        isTransfer && crossCurrency -> Column(horizontalAlignment = Alignment.End) {
            Text("+" + formatAmount(txn.toAmount.takeIf { it > 0 } ?: txn.amount, txn.toCurrency), color = incomeTextColor(), style = MaterialTheme.typography.bodyLarge)
            Text("−" + formatAmount(txn.amount, txn.currency), color = expenseTextColor(), style = MaterialTheme.typography.bodySmall)
        }
        isTransfer -> Text(formatAmount(txn.amount, currency), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
        else -> {
            val isIncome = txn.type == TransactionType.INCOME || txn.type == TransactionType.DEBT_BORROWED
            val isExpense = txn.type == TransactionType.EXPENSE || txn.type == TransactionType.DEBT_LENT
            val sign = if (isIncome) "+" else if (isExpense) "−" else ""
            Text(
                text = sign + formatAmount(txn.amount, currency),
                color = when {
                    isIncome -> incomeTextColor()
                    isExpense -> expenseTextColor()
                    else -> MaterialTheme.colorScheme.onSurface
                },
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

private fun categoryColorOf(category: Category): Color? =
    runCatching { Color(android.graphics.Color.parseColor(category.color)) }.getOrNull()

/** Real `useTransactions.ts#formatGroupLabel`: "d MMM · EEEE", year appended only if not this year. */
private fun formatDateHeader(iso: String): String = runCatching {
    val date = LocalDate.parse(iso)
    val datePattern = if (date.year == LocalDate.now().year) "d MMM" else "d MMM yyyy"
    val datePart = date.format(DateTimeFormatter.ofPattern(datePattern, Locale.getDefault()))
    val weekdayPart = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        .replaceFirstChar { it.uppercase() }
    "$datePart · $weekdayPart"
}.getOrDefault(iso)
