package com.stler.money.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.stler.money.ui.theme.ColorPresets

private const val COLUMNS = 11

/**
 * 11-column grid of the 22 swatches — real `ColorPicker.tsx`, tech spec §10.3.
 * A plain chunked `Column`/`Row` grid, not `LazyVerticalGrid` — this is always
 * embedded inside an already-scrolling `Column(Modifier.verticalScroll())`
 * (Account/Category form sheets), and a lazy grid there gets an infinite max-
 * height constraint and crashes (`checkScrollableContainerConstraints`). 22
 * items is nowhere near enough to need laziness anyway.
 */
@Composable
fun ColorPickerGrid(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ColorPresets.chunked(COLUMNS).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { color ->
                    val hex = "#%06X".format(0xFFFFFF and color.toArgb())
                    val selected = value.equals(hex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clickable { onChange(hex) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier.size(28.dp).clip(CircleShape).background(color),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
