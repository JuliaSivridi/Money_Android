package com.stler.money.ui.accounts

import com.stler.money.data.repository.AccountRepository
import com.stler.money.domain.model.Account
import com.stler.money.ui.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Backs [AccountFormSheet] — real `AccountModal.tsx`; no delete (PWA has none, only archive). */
@HiltViewModel
class AccountFormViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
) : BaseViewModel() {

    fun save(account: Account, isEdit: Boolean, onDone: () -> Unit) = safeLaunch {
        if (isEdit) accountRepository.updateAccount(account) else accountRepository.createAccount(account)
        onDone()
    }
}
