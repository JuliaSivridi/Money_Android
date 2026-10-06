package com.stler.money.ui.main

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stler.money.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    object UpToDate : UpdateState()
    data class UpdateAvailable(val version: String, val downloadUrl: String) : UpdateState()
    data class Downloading(val progress: Int) : UpdateState()
    data class ReadyToInstall(val apkFile: File) : UpdateState()
    data class Error(val message: String) : UpdateState()
}

/** In-app update — see tech spec §14. Same flow as Tasks Android: latest GitHub release →
 * download the APK into the cache dir → hand it to the system installer via FileProvider. */
@HiltViewModel
class AboutViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    // Plain client — no Bearer-token interceptor (different host than Sheets) and no read
    // timeout, since the APK download streams for as long as it takes.
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    fun checkForUpdate() {
        if (_updateState.value is UpdateState.Checking) return
        viewModelScope.launch {
            _updateState.value = UpdateState.Checking
            runCatching {
                withContext(Dispatchers.IO) { fetchLatestRelease() }
            }.onSuccess { (tagName, downloadUrl) ->
                _updateState.value = if (tagName == "v${BuildConfig.VERSION_NAME}") {
                    UpdateState.UpToDate
                } else {
                    UpdateState.UpdateAvailable(tagName, downloadUrl)
                }
            }.onFailure { e ->
                _updateState.value = UpdateState.Error(e.message ?: "Network error")
            }
        }
    }

    fun downloadUpdate(downloadUrl: String) {
        viewModelScope.launch {
            _updateState.value = UpdateState.Downloading(0)
            runCatching {
                withContext(Dispatchers.IO) {
                    downloadApk(downloadUrl) { progress -> _updateState.value = UpdateState.Downloading(progress) }
                }
            }.onSuccess { apkFile ->
                _updateState.value = UpdateState.ReadyToInstall(apkFile)
            }.onFailure { e ->
                _updateState.value = UpdateState.Error(e.message ?: "Download failed")
            }
        }
    }

    fun installApk(apkFile: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    fun resetState() {
        _updateState.value = UpdateState.Idle
    }

    private fun fetchLatestRelease(): Pair<String, String> {
        val request = Request.Builder()
            .url("https://api.github.com/repos/JuliaSivridi/Money_Android/releases/latest")
            .header("Accept", "application/vnd.github.v3+json")
            .build()
        val body = httpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            response.body!!.string()
        }
        val json = JSONObject(body)
        val tagName = json.getString("tag_name")
        val assets = json.getJSONArray("assets")
        val apkUrl = (0 until assets.length())
            .map { assets.getJSONObject(it) }
            .firstOrNull { it.getString("name").endsWith(".apk") }
            ?.getString("browser_download_url")
        check(apkUrl != null) { "No APK asset found in release $tagName" }
        return tagName to apkUrl
    }

    private fun downloadApk(url: String, onProgress: (Int) -> Unit): File {
        val dir = File(context.cacheDir, "updates").also { it.mkdirs() }
        val apkFile = File(dir, "stler-money-update.apk")

        httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            val body = response.body!!
            val total = body.contentLength()
            var downloaded = 0L
            var lastReported = -1

            body.byteStream().use { input ->
                apkFile.outputStream().use { output ->
                    val buffer = ByteArray(8_192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) {
                            // Only on a changed percentage — not every 8 KB chunk — so the
                            // StateFlow isn't hammered with thousands of identical-ish updates.
                            val percent = (downloaded * 100 / total).toInt()
                            if (percent != lastReported) {
                                lastReported = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }
        }
        return apkFile
    }
}
