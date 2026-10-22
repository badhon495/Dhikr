package com.dhikr.app.feature.dua

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dhikr.app.core.database.DuaRepository
import com.dhikr.app.core.database.entity.DuaEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

data class DuaEditorUiState(
    val category: String = "",
    val name: String = "",
    val nameBn: String = "",
    val arabic: String = "",
    val pronunciation: String = "",
    val pronunciationBn: String = "",
    val translation: String = "",
    val translationBn: String = "",
    val source: String = "",
    val repeatCount: Int = 1,
    val isSaveEnabled: Boolean = false,
    val isEditMode: Boolean = false,
)

enum class DuaBanglaField { NAME, PRONUNCIATION, TRANSLATION }

class DuaEditorViewModel(
    private val repository: DuaRepository,
    private val duaId: String? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DuaEditorUiState(isEditMode = duaId != null))
    val uiState: StateFlow<DuaEditorUiState> = _uiState.asStateFlow()

    private var originalEntity: DuaEntity? = null

    init {
        if (duaId != null) {
            viewModelScope.launch {
                repository.getById(duaId)?.let { entity ->
                    originalEntity = entity
                    update {
                        it.copy(
                            category = entity.category,
                            name = entity.name,
                            nameBn = entity.nameBn.orEmpty(),
                            arabic = entity.arabic,
                            pronunciation = entity.pronunciation.orEmpty(),
                            pronunciationBn = entity.pronunciationBn.orEmpty(),
                            translation = entity.translation,
                            translationBn = entity.translationBn.orEmpty(),
                            source = entity.source.orEmpty(),
                            repeatCount = entity.repeatCount,
                            isEditMode = true,
                        )
                    }
                    recomputeSaveEnabled()
                }
            }
        }
    }

    fun onCategoryChange(value: String) = updateField { it.copy(category = value) }
    fun onNameChange(value: String) = updateField { it.copy(name = value) }
    fun onArabicChange(value: String) = updateField { it.copy(arabic = value) }
    fun onTranslationChange(value: String) = updateField { it.copy(translation = value) }
    fun onPronunciationChange(value: String) = updateField { it.copy(pronunciation = value) }
    fun onSourceChange(value: String) = updateField { it.copy(source = value) }
    fun onRepeatCountChange(value: Int) = updateField { it.copy(repeatCount = value.coerceAtLeast(1)) }

    fun onBanglaFieldChange(field: DuaBanglaField, value: String) = updateField {
        when (field) {
            DuaBanglaField.NAME -> it.copy(nameBn = value)
            DuaBanglaField.PRONUNCIATION -> it.copy(pronunciationBn = value)
            DuaBanglaField.TRANSLATION -> it.copy(translationBn = value)
        }
    }

    fun save(onDone: () -> Unit) {
        val s = _uiState.value
        if (!s.isSaveEnabled) return
        viewModelScope.launch {
            val original = originalEntity
            val categoryOrder: Int
            val order: Int
            if (original != null && original.category == s.category) {
                // Edit mode, category unchanged: keep existing position.
                categoryOrder = original.categoryOrder
                order = original.order
            } else {
                val snapshot = repository.observeAll().first()
                val sameCategory = snapshot.filter { it.category == s.category }
                if (sameCategory.isNotEmpty()) {
                    categoryOrder = sameCategory.first().categoryOrder
                    order = sameCategory.maxOf { it.order } + 1
                } else {
                    categoryOrder = (snapshot.maxOfOrNull { it.categoryOrder } ?: -1) + 1
                    order = 0
                }
            }
            val id = duaId ?: UUID.randomUUID().toString()
            val entity = DuaEntity(
                id = id,
                category = s.category,
                categoryOrder = categoryOrder,
                order = order,
                name = s.name,
                nameBn = s.nameBn.ifBlank { null },
                arabic = s.arabic,
                pronunciation = s.pronunciation.ifBlank { null },
                pronunciationBn = s.pronunciationBn.ifBlank { null },
                translation = s.translation,
                translationBn = s.translationBn.ifBlank { null },
                source = s.source.ifBlank { null },
                repeatCount = s.repeatCount,
            )
            repository.upsert(entity)
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = duaId ?: return
        viewModelScope.launch {
            repository.delete(id)
            onDone()
        }
    }

    private inline fun updateField(block: (DuaEditorUiState) -> DuaEditorUiState) {
        update(block)
        recomputeSaveEnabled()
    }

    private fun recomputeSaveEnabled() {
        update { s ->
            s.copy(
                isSaveEnabled = s.name.isNotBlank() && s.arabic.isNotBlank() && s.translation.isNotBlank(),
            )
        }
    }

    private inline fun update(block: (DuaEditorUiState) -> DuaEditorUiState) {
        _uiState.value = block(_uiState.value)
    }

    class Factory(
        private val repository: DuaRepository,
        private val duaId: String? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DuaEditorViewModel(repository, duaId) as T
    }
}
