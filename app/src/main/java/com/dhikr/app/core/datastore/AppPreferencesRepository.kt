package com.dhikr.app.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dhikr.app.core.ai.BenefitsLanguage
import com.dhikr.app.core.prayer.AsrMethod
import com.dhikr.app.core.prayer.CalculationMethod
import com.dhikr.app.core.prayer.DEFAULT_PRAYER_OFFSET_MINUTES
import com.dhikr.app.core.prayer.Prayer
import com.dhikr.app.core.prayer.PrayerSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.preferencesDataStore by preferencesDataStore(name = "preferences")

/** User's app-theme choice. SYSTEM defers to the device dark/light setting;
 *  AUTO_TIME switches light/dark by the clock (see [resolveIsDark]). */
enum class ThemeMode { SYSTEM, LIGHT, DARK, AUTO_TIME }

const val DEFAULT_AUTO_LIGHT_START = 7 * 60
const val DEFAULT_AUTO_DARK_START = 18 * 60

/** Default dim level (5% brightness) for [AppPreferencesRepository.dimBrightnessLevel]. */
const val DEFAULT_DIM_BRIGHTNESS_LEVEL = 0.05f

/**
 * When the counter vibrates.
 *  - OFF: never
 *  - EVERY_TAP: on every registered count
 *  - LAP_ONLY: only when a lap completes
 */
enum class HapticMode { OFF, EVERY_TAP, LAP_ONLY }

/** Which script the counter screen shows for a dhikr. The chosen one also
 *  becomes the mandatory field in the tasbih editor. */
enum class CounterScript { PRONUNCIATION, ARABIC }

/** Size of the dhikr text on the counter screen. */
enum class CounterTextSize(val scale: Float) { SMALL(0.85f), DEFAULT(1f), LARGE(1.2f), EXTRA_LARGE(1.4f) }

/** Preset weekly-goal targets; 0 means no weekly goal. */
val WEEKLY_GOAL_OPTIONS = listOf(0, 2_000, 3_500, 7_000)

class AppPreferencesRepository(private val context: Context) {
    private val dailyGoalKey = intPreferencesKey("daily_goal_target")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val autoLightStartKey = intPreferencesKey("auto_light_start")
    private val autoDarkStartKey = intPreferencesKey("auto_dark_start")
    private val hapticsEnabledKey =booleanPreferencesKey("haptics_enabled")
    private val hapticModeKey = stringPreferencesKey("haptic_mode")
    private val reducedMotionKey = booleanPreferencesKey("reduced_motion")
    private val strongBordersKey = booleanPreferencesKey("strong_borders")
    private val dynamicColorKey = booleanPreferencesKey("dynamic_color")
    private val counterScriptKey = stringPreferencesKey("counter_script")
    private val autoCounterEnabledKey = booleanPreferencesKey("auto_counter_enabled")
    private val keepScreenOnKey = booleanPreferencesKey("keep_screen_on")
    private val volumeKeyCountingKey = booleanPreferencesKey("volume_key_counting")
    private val weeklyGoalKey = intPreferencesKey("weekly_goal_target")
    private val counterTextSizeKey = stringPreferencesKey("counter_text_size")
    private val hijriAdjustKey = intPreferencesKey("hijri_adjust_days")
    private val prayerLatKey = doublePreferencesKey("prayer_latitude")
    private val prayerLngKey = doublePreferencesKey("prayer_longitude")
    private val prayerLocationNameKey = stringPreferencesKey("prayer_location_name")
    private val prayerMethodKey = stringPreferencesKey("prayer_method")
    private val prayerAsrKey = stringPreferencesKey("prayer_asr")
    private val prayerMaskKey = intPreferencesKey("prayer_reminders_mask")
    private val prayerOffsetKey = intPreferencesKey("prayer_offset_minutes")
    private val fastingReminderKey = booleanPreferencesKey("fasting_reminder_enabled")
    private fun prayerRoutineKey(prayer: Prayer) = stringPreferencesKey("prayer_routine_${prayer.name}")
    private val hasSeenOnboardingKey = booleanPreferencesKey("has_seen_onboarding")
    private val dimScreenEnabledKey = booleanPreferencesKey("dim_screen_enabled")
    private val dimBrightnessLevelKey = floatPreferencesKey("dim_brightness_level")
    private val benefitsLanguageKey = stringPreferencesKey("benefits_language")
    private val benefitsPromptOverrideKey = stringPreferencesKey("benefits_prompt_override")

    val dailyGoalTarget = context.preferencesDataStore.data.map { it[dailyGoalKey] ?: 500 }

    suspend fun setDailyGoalTarget(value: Int) {
        context.preferencesDataStore.edit { it[dailyGoalKey] = value }
    }

    val themeMode = context.preferencesDataStore.data.map { prefs ->
        // Unknown/absent value falls back to SYSTEM rather than crashing on a
        // stale or hand-edited store (plan.md §57).
        when (prefs[themeModeKey]) {
            ThemeMode.LIGHT.name -> ThemeMode.LIGHT
            ThemeMode.DARK.name -> ThemeMode.DARK
            ThemeMode.AUTO_TIME.name -> ThemeMode.AUTO_TIME
            else -> ThemeMode.SYSTEM
        }
    }

    suspend fun setThemeMode(value: ThemeMode) {
        context.preferencesDataStore.edit { it[themeModeKey] = value.name }
    }

    /** Minute-of-day at which [ThemeMode.AUTO_TIME] switches to light. Default 7:00. */
    val autoLightStart = context.preferencesDataStore.data.map { it[autoLightStartKey] ?: DEFAULT_AUTO_LIGHT_START }

    /** Minute-of-day at which [ThemeMode.AUTO_TIME] switches to dark. Default 18:00. */
    val autoDarkStart = context.preferencesDataStore.data.map { it[autoDarkStartKey] ?: DEFAULT_AUTO_DARK_START }

    suspend fun setAutoLightStart(minuteOfDay: Int) {
        context.preferencesDataStore.edit { it[autoLightStartKey] = minuteOfDay.coerceIn(0, 1439) }
    }

    suspend fun setAutoDarkStart(minuteOfDay: Int) {
        context.preferencesDataStore.edit { it[autoDarkStartKey] = minuteOfDay.coerceIn(0, 1439) }
    }

    /**
     * When the counter vibrates. Falls back to the pre-3.x boolean
     * `haptics_enabled` so an existing user's choice carries over: false →
     * OFF, true → EVERY_TAP. If neither key was ever set (fresh install),
     * defaults to LAP_ONLY. An unknown stored string also falls back to
     * LAP_ONLY rather than crashing on a hand-edited store.
     */
    val hapticMode = context.preferencesDataStore.data.map { prefs ->
        when (prefs[hapticModeKey]) {
            HapticMode.OFF.name -> HapticMode.OFF
            HapticMode.EVERY_TAP.name -> HapticMode.EVERY_TAP
            HapticMode.LAP_ONLY.name -> HapticMode.LAP_ONLY
            else -> when (prefs[hapticsEnabledKey]) {
                false -> HapticMode.OFF
                true -> HapticMode.EVERY_TAP
                else -> HapticMode.LAP_ONLY
            }
        }
    }

    suspend fun setHapticMode(value: HapticMode) {
        context.preferencesDataStore.edit { it[hapticModeKey] = value.name }
    }

    val reducedMotion = context.preferencesDataStore.data.map { it[reducedMotionKey] ?: false }

    suspend fun setReducedMotion(value: Boolean) {
        context.preferencesDataStore.edit { it[reducedMotionKey] = value }
    }

    /** Heavier card borders, section dividers and ring track — a legibility aid
     *  for when the soft default hairlines blur adjacent surfaces together.
     *  Off by default; applied in [com.dhikr.app.ui.theme.DhikrTheme]. */
    val strongBorders = context.preferencesDataStore.data.map { it[strongBordersKey] ?: false }

    suspend fun setStrongBorders(value: Boolean) {
        context.preferencesDataStore.edit { it[strongBordersKey] = value }
    }

    /** Material You: derive the app palette from the device wallpaper. Only
     *  honoured on Android 12+ (see [DhikrTheme]); the store value is kept
     *  regardless so toggling back and forth on an older device is harmless.
     *  Defaults on: unset means the user has never touched the toggle, and
     *  Material You is the intended out-of-the-box look on supported devices. */
    val dynamicColorEnabled = context.preferencesDataStore.data.map { it[dynamicColorKey] ?: true }

    suspend fun setDynamicColorEnabled(value: Boolean) {
        context.preferencesDataStore.edit { it[dynamicColorKey] = value }
    }

    /** Script shown on the counter. Absent/unknown falls back to PRONUNCIATION. */
    val counterScript = context.preferencesDataStore.data.map { prefs ->
        when (prefs[counterScriptKey]) {
            CounterScript.ARABIC.name -> CounterScript.ARABIC
            else -> CounterScript.PRONUNCIATION
        }
    }

    suspend fun setCounterScript(value: CounterScript) {
        context.preferencesDataStore.edit { it[counterScriptKey] = value.name }
    }

    /** Off by default (plan.md §40: "keep it disabled by default"). */
    val autoCounterEnabled = context.preferencesDataStore.data.map { it[autoCounterEnabledKey] ?: false }

    suspend fun setAutoCounterEnabled(value: Boolean) {
        context.preferencesDataStore.edit { it[autoCounterEnabledKey] = value }
    }

    /** Keeps the display on while the Counter screen is open and counting
     *  (plan.md §34). Off by default — never applied globally. */
    val keepScreenOn = context.preferencesDataStore.data.map { it[keepScreenOnKey] ?: false }

    suspend fun setKeepScreenOn(value: Boolean) {
        context.preferencesDataStore.edit { it[keepScreenOnKey] = value }
    }

    /** Hardware volume buttons count on the Counter screen instead of changing
     *  the volume. Off by default, since it overrides a system control. */
    val volumeKeyCounting = context.preferencesDataStore.data.map { it[volumeKeyCountingKey] ?: false }

    suspend fun setVolumeKeyCounting(value: Boolean) {
        context.preferencesDataStore.edit { it[volumeKeyCountingKey] = value }
    }

    /** Rolling seven-day goal shown on Insights; 0 (default) = off. */
    val weeklyGoalTarget = context.preferencesDataStore.data.map { it[weeklyGoalKey] ?: 0 }

    suspend fun setWeeklyGoalTarget(value: Int) {
        context.preferencesDataStore.edit { it[weeklyGoalKey] = value.coerceAtLeast(0) }
    }

    /** Absent/unknown falls back to DEFAULT. */
    val counterTextSize = context.preferencesDataStore.data.map { prefs ->
        CounterTextSize.entries.firstOrNull { it.name == prefs[counterTextSizeKey] } ?: CounterTextSize.DEFAULT
    }

    suspend fun setCounterTextSize(value: CounterTextSize) {
        context.preferencesDataStore.edit { it[counterTextSizeKey] = value.name }
    }

    /** Days added to the computed Hijri date to match local moon sighting. */
    val hijriAdjustDays = context.preferencesDataStore.data.map { (it[hijriAdjustKey] ?: 0).coerceIn(-2, 2) }

    suspend fun setHijriAdjustDays(value: Int) {
        context.preferencesDataStore.edit { it[hijriAdjustKey] = value.coerceIn(-2, 2) }
    }

    val prayerSettings = context.preferencesDataStore.data.map { prefs ->
        PrayerSettings(
            latitude = prefs[prayerLatKey],
            longitude = prefs[prayerLngKey],
            locationName = prefs[prayerLocationNameKey],
            method = CalculationMethod.entries.firstOrNull { it.name == prefs[prayerMethodKey] }
                ?: CalculationMethod.KARACHI,
            asr = AsrMethod.entries.firstOrNull { it.name == prefs[prayerAsrKey] } ?: AsrMethod.HANAFI,
            remindersMask = prefs[prayerMaskKey] ?: 0,
            offsetMinutes = prefs[prayerOffsetKey] ?: DEFAULT_PRAYER_OFFSET_MINUTES,
            routineIds = Prayer.entries.mapNotNull { p -> prefs[prayerRoutineKey(p)]?.let { p to it } }.toMap(),
        )
    }

    suspend fun setPrayerRoutine(prayer: Prayer, routineId: String) {
        context.preferencesDataStore.edit { it[prayerRoutineKey(prayer)] = routineId }
    }

    suspend fun setPrayerLocation(latitude: Double, longitude: Double, name: String?) {
        context.preferencesDataStore.edit { prefs ->
            prefs[prayerLatKey] = latitude.coerceIn(-90.0, 90.0)
            prefs[prayerLngKey] = longitude.coerceIn(-180.0, 180.0)
            if (name == null) prefs.remove(prayerLocationNameKey) else prefs[prayerLocationNameKey] = name
        }
    }

    suspend fun setPrayerMethod(value: CalculationMethod) {
        context.preferencesDataStore.edit { it[prayerMethodKey] = value.name }
    }

    suspend fun setPrayerAsr(value: AsrMethod) {
        context.preferencesDataStore.edit { it[prayerAsrKey] = value.name }
    }

    suspend fun setPrayerRemindersMask(value: Int) {
        context.preferencesDataStore.edit { it[prayerMaskKey] = value and 0x1F }
    }

    suspend fun setPrayerOffsetMinutes(value: Int) {
        context.preferencesDataStore.edit { it[prayerOffsetKey] = value.coerceIn(0, 120) }
    }

    /** Evening-before reminder for Sunnah fasts; off by default. */
    val fastingReminderEnabled = context.preferencesDataStore.data.map { it[fastingReminderKey] ?: false }

    suspend fun setFastingReminderEnabled(value: Boolean) {
        context.preferencesDataStore.edit { it[fastingReminderKey] = value }
    }

    /** Language the AI benefits write-up is generated in. Absent/unknown falls
     *  back to ENGLISH. Only touches the Gemini prompt/response, not the app UI. */
    val benefitsLanguage = context.preferencesDataStore.data.map { prefs ->
        when (prefs[benefitsLanguageKey]) {
            BenefitsLanguage.BANGLA.name -> BenefitsLanguage.BANGLA
            else -> BenefitsLanguage.ENGLISH
        }
    }

    suspend fun setBenefitsLanguage(value: BenefitsLanguage) {
        context.preferencesDataStore.edit { it[benefitsLanguageKey] = value.name }
    }

    /** User's edited benefits prompt template, or null to use the built-in
     *  template for [benefitsLanguage]. Blank is stored as "cleared" (null). */
    val benefitsPromptOverride = context.preferencesDataStore.data.map {
        it[benefitsPromptOverrideKey]?.takeIf { s -> s.isNotBlank() }
    }

    suspend fun setBenefitsPromptOverride(value: String?) {
        context.preferencesDataStore.edit { prefs ->
            val trimmed = value?.takeIf { it.isNotBlank() }
            if (trimmed == null) prefs.remove(benefitsPromptOverrideKey)
            else prefs[benefitsPromptOverrideKey] = trimmed
        }
    }

    /** One-shot UX flag, not a real preference — never wired into backup/restore
     *  (plan.md §25 onboarding is per-device, not something worth carrying
     *  across a backup). Absent (fresh install) means false: show onboarding. */
    val hasSeenOnboarding = context.preferencesDataStore.data.map { it[hasSeenOnboardingKey] ?: false }

    suspend fun setHasSeenOnboarding(value: Boolean) {
        context.preferencesDataStore.edit { it[hasSeenOnboardingKey] = value }
    }

    /** Dims the screen to a fixed low brightness app-wide, toggled from the
     *  Counter screen's top bar. Off by default. Applied in [MainActivity]. */
    val dimScreenEnabled = context.preferencesDataStore.data.map { it[dimScreenEnabledKey] ?: false }

    suspend fun setDimScreenEnabled(value: Boolean) {
        context.preferencesDataStore.edit { it[dimScreenEnabledKey] = value }
    }

    /** How dark the dim toggle makes the screen (0.01-1.0 fraction of max
     *  brightness), user-adjustable via the Counter screen's dim slider.
     *  Only takes effect while [dimScreenEnabled] is on — "off" always means
     *  the device's normal (unmanaged) brightness, not a stored level. */
    val dimBrightnessLevel = context.preferencesDataStore.data.map {
        it[dimBrightnessLevelKey] ?: DEFAULT_DIM_BRIGHTNESS_LEVEL
    }

    suspend fun setDimBrightnessLevel(value: Float) {
        context.preferencesDataStore.edit { it[dimBrightnessLevelKey] = value.coerceIn(0.01f, 1f) }
    }

    /**
     * Synchronous read of every preference the Settings screen shows. It seeds
     * its initial UI state with this so the first frame already reflects the
     * saved choices rather than the data-class defaults — otherwise every pill
     * and toggle (and the sections whose height depends on a value) visibly
     * snaps once the Flows emit, reflowing the page on each visit.
     *
     * Safe to block here: by the time Settings is reachable the DataStore file
     * has already been read (MainActivity opens the same store on launch), so
     * every `first()` returns from the in-memory cache without disk I/O.
     */
    fun settingsInitialValues(): SettingsInitialValues = runBlocking {
        SettingsInitialValues(
            themeMode = themeMode.first(),
            hapticMode = hapticMode.first(),
            reducedMotion = reducedMotion.first(),
            strongBorders = strongBorders.first(),
            dailyGoalTarget = dailyGoalTarget.first(),
            dynamicColorEnabled = dynamicColorEnabled.first(),
            counterScript = counterScript.first(),
            autoCounterEnabled = autoCounterEnabled.first(),
            keepScreenOn = keepScreenOn.first(),
            volumeKeyCounting = volumeKeyCounting.first(),
            weeklyGoalTarget = weeklyGoalTarget.first(),
            counterTextSize = counterTextSize.first(),
            hijriAdjustDays = hijriAdjustDays.first(),
            prayerSettings = prayerSettings.first(),
            fastingReminderEnabled = fastingReminderEnabled.first(),
            benefitsLanguage = benefitsLanguage.first(),
            benefitsPromptOverride = benefitsPromptOverride.first(),
        )
    }

    /** One-shot read of every user-facing preference, for a backup export. */
    suspend fun snapshot(): PreferencesSnapshot = PreferencesSnapshot(
        dailyGoalTarget = dailyGoalTarget.first(),
        themeMode = themeMode.first(),
        hapticMode = hapticMode.first(),
        reducedMotion = reducedMotion.first(),
        strongBorders = strongBorders.first(),
        dynamicColorEnabled = dynamicColorEnabled.first(),
        counterScript = counterScript.first(),
        benefitsLanguage = benefitsLanguage.first(),
        benefitsPromptOverride = benefitsPromptOverride.first(),
        autoLightStart = autoLightStart.first(),
        autoDarkStart = autoDarkStart.first(),
        keepScreenOn = keepScreenOn.first(),
        volumeKeyCounting = volumeKeyCounting.first(),
        weeklyGoalTarget = weeklyGoalTarget.first(),
        counterTextSize = counterTextSize.first(),
        hijriAdjustDays = hijriAdjustDays.first(),
        prayerSettings = prayerSettings.first(),
        fastingReminderEnabled = fastingReminderEnabled.first(),
    )

    /**
     * Apply a snapshot from a restored backup. Each field is optional: a null
     * leaves the current value untouched, so a backup written by an older app
     * version that lacked a field is harmless.
     */
    suspend fun restore(
        dailyGoalTarget: Int?,
        themeMode: ThemeMode?,
        hapticMode: HapticMode?,
        reducedMotion: Boolean?,
        strongBorders: Boolean?,
        dynamicColorEnabled: Boolean?,
        counterScript: CounterScript?,
        benefitsLanguage: BenefitsLanguage?,
        benefitsPromptOverride: String?,
        autoLightStart: Int? = null,
        autoDarkStart: Int? = null,
        keepScreenOn: Boolean? = null,
        volumeKeyCounting: Boolean? = null,
        weeklyGoalTarget: Int? = null,
        counterTextSize: CounterTextSize? = null,
        hijriAdjustDays: Int? = null,
        prayerSettings: PrayerSettings? = null,
        fastingReminderEnabled: Boolean? = null,
    ) {
        context.preferencesDataStore.edit { prefs ->
            fastingReminderEnabled?.let { prefs[fastingReminderKey] = it }
            autoLightStart?.let { prefs[autoLightStartKey] = it.coerceIn(0, 1439) }
            autoDarkStart?.let { prefs[autoDarkStartKey] = it.coerceIn(0, 1439) }
            keepScreenOn?.let { prefs[keepScreenOnKey] = it }
            volumeKeyCounting?.let { prefs[volumeKeyCountingKey] = it }
            weeklyGoalTarget?.let { prefs[weeklyGoalKey] = it.coerceAtLeast(0) }
            counterTextSize?.let { prefs[counterTextSizeKey] = it.name }
            hijriAdjustDays?.let { prefs[hijriAdjustKey] = it.coerceIn(-2, 2) }
            prayerSettings?.let { p ->
                val lat = p.latitude
                val lng = p.longitude
                if (lat != null && lng != null) {
                    prefs[prayerLatKey] = lat.coerceIn(-90.0, 90.0)
                    prefs[prayerLngKey] = lng.coerceIn(-180.0, 180.0)
                    val name = p.locationName
                    if (name == null) prefs.remove(prayerLocationNameKey) else prefs[prayerLocationNameKey] = name
                }
                prefs[prayerMethodKey] = p.method.name
                prefs[prayerAsrKey] = p.asr.name
                prefs[prayerMaskKey] = p.remindersMask and 0x1F
                prefs[prayerOffsetKey] = p.offsetMinutes.coerceIn(0, 120)
                p.routineIds.forEach { (prayer, id) -> prefs[prayerRoutineKey(prayer)] = id }
            }
            dailyGoalTarget?.let { prefs[dailyGoalKey] = it }
            themeMode?.let { prefs[themeModeKey] = it.name }
            hapticMode?.let { prefs[hapticModeKey] = it.name }
            reducedMotion?.let { prefs[reducedMotionKey] = it }
            strongBorders?.let { prefs[strongBordersKey] = it }
            dynamicColorEnabled?.let { prefs[dynamicColorKey] = it }
            counterScript?.let { prefs[counterScriptKey] = it.name }
            benefitsLanguage?.let { prefs[benefitsLanguageKey] = it.name }
            benefitsPromptOverride?.takeIf { it.isNotBlank() }?.let { prefs[benefitsPromptOverrideKey] = it }
        }
    }
}

/** The Settings screen's initial UI values, read synchronously at construction
 *  — see [AppPreferencesRepository.settingsInitialValues]. */
data class SettingsInitialValues(
    val themeMode: ThemeMode,
    val hapticMode: HapticMode,
    val reducedMotion: Boolean,
    val strongBorders: Boolean,
    val dailyGoalTarget: Int,
    val dynamicColorEnabled: Boolean,
    val counterScript: CounterScript,
    val autoCounterEnabled: Boolean,
    val keepScreenOn: Boolean,
    val volumeKeyCounting: Boolean,
    val weeklyGoalTarget: Int,
    val counterTextSize: CounterTextSize,
    val hijriAdjustDays: Int,
    val prayerSettings: PrayerSettings,
    val fastingReminderEnabled: Boolean,
    val benefitsLanguage: BenefitsLanguage,
    val benefitsPromptOverride: String?,
)

data class PreferencesSnapshot(
    val dailyGoalTarget: Int,
    val themeMode: ThemeMode,
    val hapticMode: HapticMode,
    val reducedMotion: Boolean,
    val strongBorders: Boolean,
    val dynamicColorEnabled: Boolean,
    val counterScript: CounterScript,
    val benefitsLanguage: BenefitsLanguage,
    val benefitsPromptOverride: String?,
    val autoLightStart: Int,
    val autoDarkStart: Int,
    val keepScreenOn: Boolean,
    val volumeKeyCounting: Boolean,
    val weeklyGoalTarget: Int,
    val counterTextSize: CounterTextSize,
    val hijriAdjustDays: Int,
    val prayerSettings: PrayerSettings,
    val fastingReminderEnabled: Boolean,
)
