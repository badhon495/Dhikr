package com.dhikr.app.core.utilities

import android.content.Context
import android.provider.Settings

/** The practical floor for [android.view.WindowManager.LayoutParams.screenBrightness]
 *  — low enough to read as "minimum" without going to 0f, which some panels
 *  clip to the same black level as the screen simply being off. */
const val MIN_SCREEN_BRIGHTNESS = 0.01f

/**
 * The device's current system brightness, normalized to the 0f-1f scale
 * [android.view.WindowManager.LayoutParams.screenBrightness] uses — as
 * opposed to [Settings.System.SCREEN_BRIGHTNESS]'s legacy 0-255 int scale.
 * Used as the dim slider's upper bound: "light mode" is whatever brightness
 * the user already has set system-wide, so the slider never offers to go
 * brighter than that.
 */
fun currentSystemBrightnessFraction(context: Context): Float {
    val raw = try {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
    } catch (_: Settings.SettingNotFoundException) {
        return 1f
    }
    return (raw / 255f).coerceIn(MIN_SCREEN_BRIGHTNESS, 1f)
}
