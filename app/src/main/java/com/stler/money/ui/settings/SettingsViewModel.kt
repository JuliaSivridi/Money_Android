package com.stler.money.ui.settings

import androidx.lifecycle.viewModelScope
import com.stler.money.auth.AuthPreferences
import com.stler.money.auth.GoogleAuthRepository
import com.stler.money.data.local.dao.SyncQueueDao
import com.stler.money.data.remote.dto.DriveFile
import com.stler.money.data.repository.AccountRepository
import com.stler.money.data.repository.CategoryRepository
import com.stler.money.data.repository.ExchangeRateRepository
import com.stler.money.data.repository.TransactionRepository
import com.stler.money.sync.SyncManager
import com.stler.money.ui.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** See tech spec §8.6 — Spreadsheet card + base-currency card (no Navigation section: bottom nav only). */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authPreferences: AuthPreferences,
    private val authRepository: GoogleAuthRepository,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val exchangeRateRepository: ExchangeRateRepository,
    private val syncManager: SyncManager,
    private val syncQueueDao: SyncQueueDao,
) : BaseViewModel() {

    val spreadsheetName = authPreferences.spreadsheetName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "db_money")

    val spreadsheetId = authPreferences.spreadsheetId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val baseCurrency: StateFlow<String> = exchangeRateRepository.baseCurrency

    private val _files = MutableStateFlow<List<DriveFile>>(emptyList())
    val files: StateFlow<List<DriveFile>> = _files.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _switching = MutableStateFlow(false)
    val switching: StateFlow<Boolean> = _switching.asStateFlow()

    private val _savingCurrency = MutableStateFlow(false)
    val savingCurrency: StateFlow<Boolean> = _savingCurrency.asStateFlow()

    fun loadUserSheets() = safeLaunch {
        _loading.value = true
        _files.value = authRepository.listUserSheets()
        _loading.value = false
    }

    fun switchSpreadsheet(id: String, name: String) = safeLaunch {
        _switching.value = true
        // Drop pending ops for the OLD spreadsheet before anything else — signOut() does the
        // same (see GoogleAuthRepository.signOut). Without this, any queued INSERT/UPDATE/DELETE
        // survives clearAllLocalData() (that only clears the entity tables, not sync_queue) and
        // gets pushed against the NEW spreadsheetId on the next sync, silently corrupting it with
        // data from the old file. This doesn't fully close the race against an in-flight worker
        // that already read the old queue into memory before this runs — acceptable for a
        // deliberate, rare, user-initiated action, not worth coordinating WorkManager over.
        syncQueueDao.deleteAll()
        authPreferences.setSpreadsheet(id, name)
        transactionRepository.clearAllLocalData()
        accountRepository.clearAllLocalData()
        categoryRepository.clearAllLocalData()
        syncManager.triggerSync()
        _switching.value = false
    }

    fun setBaseCurrency(currency: String) = safeLaunch {
        _savingCurrency.value = true
        exchangeRateRepository.setBaseCurrency(currency)
        _savingCurrency.value = false
    }
}
