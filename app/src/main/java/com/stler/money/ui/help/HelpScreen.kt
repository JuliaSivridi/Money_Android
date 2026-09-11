package com.stler.money.ui.help

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class HelpItem(val term: String, val text: String)
private data class HelpSection(val title: String, val items: List<HelpItem>)

/** Verbatim from Money PWA's `HelpPage.tsx` — tech spec §8.7 undersold this as generic paragraphs. */
private val SECTIONS = listOf(
    HelpSection(
        "Basics",
        listOf(
            HelpItem("Transactions", "The home view. Tap + to add an expense, income, transfer between accounts, or a debt record. Tap a transaction to edit it."),
            HelpItem("Accounts", "Your wallets, cards and bank accounts with current balances. Balances update automatically with every transaction."),
            HelpItem("Categories", "Expense and income categories with optional monthly limits. Drag to reorder; tap to edit."),
            HelpItem("Analytics", "Yearly and monthly charts: income vs expenses, balance line, per-category breakdown. Tap a month for details."),
        ),
    ),
    HelpSection(
        "Data & sync",
        listOf(
            HelpItem("Where is my data?", "Everything lives in a Google Sheets file (db_money) in your own Google Drive. You can open and inspect it any time."),
            HelpItem("Cloud icon", "Shows sync status: a number badge means changes waiting to be sent; a spinning arrow means syncing. Tap it to sync now."),
            HelpItem("Offline", "The app works offline — changes are queued locally and sent to Google Sheets when you are back online."),
            HelpItem("Multiple devices", "You can use the app on several devices with the same Google account; the most recent edit wins."),
        ),
    ),
    HelpSection(
        "Tips",
        listOf(
            HelpItem("Currencies", "Each account has its own currency. Totals are converted to your base currency using daily exchange rates."),
            HelpItem("Search & filters", "On Transactions, search by comment or open the filter panel: accounts, types, categories, dates, amounts."),
            HelpItem("Limits", "Set a monthly limit on a category to track it in Categories and Analytics."),
        ),
    ),
)

/** See tech spec §8.7. Physical back returns to the main screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onNavigateBack: () -> Unit) {
    BackHandler(onBack = onNavigateBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Help") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(SECTIONS) { section ->
                Column {
                    Text(
                        text = section.title.uppercase(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    OutlinedCard(shape = RoundedCornerShape(12.dp)) {
                        section.items.forEachIndexed { index, item ->
                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                                Text(item.term, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(
                                    item.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                            if (index != section.items.lastIndex) HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
