package com.stler.money.ui.menu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/** See tech spec §8.5 — bottom-nav "Menu" tab. */
@Composable
fun MenuScreen(
    userName: String,
    userEmail: String,
    userAvatarUrl: String,
    onSettings: () -> Unit,
    onHelp: () -> Unit,
    onFeedback: () -> Unit,
    onAbout: () -> Unit,
    onSignOut: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (userAvatarUrl.isNotBlank()) {
                AsyncImage(
                    model = userAvatarUrl,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                )
            } else {
                Icon(Icons.Outlined.AccountCircle, contentDescription = null, modifier = Modifier.size(72.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(userName, style = MaterialTheme.typography.titleMedium)
                Text(userEmail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.padding(top = 8.dp))
        HorizontalDivider()

        MenuRow(Icons.Outlined.Settings, "Settings", onSettings)
        MenuRow(Icons.AutoMirrored.Outlined.HelpOutline, "Help", onHelp)
        MenuRow(Icons.Outlined.Feedback, "Feedback", onFeedback)
        MenuRow(Icons.Outlined.Info, "About", onAbout)
        MenuRow(Icons.AutoMirrored.Outlined.Logout, "Sign out", onSignOut, tint = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = tint)
        Spacer(modifier = Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}
