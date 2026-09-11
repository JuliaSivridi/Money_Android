package com.stler.money.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.stler.money.ui.icons.CategoryIcons

private const val COLUMNS = 6

/** The 36-name list — verbatim from `IconPicker.tsx`'s `ICON_NAMES`. */
val PICKER_ICON_NAMES = listOf(
    "ShoppingCart", "UtensilsCrossed", "Car", "Bus", "Heart", "Pill",
    "Shirt", "Home", "Zap", "Wifi", "Smartphone", "Gamepad2",
    "Plane", "GraduationCap", "Gift", "Dumbbell", "Coffee", "Smile",
    "Sprout", "Baby", "PawPrint", "Wrench", "Banknote", "TrendingUp",
    "HandCoins", "Landmark", "PiggyBank", "BookOpen", "Music",
    "Scissors", "Sparkles", "Tag", "ShoppingBag", "Fuel", "Train", "Beer",
)

/**
 * Search field + 6-column grid — real `IconPicker.tsx` (search input, 48dp
 * cells — bumped from the PWA's 40dp for a real ≥48dp touch target, 18dp
 * glyph, `border-primary bg-accent ring-2 ring-primary` when
 * selected). Only offers the 36 curated names, same as the PWA — a category
 * whose current icon is outside that list (see `CategoryIcons` — real user
 * data has some) keeps rendering fine via [CategoryIcons], it just isn't
 * re-selectable from this grid, matching PWA behavior exactly.
 *
 * A plain chunked `Column`/`Row` grid, not `LazyVerticalGrid` — this is
 * always embedded inside an already-scrolling `Column(Modifier.verticalScroll())`
 * (Account/Category form sheets), and a lazy grid there gets an infinite
 * max-height constraint and crashes (`checkScrollableContainerConstraints`).
 */
@Composable
fun IconPickerGrid(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var search by remember { mutableStateOf("") }
    val filtered = PICKER_ICON_NAMES.filter { it.contains(search, ignoreCase = true) }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text("Search icons...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            filtered.chunked(COLUMNS).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    row.forEach { name ->
                        val selected = name == value
                        Box(
                            modifier = Modifier
                                .padding(3.dp)
                                .size(48.dp)
                                .border(
                                    if (selected) 2.dp else 1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(8.dp),
                                )
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onChange(name) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = CategoryIcons.resolveMaterial(name),
                                contentDescription = name,
                                modifier = Modifier.size(18.dp),
                                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}
