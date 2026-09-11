package com.stler.money.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.stler.money.domain.model.Account
import com.stler.money.domain.model.AccountType
import com.stler.money.ui.common.ColorPickerGrid
import com.stler.money.ui.common.SimpleDropdownField
import com.stler.money.ui.theme.DEFAULT_ENTITY_COLOR
import com.stler.money.ui.util.ErrorSnackbarEffect
import java.time.Instant

private val CURRENCIES = listOf("EUR", "USD", "RUB")

/** Create/edit account — real `AccountModal.tsx`. No delete (PWA has none — only the Archive checkbox). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountFormSheet(
    account: Account?,
    onDismiss: () -> Unit,
    viewModel: AccountFormViewModel = hiltViewModel(),
) {
    ErrorSnackbarEffect(viewModel)
    val isEdit = account != null

    var name by remember(account) { mutableStateOf(account?.name ?: "") }
    var currency by remember(account) { mutableStateOf(account?.currency ?: "EUR") }
    var type by remember(account) { mutableStateOf(account?.type ?: AccountType.CASH) }
    var color by remember(account) { mutableStateOf(account?.color ?: DEFAULT_ENTITY_COLOR) }
    var balanceStr by remember(account) { mutableStateOf(formatForInput(account?.balance ?: 0.0)) }
    var archived by remember(account) { mutableStateOf(account?.archived ?: false) }
    var showColorPicker by remember { mutableStateOf(false) }

    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { nameFocus.requestFocus() }

    val parsedColor = runCatching { Color(android.graphics.Color.parseColor(color)) }.getOrDefault(Color.Gray)
    val canSave = name.isNotBlank()

    fun buildResult(): Account {
        val now = Instant.now().toString()
        return Account(
            id = account?.id ?: com.stler.money.util.generateId("acc"),
            name = name.trim(),
            currency = currency,
            type = type,
            color = color,
            balance = balanceStr.toDoubleOrNull() ?: 0.0,
            archived = archived,
            sortOrder = account?.sortOrder ?: 0,
            createdAt = account?.createdAt ?: now,
            updatedAt = now,
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (isEdit) "Edit account" else "New account", style = MaterialTheme.typography.titleMedium)

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // 48dp clickable area (≥ accessibility touch-target minimum) around the same
                // 36dp visual swatch, rather than enlarging the swatch itself.
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { showColorPicker = !showColorPicker },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(parsedColor)
                            .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Account name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).focusRequester(nameFocus),
                )
            }

            if (showColorPicker) {
                ColorPickerGrid(value = color, onChange = { color = it; showColorPicker = false })
            }

            SimpleDropdownField(
                label = "Currency",
                options = CURRENCIES,
                selected = currency,
                optionLabel = { it },
                onSelect = { currency = it },
                modifier = Modifier.fillMaxWidth(),
            )

            SimpleDropdownField(
                label = "Type",
                options = AccountType.entries,
                selected = type,
                optionLabel = { typeLabel(it) },
                onSelect = { type = it },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = balanceStr,
                onValueChange = { balanceStr = it },
                label = { Text(if (isEdit) "Current balance" else "Opening balance") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            if (isEdit) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = archived, onCheckedChange = { archived = it })
                    Text("Archive account")
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.size(8.dp))
                TextButton(
                    enabled = canSave,
                    onClick = { viewModel.save(buildResult(), isEdit, onDismiss) },
                ) { Text("Save") }
            }
        }
    }
}

private fun typeLabel(type: AccountType): String = when (type) {
    AccountType.CARD -> "Card"
    AccountType.CASH -> "Cash"
    AccountType.SAVINGS -> "Savings"
    AccountType.INVESTMENT -> "Investment"
}

private fun formatForInput(amount: Double): String =
    if (amount == amount.toLong().toDouble()) amount.toLong().toString() else amount.toString()
