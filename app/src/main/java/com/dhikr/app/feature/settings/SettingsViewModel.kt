package com.dhikr.app.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dhikr.app.core.ai.BenefitsLanguage
import com.dhikr.app.core.localization.AppLanguage
import com.dhikr.app.core.ai.SecureKeyStore
import com.dhikr.app.core.ai.defaultBenefitsTemplate
import com.dhikr.app.DhikrApplication
import com.dhikr.app.core.counter.AutoCounterSensorListener
import com.dhikr.app.core.fasting.FastingReminder
import com.dhikr.app.core.localization.displayName
import com.dhikr.app.core.datastore.CounterTextSize
import com.dhikr.app.core.prayer.AsrMethod
import com.dhikr.app.core.prayer.CalculationMethod
import com.dhikr.app.core.prayer.Prayer
import com.dhikr.app.core.prayer.PrayerReminderScheduler
import com.dhikr.app.core.prayer.PrayerSettings
import com.dhikr.app.core.prayer.PrayerTimes
import kotlinx.coroutines.flow.distinctUntilChanged
import com.dhikr.app.core.datastore.AppPreferencesRepository
import com.dhikr.app.core.widget.DhikrWidgets
import com.dhikr.app.core.datastore.CounterScript
import com.dhikr.app.core.datastore.DEFAULT_AUTO_DARK_START
import com.dhikr.app.core.datastore.DEFAULT_AUTO_LIGHT_START
import com.dhikr.app.core.datastore.HapticMode
import com.dhikr.app.core.datastore.ThemeMode
import com.dhikr.app.ui.theme.supportsDynamicColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val autoLightStart: Int = DEFAULT_AUTO_LIGHT_START,
    val autoDarkStart: Int = DEFAULT_AUTO_DARK_START,
    val hapticMode: HapticMode = HapticMode.EVERY_TAP,
    val reducedMotion: Boolean = false,
    val strongBorders: Boolean = false,
    val dailyGoalTarget: Int = 500,
    val dynamicColorEnabled: Boolean = true,
    val dynamicColorSupported: Boolean = supportsDynamicColor(),
    val counterScript: CounterScript = CounterScript.PRONUNCIATION,
    val appLanguage: AppLanguage = AppLanguage.ENGLISH,
    val appVersion: String = "",
    val hasGeminiKey: Boolean = false,
    val autoCounterEnabled: Boolean = false,
    val autoCounterSupported: Boolean = true,
    val keepScreenOn: Boolean = false,
    val volumeKeyCounting: Boolean = false,
    val weeklyGoalTarget: Int = 0,
    val counterTextSize: CounterTextSize = CounterTextSize.DEFAULT,
    val hijriAdjustDays: Int = 0,
    val prayerSettings: PrayerSettings = PrayerSettings(),
    // Today's computed prayer times, shown beside each reminder toggle.
    val prayerTimesToday: Map<Prayer, Long> = emptyMap(),
    // (routine id, display name) choices for each prayer's reminder.
    val routineOptions: List<Pair<String, String>> = emptyList(),
    val fastingReminderEnabled: Boolean = false,
    val benefitsLanguage: BenefitsLanguage = BenefitsLanguage.ENGLISH,
    // null = no user override; the built-in template for [benefitsLanguage] is used.
    val benefitsPromptOverride: String? = null,
) {
    /** What the "Customize prompt" field shows: the user's override if set,
     *  otherwise the built-in template for the current language. */
    val effectiveBenefitsPrompt: String
        get() = benefitsPromptOverride ?: defaultBenefitsTemplate(benefitsLanguage)

    val hasBenefitsPromptOverride: Boolean get() = benefitsPromptOverride != null

    /** Common tasbih counts offered as quick-pick daily-goal targets. */
    val dailyGoalOptions: List<Int> get() = DAILY_GOAL_OPTIONS

    /** True when the current target isn't one of the quick-pick presets, i.e.
     *  it was set through the custom number field. */
    val isCustomGoal: Boolean get() = dailyGoalTarget !in DAILY_GOAL_OPTIONS

    companion object {
        val DAILY_GOAL_OPTIONS = listOf(500, 1000)
        const val DAILY_GOAL_MIN = 1
        const val DAILY_GOAL_MAX = 99_999
    }
}

class SettingsViewModel(
    private val preferencesRepository: AppPreferencesRepository,
    private val appVersion: String,
    private val appContext: Context,
    private val secureKeyStore: SecureKeyStore,
) : ViewModel() {

    // Cheap, synchronous hardware check (getSystemService + getDefaultSensor,
    // no listener registered) — safe to read once up front rather than as a
    // Flow, since it can't change while the app is running.
    private val autoCounterSupported = AutoCounterSensorListener(appContext).isSupported

    // Seed the state with the persisted values read synchronously, so the first
    // frame the screen paints already shows the saved choices. A default-valued
    // SettingsUiState here would make every pill, toggle and value-sized section
    // snap to its real state one frame later, reflowing the page on each visit.
    private val _uiState = MutableStateFlow(
        preferencesRepository.settingsInitialValues().let { p ->
            SettingsUiState(
                themeMode = p.themeMode,
                hapticMode = p.hapticMode,
                reducedMotion = p.reducedMotion,
                strongBorders = p.strongBorders,
                dailyGoalTarget = p.dailyGoalTarget,
                dynamicColorEnabled = p.dynamicColorEnabled,
                counterScript = p.counterScript,
                autoCounterEnabled = p.autoCounterEnabled,
                keepScreenOn = p.keepScreenOn,
                volumeKeyCounting = p.volumeKeyCounting,
                weeklyGoalTarget = p.weeklyGoalTarget,
                counterTextSize = p.counterTextSize,
                hijriAdjustDays = p.hijriAdjustDays,
                prayerSettings = p.prayerSettings,
                prayerTimesToday = todayTimes(p.prayerSettings),
                fastingReminderEnabled = p.fastingReminderEnabled,
                benefitsLanguage = p.benefitsLanguage,
                benefitsPromptOverride = p.benefitsPromptOverride,
                appVersion = appVersion,
                hasGeminiKey = secureKeyStore.hasKey,
                autoCounterSupported = autoCounterSupported,
                appLanguage = AppLanguage.current,
            )
        },
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        combine(
            preferencesRepository.themeMode,
            preferencesRepository.hapticMode,
            preferencesRepository.reducedMotion,
            preferencesRepository.dailyGoalTarget,
            preferencesRepository.dynamicColorEnabled,
        ) { themeMode, hapticMode, reducedMotion, dailyGoal, dynamicColor ->
            SettingsUiState(
                themeMode = themeMode,
                hapticMode = hapticMode,
                reducedMotion = reducedMotion,
                dailyGoalTarget = dailyGoal,
                dynamicColorEnabled = dynamicColor,
                appVersion = appVersion,
                hasGeminiKey = secureKeyStore.hasKey,
                autoCounterSupported = autoCounterSupported,
            )
        }
            .combine(preferencesRepository.strongBorders) { state, strongBorders ->
                state.copy(strongBorders = strongBorders)
            }
            .combine(preferencesRepository.autoLightStart) { state, v -> state.copy(autoLightStart = v) }
            .combine(preferencesRepository.autoDarkStart) { state, v -> state.copy(autoDarkStart = v) }
            .combine(preferencesRepository.counterScript) { state, counterScript ->
                state.copy(counterScript = counterScript)
            }
            .combine(preferencesRepository.autoCounterEnabled) { state, autoCounterEnabled ->
                state.copy(autoCounterEnabled = autoCounterEnabled)
            }
            .combine(preferencesRepository.keepScreenOn) { state, v -> state.copy(keepScreenOn = v) }
            .combine(preferencesRepository.volumeKeyCounting) { state, v -> state.copy(volumeKeyCounting = v) }
            .combine(preferencesRepository.weeklyGoalTarget) { state, v -> state.copy(weeklyGoalTarget = v) }
            .combine(preferencesRepository.counterTextSize) { state, v -> state.copy(counterTextSize = v) }
            .combine(preferencesRepository.hijriAdjustDays) { state, v -> state.copy(hijriAdjustDays = v) }
            .combine(preferencesRepository.prayerSettings) { state, v ->
                state.copy(prayerSettings = v, prayerTimesToday = todayTimes(v))
            }
            .combine(preferencesRepository.fastingReminderEnabled) { state, v ->
                state.copy(fastingReminderEnabled = v)
            }
            .combine(routineOptionsFlow()) { state, v -> state.copy(routineOptions = v) }
            .combine(preferencesRepository.benefitsLanguage) { state, benefitsLanguage ->
                state.copy(benefitsLanguage = benefitsLanguage)
            }
            .combine(preferencesRepository.benefitsPromptOverride) { state, override ->
                state.copy(benefitsPromptOverride = override)
            }
            // Language lives outside DataStore (AppCompatDelegate owns it), so
            // carry the value read at construction through each rebuild.
            .onEach { _uiState.value = it.copy(appLanguage = _uiState.value.appLanguage) }
            .launchIn(viewModelScope)

        // Keep the alarms in step with the prayer settings: every change (and
        // the first emission, which also heals alarms lost to a force-stop)
        // re-arms or cancels each prayer's next reminder.
        preferencesRepository.prayerSettings
            .distinctUntilChanged()
            .onEach { prayerScheduler.apply(it) }
            .launchIn(viewModelScope)
        preferencesRepository.fastingReminderEnabled
            .distinctUntilChanged()
            .onEach { FastingReminder.apply(appContext, it) }
            .launchIn(viewModelScope)
    }

    private fun routineOptionsFlow() =
        (appContext as DhikrApplication).database.routineDao().observeAllWithSteps()
            .combine(AppLanguage.state) { routines, lang ->
                routines.map { it.routine.id to it.routine.displayName(lang) }
            }

    fun onPrayerRoutineChange(prayer: Prayer, routineId: String) {
        viewModelScope.launch { preferencesRepository.setPrayerRoutine(prayer, routineId) }
    }

    fun onFastingReminderChange(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setFastingReminderEnabled(enabled) }
    }

    private val prayerScheduler get() = PrayerReminderScheduler(appContext)

    private fun todayTimes(settings: PrayerSettings): Map<Prayer, Long> {
        val lat = settings.latitude ?: return emptyMap()
        val lng = settings.longitude ?: return emptyMap()
        return PrayerTimes.forDay(System.currentTimeMillis(), lat, lng, settings.method, settings.asr)
    }

    fun onWeeklyGoalChange(value: Int) {
        viewModelScope.launch { preferencesRepository.setWeeklyGoalTarget(value) }
    }

    fun onCounterTextSizeChange(value: CounterTextSize) {
        viewModelScope.launch { preferencesRepository.setCounterTextSize(value) }
    }

    fun onHijriAdjustChange(value: Int) {
        viewModelScope.launch { preferencesRepository.setHijriAdjustDays(value) }
    }

    fun onPrayerLocationChange(latitude: Double, longitude: Double, name: String?) {
        viewModelScope.launch { preferencesRepository.setPrayerLocation(latitude, longitude, name) }
    }

    fun onPrayerMethodChange(value: CalculationMethod) {
        viewModelScope.launch { preferencesRepository.setPrayerMethod(value) }
    }

    fun onPrayerAsrChange(value: AsrMethod) {
        viewModelScope.launch { preferencesRepository.setPrayerAsr(value) }
    }

    fun onPrayerOffsetChange(minutes: Int) {
        viewModelScope.launch { preferencesRepository.setPrayerOffsetMinutes(minutes) }
    }

    fun onPrayerReminderToggle(prayer: Prayer, enabled: Boolean) {
        val mask = _uiState.value.prayerSettings.remindersMask
        val bit = 1 shl prayer.bit
        viewModelScope.launch {
            preferencesRepository.setPrayerRemindersMask(if (enabled) mask or bit else mask and bit.inv())
        }
    }

    fun onThemeModeChange(mode: ThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun onAutoLightStartChange(minuteOfDay: Int) {
        viewModelScope.launch { preferencesRepository.setAutoLightStart(minuteOfDay) }
    }

    fun onAutoDarkStartChange(minuteOfDay: Int) {
        viewModelScope.launch { preferencesRepository.setAutoDarkStart(minuteOfDay) }
    }

    fun onHapticModeChange(mode: HapticMode) {
        viewModelScope.launch { preferencesRepository.setHapticMode(mode) }
    }

    fun onReducedMotionChange(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setReducedMotion(enabled) }
    }

    fun onStrongBordersChange(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setStrongBorders(enabled) }
    }

    fun onDynamicColorChange(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setDynamicColorEnabled(enabled) }
    }

    fun onCounterScriptChange(value: CounterScript) {
        viewModelScope.launch { preferencesRepository.setCounterScript(value) }
    }

    /**
     * Switches the app language immediately. On API 33+ the system recreates the
     * activity; on older versions AppCompat does. Either way the new
     * [SettingsViewModel] reads the fresh value via [AppLanguage.current], so no
     * local state update is needed here.
     */
    fun onAppLanguageChange(value: AppLanguage) {
        if (value == _uiState.value.appLanguage) return
        AppLanguage.apply(value)
        // Reflect the choice right away so the pill updates even on devices/API
        // levels where the locale switch doesn't recreate this activity.
        _uiState.value = _uiState.value.copy(appLanguage = value)
        // Keep the AI benefits language in step with the app language by
        // default, so switching to Bangla doesn't leave benefit write-ups
        // generating in English until the user notices the separate setting.
        val matchingBenefitsLanguage = when (value) {
            AppLanguage.ENGLISH -> BenefitsLanguage.ENGLISH
            AppLanguage.BANGLA -> BenefitsLanguage.BANGLA
        }
        onBenefitsLanguageChange(matchingBenefitsLanguage)
    }

    fun onAutoCounterEnabledChange(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setAutoCounterEnabled(enabled) }
    }

    fun onKeepScreenOnChange(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setKeepScreenOn(enabled) }
    }

    fun onVolumeKeyCountingChange(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setVolumeKeyCounting(enabled) }
    }

    fun onDailyGoalChange(value: Int) {
        viewModelScope.launch {
            preferencesRepository.setDailyGoalTarget(
                value.coerceIn(SettingsUiState.DAILY_GOAL_MIN, SettingsUiState.DAILY_GOAL_MAX),
            )
            // The insights widget's "Today / goal" line is otherwise stale until
            // its next 30-min tick — push a refresh now that the goal changed.
            DhikrWidgets.refreshAll(appContext)
        }
    }

    fun onBenefitsLanguageChange(value: BenefitsLanguage) {
        viewModelScope.launch { preferencesRepository.setBenefitsLanguage(value) }
    }

    /** Persists an edited prompt template. Blank / equal-to-default clears the
     *  override so the built-in template is used again. */
    fun onBenefitsPromptChange(value: String) {
        viewModelScope.launch {
            val cleared = value.isBlank() ||
                value.trim() == defaultBenefitsTemplate(_uiState.value.benefitsLanguage).trim()
            preferencesRepository.setBenefitsPromptOverride(if (cleared) null else value)
        }
    }

    fun onResetBenefitsPrompt() {
        viewModelScope.launch { preferencesRepository.setBenefitsPromptOverride(null) }
    }

    fun saveGeminiKey(raw: String) {
        viewModelScope.launch {
            secureKeyStore.setGeminiKey(raw)
            _uiState.value = _uiState.value.copy(hasGeminiKey = secureKeyStore.hasKey)
        }
    }

    fun clearGeminiKey() {
        viewModelScope.launch {
            secureKeyStore.setGeminiKey(null)
            _uiState.value = _uiState.value.copy(hasGeminiKey = false)
        }
    }

    class Factory(
        private val preferencesRepository: AppPreferencesRepository,
        private val appVersion: String,
        private val appContext: Context,
        private val secureKeyStore: SecureKeyStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(preferencesRepository, appVersion, appContext, secureKeyStore) as T
    }
}
