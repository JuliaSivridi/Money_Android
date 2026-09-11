package com.stler.money.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.stler.money.ui.icons.CategoryIconView
import com.stler.money.ui.theme.ExpenseColor
import com.stler.money.ui.theme.IncomeColor
import com.stler.money.ui.theme.expenseTextColor
import com.stler.money.ui.theme.incomeTextColor
import com.stler.money.util.formatAmount
import kotlin.math.cos
import kotlin.math.sin

data class DonutSlice(
    val categoryId: String,
    val name: String,
    val icon: String,
    val color: Color,
    val amount: Double,
    val limit: Double,
)

private data class SliceGeometry(val slice: DonutSlice, val startAngle: Float, val sweepAngle: Float, val sharePct: Float)

/**
 * Hand-rolled `Canvas` donut — real `CategoryDonut.tsx`. No charting library does a
 * ring-with-icon-badges donut (tech spec §17.1), so this is a from-scratch port: a
 * `Stroke`-style ring (drawn as an arc stroke rather than a filled path — simpler
 * and avoids path math) + small colored icon badges positioned by trig just outside
 * the ring for slices with >3% share, matching the real component's `renderPieLabel`.
 * Starts at 12 o'clock (Android `Canvas.drawArc` convention) rather than the PWA's
 * 3-o'clock start — a cosmetic difference only, the ring reads the same either way.
 */
@Composable
fun CategoryDonut(
    slices: List<DonutSlice>,
    total: Double,
    baseCurrency: String,
    periodLabel: String,
    /** 0–1 fraction of the current month elapsed — shows a "today" tick on progress bars. */
    todayFraction: Float?,
    onCategoryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (slices.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.BarChart, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                Text("No data for this period", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    val geometry = remember(slices) {
        val gapDeg = 2f
        var cursor = -90f
        slices.map { s ->
            val share = if (total > 0) (s.amount / total).toFloat() else 0f
            val sweep = (share * 360f - gapDeg).coerceAtLeast(0f)
            val g = SliceGeometry(s, cursor, sweep, share * 100f)
            cursor += share * 360f
            g
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(240.dp)) {
            val diameterDp = maxWidth.coerceAtMost(200.dp)
            val outerRadiusDp = diameterDp / 2
            val ringThicknessDp = outerRadiusDp * 0.33f
            val badgeCenterOffsetDp = outerRadiusDp + 18.dp

            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                val outerRadiusPx = outerRadiusDp.toPx()
                val ringThicknessPx = ringThicknessDp.toPx()
                val midRadiusPx = outerRadiusPx - ringThicknessPx / 2
                geometry.forEach { g ->
                    drawArc(
                        color = g.slice.color,
                        startAngle = g.startAngle,
                        sweepAngle = g.sweepAngle,
                        useCenter = false,
                        topLeft = Offset(center.x - midRadiusPx, center.y - midRadiusPx),
                        size = Size(midRadiusPx * 2, midRadiusPx * 2),
                        style = Stroke(width = ringThicknessPx),
                    )
                }
            }

            // Icon badges just outside the ring — only for slices with a large enough share to read.
            geometry.filter { it.sharePct > 3f }.forEach { g ->
                val midAngleRad = Math.toRadians((g.startAngle + g.sweepAngle / 2).toDouble())
                val xDp = badgeCenterOffsetDp * cos(midAngleRad).toFloat()
                val yDp = badgeCenterOffsetDp * sin(midAngleRad).toFloat()
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset { IntOffset(xDp.roundToPx(), yDp.roundToPx()) },
                ) {
                    CategoryIconView(iconName = g.slice.icon, color = g.slice.color, size = 18.dp)
                }
            }

            Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatAmount(total, baseCurrency), style = MaterialTheme.typography.titleMedium)
                Text(
                    periodLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            slices.forEach { item ->
                val pct = if (item.limit > 0) (item.amount / item.limit).toFloat().coerceIn(0f, 1f) else 0f
                val exceeded = item.limit > 0 && item.amount > item.limit
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth().clickable { onCategoryClick(item.categoryId) },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CategoryIconView(iconName = item.icon, color = item.color, size = 14.dp)
                        Text(item.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(formatAmount(item.amount, baseCurrency), style = MaterialTheme.typography.bodyMedium)
                        if (item.limit > 0) {
                            Text(
                                "${if (exceeded) "✗" else "✓"} ${formatAmount(item.limit, baseCurrency)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (exceeded) expenseTextColor() else incomeTextColor(),
                            )
                        }
                    }
                    if (item.limit > 0) {
                        Box(modifier = Modifier.fillMaxWidth().height(6.dp)) {
                            Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant))
                            Box(
                                modifier = Modifier.fillMaxWidth(pct).fillMaxHeight()
                                    .clip(RoundedCornerShape(50))
                                    .background(if (exceeded) ExpenseColor else IncomeColor),
                            )
                            if (todayFraction != null) {
                                Box(modifier = Modifier.fillMaxWidth(todayFraction.coerceIn(0f, 1f)).fillMaxHeight()) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .width(2.dp)
                                            .fillMaxHeight()
                                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
