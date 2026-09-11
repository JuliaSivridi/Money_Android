package com.stler.money.ui.icons

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Round icon container: `background = color`, `icon = white` — mirrors Money
 * PWA's `CategoryIcon.tsx` (container size = icon size + 12dp). See
 * [CategoryIcons] for the Lucide-name -> Material Icons Extended mapping.
 */
@Composable
fun CategoryIconView(
    iconName: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
) {
    Box(
        modifier = modifier.size(size + 12.dp).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = CategoryIcons.resolveMaterial(iconName),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size),
        )
    }
}
