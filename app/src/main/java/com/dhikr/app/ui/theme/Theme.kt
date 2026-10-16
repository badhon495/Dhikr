package com.dhikr.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import com.dhikr.app.core.datastore.DEFAULT_AUTO_DARK_START
import com.dhikr.app.core.datastore.DEFAULT_AUTO_LIGHT_START
import com.dhikr.app.core.datastore.ThemeMode
import java.util.Calendar
import kotlinx.coroutines.delay

/** True when now (minute-of-day) falls in the light window [lightStart, darkStart),
 *  wrapping past midnight when darkStart <= lightStart. */
private fun isDaylightNow(lightStart: Int, darkStart: Int): Boolean {
    val now = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
    return if (lightStart <= darkStart) now in lightStart until darkStart
    else now >= lightStart || now < darkStart
}

val LocalDhikrColors = compositionLocalOf { LightDhikrColors }

object DhikrTheme {
    val colors: DhikrColorTokens
        @Composable get() = LocalDhikrColors.current
}

/** Resolves the user's [ThemeMode] against the system dark/light signal, or
 *  (for [ThemeMode.AUTO_TIME]) the wall clock. The clock read is re-checked
 *  once a minute so a session left open across the light/dark boundary flips
 *  without needing a restart. */
@Composable
fun ThemeMode.resolveIsDark(
    autoLightStart: Int = DEFAULT_AUTO_LIGHT_START,
    autoDarkStart: Int = DEFAULT_AUTO_DARK_START,
): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.AUTO_TIME -> {
        val isDaylight by produceState(
            initialValue = isDaylightNow(autoLightStart, autoDarkStart),
            autoLightStart,
            autoDarkStart,
        ) {
            while (true) {
                value = isDaylightNow(autoLightStart, autoDarkStart)
                delay(60_000L)
            }
        }
        !isDaylight
    }
}

@Composable
fun DhikrTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    strongBorders: Boolean = false,
    autoLightStart: Int = DEFAULT_AUTO_LIGHT_START,
    autoDarkStart: Int = DEFAULT_AUTO_DARK_START,
    content: @Composable () -> Unit,
) {
    val darkTheme = themeMode.resolveIsDark(autoLightStart, autoDarkStart)
    val useDynamic = dynamicColor && supportsDynamicColor()
    val context = LocalContext.current
    val tokens = when {
        useDynamic -> dynamicDhikrColors(context, darkTheme, strongBorders)
        darkTheme -> DarkDhikrColors.let { if (strongBorders) it.withStrongBorders() else it }
        else -> LightDhikrColors.let { if (strongBorders) it.withStrongBorders() else it }
    }
    // Material3's default colorScheme (background ~white, surface ~white) is
    // otherwise left untouched by DhikrColorTokens — components that read it
    // directly instead of DhikrTheme.colors (Scaffold's containerColor,
    // NavigationBar's default containerColor, etc.) paint that default
    // regardless of dark/light app theme. Mapping the two closest fields onto
    // it is what keeps Scaffold's own background — the area under the status
    // bar, which app content never draws into — from showing as a stray white
    // strip in dark mode.
    val colorScheme = when {
        useDynamic -> dynamicColorScheme(context, darkTheme)
        darkTheme -> darkColorScheme(background = tokens.bg, surface = tokens.surface)
        else -> lightColorScheme(background = tokens.bg, surface = tokens.surface)
    }
    CompositionLocalProvider(LocalDhikrColors provides tokens) {
        MaterialTheme(colorScheme = colorScheme, typography = DhikrTypography, content = content)
    }
}
