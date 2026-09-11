package com.stler.money.ui.categories

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stler.money.domain.model.Category
import com.stler.money.ui.common.ColorPickerGrid
import com.stler.money.ui.common.IconPickerGrid
import com.stler.money.ui.common.PickerFieldTrigger
import com.stler.money.ui.icons.CategoryIconView
import com.stler.money.ui.theme.DEFAULT_ENTITY_COLOR
import com.stler.money.ui.util.ErrorSnackbarEffect
import com.stler.money.util.generateId
import java.time.Instant

/** Create/edit category — real `CategoryModal.tsx`, including its delete-with-transfer dialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryFormSheet(
    category: Category?,
    onDismiss: () -> Unit,
    viewModel: CategoryFormViewModel = hiltViewModel(),
) {
    ErrorSnackbarEffect(viewModel)
    val allCategories by viewModel.categories.collectAsStateWithLifecycle()
    val isEdit = category != null

    var name by remember(category) { mutableStateOf(category?.name ?: "") }
    var icon by remember(category) { mutableStateOf(category?.icon ?: "Tag") }
    var color by remember(category) { mutableStateOf(category?.color ?: DEFAULT_ENTITY_COLOR) }
    var isExpense by remember(category) { mutableStateOf(category?.isExpense ?: true) }
    var expenseLimitStr by remember(category) { mutableStateOf(category?.expenseLimit?.takeIf { it > 0 }?.let { formatForInput(it) } ?: "") }
    var isIncome by remember(category) { mutableStateOf(category?.isIncome ?: false) }
    var incomeLimitStr by remember(category) { mutableStateOf(category?.incomeLimit?.takeIf { it > 0 }?.let { formatForInput(it) } ?: "") }
    var showIconPicker by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { nameFocus.requestFocus() }

    val parsedColor = runCatching { Color(android.graphics.Color.parseColor(color)) }.getOrDefault(Color.Gray)
    val canSave = name.isNotBlank()

    fun buildResult(): Category {
        val now = Instant.now().toString()
        return Category(
            id = category?.id ?: generateId("cat"),
            name = name.trim(),
            icon = icon,
            color = color,
            isExpense = isExpense,
            expenseLimit = expenseLimitStr.toDoubleOrNull() ?: 0.0,
            isIncome = isIncome,
            incomeLimit = incomeLimitStr.toDoubleOrNull() ?: 0.0,
            sortOrder = category?.sortOrder ?: 0,
            createdAt = category?.createdAt ?: now,
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
            Text(if (isEdit) "Edit category" else "New category", style = MaterialTheme.typography.titleMedium)

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 48dp clickable areas (≥ accessibility touch-target minimum) around the same
                // visual icon/swatch sizes, rather than enlarging them.
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { showIconPicker = !showIconPicker; showColorPicker = false },
                    contentAlignment = Alignment.Center,
                ) {
                    CategoryIconView(iconName = icon, color = parsedColor, size = 24.dp)
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { showColorPicker = !showColorPicker; showIconPicker = false },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(parsedColor)
                            .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    )
                }
                Text(
                    "Tap to change icon / color",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (showIconPicker) IconPickerGrid(value = icon, onChange = { icon = it; showIconPicker = false })
            if (showColorPicker) ColorPickerGrid(value = color, onChange = { color = it; showColorPicker = false })

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Category name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
            )

            // `label` instead of a separate caption `Text` before the field — matches every
            // other `OutlinedTextField` in the form (name field, comment field elsewhere):
            // this was the one field in the app using an ad-hoc external caption instead of
            // the field's own label, which made it render at a visibly different height.
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Checkbox(checked = isExpense, onCheckedChange = { isExpense = it })
                    Text("Expense")
                }
                if (isExpense) {
                    OutlinedTextField(
                        value = expenseLimitStr,
                        onValueChange = { expenseLimitStr = it },
                        label = { Text("Monthly") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(110.dp),
                    )
                }
            }

            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Checkbox(checked = isIncome, onCheckedChange = { isIncome = it })
                    Text("Income")
                }
                if (isIncome) {
                    OutlinedTextField(
                        value = incomeLimitStr,
                        onValueChange = { incomeLimitStr = it },
                        label = { Text("Monthly") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(110.dp),
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (isEdit) {
                    TextButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.size(4.dp))
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    Spacer(Modifier.size(1.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(
                        enabled = canSave,
                        onClick = { viewModel.save(buildResult(), isEdit, onDismiss) },
                    ) { Text("Save") }
                }
            }
        }
    }

    if (showDeleteDialog && category != null) {
        DeleteCategoryDialog(
            category = category,
            otherCategories = allCategories.filter { it.id != category.id },
            onConfirm = { transferToId ->
                showDeleteDialog = false
                viewModel.deleteWithTransfer(category.id, transferToId, onDismiss)
            },
            onCancel = { showDeleteDialog = false },
        )
    }
}

/** "Move transactions in this category to: [select]" — real `CategoryModal.tsx`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeleteCategoryDialog(
    category: Category,
    otherCategories: List<Category>,
    onConfirm: (transferToId: String) -> Unit,
    onCancel: () -> Unit,
) {
    var transferTo by remember { mutableStateOf("") }
    var showPicker by remember { mutableStateOf(false) }
    val transferToCategory = otherCategories.find { it.id == transferTo }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Delete category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Move transactions in \"${category.name}\" to:", style = MaterialTheme.typography.bodyMedium)
                PickerFieldTrigger(
                    value = transferToCategory?.name ?: "Select category",
                    onClick = { showPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = transferTo.isNotBlank(), onClick = { onConfirm(transferTo) }) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )

    if (showPicker) {
        ModalBottomSheet(
            onDismissRequest = { showPicker = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Text(
                    text = "Move to category",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                otherCategories.forEach { option ->
                    val color = runCatching { Color(android.graphics.Color.parseColor(option.color)) }.getOrDefault(Color.Gray)
                    ListItem(
                        headlineContent = { Text(option.name) },
                        leadingContent = { CategoryIconView(iconName = option.icon, color = color, size = 20.dp) },
                        trailingContent = if (option.id == transferTo) {
                            { Icon(Icons.Filled.Check, contentDescription = "Selected") }
                        } else null,
                        modifier = Modifier.clickable { transferTo = option.id; showPicker = false },
                    )
                }
            }
        }
    }
}

private fun formatForInput(amount: Double): String =
    if (amount == amount.toLong().toDouble()) amount.toLong().toString() else amount.toString()
