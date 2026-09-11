package com.stler.money.ui.analytics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stler.money.domain.model.TransactionType
import com.stler.money.ui.common.DateFieldChip
import com.stler.money.ui.common.PillChip
import com.stler.money.ui.theme.ExpenseColor
import com.stler.money.ui.theme.IncomeColor
import com.stler.money.util.formatAmount
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToLong

private enum class TxType { EXPENSE, INCOME }
private enum class PeriodMode(val label: String) {
    MONTH("Month"), THREE_MONTHS("3M"), SIX_MONTHS("6M"), YEAR("Year"),
}

/**
 * Monthly analytics tab — real `MonthlyView.tsx`: month nav, an editable date
 * range (period chips + [DateFieldChip] fields, the same shared `ui/common`
 * chip used by `FilterMenu`/`YearlyChartView`/`TransactionFormSheet`), an
 * Expense/Income toggle with totals, then [CategoryDonut].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyView(
    onCategoryDrillDown: (categoryId: String, dateFrom: String, dateTo: String) -> Unit,
    viewModel: AnalyticsViewModel = hiltViewModel(),
) {
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val baseCurrency by viewModel.baseCurrency.collectAsStateWithLifecycle()

    var month by remember { mutableStateOf(YearMonth.now()) }
    var txType by remember { mutableStateOf(TxType.EXPENSE) }
    var mode by remember { mutableStateOf(PeriodMode.MONTH) }
    var customMode by remember { mutableStateOf(false) }
    var dateFrom by remember { mutableStateOf(month.atDay(1)) }
    var dateTo by remember { mutableStateOf(month.atEndOfMonth()) }

    fun applyRange(m: PeriodMode, anchor: YearMonth) {
        val r = periodRange(m, anchor)
        dateFrom = r.first
        dateTo = r.second
    }

    fun goMonth(delta: Long) {
        month = month.plusMonths(delta)
        applyRange(mode, month)
        customMode = false
    }

    fun selectChip(m: PeriodMode) {
        mode = m
        applyRange(m, month)
        customMode = false
    }

    val monthCount = (ChronoUnit.MONTHS.between(YearMonth.from(dateFrom), YearMonth.from(dateTo)) + 1)
        .coerceAtLeast(1)
    val isAverage = monthCount > 1

    var expenseTotal = 0.0
    var incomeTotal = 0.0
    val fromStr = dateFrom.toString()
    val toStr = dateTo.toString()
    for (t in transactions) {
        if (t.type != TransactionType.EXPENSE && t.type != TransactionType.INCOME) continue
        if (t.date < fromStr || t.date > toStr) continue
        if (t.type == TransactionType.EXPENSE) expenseTotal += t.amountBase else incomeTotal += t.amountBase
    }
    val displayExpense = if (isAverage) expenseTotal / monthCount else expenseTotal
    val displayIncome = if (isAverage) incomeTotal / monthCount else incomeTotal

    val today = LocalDate.now()
    val currentMonth = YearMonth.now()
    val todayFraction = if (!customMode && mode == PeriodMode.MONTH && month == currentMonth) {
        today.dayOfMonth / currentMonth.lengthOfMonth().toFloat()
    } else null

    val donutLabel = periodLabel(dateFrom, dateTo, isAverage)
    val defaultRange = periodRange(PeriodMode.MONTH, currentMonth)
    val isDefaultPeriod = dateFrom == defaultRange.first && dateTo == defaultRange.second

    fun resetToNow() {
        month = currentMonth
        mode = PeriodMode.MONTH
        applyRange(PeriodMode.MONTH, month)
        customMode = false
    }

    val activeType = if (txType == TxType.EXPENSE) TransactionType.EXPENSE else TransactionType.INCOME
    val byCategory = HashMap<String, Double>()
    for (t in transactions) {
        if (t.type != activeType) continue
        if (t.date < fromStr || t.date > toStr) continue
        val primaryId = t.categoryIds.firstOrNull() ?: continue
        byCategory[primaryId] = (byCategory[primaryId] ?: 0.0) + t.amountBase
    }
    val slices = categories
        .filter { byCategory.containsKey(it.id) }
        .sortedBy { it.sortOrder }
        .map { c ->
            val raw = byCategory.getValue(c.id)
            DonutSlice(
                categoryId = c.id,
                name = c.name,
                icon = c.icon,
                color = runCatching { Color(android.graphics.Color.parseColor(c.color)) }.getOrDefault(Color.Gray),
                amount = if (isAverage) raw / monthCount else raw,
                limit = if (txType == TxType.EXPENSE) c.expenseLimit else 0.0,
            )
        }
    val donutTotal = slices.sumOf { it.amount }

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { goMonth(-1) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Previous month") }
            Text(monthYearLabel(month), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { goMonth(1) }, enabled = month.plusMonths(1) <= currentMonth) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Next month")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DateFieldChip(
                label = "From",
                value = dateFrom.toString(),
                onChange = { it?.let { iso -> dateFrom = LocalDate.parse(iso); customMode = true } },
                clearable = false,
            )
            DateFieldChip(
                label = "To",
                value = dateTo.toString(),
                onChange = { it?.let { iso -> dateTo = LocalDate.parse(iso); customMode = true } },
                clearable = false,
            )
            if (!isDefaultPeriod) {
                IconButton(onClick = { resetToNow() }) {
                    Icon(Icons.Outlined.RestartAlt, contentDescription = "Reset to current month", modifier = Modifier.size(18.dp))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PeriodMode.entries.forEach { m ->
                PillChip(selected = !customMode && mode == m, onClick = { selectChip(m) }, label = m.label)
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            TxTypeButton(
                label = "Expenses",
                amount = displayExpense,
                suffix = if (isAverage) "/mo" else "",
                color = ExpenseColor,
                selected = txType == TxType.EXPENSE,
                baseCurrency = baseCurrency,
                onClick = { txType = TxType.EXPENSE },
                modifier = Modifier.weight(1f),
            )
            TxTypeButton(
                label = "Income",
                amount = displayIncome,
                suffix = if (isAverage) "/mo" else "",
                color = IncomeColor,
                selected = txType == TxType.INCOME,
                baseCurrency = baseCurrency,
                onClick = { txType = TxType.INCOME },
                modifier = Modifier.weight(1f),
            )
        }

        CategoryDonut(
            slices = slices,
            total = donutTotal,
            baseCurrency = baseCurrency,
            periodLabel = donutLabel,
            todayFraction = todayFraction,
            onCategoryClick = { categoryId -> onCategoryDrillDown(categoryId, fromStr, toStr) },
            modifier = Modifier.padding(top = 12.dp),
        )
    }

}

/** Full-width Expenses/Income toggle button with an inline total — real `MonthlyView.tsx`. */
@Composable
private fun TxTypeButton(
    label: String,
    amount: Double,
    suffix: String,
    color: Color,
    selected: Boolean,
    baseCurrency: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = formatAmount(amount, baseCurrency) + suffix,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(
            thickness = 2.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        )
    }
}

private fun periodRange(mode: PeriodMode, anchor: YearMonth): Pair<LocalDate, LocalDate> = when (mode) {
    PeriodMode.MONTH -> anchor.atDay(1) to anchor.atEndOfMonth()
    PeriodMode.THREE_MONTHS -> anchor.minusMonths(2).atDay(1) to anchor.atEndOfMonth()
    PeriodMode.SIX_MONTHS -> anchor.minusMonths(5).atDay(1) to anchor.atEndOfMonth()
    PeriodMode.YEAR -> anchor.minusMonths(11).atDay(1) to anchor.atEndOfMonth()
}

/** "avg /3M", "1Y", "5D" — real `MonthlyView.tsx` `periodLabel()`. */
private fun periodLabel(from: LocalDate, to: LocalDate, isAverage: Boolean): String {
    val days = ChronoUnit.DAYS.between(from, to) + 1
    val abbr = when {
        days <= 6 -> "${days}D"
        days <= 27 -> "${(days / 7.0).roundToLong()}W"
        days < 360 -> "${(days / 30.0).roundToLong()}M"
        else -> "${(days / 365.0).roundToLong()}Y"
    }
    return if (isAverage) "avg /$abbr" else abbr
}

private fun monthYearLabel(month: YearMonth): String =
    "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}"
