package com.stler.money.ui.auth

import android.app.PendingIntent

sealed class AuthUiState {
    /** Initial state while DataStore is read. */
    data object Loading : AuthUiState()

    /** No stored token — show sign-in screen. */
    data object SignedOut : AuthUiState()

    /** User approved scopes via this intent; UI must launch it. */
    data class NeedsAuthorization(val pendingIntent: PendingIntent) : AuthUiState()

    /** Fully authenticated. */
    data class SignedIn(
        val userName: String,
        val userAvatarUrl: String,
        val spreadsheetId: String,
    ) : AuthUiState()

    data class Error(val message: String) : AuthUiState()
}
