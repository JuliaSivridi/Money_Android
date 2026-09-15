package com.stler.money.ui.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stler.money.notifications.NotificationSource

/**
 * Multi-select sheet for which notification sources an Account recognizes — same shell as
 * the Tasks Android sibling's `LabelPickerSheet` (title + "Done" Row, then one `ListItem`
 * per option with a trailing check icon only when selected, whole row toggling a local
 * `Set`), per the user's explicit request to match that pattern rather than a plain
 * checkbox list. Selection only commits via "Done" — [onDismiss] discards it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSourcePickerSheet(
    allSources: List<NotificationSource>,
    selectedPackages: List<String>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(selectedPackages.toSet()) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Notification sources", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { onConfirm(selected.toList()) }) { Text("Done") }
            }

            if (allSources.isEmpty()) {
                Text(
                    "No notification sources available",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                allSources.forEach { source ->
                    val isSelected = source.packageName in selected
                    ListItem(
                        headlineContent = { Text(source.label) },
                        leadingContent = { Icon(Icons.Outlined.NotificationsActive, contentDescription = null) },
                        trailingContent = if (isSelected) {
                            { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(20.dp)) }
                        } else null,
                        modifier = Modifier.clickable {
                            selected = if (isSelected) selected - source.packageName else selected + source.packageName
                        },
                    )
                }
            }
        }
    }
}
