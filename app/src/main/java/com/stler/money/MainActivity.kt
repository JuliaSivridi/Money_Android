package com.stler.money

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    // onResume fires after onCreate too. Silent: no snackbar, the drafts list/badge itself
    // is the feedback. Also nudges a rebind (see rescanActiveNotifications) if the listener
    // isn't currently connected, so the *next* open picks up whatever this one missed.
    override fun onResume() {
        super.onResume()
        MoneyNotificationListenerService.rescanActiveNotifications(this)
    }
}
