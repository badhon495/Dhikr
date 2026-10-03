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
import com.dhikr.app.core.datastore.ThemeMode
import java.util.Calendar
import kotlinx.coroutines.delay

/** Light hours for [ThemeMode.AUTO_TIME]: 7am (inclusive) to 6pm (exclusive). */
private val autoLightHours = 7..17

private fun isDaylightHourNow(): Boolean =
    Calendar.getInstance().get(Calendar.HOUR_OF_DAY) in autoLightHours

val LocalDhikrColors = compositionLocalOf { LightDhikrColors }

object DhikrTheme {
    val colors: DhikrColorTokens
        @Composable get() = LocalDhikrColors.current
}

/** Resolves the user's [ThemeMode] against the system dark/light signal, or
 *  (for [ThemeMode.AUTO_TIME]) the wall clock. The clock read is re-checked
 *  once a minute so a session left open across the 7am/6pm boundary flips
 *  without needing a restart. */
@Composable
fun ThemeMode.resolveIsDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.AUTO_TIME -> {
        val isDaylight by produceState(initialValue = isDaylightHourNow()) {
            while (true) {
                value = isDaylightHourNow()
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
    content: @Composable () -> Unit,
) {
    val darkTheme = themeMode.resolveIsDark()
    val useDynamic = dynamicColor && supportsDynamicColor()
    val context = LocalContext.current
    val tokens = when {
        useDynamic -> dynamicDhikrColors(context, darkTheme)
        darkTheme -> DarkDhikrColors
        else -> LightDhikrColors
    }.let { if (strongBorders) it.withStrongBorders() else it }
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
