package com.stler.money.notifications

/**
 * Shared amount+currency extraction used by every [NotificationParser] — the one bit of
 * "understand the notification text" logic that's actually common across sources, unlike
 * the source-specific phrasing each parser would otherwise need to know about.
 */
object MoneyAmountExtractor {

    data class Money(val amount: Double, val currency: String)

    private val SYMBOL_TO_CURRENCY = mapOf('€' to "EUR", '$' to "USD", '£' to "GBP", '₽' to "RUB")
    private val NUMBER = """\d[\d.,\s]*\d|\d"""
    private val SYMBOL_THEN_NUMBER = Regex("""[€$£₽]\s?($NUMBER)""")
    private val NUMBER_THEN_SYMBOL = Regex("""($NUMBER)\s?[€$£₽]""")
    private val CODE_THEN_NUMBER = Regex("""\b(EUR|USD|RUB|GBP)\b\s?($NUMBER)""")
    private val NUMBER_THEN_CODE = Regex("""($NUMBER)\s?\b(EUR|USD|RUB|GBP)\b""")

    fun extract(text: String): Money? {
        SYMBOL_THEN_NUMBER.find(text)?.let { m ->
            val symbol = m.value.first { it in SYMBOL_TO_CURRENCY }
            return normalize(m.groupValues[1])?.let { Money(it, SYMBOL_TO_CURRENCY.getValue(symbol)) }
        }
        NUMBER_THEN_SYMBOL.find(text)?.let { m ->
            val symbol = m.value.last { it in SYMBOL_TO_CURRENCY }
            return normalize(m.groupValues[1])?.let { Money(it, SYMBOL_TO_CURRENCY.getValue(symbol)) }
        }
        CODE_THEN_NUMBER.find(text)?.let { m ->
            return normalize(m.groupValues[2])?.let { Money(it, m.groupValues[1]) }
        }
        NUMBER_THEN_CODE.find(text)?.let { m ->
            return normalize(m.groupValues[1])?.let { Money(it, m.groupValues[2]) }
        }
        return null
    }

    /** Handles both "1,234.56" and "1.234,56" style grouping — whichever separator is last wins as decimal. */
    private fun normalize(raw: String): Double? {
        val cleaned = raw.replace(" ", "").replace(" ", "")
        val hasComma = cleaned.contains(',')
        val hasDot = cleaned.contains('.')
        val normalized = when {
            hasComma && hasDot ->
                if (cleaned.lastIndexOf(',') > cleaned.lastIndexOf('.')) cleaned.replace(".", "").replace(',', '.')
                else cleaned.replace(",", "")
            hasComma ->
                if (cleaned.substringAfterLast(',').length == 2) cleaned.replace(',', '.') else cleaned.replace(",", "")
            else -> cleaned
        }
        return normalized.toDoubleOrNull()
    }
}
