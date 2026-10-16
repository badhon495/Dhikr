package com.dhikr.app.ui.theme

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color

/** True on devices where the wallpaper-derived Material You palette is available. */
fun supportsDynamicColor(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Projects the device's Material You [ColorScheme] onto the app's own
 * [DhikrColorTokens] so every screen — which paints from these tokens, not from
 * MaterialTheme.colorScheme — picks up the wallpaper colours.
 *
 * The [line] and [track] tokens carry a low alpha in the static palettes; the
 * same alphas are re-applied here over `outlineVariant` so hairlines stay subtle.
 */
@RequiresApi(Build.VERSION_CODES.S)
fun dynamicDhikrColors(context: Context, dark: Boolean, strongBorders: Boolean = false): DhikrColorTokens {
    val scheme: ColorScheme =
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    // outlineVariant sits too close to surface in most wallpaper-derived palettes to
    // read as a border even at raised alpha; outline carries real contrast, so the
    // "Stronger borders" setting swaps to it instead of just scaling alpha further.
    val lineBase = if (strongBorders) scheme.outline else scheme.outlineVariant
    return DhikrColorTokens(
        bg = scheme.surface,
        surface = scheme.surfaceContainerLow,
        card = scheme.surfaceContainerHighest,
        text = scheme.onSurface,
        dim = scheme.onSurfaceVariant,
        faint = scheme.outline,
        line = lineBase.copy(alpha = if (strongBorders) 0.45f else 0.13f),
        sage = scheme.primary,
        sageSoft = scheme.primaryContainer,
        sageMid = scheme.secondary,
        terra = scheme.tertiary,
        terraSoft = scheme.tertiaryContainer,
        track = lineBase.copy(alpha = if (strongBorders) 0.40f else 0.10f),
        onSage = scheme.onPrimary,
    )
}

/** The Material You [ColorScheme] itself, for the MaterialTheme wrapper. */
@RequiresApi(Build.VERSION_CODES.S)
fun dynamicColorScheme(context: Context, dark: Boolean): ColorScheme =
    if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
