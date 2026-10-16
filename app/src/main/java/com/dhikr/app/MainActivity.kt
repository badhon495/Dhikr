package com.dhikr.app

import android.content.Intent
import android.graphics.Color.TRANSPARENT
import android.net.Uri
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.dhikr.app.core.datastore.AppPreferencesRepository
import com.dhikr.app.core.datastore.DEFAULT_AUTO_DARK_START
import com.dhikr.app.core.datastore.DEFAULT_AUTO_LIGHT_START
import com.dhikr.app.core.datastore.DEFAULT_DIM_BRIGHTNESS_LEVEL
import com.dhikr.app.core.datastore.ThemeMode
import com.dhikr.app.core.notifications.ReminderNotifications
import com.dhikr.app.ui.theme.resolveIsDark

class MainActivity : AppCompatActivity() {

    // Set from the launch Intent (reminder-notification tap) and consumed once
    // by DhikrApp, which navigates to that routine's counter.
    private var pendingRoutineId by mutableStateOf<String?>(null)

    // Set from the launch Intent (per-tasbih reminder-notification tap) and
    // consumed once by DhikrApp, which navigates to that tasbih's counter.
    private var pendingTasbihId by mutableStateOf<String?>(null)

    // Set from the launch Intent (widget body tap) and consumed once by
    // DhikrApp, which navigates to the counter or insights tab.
    private var pendingOpen by mutableStateOf<String?>(null)

    // Set from an ACTION_VIEW launch (a tapped .dhikrroutine file) and consumed
    // once by DhikrApp, which navigates to the import preview.
    private var pendingShareUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingRoutineId = intent?.getStringExtra(ReminderNotifications.EXTRA_ROUTINE_ID)
        pendingTasbihId = intent?.getStringExtra(ReminderNotifications.EXTRA_TASBIH_ID)
        pendingOpen = intent?.getStringExtra(EXTRA_OPEN)
        pendingShareUri = if (intent?.action == Intent.ACTION_VIEW) intent?.data else null
        setContent {
            val context = LocalContext.current
            val preferencesRepository = remember {
                AppPreferencesRepository(context.applicationContext)
            }
            val themeMode by preferencesRepository.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val dynamicColor by preferencesRepository.dynamicColorEnabled.collectAsState(initial = true)
            val strongBorders by preferencesRepository.strongBorders.collectAsState(initial = false)
            val autoLightStart by preferencesRepository.autoLightStart
                .collectAsState(initial = DEFAULT_AUTO_LIGHT_START)
            val autoDarkStart by preferencesRepository.autoDarkStart
                .collectAsState(initial = DEFAULT_AUTO_DARK_START)
            val dimScreenEnabled by preferencesRepository.dimScreenEnabled.collectAsState(initial = false)
            val dimBrightnessLevel by preferencesRepository.dimBrightnessLevel
                .collectAsState(initial = DEFAULT_DIM_BRIGHTNESS_LEVEL)

            // Explicit transparent style, re-applied via SideEffect whenever the
            // resolved dark/light signal flips — now driven by the user's theme
            // choice as well as isSystemInDarkTheme(), the same input DhikrTheme
            // resolves for its own colors — instead of the plain no-arg
            // enableEdgeToEdge() default, which (combined with the window
            // background left at platform white, see themes.xml) was what made
            // the status bar render as an opaque white bar.
            val darkTheme = themeMode.resolveIsDark(autoLightStart, autoDarkStart)
            // Keyed on darkTheme (not a plain SideEffect) so this only
            // re-runs when the dark/light signal actually flips — enableEdgeToEdge
            // re-fetches and re-sets window.attributes wholesale, which was
            // clobbering the dim SideEffect's screenBrightness write whenever
            // both fired on the same recomposition (e.g. toggling dim).
            LaunchedEffect(darkTheme) {
                val style = if (darkTheme) {
                    SystemBarStyle.dark(TRANSPARENT)
                } else {
                    SystemBarStyle.light(TRANSPARENT, TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }

            // App-wide screen dim, toggled from the Counter screen's top bar and
            // leveled via its long-press slider. Window-level, so it applies no
            // matter which screen is visible and survives navigation — unlike a
            // per-screen brightness override. "Off" is -1f (WindowManager's
            // "defer to the device's own brightness"), not a stored level, so
            // light mode always tracks whatever the user has set system-wide.
            // Keyed on the two dim inputs (not a plain SideEffect), and delayed
            // past the current frame: enableEdgeToEdge's own window.attributes
            // write — even scoped to its own LaunchedEffect(darkTheme) above —
            // can still land in the same frame as this one and clobber whichever
            // write loses the race at the WindowManager/OS level. A short delay
            // lets that settle first so this write always lands last.
            LaunchedEffect(dimScreenEnabled, dimBrightnessLevel) {
                kotlinx.coroutines.delay(16L)
                val targetBrightness = if (dimScreenEnabled) dimBrightnessLevel else -1f
                val params = window.attributes
                if (params.screenBrightness != targetBrightness) {
                    params.screenBrightness = targetBrightness
                    window.attributes = params
                }
            }
            DhikrApp(
                themeMode = themeMode,
                dynamicColor = dynamicColor,
                strongBorders = strongBorders,
                autoLightStart = autoLightStart,
                autoDarkStart = autoDarkStart,
                pendingRoutineId = pendingRoutineId,
                onPendingRoutineConsumed = { pendingRoutineId = null },
                pendingTasbihId = pendingTasbihId,
                onPendingTasbihConsumed = { pendingTasbihId = null },
                pendingOpen = pendingOpen,
                onPendingOpenConsumed = { pendingOpen = null },
                pendingShareUri = pendingShareUri,
                onPendingShareConsumed = { pendingShareUri = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoutineId = intent.getStringExtra(ReminderNotifications.EXTRA_ROUTINE_ID)
        pendingTasbihId = intent.getStringExtra(ReminderNotifications.EXTRA_TASBIH_ID)
        pendingOpen = intent.getStringExtra(EXTRA_OPEN)
        pendingShareUri = if (intent.action == Intent.ACTION_VIEW) intent.data else null
    }

    companion object {
        const val EXTRA_OPEN = "com.dhikr.app.extra.OPEN"
        const val OPEN_COUNTER = "counter"
        const val OPEN_INSIGHTS = "insights"
    }
}
