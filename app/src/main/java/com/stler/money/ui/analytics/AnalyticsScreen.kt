package com.stler.money.ui.analytics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.stler.money.ui.theme.ControlShape
import com.stler.money.ui.util.ErrorSnackbarEffect

private enum class AnalyticsTab { YEARLY, MONTHLY }

/**
 * Yearly/Monthly tab bar — real `AnalyticsPage.tsx`. The two tabs are fully
 * independent here: the real PWA lets a Yearly bar tap jump to Monthly at
 * that month, but the user never used that affordance and asked for it to be
 * dropped rather than built out further (Vico's Cartesian API doesn't expose
 * a simple per-bar tap the way Recharts does) — decided against porting this
 * one PWA interaction to Android.
 */
@Composable
fun AnalyticsScreen(
    onCategoryDrillDown: (categoryId: String, dateFrom: String, dateTo: String) -> Unit,
    viewModel: AnalyticsViewModel = hiltViewModel(),
) {
    ErrorSnackbarEffect(viewModel)
    var tab by remember { mutableStateOf(AnalyticsTab.YEARLY) }

    Column(modifier = Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            AnalyticsTab.entries.forEachIndexed { index, t ->
                SegmentedButton(
                    selected = tab == t,
                    onClick = { tab = t },
                    shape = SegmentedButtonDefaults.itemShape(index, AnalyticsTab.entries.size, baseShape = ControlShape),
                    icon = {},
                ) {
                    Text(
                        if (t == AnalyticsTab.YEARLY) "Yearly" else "Monthly",
                        maxLines = 1,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            when (tab) {
                AnalyticsTab.YEARLY -> YearlyChartView(viewModel = viewModel)
                AnalyticsTab.MONTHLY -> MonthlyView(onCategoryDrillDown = onCategoryDrillDown, viewModel = viewModel)
            }
        }
    }
}
