package com.stler.money.ui.theme

import androidx.compose.ui.graphics.Color

// Tokens ported from Money PWA's src/index.css HSL custom properties — see tech
// spec §11. Kept in one file, mirroring the Tasks Android sibling's Color.kt.

// ── Brand / Primary ──────────────────────────────────────────────────────────
// Must be byte-identical across the whole app family (Money PWA, Money Android,
// Tasks Android) — this project's own previous values (#E0813D / #D9995E) were a
// slightly-off hand conversion of the PWA's hsl(25 75% 55%)/hsl(25 65% 63%)
// tokens; #E07E38 is the actual PWA value (matches its icon.svg/theme-color
// exactly) and #D98D52 is Tasks Android's already-corrected dark-mode match —
// both adopted verbatim rather than re-deriving from HSL by hand again.
val Primary = Color(0xFFE07E38)
val PrimaryDark = Color(0xFFD98D52)
val OnPrimary = Color(0xFFFFFFFF)

// ── Backgrounds ──────────────────────────────────────────────────────────────
val Background = Color(0xFFFFFFFF)
val BackgroundDark = Color(0xFF1C1C1C)   // hsl(0 0% 11%)

val Surface = Color(0xFFFFFFFF)
val SurfaceDark = Color(0xFF363636)      // hsl(0 0% 21%) – card

val Popover = Color(0xFFFFFFFF)
val PopoverDark = Color(0xFF242424)      // hsl(0 0% 14%)

// ── Text / Foreground ─────────────────────────────────────────────────────────
val Foreground = Color(0xFF18181F)       // hsl(240 10% 10%)
val ForegroundDark = Color(0xFFF2F2F2)   // hsl(0 0% 95%)

val MutedForeground = Color(0xFF6B6B6B)      // hsl(0 0% 42%)
// Lightened from the PWA's literal hsl(0 0% 58%) (#949494) — that value only
// reaches ~3.98:1 against this app's SurfaceDark (#363636)/SurfaceContainerDark
// cards, under WCAG AA's 4.5:1 text threshold. #A3A3A3 clears it (~4.8:1)
// while staying the same neutral gray, no hue shift — an Android-only
// deviation from the exact PWA value for a real contrast fix, not a redesign.
val MutedForegroundDark = Color(0xFFA3A3A3)

// ── Accent ────────────────────────────────────────────────────────────────────
val Accent = Color(0xFFFDF6ED)           // hsl(38 60% 96%) — warm cream
val AccentForeground = Color(0xFF8F4F1C) // hsl(25 60% 35%)
val AccentDark = Color(0xFF2E2E2E)       // hsl(0 0% 18%)

// ── Secondary / Muted ─────────────────────────────────────────────────────────
val Secondary = Color(0xFFF5F3F1)        // hsl(25 8% 95%)
val SecondaryDark = Color(0xFF363636)

// ── Unified selection highlight ───────────────────────────────────────────────
// Single neutral gray for selected/highlighted states (FilterMenu chips, segmented
// buttons, week-strip-equivalent pickers), same rationale as Tasks Android.
val SelectedHighlightLight = Color(0xFFD8D8D8)
val SelectedHighlightDark = Color(0xFF515151)
val OnSelectedHighlightDark = Color(0xFFF2F2F2)

val SecondaryContainerGray = SelectedHighlightLight
val OnSecondaryContainerGray = Color(0xFF1A1A1A)
val PrimaryContainerGrayLight = SelectedHighlightLight
val PrimaryContainerGrayDark = SelectedHighlightDark
val OnPrimaryContainerGrayDark = OnSelectedHighlightDark

// ── Borders ───────────────────────────────────────────────────────────────────
val Border = Color(0xFFE0E0E0)           // hsl(0 0% 88%)
val BorderDark = Color(0xFF4A4A4A)       // hsl(0 0% 29%)
val Input = Color(0xFFE0E0E0)
val InputDark = Color(0xFF383838)        // hsl(0 0% 22%)

// ── Elevated surface tiers ────────────────────────────────────────────────────
// `darkColorScheme()`/`lightColorScheme()` leave `surfaceContainer*` and
// `surfaceTint` at Material3's own purple-tinted baseline unless given
// explicitly — that's what made `ModalBottomSheet` (and other elevated
// surfaces using these tokens) read faintly purple instead of neutral gray.
// A small gray ladder instead, keeping the app's "orange + gray only" palette.
val SurfaceContainerLowest = Color(0xFFFFFFFF)
val SurfaceContainerLow = Color(0xFFF7F7F7)
val SurfaceContainer = Color(0xFFF0F0F0)
val SurfaceContainerHigh = Color(0xFFEAEAEA)
val SurfaceContainerHighest = Color(0xFFE3E3E3)

val SurfaceContainerLowestDark = Color(0xFF151515)
val SurfaceContainerLowDark = Color(0xFF242424)
val SurfaceContainerDark = Color(0xFF363636)
val SurfaceContainerHighDark = Color(0xFF404040)
val SurfaceContainerHighestDark = Color(0xFF4A4A4A)

// ── Destructive ───────────────────────────────────────────────────────────────
val Destructive = Color(0xFFE96060)      // hsl(0 70% 67%)
val DestructiveDark = Color(0xFFCC5252)  // hsl(0 55% 58%)
val OnDestructive = Color(0xFFFFFFFF)

// ── Transaction type colors ───────────────────────────────────────────────────
// red-400/green-500 — fine as decorative fills (progress bars, dots, the small
// donut-ring icon badges): those only need WCAG's 3:1 graphic-object threshold.
// As literal TEXT color on a light background they fail badly (~2.8:1/2.3:1 on
// white) — this is the actual color of every expense/income amount in the app,
// so [expenseTextColor]/[incomeTextColor] below swap in a darker shade for text
// specifically, keeping these bright values for everything else.
val ExpenseColor = Color(0xFFF87171)     // red-400
val IncomeColor = Color(0xFF22C55E)      // green-500
val DebtLentColor = Color(0xFFF87171)    // red-400 (unresolved lent, owed to user)
val DebtBorrowedColor = Color(0xFF22C55E) // green-500 (unresolved borrowed, owed by user)

// Light-mode-only text variants (Tailwind red-600/green-700) — ≥4.5:1 on white.
// Dark mode keeps the bright values above: they already clear 4.5:1 against
// this app's near-black dark backgrounds, and a darkened shade would fail there
// instead (lower luminance = less contrast against an already-dark background).
val ExpenseColorTextLight = Color(0xFFDC2626)
val IncomeColorTextLight = Color(0xFF15803D)

// ── Account / category color swatches ─────────────────────────────────────────
// Verbatim from the real `ColorPicker.tsx` SWATCHES array (11 pairs, 22 total,
// rendered in an 11-column grid) — an earlier version of this file had a
// different, invented 12-color list; fixed against the actual source.
val ColorPresets = listOf(
    Color(0xFFEF4444), Color(0xFFDC2626),
    Color(0xFFF97316), Color(0xFFEA580C),
    Color(0xFFEAB308), Color(0xFFCA8A04),
    Color(0xFF22C55E), Color(0xFF16A34A),
    Color(0xFF10B981), Color(0xFF059669),
    Color(0xFF14B8A6), Color(0xFF0D9488),
    Color(0xFF06B6D4), Color(0xFF0891B2),
    Color(0xFF3B82F6), Color(0xFF2563EB),
    Color(0xFF8B5CF6), Color(0xFF7C3AED),
    Color(0xFFEC4899), Color(0xFFDB2777),
    Color(0xFF6B7280), Color(0xFF374151),
)

val DefaultCategoryColor = Color(0xFF6B7280) // gray-500 — matches PWA fallback "#6b7280"
