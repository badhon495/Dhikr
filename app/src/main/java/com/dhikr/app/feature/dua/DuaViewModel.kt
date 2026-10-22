package com.dhikr.app.feature.dua

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dhikr.app.core.database.DuaRepository
import com.dhikr.app.core.database.entity.DuaEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DuaCategoryUi(val category: String, val entries: List<DuaEntity>)

data class DuaUiState(val categories: List<DuaCategoryUi>)

/**
 * Groups [DuaRepository.observeAll]'s already-ordered list (DAO's
 * `ORDER BY categoryOrder, order`) into per-category buckets without
 * re-sorting. `groupBy` on a `List` returns a `LinkedHashMap`, so categories
 * surface in first-seen order — i.e. the order `categoryOrder` already set.
 */
class DuaViewModel(private val repository: DuaRepository) : ViewModel() {

    val uiState: StateFlow<DuaUiState> = repository.observeAll()
        .map { entries ->
            DuaUiState(
                entries.groupBy { it.category }
                    .map { (category, groupEntries) -> DuaCategoryUi(category, groupEntries) },
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DuaUiState(emptyList()),
        )

    fun deleteDua(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    class Factory(private val repository: DuaRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DuaViewModel(repository) as T
    }
}
