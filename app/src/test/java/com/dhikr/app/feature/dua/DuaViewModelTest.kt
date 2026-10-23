package com.dhikr.app.feature.dua

import com.dhikr.app.core.database.DuaRepository
import com.dhikr.app.core.database.dao.DuaDao
import com.dhikr.app.core.database.entity.DuaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

private class FakeDuaDao(initial: List<DuaEntity>) : DuaDao {
    private val entries = MutableStateFlow(initial)
    override fun observeAll(): Flow<List<DuaEntity>> = entries
    override suspend fun getById(id: String): DuaEntity? = entries.value.find { it.id == id }
    override suspend fun insertAll(dua: List<DuaEntity>) {}
    override suspend fun upsert(entity: DuaEntity) {}
    override suspend fun delete(id: String) {}
    override suspend fun count(): Int = entries.value.size
}

private fun FakeDuaRepository(initial: List<DuaEntity>): DuaRepository = DuaRepository(FakeDuaDao(initial))

class DuaViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun uiState_groupsEntriesByCategoryInOrder() = runTest {
        val repo = FakeDuaRepository(listOf(
            DuaEntity(id = "a", category = "house", categoryOrder = 1, order = 1, name = "A", arabic = "ا", translation = "a"),
            DuaEntity(id = "b", category = "travel", categoryOrder = 2, order = 1, name = "B", arabic = "ب", translation = "b"),
        ))
        val viewModel = DuaViewModel(repo)
        val state = viewModel.uiState.first { it.categories.isNotEmpty() }
        assertEquals(listOf("house", "travel"), state.categories.map { it.category })
    }
}
