package com.stler.money.ui.feedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stler.money.auth.AuthPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject

/**
 * See tech spec §8.8. Posts to the same shared Apps Script endpoint the Tasks
 * Android sibling uses, distinguished by `app=Money` — matches Money PWA's
 * VITE_FEEDBACK_URL posting pattern ("app=Money"). If this app ends up with
 * its own dedicated Apps Script deployment, only FEEDBACK_URL needs to change.
 */
@HiltViewModel
class FeedbackViewModel @Inject constructor(
    private val authPreferences: AuthPreferences,
) : ViewModel() {

    private val _sendResult = MutableStateFlow<SendResult?>(null)
    val sendResult: StateFlow<SendResult?> = _sendResult

    fun send(message: String) {
        viewModelScope.launch {
            val email = authPreferences.userEmail.first()
            _sendResult.value = SendResult.Sending
            _sendResult.value = try {
                withContext(Dispatchers.IO) { post(email = email, message = message) }
                SendResult.Success
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Feedback send failed: ${e.message}", e)
                SendResult.Error
            }
        }
    }

    fun clearResult() { _sendResult.value = null }

    private fun post(email: String, message: String) {
        val body = buildString {
            append(URLEncoder.encode("app", "UTF-8")).append('=').append(URLEncoder.encode("Money", "UTF-8"))
            append('&')
            append(URLEncoder.encode("email", "UTF-8")).append('=').append(URLEncoder.encode(email, "UTF-8"))
            append('&')
            append(URLEncoder.encode("message", "UTF-8")).append('=').append(URLEncoder.encode(message, "UTF-8"))
        }
        val conn = URL(FEEDBACK_URL).openConnection() as HttpURLConnection
        conn.apply {
            requestMethod = "POST"
            doOutput = true
            // Don't follow redirect: Apps Script executes on the first POST and returns 302.
            // Following the redirect converts POST to GET and the script never runs.
            instanceFollowRedirects = false
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        conn.outputStream.use { out ->
            OutputStreamWriter(out, "UTF-8").use { it.write(body) }
        }
        val code = conn.responseCode
        conn.disconnect()
        if (code !in 200..399) throw Exception("HTTP $code")
    }

    sealed interface SendResult {
        data object Sending : SendResult
        data object Success : SendResult
        data object Error : SendResult
    }

    private companion object {
        const val TAG = "FeedbackViewModel"
        const val FEEDBACK_URL =
            "https://script.google.com/macros/s/AKfycbzpQS6F8V0COZlL6gSBrgUP7YMtzz0djuZp7iNiIcGfmRSjdISvpiz5gzg1bCfGzoBB8A/exec"
    }
}
