package com.stler.money.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Ported from Money PWA's `src/utils/design.ts` — that file's own comment
 * calls it "single source of truth, never hardcode these values in
 * component files"; this is the Android equivalent of the same rule.
 * Consult this before picking an icon size anywhere rather than eyeballing
 * a screenshot.
 */
object IconSizes {
    /** Tiny — small inline indicators, dropdown leading dots. */
    val xs = 14.dp

    /** Small — section headers, compact grid cells (TransactionFormSheet's category grid). */
    val sm = 16.dp

    /** Medium — secondary stacked category icon (behind the primary one). */
    val md = 20.dp

    /** Large — primary category / account icon in list rows (Transactions, Accounts, Categories). */
    val lg = 28.dp
}

/** Fallback color for accounts/categories with no color set — matches PWA's DEFAULT_ENTITY_COLOR. */
const val DEFAULT_ENTITY_COLOR = "#6b7280"
