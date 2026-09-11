package com.stler.money.ui.transaction

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stler.money.domain.model.Account
import com.stler.money.domain.model.Category
import com.stler.money.domain.model.Transaction
import com.stler.money.domain.model.TransactionType
import com.stler.money.ui.common.DateFieldChip
import com.stler.money.ui.common.PickerFieldTrigger
import com.stler.money.ui.icons.CategoryIconView
import com.stler.money.ui.theme.Accent
import com.stler.money.ui.theme.AccentDark
import com.stler.money.ui.theme.ControlShape
import com.stler.money.ui.util.ErrorSnackbarEffect
import com.stler.money.util.generateId
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TABS = listOf("Expense", "Income", "Transfer", "Debt")

/**
 * Create/edit transaction — tech spec §10, restyled to match the Tasks
 * Android sibling's `TaskFormSheet` idioms (SegmentedButton mode toggle,
 * `FilterChip` date field, bordered category cells) per the user's UI
 * review. The account picker is a `ModalBottomSheet` (`AccountPickerSheet`),
 * matching Tasks Android's `LabelPickerSheet`/`PriorityPickerSheet` — not an
 * `ExposedDropdownMenuBox`, per a later UI pass asking for popup-panel
 * pickers throughout. Uses the native
 * Android keyboard for amount entry — the custom on-screen `NumericKeyboard`
 * existed only to work around the PWA's web-keyboard-covers-the-field
 * problem, which doesn't apply here (Android resizes/pans around the real
 * IME via `imePadding()`), so it's been removed rather than ported.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionFormSheet(
    transaction: Transaction?,
    /** Pre-fills the form as a new (non-edit) transaction — the "Copy" flow below. */
    copyFrom: Transaction? = null,
    initialAccountId: String? = null,
    onDismiss: () -> Unit,
    onCopy: (Transaction) -> Unit = {},
    viewModel: TransactionFormViewModel = hiltViewModel(),
) {
    ErrorSnackbarEffect(viewModel)
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val isEdit = transaction != null
    // Source for initial field values — editing an existing transaction, or
    // prefilling from one via Copy (§10 "Copy" — ported from TransactionModal.tsx,
    // missed in the first pass of this rewrite).
    val source = transaction ?: copyFrom

    // Keyed on (transaction, copyFrom) so switching from "editing X" to "copying X"
    // (same composable instance, sheet stays open) actually resets these fields —
    // a plain unkeyed remember would keep showing X's stale edit-mode values.
    var type by remember(transaction, copyFrom) { mutableStateOf(source?.type ?: TransactionType.EXPENSE) }
    var date by remember(transaction, copyFrom) {
        // Editing keeps the original date; a fresh create or a copy both default to today
        // (copyFrom explicitly does NOT carry over the source's date — matches the PWA).
        mutableStateOf(if (transaction != null) source?.date ?: LocalDate.now().toString() else LocalDate.now().toString())
    }
    var amountStr by remember(transaction, copyFrom) { mutableStateOf(source?.amount?.let { formatForInput(it) } ?: "") }
    var accountId by remember(transaction, copyFrom) { mutableStateOf(source?.accountId ?: initialAccountId ?: "") }
    var categoryIds by remember(transaction, copyFrom) { mutableStateOf(source?.categoryIds ?: emptyList()) }
    var toAccountId by remember(transaction, copyFrom) { mutableStateOf(source?.toAccountId ?: "") }
    var toAmountStr by remember(transaction, copyFrom) { mutableStateOf(source?.toAmount?.takeIf { it > 0 }?.let { formatForInput(it) } ?: "") }
    var comment by remember(transaction, copyFrom) { mutableStateOf(source?.comment ?: "") }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAccountPicker by remember { mutableStateOf(false) }
    var showToAccountPicker by remember { mutableStateOf(false) }

    // Default the account once the list has loaded, if nothing was pre-selected.
    // firstOrNull, not first — a non-empty list where every account happens to be archived
    // (e.g. right after archiving the last active one) threw NoSuchElementException here;
    // falling back to accounts.first() in that case still gives a real, pickable account.
    LaunchedEffect(accounts) {
        if (accountId.isBlank() && accounts.isNotEmpty()) {
            accountId = (accounts.firstOrNull { !it.archived } ?: accounts.first()).id
        }
    }

    // Amount is what nearly every open of this sheet is actually for (account is
    // almost always already right) — focus it immediately so the keyboard is up
    // and ready without an extra tap, same intent as Tasks Android's title-field
    // autofocus in TaskFormSheet.
    val amountFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { amountFocus.requestFocus() }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selectedAccount = accounts.find { it.id == accountId }

    fun toggleCategory(id: String) {
        val idx = categoryIds.indexOf(id)
        categoryIds = when {
            idx == -1 && categoryIds.size < 2 -> categoryIds + id
            idx == -1 -> listOf(categoryIds[0], id)
            else -> categoryIds - id
        }
    }

    fun buildResult(): Transaction {
        val amount = amountStr.toDoubleOrNull() ?: 0.0
        val currency = selectedAccount?.currency ?: "EUR"
        val now = Instant.now().toString()
        val toAccount = accounts.find { it.id == toAccountId }
        return Transaction(
            id = transaction?.id ?: generateId("txn"),
            date = date,
            time = transaction?.time ?: "00:00",
            type = type,
            amount = amount,
            currency = currency,
            amountBase = 0.0, // recomputed in TransactionFormViewModel.save()
            accountId = accountId,
            categoryIds = categoryIds,
            toAccountId = if (type == TransactionType.TRANSFER) toAccountId else "",
            toAmount = if (type == TransactionType.TRANSFER) (toAmountStr.toDoubleOrNull() ?: amount) else 0.0,
            toCurrency = if (type == TransactionType.TRANSFER) (toAccount?.currency ?: currency) else "",
            debtRefId = transaction?.debtRefId ?: "",
            comment = comment,
            createdAt = transaction?.createdAt ?: now,
            updatedAt = now,
        )
    }

    val canSave = accountId.isNotBlank() && (amountStr.toDoubleOrNull() ?: 0.0) > 0.0 &&
        (type != TransactionType.TRANSFER || toAccountId.isNotBlank())

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val tabIndex = when (type) {
                TransactionType.EXPENSE -> 0
                TransactionType.INCOME -> 1
                TransactionType.TRANSFER -> 2
                TransactionType.DEBT_LENT, TransactionType.DEBT_BORROWED -> 3
            }
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                TABS.forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = tabIndex == index,
                        onClick = {
                            type = when (index) {
                                0 -> TransactionType.EXPENSE
                                1 -> TransactionType.INCOME
                                2 -> TransactionType.TRANSFER
                                else -> if (type == TransactionType.DEBT_BORROWED) TransactionType.DEBT_BORROWED else TransactionType.DEBT_LENT
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, TABS.size, baseShape = ControlShape),
                        icon = {}, // no built-in checkmark — the container fill already shows selection, and the reserved icon space was pushing "Expense"/"Transfer" onto 2 lines
                    ) {
                        Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            DateFieldChip(
                label = "Date",
                value = date,
                onChange = { it?.let { date = it } },
                clearable = false,
                format = { it.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())) },
            )

            if (type == TransactionType.DEBT_LENT || type == TransactionType.DEBT_BORROWED) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = type == TransactionType.DEBT_LENT,
                        onClick = { type = TransactionType.DEBT_LENT },
                        shape = SegmentedButtonDefaults.itemShape(0, 2, baseShape = ControlShape),
                        icon = {},
                    ) { Text("I lent", maxLines = 1, style = MaterialTheme.typography.labelMedium) }
                    SegmentedButton(
                        selected = type == TransactionType.DEBT_BORROWED,
                        onClick = { type = TransactionType.DEBT_BORROWED },
                        shape = SegmentedButtonDefaults.itemShape(1, 2, baseShape = ControlShape),
                        icon = {},
                    ) { Text("I borrowed", maxLines = 1, style = MaterialTheme.typography.labelMedium) }
                }
                if (isEdit && transaction!!.debtRefId.isBlank()) {
                    TextButton(onClick = { viewModel.markAsRepaid(transaction, onDismiss) }) {
                        Text("Mark as repaid")
                    }
                }
            }

            if (type == TransactionType.EXPENSE || type == TransactionType.INCOME) {
                val filtered = categories.filter { if (type == TransactionType.EXPENSE) it.isExpense else it.isIncome }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(filtered, key = { it.id }) { category ->
                        CategoryCell(
                            category = category,
                            position = categoryIds.indexOf(category.id).takeIf { it >= 0 },
                            onClick = { toggleCategory(category.id) },
                        )
                    }
                }
            }

            // Bottom-aligned, not center: `AccountFieldTrigger` (PickerFieldTrigger's hand-rolled
            // external-caption anatomy) and `AmountField` (a stock `OutlinedTextField` with its
            // own internal floating label) aren't the same height, so center alignment left them
            // visibly offset — see dev plan for the longer-term fix (unifying both under one field
            // anatomy). Bottom alignment is the safe fix that doesn't depend on getting that
            // height difference pixel-exact.
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccountFieldTrigger(
                    label = if (type == TransactionType.TRANSFER) "From" else "Account",
                    account = selectedAccount,
                    onClick = { showAccountPicker = true },
                    modifier = Modifier.weight(1f),
                )
                AmountField(
                    value = amountStr,
                    onChange = { amountStr = sanitizeAmountInput(it) },
                    currency = selectedAccount?.currency ?: "",
                    focusRequester = amountFocus,
                )
            }

            if (type == TransactionType.TRANSFER) {
                val toAccount = accounts.find { it.id == toAccountId }
                val crossCurrency = selectedAccount != null && toAccount != null && selectedAccount.currency != toAccount.currency
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccountFieldTrigger(
                        label = "To",
                        account = toAccount,
                        onClick = { showToAccountPicker = true },
                        modifier = Modifier.weight(1f),
                    )
                    if (crossCurrency) {
                        AmountField(
                            value = toAmountStr,
                            onChange = { toAmountStr = sanitizeAmountInput(it) },
                            currency = toAccount?.currency ?: "",
                        )
                    }
                }
            }

            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                label = { Text(if (type == TransactionType.DEBT_LENT || type == TransactionType.DEBT_BORROWED) "Person's name" else "Comment") },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (isEdit) {
                    Row {
                        TextButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.size(4.dp))
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                        TextButton(onClick = { onCopy(transaction!!) }) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("Copy")
                        }
                    }
                } else {
                    Spacer(Modifier.size(1.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(
                        enabled = canSave,
                        onClick = { viewModel.save(buildResult(), isEdit, onDismiss) },
                    ) {
                        Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (isEdit) "Save" else "Create")
                    }
                }
            }
        }
    }

    if (showDeleteConfirm && transaction != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete transaction?") },
            text = { Text("This will reverse the balance change.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.delete(transaction.id, onDismiss)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
        )
    }

    if (showAccountPicker) {
        AccountPickerSheet(
            title = if (type == TransactionType.TRANSFER) "From" else "Account",
            accounts = accounts.filter { !it.archived }.sortedBy { it.sortOrder },
            selectedId = accountId,
            onSelect = { accountId = it },
            onDismiss = { showAccountPicker = false },
        )
    }

    if (showToAccountPicker) {
        AccountPickerSheet(
            title = "To",
            accounts = accounts.filter { !it.archived && it.id != accountId }.sortedBy { it.sortOrder },
            selectedId = toAccountId,
            onSelect = { toAccountId = it },
            onDismiss = { showToAccountPicker = false },
        )
    }
}

/**
 * Bordered "button" look, matching PWA's category grid (`border-primary
 * bg-accent` when selected — real source: `TransactionModal.tsx`
 * `categoryGrid`, not the tech spec's shorthand). Two things fixed after the
 * first pass, both against that same real source:
 *  - the cell must fill its full grid-column width with `contentAlignment =
 *    Center` on the outer `Box`; without it, `Box` defaults to `TopStart` and
 *    a `Column` narrower than the icon+text intrinsic width sits pinned to
 *    the left edge instead of centered — that's what read as "text drifts
 *    left" and "badge shifts everything" (it didn't shift anything; the
 *    whole cell was mis-centered to begin with, the badge just made it obvious)
 *  - the position-2 badge is muted GRAY (`bg-muted-foreground/60`), not a
 *    second copy of the primary color — only position 1 (`isPrimary`) is
 *    the accent color badge
 */
@Composable
private fun CategoryCell(category: Category, position: Int?, onClick: () -> Unit) {
    val color = runCatching { Color(android.graphics.Color.parseColor(category.color)) }.getOrDefault(Color.Gray)
    val selected = position != null
    val isPrimary = position == 0
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val borderWidth = if (selected) 2.dp else 1.dp
    val selectedBackground = if (isSystemInDarkTheme()) AccentDark else Accent
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(borderWidth, borderColor, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .then(if (selected) Modifier.background(selectedBackground) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CategoryIconView(iconName = category.icon, color = color, size = 16.dp)
            Text(
                text = category.name,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            )
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${position!! + 1}",
                    style = TextStyle(fontSize = 9.sp, lineHeight = 9.sp, fontWeight = FontWeight.Bold),
                    color = if (isPrimary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.surface,
                )
            }
        }
    }
}

@Composable
private fun AccountFieldTrigger(
    label: String,
    account: Account?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = account?.color?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
    PickerFieldTrigger(
        value = account?.name ?: "Select account",
        onClick = onClick,
        modifier = modifier,
        label = label,
        leading = { Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(color ?: MaterialTheme.colorScheme.outlineVariant)) },
    )
}

/** Popup-panel account picker — same idiom as Tasks Android's `LabelPickerSheet`/`PriorityPickerSheet`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountPickerSheet(
    title: String,
    accounts: List<Account>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (accounts.isEmpty()) {
                Text(
                    text = "No accounts",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                accounts.forEach { account ->
                    val color = runCatching { Color(android.graphics.Color.parseColor(account.color)) }.getOrDefault(Color.Gray)
                    ListItem(
                        headlineContent = { Text(account.name) },
                        supportingContent = { Text(account.currency) },
                        leadingContent = { Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(color)) },
                        trailingContent = if (account.id == selectedId) {
                            { Icon(Icons.Outlined.Check, contentDescription = "Selected") }
                        } else null,
                        modifier = Modifier.clickable { onSelect(account.id); onDismiss() },
                    )
                }
            }
        }
    }
}

@Composable
private fun AmountField(
    value: String,
    onChange: (String) -> Unit,
    currency: String,
    focusRequester: FocusRequester? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(currency) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.width(110.dp).let { if (focusRequester != null) it.focusRequester(focusRequester) else it },
    )
}

/** Same rules as the old NumericKeyboard: digits + at most one '.', max 2 decimals. */
private fun sanitizeAmountInput(raw: String): String {
    val filtered = buildString {
        var seenDot = false
        for (c in raw) {
            if (c.isDigit()) append(c)
            else if (c == '.' && !seenDot) { append(c); seenDot = true }
        }
    }
    val dot = filtered.indexOf('.')
    return if (dot == -1) filtered else filtered.take(dot + 1 + 2)
}

private fun formatForInput(amount: Double): String =
    if (amount == amount.toLong().toDouble()) amount.toLong().toString() else amount.toString()
