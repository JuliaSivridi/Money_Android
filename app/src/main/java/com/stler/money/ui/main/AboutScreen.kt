package com.stler.money.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stler.money.BuildConfig
import com.stler.money.ui.theme.ControlShape

/** See tech spec §17 resolved item 4 and §14 (in-app update). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit,
    viewModel: AboutViewModel = hiltViewModel(),
) {
    BackHandler(onBack = onNavigateBack)

    val updateState by viewModel.updateState.collectAsStateWithLifecycle()

    // Hand the APK to the system installer as soon as the download finishes.
    LaunchedEffect(updateState) {
        (updateState as? UpdateState.ReadyToInstall)?.let { viewModel.installApk(it.apkFile) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Version",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = BuildConfig.VERSION_NAME,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            UpdateSection(
                state = updateState,
                onCheck = viewModel::checkForUpdate,
                onDownload = viewModel::downloadUpdate,
                onReset = viewModel::resetState,
            )
        }
    }
}

@Composable
private fun UpdateSection(
    state: UpdateState,
    onCheck: () -> Unit,
    onDownload: (String) -> Unit,
    onReset: () -> Unit,
) {
    when (state) {
        is UpdateState.Idle -> OutlinedButton(onClick = onCheck, shape = ControlShape) {
            Text("Check for updates")
        }

        is UpdateState.Checking -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text("Checking...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        is UpdateState.UpToDate -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "You're on the latest version.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onReset, shape = ControlShape) { Text("Check again") }
        }

        is UpdateState.UpdateAvailable -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Version ${state.version} is available.", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onDownload(state.downloadUrl) }, shape = ControlShape) {
                    Text("Download & Install")
                }
                TextButton(onClick = onReset, shape = ControlShape) { Text("Later") }
            }
        }

        is UpdateState.Downloading -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Downloading... ${state.progress}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth())
        }

        is UpdateState.ReadyToInstall -> Text(
            "Opening installer…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        is UpdateState.Error -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Could not check for updates: ${state.message}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = onReset, shape = ControlShape) { Text("Try again") }
        }
    }
}
