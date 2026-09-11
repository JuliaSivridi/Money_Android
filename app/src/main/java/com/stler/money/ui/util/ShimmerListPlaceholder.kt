package com.stler.money.ui.util

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Placeholder shimmer mimicking the shared row shape used by Transactions/Accounts/
 * Categories (leading circle icon, two-line text column, trailing amount) — same
 * pulse-alpha idiom as the Tasks Android sibling's `ShimmerTaskList`. Shown while a
 * screen's first real data emission hasn't landed yet (see each screen's use of
 * `SyncState.Syncing`), replacing what used to be a flash of the real empty state on
 * every cold start.
 */
@Composable
fun ShimmerListPlaceholder(
    itemCount: Int = 6,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmerAlpha",
    )

    Column(modifier = modifier) {
        repeat(itemCount) { index -> ShimmerRow(alpha, index) }
    }
}

private val titleFractions = listOf(0.55f, 0.4f, 0.6f, 0.45f, 0.5f, 0.35f)

@Composable
private fun ShimmerRow(alpha: Float, index: Int) {
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
    val shape = RoundedCornerShape(4.dp)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(color))
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(titleFractions[index % titleFractions.size])
                    .height(14.dp)
                    .clip(shape)
                    .background(color),
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier.width(70.dp).height(11.dp).clip(shape)
                    .background(color.copy(alpha = color.alpha * 0.7f)),
            )
        }
        Box(modifier = Modifier.width(50.dp).height(14.dp).clip(shape).background(color))
    }
}
