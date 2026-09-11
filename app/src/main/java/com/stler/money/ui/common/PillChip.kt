package com.stler.money.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.stler.money.ui.theme.ControlShape
import com.stler.money.ui.theme.selectedChipTextColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The one toggle-pill/trigger-chip component for the whole app — every
 * chip-shaped tap target (filter toggles, date fields, period presets) goes
 * through this, not a one-off `Material3.FilterChip` per screen. That
 * matters for two concrete reasons found while building the Transactions
 * filter sheet: Material3's `FilterChip` reserves a fixed ~48dp touch-target
 * height regardless of visual size (breaks tight capped-height chip-wrap
 * layouts) and its internal horizontal padding isn't adjustable through the
 * public API (four short labels wrapped to two lines). This is a plain
 * clickable `Surface` instead — same `px-3 py-1.5`-equivalent padding as the
 * real PWA's own `FilterPanel.tsx` `<Chip>` — bg tint + colored border +
 * colored text when selected, neutral outline otherwise, rounded-rectangle
 * [ControlShape] rather than Material3's default fully-rounded chip shape.
 */
@Composable
fun PillChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingContent: (@Composable () -> Unit)? = null,
) {
    val contentColor = if (selected) selectedChipTextColor() else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ControlShape,
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
        contentColor = contentColor,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            leadingContent?.invoke()
            Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        }
    }
}

/**
 * A [PillChip] that opens a `DatePickerDialog` and shows the picked date —
 * used for the Transactions filter sheet's From/To, the Analytics balance
 * chart's custom-range From/To, and the transaction form's own date field
 * (that last one non-[clearable]: a transaction always has a date, so there's
 * no empty state to clear back to). [format] defaults to a compact "d MMM yy"
 * (the filter sheet's own style); pass a full-year pattern for fields where
 * the year matters more than density, like the transaction form.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateFieldChip(
    label: String,
    value: String?,
    onChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
    clearable: Boolean = true,
    format: (LocalDate) -> String = { it.format(DateTimeFormatter.ofPattern("d MMM yy", Locale.getDefault())) },
) {
    var showPicker by remember { mutableStateOf(false) }
    PillChip(
        selected = value != null,
        onClick = { showPicker = true },
        label = value?.let { iso -> runCatching { format(LocalDate.parse(iso)) }.getOrDefault(iso) } ?: label,
        modifier = modifier,
        leadingContent = {
            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
            if (clearable && value != null) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "Clear $label",
                    modifier = Modifier.size(14.dp).clickable(onClick = { onChange(null) }),
                )
            }
        },
    )
    if (showPicker) {
        val initialMillis = value?.let {
            runCatching { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
        }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = pickerState) }
    }
}
