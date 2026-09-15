package com.stler.money.ui.accounts

import com.stler.money.data.repository.AccountRepository
import com.stler.money.domain.model.Account
import com.stler.money.notifications.NotificationParserRegistry
import com.stler.money.notifications.NotificationSource
import com.stler.money.ui.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Backs [AccountFormSheet] — real `AccountModal.tsx`; no delete (PWA has none, only archive). */
@HiltViewModel
class AccountFormViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    notificationParserRegistry: NotificationParserRegistry,
) : BaseViewModel() {

    /** Whitelisted notification sources to offer as per-account recognition checkboxes. */
    val availableNotificationSources: List<NotificationSource> = notificationParserRegistry.allSources

    suspend fun loadNotificationSources(accountId: String): Set<String> =
        accountRepository.getNotificationSources(accountId)

    fun save(account: Account, isEdit: Boolean, notificationSources: Set<String>, onDone: () -> Unit) = safeLaunch {
        if (isEdit) accountRepository.updateAccount(account) else accountRepository.createAccount(account)
        accountRepository.setNotificationSources(account.id, notificationSources)
        onDone()
    }
}
