package com.stler.money.ui.navigation

import android.net.Uri

/**
 * Centralized route constants for Navigation Compose — tech spec §12.
 * Transactions is the only parameterized route: the optional args let the
 * Accounts/Categories-row tap-to-filter (§8.2/§8.3) and the Analytics-donut
 * tap-to-filter (§8.4) hand it a starting filter without a shared mutable
 * store.
 */
object Screen {
    const val TRANSACTIONS =
        "transactions?accountId={accountId}&categoryId={categoryId}&dateFrom={dateFrom}&dateTo={dateTo}"
    const val ACCOUNTS = "accounts"
    const val CATEGORIES = "categories"
    const val ANALYTICS = "analytics"
    const val MENU = "menu"

    const val ARG_ACCOUNT_ID = "accountId"
    const val ARG_CATEGORY_ID = "categoryId"
    const val ARG_DATE_FROM = "dateFrom"
    const val ARG_DATE_TO = "dateTo"

    fun transactionsFilteredByAccount(accountId: String) =
        "transactions?accountId=${Uri.encode(accountId)}"

    fun transactionsFilteredByCategory(categoryId: String) =
        "transactions?categoryId=${Uri.encode(categoryId)}"

    /** Analytics donut drill-down — filters by category AND the analytics period (§8.4). */
    fun transactionsFilteredByCategoryAndRange(categoryId: String, dateFrom: String, dateTo: String) =
        "transactions?categoryId=${Uri.encode(categoryId)}&dateFrom=${Uri.encode(dateFrom)}&dateTo=${Uri.encode(dateTo)}"
}
