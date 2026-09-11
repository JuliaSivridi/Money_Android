package com.stler.money.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * All Android chrome uses rounded rectangles, never fully-rounded/oval pills
 * — a deliberate Android-only deviation from the PWA (which uses
 * `rounded-full` pills for filter chips specifically; everything else there
 * — form fields, buttons — is already a rounded rectangle; the user chose to
 * make that consistent app-wide on Android instead of mixing the two shapes).
 * Material3's `Button`/`SegmentedButton`/`FilterChip` default to a
 * fully-rounded "corner full" shape token that isn't reachable through
 * `MaterialTheme.shapes` (that 5-step scale isn't what those components read
 * their corner radius from), so this is applied explicitly at each call site
 * — filter/date chips, segmented-button tabs (Categories/Analytics tab
 * headers, transaction-type picker, base-currency picker), and standalone
 * buttons (Feedback's Send, Sign-in, About's "Check for updates") — rather
 * than through a single theme-level override.
 */
val ControlShape = RoundedCornerShape(10.dp)
