package com.stler.money.data.repository

import kotlinx.coroutines.flow.StateFlow

/**
 * Fetch + cache exchange rates — tech spec §7 "Exchange Rate Sync". Not part
 * of the SyncQueue: refreshed on base-currency change and once at startup,
 * not on the periodic 30-minute worker.
 */
interface ExchangeRateRepository {
    /** `{ "USD": 1.08, "RUB": 95.4, ... }` — 1 baseCurrency = N foreignCurrency. */
    val rates: StateFlow<Map<String, Double>>
    val baseCurrency: StateFlow<String>

    /** Synchronous lookup against the current in-memory snapshot — 1.0 fallback if unknown. */
    fun getRate(currency: String): Double

    /** Fetches fresh rates for [baseCurrency], caches locally, and best-effort mirrors to settings!A1. */
    suspend fun refresh(baseCurrency: String)

    /** Persists the new base currency (local cache + settings!A1) and refreshes rates for it. */
    suspend fun setBaseCurrency(currency: String)
}

/** See tech spec §16.5. */
fun convertToBase(amount: Double, currency: String, baseCurrency: String, rates: Map<String, Double>): Double {
    if (currency == baseCurrency) return amount
    val rate = rates[currency] ?: return amount // fallback: assume 1:1
    return amount / rate
}
