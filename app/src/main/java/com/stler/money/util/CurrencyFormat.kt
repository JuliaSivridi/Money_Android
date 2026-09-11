package com.stler.money.util

import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Ported from Money PWA's `currencyUtils.ts formatAmount()`, which forces
 * `currencyDisplay: 'narrowSymbol'` in `Intl.NumberFormat` specifically
 * because the default display falls back to the ISO code ("RUB") instead of
 * the symbol (₽) for some currencies/locales — a bug already hit and fixed
 * once in the PWA. `java.text.NumberFormat` has no narrowSymbol equivalent
 * and is just as prone to the same ICU-data gap, so the fix here is the same
 * shape: force the known-good narrow symbol for the app's supported
 * currencies (§8.6 CURRENCIES) instead of trusting `Currency.getSymbol()`
 * to resolve it correctly for the device's locale.
 */
private val NARROW_SYMBOLS = mapOf("EUR" to "€", "USD" to "$", "RUB" to "₽")

fun formatAmount(amount: Double, currencyCode: String): String = runCatching {
    val fmt = NumberFormat.getCurrencyInstance(Locale.getDefault()) as DecimalFormat
    // `setCurrency()` MUST run before touching decimalFormatSymbols — per its own
    // Javadoc it resets the currency symbol as a side effect, which was silently
    // clobbering the override below (that was the actual bug: right symbol map,
    // wrong order, so RUB kept rendering as the ISO code regardless of the fix).
    fmt.currency = Currency.getInstance(currencyCode)
    val symbols = fmt.decimalFormatSymbols
    symbols.currencySymbol = NARROW_SYMBOLS[currencyCode]
        ?: runCatching { Currency.getInstance(currencyCode).symbol }.getOrDefault(currencyCode)
    fmt.decimalFormatSymbols = symbols
    fmt.minimumFractionDigits = 2
    fmt.maximumFractionDigits = 2
    fmt.format(amount)
}.getOrDefault("%.2f %s".format(amount, currencyCode))
