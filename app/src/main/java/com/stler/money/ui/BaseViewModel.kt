package com.stler.money.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Base ViewModel providing [safeLaunch] — a drop-in replacement for
 * `viewModelScope.launch` that catches and logs any non-cancellation
 * exception and forwards a user-visible message via [uiError], instead of
 * silently swallowing it. All ViewModels that perform repository mutations
 * extend this class.
 */
abstract class BaseViewModel : ViewModel() {

    /**
     * Single-shot error events for user-visible Snackbar messages.
     * A Channel (not StateFlow) means each error is delivered exactly once
     * even if the UI is briefly off-screen.
     */
    private val _uiError = Channel<String>(Channel.BUFFERED)
    val uiError = _uiError.receiveAsFlow()

    protected fun safeLaunch(block: suspend CoroutineScope.() -> Unit) =
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(
                    this@BaseViewModel::class.simpleName ?: "BaseViewModel",
                    "Unhandled error in safeLaunch",
                    e,
                )
                _uiError.trySend("Something went wrong. Please try again.")
            }
        }
}
