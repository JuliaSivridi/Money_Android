package com.stler.money.data.repository

import com.stler.money.domain.model.Transaction
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

/**
 * See tech spec §16.5. Fallback chain when [rates] has no entry for [currency] (offline at
 * save time, before the first refresh, or the currency isn't one jsDelivr's currency-api
 * covers): average the *implied* rate from the user's own recent [sameCurrencyHistory] —
 * real past transactions in that currency, each already carrying a real `amount`/`amountBase`
 * pair — rather than assuming 1:1 outright. Only actually falls through to 1:1 when there's
 * no live rate *and* no history yet (the very first transaction ever in a new currency).
 * Deliberately not revisited when the base currency changes later: the user chose base once,
 * existing `amountBase` values are left as-is (see dev-plan) — this fallback only concerns
 * what to do *at save time* when a live rate isn't available right now.
 */
fun convertToBase(
    amount: Double,
    currency: String,
    baseCurrency: String,
    rates: Map<String, Double>,
    sameCurrencyHistory: List<Transaction> = emptyList(),
): Double {
    if (currency == baseCurrency) return amount
    rates[currency]?.let { return amount / it }
    val impliedRates = sameCurrencyHistory
        .filter { it.currency == currency && it.amountBase != 0.0 }
        .map { it.amount / it.amountBase }
    val avgRate = impliedRates.takeIf { it.isNotEmpty() }?.average()
    if (avgRate != null && avgRate.isFinite() && avgRate != 0.0) return amount / avgRate
    return amount // final fallback: assume 1:1 — no live rate, no history to average either
}
