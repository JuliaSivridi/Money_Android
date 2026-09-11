package com.stler.money.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.stler.money.data.repository.convertToBase
import com.stler.money.domain.model.Account
import com.stler.money.domain.model.AccountType
import com.stler.money.domain.model.TransactionType
import com.stler.money.ui.common.DateFieldChip
import com.stler.money.ui.common.PillChip
import com.stler.money.ui.theme.ExpenseColor
import com.stler.money.ui.theme.IncomeColor
import com.stler.money.util.formatAmount
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private enum class YearPeriod(val label: String) {
    THIS_YEAR("This year"), ONE_Y("1Y"), TWO_Y("2Y"), THREE_Y("3Y"), CUSTOM("Custom"),
}

private data class ShowState(val income: Boolean = true, val expenses: Boolean = true, val balance: Boolean = true)

private data class YearRow(val month: YearMonth, val label: String, val income: Double, val negExpense: Double, val balance: Double)

/**
 * Yearly analytics tab — real `YearlyChart.tsx`: year nav, an editable date
 * range, period chips, a bar (income up / expense down) + balance-line combo
 * chart (Vico — the risk this whole phase was flagged for in the dev plan),
 * series toggles, and a "balance accounts" picker.
 *
 * Deliberately dropped rather than ported: the real component lets a bar tap
 * jump to the Monthly tab at that month. Vico's Cartesian API doesn't expose
 * a simple per-datapoint tap the way Recharts does, and the user confirmed
 * she never used that PWA affordance — decided to drop the interaction
 * rather than build a workaround for it (tech spec §8.4 updated to record
 * this as a deliberate decision, not a gap).
 *
 * The chart's Y range is Vico's auto-range across both layers, not the real
 * component's manually computed combined domain — the one remaining
 * simplification, scoped down for risk, not requested.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearlyChartView(
    viewModel: AnalyticsViewModel = hiltViewModel(),
) {
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val baseCurrency by viewModel.baseCurrency.collectAsStateWithLifecycle()
    val rates by viewModel.rates.collectAsStateWithLifecycle()
    val selectedAccountIds by viewModel.balanceAccountIds.collectAsStateWithLifecycle()

    val today = LocalDate.now()
    val currentYear = today.year

    var year by remember { mutableStateOf(currentYear) }
    var periodChip by remember { mutableStateOf(YearPeriod.THIS_YEAR) }
    var dateFrom by remember { mutableStateOf(LocalDate.of(currentYear, 1, 1)) }
    var dateTo by remember { mutableStateOf(today) }
    var show by remember { mutableStateOf(ShowState()) }
    var pickerOpen by remember { mutableStateOf(false) }

    fun selectChip(chip: YearPeriod) {
        periodChip = chip
        val r = yearChipRange(chip, today)
        year = currentYear
        dateFrom = r.first
        dateTo = r.second
    }

    fun goYear(delta: Int) {
        val y = year + delta
        year = y
        periodChip = YearPeriod.CUSTOM
        dateFrom = LocalDate.of(y, 1, 1)
        dateTo = if (y == currentYear) today else LocalDate.of(y, 12, 31)
    }

    fun resetToNow() {
        periodChip = YearPeriod.THIS_YEAR
        year = currentYear
        val r = yearChipRange(YearPeriod.THIS_YEAR, today)
        dateFrom = r.first
        dateTo = r.second
    }

    // Only accounts we can actually convert to baseCurrency (matches real YearlyChart.tsx —
    // without this guard an account in an unconvertible currency would be treated as 1:1).
    val balanceAccountIds = remember(accounts, selectedAccountIds, baseCurrency, rates) {
        val pool = accounts.filter { !it.archived && (it.currency == baseCurrency || rates.containsKey(it.currency)) }
        val eligible = if (selectedAccountIds.isNotEmpty()) pool.filter { it.id in selectedAccountIds } else pool
        eligible.map { it.id }.toSet()
    }

    val currentBalance = remember(accounts, balanceAccountIds, baseCurrency, rates) {
        accounts.filter { it.id in balanceAccountIds }
            .sumOf { convertToBase(it.balance, it.currency, baseCurrency, rates) }
    }

    val monthlyNet = remember(transactions, balanceAccountIds) {
        val net = HashMap<YearMonth, Double>()
        for (t in transactions) {
            if (t.type != TransactionType.INCOME && t.type != TransactionType.EXPENSE) continue
            if (t.accountId !in balanceAccountIds) continue
            val m = runCatching { YearMonth.parse(t.date.take(7)) }.getOrNull() ?: continue
            net[m] = (net[m] ?: 0.0) + (if (t.type == TransactionType.INCOME) t.amountBase else -t.amountBase)
        }
        net
    }

    val data = remember(transactions, dateFrom, dateTo, monthlyNet, currentBalance) {
        val nowMonth = YearMonth.now()
        val fromMonth = YearMonth.from(dateFrom)
        val toMonth = minOf(YearMonth.from(dateTo), nowMonth)
        if (fromMonth > toMonth) return@remember emptyList<YearRow>()

        val multiYear = fromMonth.year != toMonth.year
        val months = generateSequence(fromMonth) { if (it >= toMonth) null else it.plusMonths(1) }.toList()

        var income = 0.0
        var expense = 0.0
        val incomeByMonth = HashMap<YearMonth, Double>()
        val expenseByMonth = HashMap<YearMonth, Double>()
        for (t in transactions) {
            val m = runCatching { YearMonth.parse(t.date.take(7)) }.getOrNull() ?: continue
            if (m < fromMonth || m > toMonth) continue
            when (t.type) {
                TransactionType.INCOME -> incomeByMonth[m] = (incomeByMonth[m] ?: 0.0) + t.amountBase
                TransactionType.EXPENSE -> expenseByMonth[m] = (expenseByMonth[m] ?: 0.0) + t.amountBase
                else -> {}
            }
        }

        val balAt = HashMap<YearMonth, Double>()
        var bal = currentBalance
        var cur = nowMonth
        while (cur >= fromMonth) {
            balAt[cur] = bal
            bal -= (monthlyNet[cur] ?: 0.0)
            cur = cur.minusMonths(1)
        }

        months.map { m ->
            val label = if (multiYear) {
                m.format(DateTimeFormatter.ofPattern("MMM ''yy", Locale.getDefault()))
            } else {
                m.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            }
            YearRow(m, label, incomeByMonth[m] ?: 0.0, -(expenseByMonth[m] ?: 0.0), balAt[m] ?: currentBalance)
        }
    }

    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(data, show) {
        if (data.isEmpty()) return@LaunchedEffect
        modelProducer.runTransaction {
            columnSeries {
                series(x = data.indices.toList(), y = data.map { if (show.income) it.income else 0.0 })
                series(x = data.indices.toList(), y = data.map { if (show.expenses) it.negExpense else 0.0 })
            }
            if (show.balance) {
                lineSeries { series(x = data.indices.toList(), y = data.map { it.balance }) }
            }
        }
    }

    // No Vico HorizontalAxis here — its own X-axis labels duplicated the month
    // strip already rendered below the chart, and computing them was the direct
    // cause of a crash at 2Y/3Y (`CartesianValueFormatter.format` returned an
    // empty string when the axis probed an index past `labels`' bounds). The
    // month strip is the only X-axis label now.
    val columnLayer = rememberColumnCartesianLayer(
        columnProvider = ColumnCartesianLayer.ColumnProvider.series(
            listOf(
                rememberLineComponent(fill(IncomeColor), 8.dp),
                rememberLineComponent(fill(ExpenseColor), 8.dp),
            ),
        ),
        mergeMode = { ColumnCartesianLayer.MergeMode.Grouped() },
    )
    // No separate zero-reference decoration — the grouped columns already have one:
    // income bars rise from 0, expense bars drop from 0, so the gap where they meet
    // *is* the zero line. A second explicit line on top of that just duplicated it.
    val chart = if (show.balance) {
        val lineLayer = rememberLineCartesianLayer(
            lineProvider = LineCartesianLayer.LineProvider.series(
                listOf(LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(MaterialTheme.colorScheme.primary)))),
            ),
        )
        rememberCartesianChart(columnLayer, lineLayer)
    } else {
        rememberCartesianChart(columnLayer)
    }

    val isDefault = periodChip == YearPeriod.THIS_YEAR

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { goYear(-1) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Previous year") }
            Text(year.toString(), style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { goYear(1) }, enabled = year < currentYear) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Next year")
                }
                IconButton(onClick = { pickerOpen = true }) {
                    Icon(
                        Icons.Outlined.Tune,
                        contentDescription = "Balance accounts",
                        tint = if (selectedAccountIds.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DateFieldChip(
                label = "From",
                value = dateFrom.toString(),
                onChange = { it?.let { iso -> dateFrom = LocalDate.parse(iso); periodChip = YearPeriod.CUSTOM } },
                clearable = false,
            )
            DateFieldChip(
                label = "To",
                value = dateTo.toString(),
                onChange = { it?.let { iso -> dateTo = LocalDate.parse(iso); periodChip = YearPeriod.CUSTOM } },
                clearable = false,
            )
            if (!isDefault) {
                IconButton(onClick = { resetToNow() }) {
                    Icon(Icons.Outlined.RestartAlt, contentDescription = "Reset to current period", modifier = Modifier.size(18.dp))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            YearPeriod.entries.filter { it != YearPeriod.CUSTOM }.forEach { chip ->
                PillChip(selected = periodChip == chip, onClick = { selectChip(chip) }, label = chip.label)
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            SeriesToggle("Income", IncomeColor, show.income) { show = show.copy(income = !show.income) }
            SeriesToggle("Expenses", ExpenseColor, show.expenses) { show = show.copy(expenses = !show.expenses) }
            SeriesToggle("Balance", MaterialTheme.colorScheme.primary, show.balance) { show = show.copy(balance = !show.balance) }
        }

        if (data.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                Text("No data for this period", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            // scrollEnabled = false puts Vico in "fit everything, no scroll" mode (Zoom.Content):
            // it scales bars/spacing down as item count grows instead of overflowing the width,
            // matching Recharts' ResponsiveContainer auto-fit — the real fix for "not all months
            // visible at 2Y/3Y", not just a cosmetic thinner bar.
            CartesianChartHost(
                chart = chart,
                modelProducer = modelProducer,
                modifier = Modifier.height(220.dp),
                scrollState = rememberVicoScrollState(scrollEnabled = false),
            )

            // Plain (non-interactive) month labels — replaces Vico's own X-axis, see above.
            // Equal-width cells approximate the auto-fitted bar centers; not pixel-exact against
            // Vico's own edge padding, but close enough to read which label goes with which bar.
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                data.forEach { row ->
                    Text(
                        text = row.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Text(
            "Balance in $baseCurrency" +
                if (selectedAccountIds.isNotEmpty()) " · ${selectedAccountIds.size} account${if (selectedAccountIds.size > 1) "s" else ""}" else "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }

    if (pickerOpen) {
        BalanceAccountsSheet(
            accounts = accounts.filter { !it.archived }.sortedBy { it.sortOrder },
            selectedIds = selectedAccountIds,
            baseCurrency = baseCurrency,
            rates = rates,
            onToggle = { id ->
                val next = when {
                    selectedAccountIds.isEmpty() -> accounts.filter { !it.archived && it.id != id }.map { it.id }.toSet()
                    id in selectedAccountIds -> (selectedAccountIds - id).let { if (it.isEmpty()) emptySet() else it }
                    else -> (selectedAccountIds + id).let { candidate ->
                        if (candidate.size == accounts.count { a -> !a.archived }) emptySet() else candidate
                    }
                }
                viewModel.setBalanceAccountIds(next)
            },
            onSelectAll = { viewModel.setBalanceAccountIds(emptySet()) },
            onDismiss = { pickerOpen = false },
        )
    }
}

@Composable
private fun SeriesToggle(label: String, color: Color, active: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(if (active) color else color.copy(alpha = 0.35f)))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "Balance trend: accounts" popup panel — real `AnalyticsAccountPicker.tsx`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BalanceAccountsSheet(
    accounts: List<Account>,
    selectedIds: Set<String>,
    baseCurrency: String,
    rates: Map<String, Double>,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val allSelected = selectedIds.isEmpty()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(modifier = Modifier.padding(bottom = 8.dp)) {
            Text(
                "Balance trend: accounts",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 2.dp),
            )
            Text(
                "All currencies converted to $baseCurrency",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
            LazyColumn(modifier = Modifier.fillMaxWidth().height(360.dp)) {
                item {
                    ListItem(
                        headlineContent = { Text("All accounts", color = if (allSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                        leadingContent = {
                            if (allSelected) Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        modifier = Modifier.clickable { onSelectAll() },
                    )
                }
                items(accounts, key = { it.id }) { account ->
                    val selected = allSelected || account.id in selectedIds
                    val converted = if (account.currency == baseCurrency) account.balance else convertToBase(account.balance, account.currency, baseCurrency, rates)
                    val color = runCatching { Color(android.graphics.Color.parseColor(account.color)) }.getOrDefault(Color.Gray)
                    ListItem(
                        headlineContent = { Text(account.name) },
                        supportingContent = { Text("${account.type.sheetValue} · ${account.currency}") },
                        leadingContent = {
                            Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                                Icon(accountTypeIcon(account.type), contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        },
                        trailingContent = {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(formatAmount(converted, baseCurrency), style = MaterialTheme.typography.bodyMedium)
                                if (selected) Icon(Icons.Outlined.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                        },
                        modifier = Modifier.clickable { onToggle(account.id) },
                    )
                }
            }
        }
    }
}

private fun accountTypeIcon(type: AccountType) = when (type) {
    AccountType.CASH -> Icons.Outlined.Wallet
    AccountType.CARD -> Icons.Outlined.CreditCard
    AccountType.SAVINGS -> Icons.Outlined.Savings
    AccountType.INVESTMENT -> Icons.AutoMirrored.Outlined.TrendingUp
}

private fun yearChipRange(chip: YearPeriod, today: LocalDate): Pair<LocalDate, LocalDate> = when (chip) {
    YearPeriod.THIS_YEAR -> LocalDate.of(today.year, 1, 1) to today
    YearPeriod.ONE_Y -> today.minusYears(1) to today
    YearPeriod.TWO_Y -> today.minusYears(2) to today
    YearPeriod.THREE_Y -> today.minusYears(3) to today
    YearPeriod.CUSTOM -> today.minusYears(1) to today
}

