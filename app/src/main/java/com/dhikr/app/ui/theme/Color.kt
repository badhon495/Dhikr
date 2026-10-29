package com.dhikr.app.ui.theme

import androidx.compose.ui.graphics.Color

data class DhikrColorTokens(
    val bg: Color,
    val surface: Color,
    val card: Color,
    val text: Color,
    val dim: Color,
    val faint: Color,
    val line: Color,
    val sage: Color,
    val sageSoft: Color,
    val sageMid: Color,
    val terra: Color,
    val terraSoft: Color,
    val track: Color,
    val onSage: Color,
)

val LightDhikrColors = DhikrColorTokens(
    bg = Color(0xFFF5EAD8),
    surface = Color(0xFFEBDDC5),
    card = Color(0xFFF9F4ED),
    text = Color(0xFF201E1D),
    dim = Color(0xFF645C50),
    faint = Color(0xFFA19786),
    line = Color(0x21201E1D), // rgba(32,30,29,.13)
    sage = Color(0xFF7A8A5E),
    sageSoft = Color(0xFFE1EECC),
    sageMid = Color(0xFFAEBF92),
    terra = Color(0xFFC67139),
    terraSoft = Color(0xFFFFE1D0),
    track = Color(0x1A201E1D), // rgba(32,30,29,.10)
    onSage = Color(0xFFF9F4ED),
)

/**
 * Heavier [line] and [track] for the "Stronger borders" setting: the two
 * hairline tokens carry a low alpha in every palette (static and Material You),
 * which leaves adjacent cards and the ring's empty arc hard to tell apart on
 * some screens. Scaling their alpha up — capped so it never turns into a hard
 * black rule — keeps the same hue but makes the edge read. Applied centrally in
 * [DhikrTheme], so every `colors.line` / `colors.track` consumer picks it up.
 */
fun DhikrColorTokens.withStrongBorders(): DhikrColorTokens = copy(
    line = line.copy(alpha = (line.alpha * 2.6f).coerceAtMost(0.45f)),
    track = track.copy(alpha = (track.alpha * 2.6f).coerceAtMost(0.40f)),
)

/**
 * "High contrast" palettes: pure black/white backgrounds and text, darker
 * accents, and opaque-ish borders. Body text, [DhikrColorTokens.dim] and
 * [DhikrColorTokens.faint] all clear WCAG AAA (7:1) on [DhikrColorTokens.bg].
 * Takes precedence over Material You, since wallpaper-derived colors can't
 * guarantee a contrast ratio.
 */
val HighContrastLightDhikrColors = DhikrColorTokens(
    bg = Color(0xFFFFFFFF),
    surface = Color(0xFFF0F0F0),
    card = Color(0xFFFFFFFF),
    text = Color(0xFF000000),
    dim = Color(0xFF1F1F1F),
    faint = Color(0xFF4A4A4A),
    line = Color(0x99000000),
    sage = Color(0xFF2F4A12),
    sageSoft = Color(0xFFE3F0CC),
    sageMid = Color(0xFF4F6B2A),
    terra = Color(0xFF8A3A00),
    terraSoft = Color(0xFFFFE1D0),
    track = Color(0x59000000),
    onSage = Color(0xFFFFFFFF),
)

val HighContrastDarkDhikrColors = DhikrColorTokens(
    bg = Color(0xFF000000),
    surface = Color(0xFF141414),
    card = Color(0xFF0A0A0A),
    text = Color(0xFFFFFFFF),
    dim = Color(0xFFE6E6E6),
    faint = Color(0xFFBDBDBD),
    line = Color(0xB3FFFFFF),
    sage = Color(0xFFC9E0A0),
    sageSoft = Color(0xFF24310F),
    sageMid = Color(0xFF9CB872),
    terra = Color(0xFFFFB27F),
    terraSoft = Color(0xFF3A1C08),
    track = Color(0x66FFFFFF),
    onSage = Color(0xFF000000),
)

val DarkDhikrColors = DhikrColorTokens(
    bg = Color(0xFF1C1A17),
    surface = Color(0xFF2A261F),
    card = Color(0xFF332E26),
    text = Color(0xFFF6EFE2),
    dim = Color(0xFFC0B6A5),
    faint = Color(0xFF82796A),
    line = Color(0x1FF6EFE2), // rgba(246,239,226,.12)
    sage = Color(0xFFAEBF92),
    sageSoft = Color(0xFF3D472B),
    sageMid = Color(0xFF728157),
    terra = Color(0xFFF6A06B),
    terraSoft = Color(0xFF4D2F18),
    track = Color(0x1AF6EFE2), // rgba(246,239,226,.10)
    onSage = Color(0xFF272E1B),
)
