package com.stler.money.ui.main

import androidx.lifecycle.viewModelScope
import com.stler.money.auth.AuthData
import com.stler.money.auth.GoogleAuthRepository
import com.stler.money.data.repository.ExchangeRateRepository
import com.stler.money.sync.SyncManager
import com.stler.money.sync.SyncState
import com.stler.money.ui.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val syncManager: SyncManager,
    authRepository: GoogleAuthRepository,
    private val exchangeRateRepository: ExchangeRateRepository,
) : BaseViewModel() {

    val syncState = syncManager.syncState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncState.Idle)

    val authData = authRepository.authData
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AuthData())

    init {
        syncManager.triggerSync()
        // ExchangeRateRepository.refresh() was previously only ever called from
        // setBaseCurrency() (Settings). A user who never touches that setting keeps
        // `rates` at whatever DataStore had cached — empty on a fresh install — forever,
        // so every convertToBase() call silently falls back to 1:1. That was the actual
        // cause of two Analytics bugs the user found on-device: the balance-accounts
        // picker showing raw (unconverted) RUB amounts labeled with the EUR symbol, and
        // the Yearly balance line sitting well above its real (partly negative) values,
        // since summing unconverted RUB balances as if they were EUR inflates the total.
        // Tech spec §7 always said "refreshed ... once at startup" — this was the missing
        // wiring for that, not a new decision.
        safeLaunch { exchangeRateRepository.refresh(exchangeRateRepository.baseCurrency.value) }
    }

    fun triggerSync() = syncManager.triggerSync()
}
