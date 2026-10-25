package com.dhikr.app.feature.tasbih

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dhikr.app.core.database.DeleteResult
import com.dhikr.app.core.database.TasbihRepository
import com.dhikr.app.core.database.entity.TasbihEntity
import com.dhikr.app.core.localization.AppLanguage
import com.dhikr.app.core.localization.displayName
import com.dhikr.app.core.notifications.ReminderScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TasbihSortField { NONE, LENGTH, RECITE_COUNT, CUSTOM, FAVORITES }
enum class SortDirection { ASC, DESC }

data class TasbihSort(
    val field: TasbihSortField = TasbihSortField.NONE,
    val direction: SortDirection = SortDirection.ASC,
)

private fun List<TasbihEntity>.sortedWith(sort: TasbihSort): List<TasbihEntity> {
    val comparator: Comparator<TasbihEntity> = when (sort.field) {
        TasbihSortField.NONE -> return this
        TasbihSortField.LENGTH -> compareBy { it.arabic.length }
        TasbihSortField.RECITE_COUNT -> compareBy { it.lapTarget }
        // ASC = custom on top (false sorts before true); sort is stable so order within groups holds.
        TasbihSortField.CUSTOM -> compareBy { it.isBuiltIn }
        // ASC = favorites on top (not-favorite = false sorts after, so negate).
        TasbihSortField.FAVORITES -> compareBy { !it.isFavorite }
    }
    val ordered = if (sort.direction == SortDirection.DESC) comparator.reversed() else comparator
    return this.sortedWith(ordered)
}

data class TasbihLibraryUiState(
    val query: String = "",
    val results: List<TasbihEntity> = emptyList(),
    val builtInCount: Int = 0,
    val customCount: Int = 0,
    // tasbihId -> 0f..1f of today's counting position toward the Tasbih's total
    // goal; drives the row's green fill. Clears on its own the next day.
    val progressByTasbihId: Map<String, Float> = emptyMap(),
    val sort: TasbihSort = TasbihSort(),
)

/** One-shot event for TasbihLibraryScreen to show when a delete is blocked
 * by an existing routine reference (finding #4) — modeled as a SharedFlow
 * rather than UI state so it fires exactly once per delete attempt instead
 * of persisting across recomposition/rotation. */
data class TasbihDeleteBlocked(val tasbihName: String, val routineNames: List<String>)

/**
 * Search re-queries the DAO (via `flatMapLatest`) rather than filtering an
 * in-memory list, so it stays correct as the Tasbih table grows past what's
 * comfortable to hold client-side. `flatMapLatest` on the query StateFlow
 * cancels any in-flight search Flow the moment the query changes, so rapid
 * typing never fans out one live query per keystroke — the same
 * responsive-but-not-wasteful principle as the Counter screen's persistence
 * debounce from Phase 1+2, just expressed as flow-switching instead of
 * `debounce()` since here we want the LATEST query's results immediately, not
 * a delayed settle.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TasbihLibraryViewModel(
    private val repository: TasbihRepository,
    private val reminderScheduler: ReminderScheduler,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(TasbihSort())

    val uiState: StateFlow<TasbihLibraryUiState> = combine(
        query,
        query.flatMapLatest { q ->
            if (q.isBlank()) repository.observeAll() else repository.search(q)
        },
        repository.observeAll(), // stable built-in/custom counts, independent of the filter
        repository.observeSessionProgressToday(),
        sort,
    ) { q, results, all, progress, sort ->
        TasbihLibraryUiState(
            query = q,
            results = results.sortedWith(sort),
            builtInCount = all.count { it.isBuiltIn },
            customCount = all.count { !it.isBuiltIn },
            progressByTasbihId = progress,
            sort = sort,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TasbihLibraryUiState(),
    )

    fun onQueryChange(newQuery: String) {
        query.value = newQuery
    }

    /** Picking the already-active field flips ASC/DESC; picking a new field starts it at ASC. */
    fun onSelectSortField(field: TasbihSortField) {
        sort.update { current ->
            if (current.field == field) {
                current.copy(direction = if (current.direction == SortDirection.ASC) SortDirection.DESC else SortDirection.ASC)
            } else {
                TasbihSort(field = field, direction = SortDirection.ASC)
            }
        }
    }

    fun onToggleFavorite(id: String, currentlyFavorite: Boolean) {
        viewModelScope.launch { repository.toggleFavorite(id, currentlyFavorite) }
    }

    private val _deleteBlocked = MutableSharedFlow<TasbihDeleteBlocked>(extraBufferCapacity = 1)
    val deleteBlocked: SharedFlow<TasbihDeleteBlocked> = _deleteBlocked

    /**
     * Deletes a Tasbih, built-in or custom. The repository still returns
     * [DeleteResult.BlockedByRoutines] when a routine references the Tasbih,
     * which surfaces the "can't delete" dialog instead of removing it.
     */
    fun onDeleteTasbih(tasbih: TasbihEntity) {
        viewModelScope.launch {
            when (val result = repository.delete(tasbih)) {
                is DeleteResult.Success -> reminderScheduler.cancelTasbih(tasbih.id)
                is DeleteResult.BlockedByRoutines -> {
                    _deleteBlocked.tryEmit(
                        TasbihDeleteBlocked(tasbih.displayName(AppLanguage.current), result.routineNames),
                    )
                }
            }
        }
    }

    class Factory(
        private val repository: TasbihRepository,
        private val reminderScheduler: ReminderScheduler,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TasbihLibraryViewModel(repository, reminderScheduler) as T
    }
}
