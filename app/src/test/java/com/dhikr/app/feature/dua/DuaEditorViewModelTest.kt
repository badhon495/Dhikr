package com.dhikr.app.feature.dua

import com.dhikr.app.core.database.DuaRepository
import com.dhikr.app.core.database.dao.DuaDao
import com.dhikr.app.core.database.entity.DuaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class RecordingFakeDuaDao(initial: List<DuaEntity>) : DuaDao {
    private val entries = MutableStateFlow(initial)
    val upsertedEntities = mutableListOf<DuaEntity>()
    val deletedIds = mutableListOf<String>()
    override fun observeAll(): Flow<List<DuaEntity>> = entries
    override suspend fun getById(id: String): DuaEntity? = entries.value.find { it.id == id }
    override suspend fun insertAll(dua: List<DuaEntity>) {}
    override suspend fun upsert(entity: DuaEntity) {
        upsertedEntities.add(entity)
        entries.value = entries.value.filterNot { it.id == entity.id } + entity
    }
    override suspend fun delete(id: String) {
        deletedIds.add(id)
        entries.value = entries.value.filterNot { it.id == id }
    }
    override suspend fun count(): Int = entries.value.size
}

/** Wraps a [RecordingFakeDuaDao] so tests can assert on `upsertedEntities`/`deletedIds`. */
private class FakeDuaRepository(initial: List<DuaEntity>) {
    val dao = RecordingFakeDuaDao(initial)
    val repository = DuaRepository(dao)
    val upsertedEntities get() = dao.upsertedEntities
    val deletedIds get() = dao.deletedIds
}

class DuaEditorViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun isSaveEnabled_falseUntilNameArabicTranslationAllNonBlank() = runTest {
        val viewModel = DuaEditorViewModel(FakeDuaRepository(emptyList()).repository, duaId = null)
        assertFalse(viewModel.uiState.value.isSaveEnabled)
        viewModel.onNameChange("Name")
        viewModel.onArabicChange("ع")
        assertFalse(viewModel.uiState.value.isSaveEnabled) // translation still blank
        viewModel.onTranslationChange("Translation")
        assertTrue(viewModel.uiState.value.isSaveEnabled)
    }

    @Test
    fun editMode_loadsExistingEntryFields() = runTest {
        val existing = DuaEntity(id = "x", category = "house", categoryOrder = 1, order = 1, name = "X", arabic = "x", translation = "xt", source = "src")
        val viewModel = DuaEditorViewModel(FakeDuaRepository(listOf(existing)).repository, duaId = "x")
        val state = viewModel.uiState.first { it.name == "X" }
        assertEquals("house", state.category)
        assertEquals("src", state.source)
        assertTrue(state.isEditMode)
    }

    @Test
    fun save_createMode_upsertsNewEntityWithGeneratedId() = runTest {
        val repo = FakeDuaRepository(emptyList())
        val viewModel = DuaEditorViewModel(repo.repository, duaId = null)
        viewModel.onCategoryChange("custom")
        viewModel.onNameChange("My Dua")
        viewModel.onArabicChange("دعاء")
        viewModel.onTranslationChange("My translation")
        var done = false
        viewModel.save { done = true }
        advanceUntilIdle()
        assertTrue(done)
        assertEquals(1, repo.upsertedEntities.size)
        assertEquals("My Dua", repo.upsertedEntities.first().name)
    }

    @Test
    fun save_newCategory_usesNextCategoryOrderAndZeroOrder() = runTest {
        val repo = FakeDuaRepository(listOf(
            DuaEntity(id = "a", category = "house", categoryOrder = 1, order = 1, name = "A", arabic = "a", translation = "a"),
            DuaEntity(id = "b", category = "travel", categoryOrder = 2, order = 1, name = "B", arabic = "b", translation = "b"),
        ))
        val viewModel = DuaEditorViewModel(repo.repository, duaId = null)
        viewModel.onCategoryChange("brandnew")
        viewModel.onNameChange("New")
        viewModel.onArabicChange("n")
        viewModel.onTranslationChange("nt")
        viewModel.save {}
        advanceUntilIdle()
        val saved = repo.upsertedEntities.first()
        assertEquals(3, saved.categoryOrder)
        assertEquals(0, saved.order)
    }

    @Test
    fun save_existingCategory_appendsAtNextOrderKeepingCategoryOrder() = runTest {
        val repo = FakeDuaRepository(listOf(
            DuaEntity(id = "a", category = "house", categoryOrder = 1, order = 1, name = "A", arabic = "a", translation = "a"),
            DuaEntity(id = "b", category = "house", categoryOrder = 1, order = 2, name = "B", arabic = "b", translation = "b"),
        ))
        val viewModel = DuaEditorViewModel(repo.repository, duaId = null)
        viewModel.onCategoryChange("house")
        viewModel.onNameChange("New")
        viewModel.onArabicChange("n")
        viewModel.onTranslationChange("nt")
        viewModel.save {}
        advanceUntilIdle()
        val saved = repo.upsertedEntities.first()
        assertEquals(1, saved.categoryOrder)
        assertEquals(3, saved.order)
    }

    @Test
    fun delete_editMode_callsRepositoryDeleteWithDuaId() = runTest {
        val existing = DuaEntity(id = "x", category = "house", categoryOrder = 1, order = 1, name = "X", arabic = "x", translation = "xt")
        val repo = FakeDuaRepository(listOf(existing))
        val viewModel = DuaEditorViewModel(repo.repository, duaId = "x")
        viewModel.uiState.first { it.name == "X" }
        var done = false
        viewModel.delete { done = true }
        advanceUntilIdle()
        assertTrue(done)
        assertEquals(listOf("x"), repo.deletedIds)
    }
}
