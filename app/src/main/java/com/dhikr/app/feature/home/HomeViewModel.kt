package com.dhikr.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dhikr.app.core.counter.CounterDisplay
import com.dhikr.app.core.database.HistoryRepository
import com.dhikr.app.core.database.RoutineRepository
import com.dhikr.app.core.database.TasbihRepository
import com.dhikr.app.core.database.dao.RoutineWithSteps
import com.dhikr.app.core.database.entity.TasbihEntity
import com.dhikr.app.core.datastore.AppPreferencesRepository
import com.dhikr.app.core.datastore.SessionRepository
import com.dhikr.app.core.localization.AppLanguage
import com.dhikr.app.core.localization.displayName
import com.dhikr.app.core.model.CounterSessionState
import com.dhikr.app.core.prayer.Prayer
import com.dhikr.app.core.prayer.PrayerTimes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.dhikr.app.core.backup.BackupNudge
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import com.dhikr.app.core.utilities.HijriCalendar
import com.dhikr.app.core.utilities.HijriDate
import com.dhikr.app.core.utilities.IslamicSeason
import com.dhikr.app.core.utilities.IslamicSeasons
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class ContinueSessionInfo(val tasbihName: String, val count: Int, val target: Int)

data class CurrentWaqtInfo(val prayer: Prayer, val startMillis: Long, val nextStartMillis: Long?)

data class HomeUiState(
    val dateLabel: String = "",
    // Null if the platform calendar can't produce one (never expected on API 24+).
    val hijriDate: HijriDate? = null,
    val season: IslamicSeason? = null,
    val currentWaqt: CurrentWaqtInfo? = null,
    // Tasbih the seasonal banner opens, when that built-in still exists.
    val seasonTasbihId: String? = null,
    val dailyGoalTarget: Int = 500,
    val todayTotal: Int = 0,
    val continueSession: ContinueSessionInfo? = null,
    val favorites: List<TasbihEntity> = emptyList(),
    val routines: List<RoutineWithSteps> = emptyList(),
    // tasbihId -> display name, for the routine cards' step preview.
    val tasbihNamesById: Map<String, String> = emptyMap(),
    // Routines completed today — their Home card shows the full sage tint.
    val completedRoutineIds: Set<String> = emptySet(),
    // routineId -> 0f..1f of today's in-progress position; drives the card's
    // green fill. Both clear on their own the next day.
    val routineProgress: Map<String, Float> = emptyMap(),
    // tasbihId -> 0f..1f of today's counting position toward its total goal;
    // drives the favourite row's green fill. Clears on its own the next day.
    val tasbihProgress: Map<String, Float> = emptyMap(),
    // Gentle "back up your data" card — see BackupNudge for the rule.
    val showBackupNudge: Boolean = false,
)

/**
 * Every input here is now a live Flow — `historyRepository.todayTotalFlow()`
 * (Room-backed, invalidates on every `session` insert), DataStore's
 * `dailyGoalTarget`/`sessionFlow`, and Room's `observeFavorites()` /
 * `observeAllWithSteps()` — combined and re-collected reactively instead of
 * read once in init (finding #6). Because the bottom nav keeps this
 * ViewModel alive across Home → Counter → Home navigation, a one-shot read
 * would otherwise go stale until the ViewModel is recreated.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val tasbihRepository: TasbihRepository,
    private val routineRepository: RoutineRepository,
    private val historyRepository: HistoryRepository,
    private val sessionRepository: SessionRepository,
    private val preferencesRepository: AppPreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // Ticks every 30s so currentWaqt (Fajr/Dhuhr/.../Maghrib label) flips on
    // time instead of waiting for an unrelated DB/DataStore emission.
    private val clockTick = flow {
        while (true) {
            emit(Unit)
            delay(30_000)
        }
    }

    init {
        combine(
            preferencesRepository.dailyGoalTarget,
            historyRepository.todayTotalFlow(),
            tasbihRepository.observeFavorites(),
            routineRepository.observeAllWithSteps(),
            sessionRepository.sessionFlow,
        ) { dailyGoal, todayTotal, favorites, routines, session ->
            HomeInputs(dailyGoal, todayTotal, favorites, routines, session)
        }
            .combine(routineRepository.observeDayProgress()) { inputs, dayProgress ->
                inputs to dayProgress
            }
            .combine(tasbihRepository.observeSessionProgressToday()) { (inputs, dayProgress), tasbihProgress ->
                Triple(inputs, dayProgress, tasbihProgress)
            }
            .combine(tasbihRepository.observeAll()) { (inputs, dayProgress, tasbihProgress), allTasbihs ->
                HomeCombined(inputs, dayProgress, tasbihProgress, allTasbihs)
            }
            // Re-emit when the language changes so the DB-derived names below
            // are rebuilt — nothing upstream fires on a locale switch.
            .combine(AppLanguage.state) { combined, lang -> combined to lang }
            .combine(preferencesRepository.hijriAdjustDays) { pair, adjust -> Triple(pair.first, pair.second, adjust) }
            .combine(preferencesRepository.prayerSettings) { triple, prayerSettings -> triple to prayerSettings }
            .combine(clockTick) { pair, _ -> pair }
            .mapLatest { (triple, prayerSettings) ->
                val (combined, lang, hijriAdjust) = triple
                val (inputs, dayProgress, tasbihProgress, allTasbihs) = combined
                val tasbihNamesById = allTasbihs.associate { it.id to it.displayName(lang) }
                val continueInfo = inputs.session?.let { s ->
                    tasbihRepository.getById(s.activeDhikrId)?.let { tasbih ->
                        val (count, target) = CounterDisplay.progress(s.count, s.lap, tasbih.lapTarget, tasbih.lapCount)
                        ContinueSessionInfo(
                            tasbihName = tasbih.displayName(lang),
                            count = count,
                            target = target,
                        )
                    }
                }
                val now = System.currentTimeMillis()
                val hijri = runCatching { HijriCalendar.of(now, hijriAdjust) }.getOrNull()
                val tomorrowMillis = now + java.util.concurrent.TimeUnit.DAYS.toMillis(1)
                val tomorrowHijri = runCatching { HijriCalendar.of(tomorrowMillis, hijriAdjust) }.getOrNull()
                val season = if (hijri != null && tomorrowHijri != null) {
                    val cal = Calendar.getInstance()
                    val dow = cal.get(Calendar.DAY_OF_WEEK)
                    cal.add(Calendar.DAY_OF_MONTH, 1)
                    IslamicSeasons.detect(hijri, dow, tomorrowHijri, cal.get(Calendar.DAY_OF_WEEK))
                } else {
                    null
                }
                val seasonTasbihId = SEASON_TASBIH[season]?.takeIf { id -> allTasbihs.any { it.id == id } }
                val currentWaqt = if (prayerSettings.hasLocation) {
                    PrayerTimes.currentWaqt(
                        now, prayerSettings.latitude!!, prayerSettings.longitude!!,
                        prayerSettings.method, prayerSettings.asr,
                    )?.let { CurrentWaqtInfo(it.prayer, it.startMillis, it.nextStartMillis) }
                } else {
                    null
                }
                HomeUiState(
                    dateLabel = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(now)),
                    hijriDate = hijri,
                    season = season,
                    currentWaqt = currentWaqt,
                    seasonTasbihId = seasonTasbihId,
                    dailyGoalTarget = inputs.dailyGoal,
                    todayTotal = inputs.todayTotal,
                    continueSession = continueInfo,
                    favorites = inputs.favorites,
                    tasbihNamesById = tasbihNamesById,
                    // Home shows exactly the favorited routines. Unstarring the
                    // last one on the Routines page clears the section (Home then
                    // renders a hint). Full-width card list, so no cap.
                    routines = inputs.routines.filter { it.routine.isFavorite },
                    completedRoutineIds = dayProgress.completedRoutineIds,
                    routineProgress = dayProgress.fractionByRoutineId,
                    tasbihProgress = tasbihProgress,
                )
            }
            // The nudge is collected separately below; carry its latest value over.
            .onEach { _uiState.value = it.copy(showBackupNudge = _uiState.value.showBackupNudge) }
            .launchIn(viewModelScope)

        combine(
            preferencesRepository.lastBackupAt,
            preferencesRepository.backupNudgeSnoozedUntil,
            historyRepository.allTimeTotalFlow(),
            clockTick,
        ) { lastBackupAt, snoozedUntil, allTime, _ ->
            BackupNudge.shouldShow(System.currentTimeMillis(), lastBackupAt, snoozedUntil, hasHistory = allTime > 0)
        }
            .distinctUntilChanged()
            .onEach { show -> _uiState.update { it.copy(showBackupNudge = show) } }
            .launchIn(viewModelScope)
    }

    /** "Later" on the backup card: hide it for [BackupNudge.SNOOZE_DAYS] days. */
    fun snoozeBackupNudge() {
        viewModelScope.launch {
            preferencesRepository.setBackupNudgeSnoozedUntil(BackupNudge.snoozeUntil(System.currentTimeMillis()))
        }
    }

    private companion object {
        // Built-in tasbih ids from SeedData for the banners that have a
        // fitting dhikr; the rest (Ramadan, Ashura, fasting) are reminders only.
        val SEASON_TASBIH = mapOf(
            IslamicSeason.LAST_TEN_NIGHTS to "afuww",
            IslamicSeason.ARAFAH to "tahlil_wahdahu",
            IslamicSeason.FRIDAY to "short_salawat",
            IslamicSeason.DHUL_HIJJAH to "akbar",
        )
    }

    private data class HomeCombined(
        val inputs: HomeInputs,
        val dayProgress: com.dhikr.app.core.database.RoutineDayProgress,
        val tasbihProgress: Map<String, Float>,
        val allTasbih: List<TasbihEntity>,
    )

    private data class HomeInputs(
        val dailyGoal: Int,
        val todayTotal: Int,
        val favorites: List<TasbihEntity>,
        val routines: List<RoutineWithSteps>,
        val session: CounterSessionState?,
    )

    class Factory(
        private val tasbihRepository: TasbihRepository,
        private val routineRepository: RoutineRepository,
        private val historyRepository: HistoryRepository,
        private val sessionRepository: SessionRepository,
        private val preferencesRepository: AppPreferencesRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(tasbihRepository, routineRepository, historyRepository, sessionRepository, preferencesRepository) as T
    }
}
