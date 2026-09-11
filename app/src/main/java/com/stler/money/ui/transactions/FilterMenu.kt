package com.stler.money.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.stler.money.domain.model.Account
import com.stler.money.domain.model.Category
import com.stler.money.domain.model.TransactionType
import com.stler.money.ui.common.DateFieldChip
import com.stler.money.ui.common.PillChip
import com.stler.money.ui.icons.CategoryIconView
import java.time.LocalDate

private val TYPE_OPTIONS = listOf(
    TransactionType.EXPENSE to "Expense",
    TransactionType.INCOME to "Income",
    TransactionType.TRANSFER to "Transfer",
)

/**
 * Real PWA's `FilterPanel.tsx` ported to a `ModalBottomSheet`: pinned "Clear
 * all" (only when something is set), a search field right after it (an
 * Android-only placement — on web search lives in the header, not the
 * panel), then Type/Accounts/Categories/Date range/Amount range sections.
 * Every toggle applies immediately (no separate Apply button — the real
 * panel doesn't have one either, `setFilter` fires straight from each chip).
 * Accounts and Categories cap their chip wrap at ~3 rows with internal
 * scroll, matching the web panel's `max-h-36 overflow-y-auto` — without it
 * a long list pushes every section below off screen. Chips are the shared
 * [PillChip]/[DateFieldChip] from `ui/common` — also used by the transaction
 * form's own date field and the Analytics balance chart's date range, so
 * every chip-shaped control in the app looks and behaves identically.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterMenu(
    filterState: TransactionFilterState,
    search: String,
    accounts: List<Account>,
    categories: List<Category>,
    onToggleType: (TransactionType) -> Unit,
    onToggleDebt: () -> Unit,
    onToggleAccount: (String) -> Unit,
    onToggleCategory: (String) -> Unit,
    onDateFromChange: (String?) -> Unit,
    onDateToChange: (String?) -> Unit,
    onAmountMinChange: (String) -> Unit,
    onAmountMaxChange: (String) -> Unit,
    onSearchChange: (String) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isDebtActive = TransactionType.DEBT_LENT in filterState.types || TransactionType.DEBT_BORROWED in filterState.types
    val hasAnything = filterState.isActive || search.isNotBlank()

    // Selected-first ordering, captured once when the sheet is composed (i.e. each time it's
    // opened — MainScreen only composes FilterMenu while showFilterMenu is true, so this
    // `remember` resets on every fresh open) rather than recomputed live on every toggle.
    // Otherwise a chip the user just tapped would jump to the front mid-session, which reads as
    // the list rearranging itself under their thumb — the point is only to make an
    // already-selected chip (arrived pre-filtered from Accounts/Categories/Analytics) visible
    // without scrolling the NEXT time the sheet opens, not to reorder while it's open.
    val initialSelectedAccounts = remember { filterState.accountIds }
    val initialSelectedCategories = remember { filterState.categoryIds }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        if (hasAnything) {
            TextButton(
                onClick = { onClearAll() },
                modifier = Modifier.padding(start = 12.dp),
            ) {
                Text("Clear all filters", color = MaterialTheme.colorScheme.error)
            }
        }

        OutlinedTextField(
            value = search,
            onValueChange = onSearchChange,
            placeholder = { Text("Search by comment…") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            FilterSection(title = "Type") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TYPE_OPTIONS.forEach { (type, label) ->
                        PillChip(selected = type in filterState.types, onClick = { onToggleType(type) }, label = label)
                    }
                    PillChip(selected = isDebtActive, onClick = onToggleDebt, label = "Debt")
                }
            }

            FilterSection(title = "Accounts") {
                Column(modifier = Modifier.heightIn(max = 156.dp).verticalScroll(rememberScrollState())) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        accounts.filter { !it.archived }
                            .sortedWith(compareByDescending<Account> { it.id in initialSelectedAccounts }.thenBy { it.sortOrder })
                            .forEach { account ->
                            val color = runCatching { Color(android.graphics.Color.parseColor(account.color)) }.getOrDefault(Color.Gray)
                            PillChip(
                                selected = account.id in filterState.accountIds,
                                onClick = { onToggleAccount(account.id) },
                                label = account.name,
                                leadingContent = { Box(Modifier.size(8.dp).clip(CircleShape).background(color)) },
                            )
                        }
                    }
                }
            }

            FilterSection(title = "Categories") {
                Column(modifier = Modifier.heightIn(max = 156.dp).verticalScroll(rememberScrollState())) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        categories
                            .sortedWith(compareByDescending<Category> { it.id in initialSelectedCategories }.thenBy { it.sortOrder })
                            .forEach { category ->
                            val color = runCatching { Color(android.graphics.Color.parseColor(category.color)) }.getOrDefault(Color.Gray)
                            PillChip(
                                selected = category.id in filterState.categoryIds,
                                onClick = { onToggleCategory(category.id) },
                                label = category.name,
                                leadingContent = { CategoryIconView(iconName = category.icon, color = color, size = 10.dp) },
                            )
                        }
                    }
                }
            }

            FilterSection(title = "Date range") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DateFieldChip(label = "From", value = filterState.dateFrom, onChange = onDateFromChange)
                        DateFieldChip(label = "To", value = filterState.dateTo, onChange = onDateToChange)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("This month" to 0L, "Last month" to -1L).forEach { (label, offset) ->
                            val (from, to) = monthRange(offset)
                            val active = filterState.dateFrom == from && filterState.dateTo == to
                            PillChip(
                                selected = active,
                                onClick = {
                                    if (active) {
                                        onDateFromChange(null); onDateToChange(null)
                                    } else {
                                        onDateFromChange(from); onDateToChange(to)
                                    }
                                },
                                label = label,
                            )
                        }
                    }
                }
            }

            FilterSection(title = "Amount range") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = filterState.amountMin,
                        onValueChange = onAmountMinChange,
                        placeholder = { Text("Min") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = filterState.amountMax,
                        onValueChange = onAmountMaxChange,
                        placeholder = { Text("Max") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        content()
    }
}

/** Matches `FilterPanel.tsx`'s `getMonthRange`: [offset] `0` = this month, `-1` = last month. */
private fun monthRange(offsetMonths: Long): Pair<String, String> {
    val month = LocalDate.now().plusMonths(offsetMonths)
    return month.withDayOfMonth(1).toString() to month.withDayOfMonth(month.lengthOfMonth()).toString()
}
