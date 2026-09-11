package com.stler.money.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainerGrayLight,
    onPrimaryContainer = OnSecondaryContainerGray,
    secondary = Secondary,
    onSecondary = Foreground,
    secondaryContainer = SecondaryContainerGray,
    onSecondaryContainer = OnSecondaryContainerGray,
    background = Background,
    onBackground = Foreground,
    surface = Surface,
    onSurface = Foreground,
    surfaceVariant = Secondary,
    onSurfaceVariant = MutedForeground,
    outline = Border,
    outlineVariant = Border,
    error = Destructive,
    onError = OnDestructive,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    inverseSurface = Foreground,
    inverseOnSurface = Background,
    surfaceContainerLowest = SurfaceContainerLowest,
    surfaceContainerLow = SurfaceContainerLow,
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceContainerHigh,
    surfaceContainerHighest = SurfaceContainerHighest,
    surfaceTint = Color.Transparent,
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainerGrayDark,
    onPrimaryContainer = OnPrimaryContainerGrayDark,
    secondary = SecondaryDark,
    onSecondary = ForegroundDark,
    secondaryContainer = PrimaryContainerGrayDark,
    onSecondaryContainer = OnPrimaryContainerGrayDark,
    background = BackgroundDark,
    onBackground = ForegroundDark,
    surface = SurfaceDark,
    onSurface = ForegroundDark,
    surfaceVariant = SecondaryDark,
    onSurfaceVariant = MutedForegroundDark,
    outline = BorderDark,
    outlineVariant = BorderDark,
    error = DestructiveDark,
    onError = OnDestructive,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = ForegroundDark,
    inverseOnSurface = BackgroundDark,
    surfaceContainerLowest = SurfaceContainerLowestDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = SurfaceContainerHighestDark,
    surfaceTint = Color.Transparent,
)

@Composable
fun MoneyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

/** [ExpenseColor]/[IncomeColor] as literal `Text` color — see the contrast note on those tokens. */
@Composable
fun expenseTextColor(): Color = if (isSystemInDarkTheme()) ExpenseColor else ExpenseColorTextLight

@Composable
fun incomeTextColor(): Color = if (isSystemInDarkTheme()) IncomeColor else IncomeColorTextLight

/**
 * Selected [com.stler.money.ui.common.PillChip] label color — real PWA's own
 * `--accent-foreground` (`FilterPanel.tsx`'s `text-primary` on a `bg-primary/15`
 * chip reads fine on the web's own accent-foreground-driven design system, but
 * `colorScheme.primary` directly fails contrast at ~2.9:1 against that tinted
 * background). Matches the PWA's actual light/dark `--accent-foreground`
 * values: a dark orange in light mode (already ported as [AccentForeground],
 * previously unused), near-white in dark mode (already ported as
 * [ForegroundDark] — the PWA's own dark `--accent-foreground` is `0 0% 95%`,
 * identical to its `--foreground`).
 */
@Composable
fun selectedChipTextColor(): Color = if (isSystemInDarkTheme()) ForegroundDark else AccentForeground
