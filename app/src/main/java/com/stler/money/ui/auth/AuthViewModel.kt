package com.stler.money.ui.auth

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stler.money.auth.GoogleAuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: GoogleAuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Loading)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        // One-shot check on startup — no DataStore Flow observer to avoid race conditions.
        viewModelScope.launch {
            val isSignedIn = authRepository.isSignedIn.first()
            _uiState.value = if (isSignedIn) {
                val data = authRepository.getAuthData()
                AuthUiState.SignedIn(
                    userName      = data.userName,
                    userAvatarUrl = data.userAvatarUrl,
                    spreadsheetId = data.spreadsheetId,
                )
            } else {
                AuthUiState.SignedOut
            }
        }
    }

    /** Start the sign-in flow. [context] must be an Activity context. */
    fun startSignIn(context: Context) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            authRepository.signIn(context).fold(
                onSuccess = { step ->
                    when (step) {
                        is GoogleAuthRepository.SignInStep.NeedsAuthorization ->
                            _uiState.value = AuthUiState.NeedsAuthorization(step.pendingIntent)

                        GoogleAuthRepository.SignInStep.Success -> {
                            val data = authRepository.getAuthData()
                            _uiState.value = AuthUiState.SignedIn(
                                userName      = data.userName,
                                userAvatarUrl = data.userAvatarUrl,
                                spreadsheetId = data.spreadsheetId,
                            )
                        }
                    }
                },
                onFailure = { e ->
                    _uiState.value = AuthUiState.Error(e.message ?: "Sign in failed")
                }
            )
        }
    }

    /** Called after the user approves scopes via the authorization intent. */
    fun finalizeAuth(intent: Intent) {
        viewModelScope.launch {
            authRepository.finalizeAuth(intent).fold(
                onSuccess = {
                    val data = authRepository.getAuthData()
                    _uiState.value = AuthUiState.SignedIn(
                        userName      = data.userName,
                        userAvatarUrl = data.userAvatarUrl,
                        spreadsheetId = data.spreadsheetId,
                    )
                },
                onFailure = { e ->
                    _uiState.value = AuthUiState.Error(e.message ?: "Authorization failed")
                }
            )
        }
    }

    fun clearError() {
        if (_uiState.value is AuthUiState.Error) _uiState.value = AuthUiState.SignedOut
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _uiState.value = AuthUiState.SignedOut
        }
    }
}
