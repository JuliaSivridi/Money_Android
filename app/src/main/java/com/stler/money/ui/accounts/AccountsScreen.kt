package com.stler.money.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stler.money.domain.model.Account
import com.stler.money.domain.model.AccountType
import com.stler.money.domain.model.Transaction
import com.stler.money.domain.model.TransactionType
import com.stler.money.ui.theme.IconSizes
import com.stler.money.ui.theme.expenseTextColor
import com.stler.money.ui.theme.incomeTextColor
import com.stler.money.ui.util.ErrorSnackbarEffect
import com.stler.money.ui.util.ShimmerListPlaceholder
import com.stler.money.util.formatAmount

/**
 * Tech spec §8.2 — fixed section order. Row tap filters Transactions by
 * account (Android-only deviation from the PWA, where the row tap opens
 * edit); the trailing `⋮` menu is the edit entry point instead.
 */
@Composable
fun AccountsScreen(
    onAccountClick: (Account) -> Unit,
    onEditAccount: (Account) -> Unit,
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    ErrorSnackbarEffect(viewModel)
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val openDebts by viewModel.openDebts.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val collapsedGroups by viewModel.collapsedGroups.collectAsStateWithLifecycle()
    var showArchived by remember { mutableStateOf(false) }

    if (accounts.isEmpty() && isLoading) {
        ShimmerListPlaceholder(modifier = Modifier.fillMaxSize())
        return
    }
    if (accounts.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Outlined.CreditCard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                )
                Text("No accounts yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        AccountType.SECTION_ORDER.forEach { type ->
            val inSection = accounts.filter { it.type == type && !it.archived }
            if (inSection.isNotEmpty()) {
                val open = type.name !in collapsedGroups
                item {
                    // Real `AccountsPage.tsx` Section: chevron + type icon + title + count,
                    // tap anywhere on the row toggles collapse — persisted per `AccountsPreferences`.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleGroup(type) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            if (open) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight,
                            contentDescription = if (open) "Collapse" else "Expand",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(
                            iconFor(type),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            // Exact PWA labels (AccountsPage.tsx TYPE_CONFIG) — not just the
                            // capitalized enum name, which produced "Card"/"Investment" instead
                            // of "Cards & Accounts"/"Investments".
                            text = sectionTitle(type),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${inSection.size}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (open) {
                    items(inSection.sortedBy { it.sortOrder }) { account ->
                        AccountRow(account, onClick = { onAccountClick(account) }, onEdit = { onEditAccount(account) })
                    }
                }
            }
        }

        if (openDebts.isNotEmpty()) {
            item {
                Text(
                    text = "Debts",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(openDebts) { debt -> DebtRow(debt) }
        }

        val archived = accounts.filter { it.archived }
        if (archived.isNotEmpty()) {
            item {
                // Simple local show/hide (real AccountsPage.tsx's `showArchived` isn't
                // persisted either — only the per-type section collapse is, via
                // settings!A1, which this doesn't implement) — the actual gap being
                // fixed here is that archived accounts were previously listed by count
                // only and never actually rendered/reachable at all.
                Text(
                    text = if (showArchived) "Hide archived (${archived.size})" else "Show archived (${archived.size})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showArchived = !showArchived }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (showArchived) {
                items(archived.sortedBy { it.sortOrder }) { account ->
                    AccountRow(account, onClick = { onAccountClick(account) }, onEdit = { onEditAccount(account) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountRow(account: Account, onClick: () -> Unit, onEdit: () -> Unit) {
    val color = runCatching { Color(android.graphics.Color.parseColor(account.color)) }.getOrDefault(Color.Gray)
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Solid color background + white icon, 40dp circle / 28dp glyph (ICON_SIZES.lg) —
            // matches Money PWA's AccountRow exactly (was a made-up 20%-tint look before).
            Box(
                modifier = Modifier.size(IconSizes.lg + 12.dp).clip(CircleShape).background(color),
                contentAlignment = Alignment.Center,
            ) {
                Icon(iconFor(account.type), contentDescription = null, tint = Color.White, modifier = Modifier.size(IconSizes.lg))
            }
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(account.name)
                // Currency code under the name — matches Money PWA's AccountRow.
                // bodyMedium, not bodySmall — matches the secondary-line size used
                // throughout Tasks Android (TaskItem's deadline/label/folder rows).
                Text(account.currency, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                // Currency symbol (€/₽/$), not the ISO code — matches Money PWA.
                text = formatAmount(account.balance, account.currency),
                color = if (account.balance < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "Account options")
            }
        }
    }

    if (menuOpen) {
        ModalBottomSheet(
            onDismissRequest = { menuOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                // No Delete here — real AccountModal.tsx has none, only an Archive checkbox inside Edit.
                ListItem(
                    headlineContent = { Text("Edit") },
                    leadingContent = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                    modifier = Modifier.clickable { menuOpen = false; onEdit() },
                )
            }
        }
    }
}

/** Real `AccountsPage.tsx` Debts section: comment (or "Unknown") + "You lent"/"You borrowed". */
@Composable
private fun DebtRow(debt: Transaction) {
    val isLent = debt.type == TransactionType.DEBT_LENT
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(debt.comment.ifBlank { "Unknown" })
            Text(
                if (isLent) "You lent" else "You borrowed",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = formatAmount(debt.amount, debt.currency),
            // Same expense/income color convention as TransactionsScreen: lending money out
            // reads as an expense, having borrowed money in hand reads as income.
            color = if (isLent) expenseTextColor() else incomeTextColor(),
        )
    }
}

private fun sectionTitle(type: AccountType): String = when (type) {
    AccountType.CARD -> "Cards & Accounts"
    AccountType.CASH -> "Cash"
    AccountType.SAVINGS -> "Savings"
    AccountType.INVESTMENT -> "Investments"
}

private fun iconFor(type: AccountType): ImageVector = when (type) {
    AccountType.CASH -> Icons.Outlined.Wallet
    AccountType.CARD -> Icons.Outlined.CreditCard
    AccountType.SAVINGS -> Icons.Outlined.Savings
    AccountType.INVESTMENT -> Icons.AutoMirrored.Outlined.TrendingUp
}
