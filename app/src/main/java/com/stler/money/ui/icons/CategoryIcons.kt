package com.stler.money.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalBar
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Monitor
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.SportsBar
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Resolves a Lucide icon name (as stored verbatim in `Category.icon` / Sheets
 * column C) to a Compose icon. Material Icons Extended for every name —
 * family consistency with Tasks Android, which uses stock Material icons
 * throughout its real UI (the few custom drawables in its resources are
 * Glance-widget-only technical workarounds, not a deliberate Lucide-style
 * choice — checked against its actual source).
 *
 * IMPORTANT: this is NOT limited to `IconPicker.tsx`'s 36-name picker list.
 * `CategoryIcon.tsx` looks icons up dynamically against the *entire*
 * lucide-react export, so real category data can (and — checked against the
 * user's actual `money-categories.csv` — does) reference names outside that
 * list; the picker only constrains what a *new* selection can use. The block
 * below the first 36 is exactly the extra names found in that real data
 * (Wine, HardHat, Package, Monitor, Palette, Sun, Shield, Ticket, Cat,
 * Briefcase, Percent, FileText) — expect more to turn up over time as new
 * categories get created outside the picker's list; add them here as found
 * rather than letting them silently fall back to the Tag icon.
 *
 * `HandCoins` — the one of the 36 with no literal match — maps to
 * `Handshake` (lending/borrowing, semantically fitting) rather than a
 * hand-drawn icon, since the user doesn't actually use it in practice.
 */
object CategoryIcons {

    fun resolveMaterial(name: String): ImageVector = when (name) {
        // ── IconPicker.tsx's 36-name list ──────────────────────────────────
        "ShoppingCart" -> Icons.Outlined.ShoppingCart
        "UtensilsCrossed" -> Icons.Outlined.Restaurant
        "Car" -> Icons.Outlined.DirectionsCar
        "Bus" -> Icons.Outlined.DirectionsBus
        "Heart" -> Icons.Outlined.FavoriteBorder
        "Pill" -> Icons.Outlined.Medication
        "Shirt" -> Icons.Outlined.Checkroom
        "Home" -> Icons.Outlined.Home
        "Zap" -> Icons.Outlined.Bolt
        "Wifi" -> Icons.Outlined.Wifi
        "Smartphone" -> Icons.Outlined.Smartphone
        "Gamepad2" -> Icons.Outlined.SportsEsports
        "Plane" -> Icons.Outlined.Flight
        "GraduationCap" -> Icons.Outlined.School
        "Gift" -> Icons.Outlined.CardGiftcard
        "Dumbbell" -> Icons.Outlined.FitnessCenter
        "Coffee" -> Icons.Outlined.LocalCafe
        "Smile" -> Icons.Outlined.Mood
        "Sprout" -> Icons.Outlined.Eco
        "Baby" -> Icons.Outlined.ChildCare
        "PawPrint" -> Icons.Outlined.Pets
        "Wrench" -> Icons.Outlined.Build
        "Banknote" -> Icons.Outlined.Payments
        "TrendingUp" -> Icons.AutoMirrored.Outlined.TrendingUp
        "HandCoins" -> Icons.Outlined.Handshake
        "Landmark" -> Icons.Outlined.AccountBalance
        "PiggyBank" -> Icons.Outlined.Savings
        "BookOpen" -> Icons.AutoMirrored.Outlined.MenuBook
        "Music" -> Icons.Outlined.MusicNote
        "Scissors" -> Icons.Outlined.ContentCut
        "Sparkles" -> Icons.Outlined.AutoAwesome
        "Tag" -> Icons.Outlined.LocalOffer
        "ShoppingBag" -> Icons.Outlined.ShoppingBag
        "Fuel" -> Icons.Outlined.LocalGasStation
        "Train" -> Icons.Outlined.Train
        "Beer" -> Icons.Outlined.SportsBar

        // ── Found in the user's real category data, not in the picker list ─
        "Wine" -> Icons.Outlined.LocalBar
        "HardHat" -> Icons.Outlined.Construction
        "Package" -> Icons.Outlined.Inventory2
        "Monitor" -> Icons.Outlined.Monitor
        "Palette" -> Icons.Outlined.Palette
        "Sun" -> Icons.Outlined.WbSunny
        "Shield" -> Icons.Outlined.Shield
        "Ticket" -> Icons.Outlined.ConfirmationNumber
        "Cat" -> Icons.Outlined.Pets // Material has no cat-specific glyph; same generic paw as PawPrint
        "Briefcase" -> Icons.Outlined.Work
        "Percent" -> Icons.Outlined.Percent
        "FileText" -> Icons.AutoMirrored.Outlined.Article

        else -> Icons.Outlined.LocalOffer // PWA's own fallback is lucide's "Tag"
    }
}
