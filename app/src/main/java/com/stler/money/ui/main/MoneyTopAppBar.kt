package com.stler.money.ui.main

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.stler.money.sync.SyncState

/**
 * Sync-status icon in the top bar — see tech spec §7. Matches the Tasks
 * Android sibling's `TasksTopAppBar` exactly: `Outlined` icons (this file
 * had drifted to `Filled`, inconsistent with the rest of the app's icon-set
 * decision — see dev plan Phase 8), one consistent neutral tint across all
 * three states, and a muted transparent-background badge instead of
 * Material3's default loud red/error `Badge` for the pending count.
 */
@Composable
fun SyncStatusIcon(state: SyncState, onClick: () -> Unit) {
    val iconTint = MaterialTheme.colorScheme.onSurfaceVariant
    when (state) {
        is SyncState.Idle -> {
            IconButton(onClick = onClick) {
                Icon(Icons.Outlined.CloudDone, contentDescription = "Synced", tint = iconTint)
            }
        }
        is SyncState.Pending -> {
            IconButton(onClick = onClick) {
                BadgedBox(
                    badge = {
                        Badge(containerColor = Color.Transparent, contentColor = iconTint) {
                            Text("${state.count}", style = MaterialTheme.typography.labelSmall)
                        }
                    },
                ) {
                    Icon(Icons.Outlined.CloudUpload, contentDescription = "Pending sync — ${state.count}", tint = iconTint)
                }
            }
        }
        is SyncState.Syncing -> {
            val transition = rememberInfiniteTransition(label = "sync-spin")
            val angle by transition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "sync-spin-angle",
            )
            IconButton(onClick = onClick) {
                Icon(
                    Icons.Outlined.Sync,
                    contentDescription = "Syncing",
                    modifier = Modifier.size(24.dp).rotate(angle),
                    tint = iconTint,
                )
            }
        }
    }
}

/**
 * Filter entry point — real PWA's `Header.tsx` `SlidersHorizontal` button:
 * filled/primary when [active] (something is actually set), muted outline
 * otherwise. Only rendered when [onClick] is non-null — mirrors the web
 * header's `SEARCHABLE` gate, since only TransactionsScreen supports filtering.
 */
@Composable
private fun FilterButton(active: Boolean, onClick: () -> Unit) {
    if (active) {
        FilledIconButton(onClick = onClick) {
            Icon(Icons.Outlined.Tune, contentDescription = "Filters active")
        }
    } else {
        IconButton(onClick = onClick) {
            Icon(Icons.Outlined.Tune, contentDescription = "Filters", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoneyTopAppBar(
    title: String,
    showBack: Boolean,
    onBack: () -> Unit,
    syncState: SyncState,
    onSyncClick: () -> Unit,
    filterActive: Boolean = false,
    onFilterClick: (() -> Unit)? = null,
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            if (showBack) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                }
            }
        },
        actions = {
            if (onFilterClick != null) {
                FilterButton(active = filterActive, onClick = onFilterClick)
            }
            SyncStatusIcon(state = syncState, onClick = onSyncClick)
        },
    )
}
