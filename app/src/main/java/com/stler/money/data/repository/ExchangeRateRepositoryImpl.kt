package com.stler.money.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.stler.money.auth.AuthPreferences
import com.stler.money.data.remote.SheetsApi
import com.stler.money.data.remote.dto.ValuesBody
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private val Context.exchangeRateDataStore by preferencesDataStore(name = "exchange_rate_prefs")

@Singleton
class ExchangeRateRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authPreferences: AuthPreferences,
    private val sheetsApi: SheetsApi,
    private val gson: Gson,
) : ExchangeRateRepository {

    private val plainHttpClient = OkHttpClient()   // no Bearer token — different host than Sheets

    private val _rates = MutableStateFlow<Map<String, Double>>(emptyMap())
    override val rates: StateFlow<Map<String, Double>> = _rates

    private val _baseCurrency = MutableStateFlow("EUR")
    override val baseCurrency: StateFlow<String> = _baseCurrency

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // One-time local hydration so rates are available offline immediately on cold start —
        // mirrors the PWA's exchangeRateStore persist-on-load behavior. Async, not
        // runBlocking: this used to block whatever thread constructs this Hilt singleton
        // (often the main thread, while some other singleton's own init pulls this one in) —
        // DataStore's first cold-start read does real disk I/O, real ANR material under
        // contention. rates/baseCurrency just start at their defaults until this lands, same
        // as any other async-loaded state.
        scope.launch {
            val prefs = context.exchangeRateDataStore.data.first()
            prefs[RATES_JSON]?.let { json ->
                runCatching {
                    val type = object : TypeToken<Map<String, Double>>() {}.type
                    _rates.value = gson.fromJson(json, type)
                }
            }
            prefs[BASE_CURRENCY]?.let { _baseCurrency.value = it }
        }
    }

    override fun getRate(currency: String): Double = _rates.value[currency] ?: 1.0

    override suspend fun refresh(baseCurrency: String) {
        // fetchRatesFromApi() makes a blocking OkHttp .execute() call (not a suspend
        // Retrofit call) — without this, it ran on whatever dispatcher the caller's
        // coroutine happened to be on (viewModelScope defaults to Dispatchers.Main),
        // which throws NetworkOnMainThreadException on a real device. That exception
        // was then silently swallowed by fetchRatesFromApi's own runCatching, so
        // `rates` just silently stayed empty forever — the real bug behind both the
        // Analytics balance-line offset and the RUB-shown-as-€ symbol mismatch.
        val fetched = withContext(Dispatchers.IO) { fetchRatesFromApi(baseCurrency) } ?: return
        _rates.value = fetched
        _baseCurrency.value = baseCurrency
        saveLocal(baseCurrency, fetched)
        mergeIntoSettings { it.put("exchange_rates", JSONObject(fetched as Map<*, *>)) }
    }

    override suspend fun setBaseCurrency(currency: String) {
        _baseCurrency.value = currency
        saveLocal(currency, _rates.value)
        mergeIntoSettings { it.put("base_currency", currency) }
        refresh(currency)
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private fun fetchRatesFromApi(base: String): Map<String, Double>? = runCatching {
        val url = "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/${base.lowercase()}.json"
        val response = plainHttpClient.newCall(Request.Builder().url(url).build()).execute()
        if (!response.isSuccessful) return null
        val body = response.body?.string() ?: return null
        val json = JSONObject(body)
        val baseObj = json.optJSONObject(base.lowercase()) ?: return null
        val map = mutableMapOf<String, Double>()
        baseObj.keys().forEach { key -> map[key.uppercase()] = baseObj.getDouble(key) }
        map
    }.onFailure { e ->
        Log.e(TAG, "fetchRatesFromApi exception: ${e.message}", e)
    }.getOrNull()

    private suspend fun saveLocal(baseCurrency: String, rates: Map<String, Double>) {
        context.exchangeRateDataStore.edit { prefs ->
            prefs[BASE_CURRENCY] = baseCurrency
            prefs[RATES_JSON] = gson.toJson(rates)
        }
    }

    /**
     * Read-modify-write against the shared settings!A1 JSON blob so this call never
     * clobbers keys other repositories/screens own (e.g. `collapsed_account_groups`,
     * tech spec §6 Database / Storage Schema).
     */
    private suspend fun mergeIntoSettings(update: (JSONObject) -> Unit) {
        val spreadsheetId = authPreferences.spreadsheetId.first()
        if (spreadsheetId.isBlank()) return
        runCatching {
            val current = runCatching {
                val resp = sheetsApi.getValues(spreadsheetId, "settings!A1")
                JSONObject(resp.values.firstOrNull()?.firstOrNull()?.toString() ?: "{}")
            }.getOrDefault(JSONObject())
            update(current)
            sheetsApi.updateValues(
                spreadsheetId = spreadsheetId,
                range = "settings!A1",
                body = ValuesBody(range = "settings!A1", values = listOf(listOf(current.toString()))),
            )
        }.onFailure { e ->
            Log.e(TAG, "mergeIntoSettings exception: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "ExchangeRateRepository"
        private val BASE_CURRENCY = stringPreferencesKey("base_currency")
        private val RATES_JSON = stringPreferencesKey("rates_json")
    }
}
