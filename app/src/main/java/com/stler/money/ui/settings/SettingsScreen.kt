package com.stler.money.ui.settings

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stler.money.notifications.MoneyNotificationListenerService
import com.stler.money.ui.theme.ControlShape
import kotlinx.coroutines.launch

private val CURRENCIES = listOf("EUR", "USD", "RUB")

/**
 * See tech spec §8.6. Physical back returns to the main screen (BackHandler).
 * The Spreadsheet card's inline expandable file list matches the Tasks
 * Android sibling's `SettingsScreen` layout, per the user's request.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    BackHandler(onBack = onNavigateBack)

    val spreadsheetName by viewModel.spreadsheetName.collectAsStateWithLifecycle()
    val spreadsheetId by viewModel.spreadsheetId.collectAsStateWithLifecycle()
    val baseCurrency by viewModel.baseCurrency.collectAsStateWithLifecycle()
    val files by viewModel.files.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val switching by viewModel.switching.collectAsStateWithLifecycle()
    val savingCurrency by viewModel.savingCurrency.collectAsStateWithLifecycle()
    var pickerExpanded by remember { mutableStateOf(false) }

    // Own SnackbarHostState, not the shared LocalSnackbarHostState — this screen renders as a
    // full-screen overlay on top of MainScreen's own Scaffold, which would hide that Scaffold's
    // snackbar host behind it.
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(viewModel) {
        viewModel.uiError.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))

            // ── Spreadsheet ──────────────────────────────────────────────────
            OutlinedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Outlined.TableChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = spreadsheetName.ifBlank { "db_money" },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "Google Sheets data source",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            if (!pickerExpanded) viewModel.loadUserSheets()
                            pickerExpanded = !pickerExpanded
                        },
                        enabled = !switching,
                        shape = ControlShape,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp),
                    ) {
                        if (switching) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("Switching…", style = MaterialTheme.typography.labelMedium)
                        } else {
                            Text(if (pickerExpanded) "Cancel" else "Change", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                AnimatedVisibility(visible = pickerExpanded) {
                    Column {
                        HorizontalDivider()
                        when {
                            loading -> Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text("Loading your sheets…", style = MaterialTheme.typography.bodySmall)
                            }

                            files.isEmpty() -> Text(
                                text = "No Google Sheets found in your Drive.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(16.dp),
                            )

                            else -> Column {
                                files.forEach { file ->
                                    val isActive = file.id == spreadsheetId
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = !switching) {
                                                pickerExpanded = false
                                                viewModel.switchSpreadsheet(file.id, file.name)
                                            }
                                            .background(
                                                if (isActive) MaterialTheme.colorScheme.surfaceVariant
                                                else Color.Transparent,
                                            )
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = file.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (isActive) {
                                            Spacer(Modifier.width(8.dp))
                                            Icon(
                                                Icons.Outlined.Edit,
                                                contentDescription = "Active",
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Base currency ────────────────────────────────────────────────
            OutlinedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("Base currency", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Used for analytics and balance totals.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        CURRENCIES.forEachIndexed { index, currency ->
                            SegmentedButton(
                                selected = currency == baseCurrency,
                                onClick = { viewModel.setBaseCurrency(currency) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = CURRENCIES.size, baseShape = ControlShape),
                                enabled = !savingCurrency,
                            ) { Text(currency) }
                        }
                    }
                    if (savingCurrency) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Text("Updating exchange rates…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Bank notifications ──────────────────────────────────────────────
            val context = LocalContext.current
            var notificationAccessGranted by remember {
                mutableStateOf(NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName))
            }
            // Distinct from the permission above — Android (MIUI especially) can silently
            // drop the live binding without revoking access, so "granted" alone isn't enough
            // to tell her whether drafts are actually being captured right now. See
            // MoneyNotificationListenerService's class doc.
            var listenerConnected by remember { mutableStateOf(MoneyNotificationListenerService.isConnected()) }
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                // The grant/revoke happens in system Settings, outside this screen — recheck
                // whenever the user comes back to it (e.g. after toggling access there, or a
                // rebind MainActivity.onResume already triggered on the way back here).
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        notificationAccessGranted =
                            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
                        listenerConnected = MoneyNotificationListenerService.isConnected()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            OutlinedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(
                            Icons.Outlined.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Access to Android notifications", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                when {
                                    !notificationAccessGranted -> "Suggest transaction drafts from Google Pay or bank notifications"
                                    listenerConnected -> "Access granted — suggesting drafts from Google Pay or banks"
                                    else -> "Access granted, but disconnected right now — the system dropped it. Reconnecting…"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (notificationAccessGranted && !listenerConnected) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                        shape = ControlShape,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (notificationAccessGranted) "Manage access" else "Grant access")
                    }

                    if (notificationAccessGranted) {
                        Spacer(Modifier.height(8.dp))
                        // MainActivity.onResume() already does this automatically on every
                        // app open — this button is the manual fallback for while the app is
                        // already open, or to force a reconnect attempt (see
                        // rescanActiveNotifications) without leaving the app.
                        OutlinedButton(
                            onClick = {
                                val triggered = MoneyNotificationListenerService.rescanActiveNotifications(context)
                                listenerConnected = triggered
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        if (triggered) "Rescanned notifications still in the shade"
                                        else "Reconnecting to notification access — try Rescan again in a moment",
                                    )
                                }
                            },
                            shape = ControlShape,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Rescan current notifications")
                        }
                    }

                    // Rebinding (above) fixes a one-off drop, but on some devices (MIUI in
                    // particular) the system kills the listener repeatedly because of its own
                    // battery/background-app management — these two are best-effort ways to
                    // exempt the app from that, shown only once a drop has actually been seen
                    // so the card stays clean while everything's working. Neither is
                    // guaranteed: standard battery-optimization exemption is a real Android
                    // API, but MIUI's autostart screen has no public API — the intent below is
                    // the widely-used but undocumented activity Xiaomi ships, so it's wrapped
                    // in a try/catch and falls back to the app's own info screen if MIUI ever
                    // changes or removes it.
                    if (notificationAccessGranted && !listenerConnected) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                val powerManager = context.getSystemService(PowerManager::class.java)
                                val intent = if (powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true) {
                                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                } else {
                                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                                }
                                runCatching { context.startActivity(intent) }
                            },
                            shape = ControlShape,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Disable battery restrictions for this app")
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                val autostartIntent = Intent().setComponent(
                                    ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                                )
                                val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                                runCatching { context.startActivity(autostartIntent) }
                                    .onFailure { if (it is ActivityNotFoundException) runCatching { context.startActivity(fallbackIntent) } }
                            },
                            shape = ControlShape,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Open autostart settings (Xiaomi/MIUI)")
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
