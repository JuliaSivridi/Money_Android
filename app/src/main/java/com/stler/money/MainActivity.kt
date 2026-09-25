package com.stler.money

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stler.money.notifications.MoneyNotificationListenerService
import com.stler.money.ui.auth.AuthScreen
import com.stler.money.ui.auth.AuthUiState
import com.stler.money.ui.auth.AuthViewModel
import com.stler.money.ui.main.MainScreen
import com.stler.money.ui.theme.MoneyTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Auth gate: shows [AuthScreen] or [MainScreen] depending on sign-in state —
 * see tech spec §8 AuthScreen / §12 Navigation. No locale override or deep
 * link handling yet (Money has no Calendar/DatePicker week-start dependency
 * that required one in the Tasks sibling).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MoneyTheme {
                val authViewModel: AuthViewModel = hiltViewModel()
                val authState by authViewModel.uiState.collectAsStateWithLifecycle()

                when (authState) {
                    is AuthUiState.Loading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }

                    is AuthUiState.SignedIn -> {
                        MainScreen(onSignOut = authViewModel::signOut)
                    }

                    else -> {
                        AuthScreen(viewModel = authViewModel)
                    }
                }
            }
        }
    }

    // Covers both the case that mattered to the user (pay in a store with no way to watch
    // Logcat live, open the app afterward to add the transaction) and a fresh cold start —
    // onResume fires after onCreate too. If the listener isn't connected, this also nudges a
    // rebind (see rescanActiveNotifications) — but a rebind is asynchronous, so *this* open
    // still can't have scanned anything. MIUI in particular can drop the binding again and
    // again with no in-app signal otherwise (the only prior feedback was Settings or Logcat,
    // neither of which she can check at the register) — a Toast here is the visible signal
    // that something needs attention, shown only when access is actually granted (an
    // unconnected listener because she never turned it on isn't worth nagging about).
    override fun onResume() {
        super.onResume()
        val connected = MoneyNotificationListenerService.rescanActiveNotifications(this)
        if (!connected && NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)) {
            Toast.makeText(
                this,
                "Notification access was dropped by the system — reconnecting… check Settings if this keeps happening",
                Toast.LENGTH_LONG,
            ).show()
        }
    }
}
