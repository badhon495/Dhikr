package com.dhikr.app.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dhikr.app.core.database.entity.DuaEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DuaDaoTest {

    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(
            context = context,
            klass = AppDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun observeAll_ordersByCategoryThenOrder() = runTest {
        val dao = database.duaDao()
        dao.insertAll(listOf(
            DuaEntity(id = "b", category = "travel", categoryOrder = 2, order = 1, name = "B", arabic = "ب", translation = "b"),
            DuaEntity(id = "a", category = "house", categoryOrder = 1, order = 1, name = "A", arabic = "ا", translation = "a"),
        ))
        val result = dao.observeAll().first()
        assertEquals(listOf("a", "b"), result.map { it.id })
    }

    @Test
    fun delete_removesEntryPermanently() = runTest {
        val dao = database.duaDao()
        dao.insertAll(listOf(DuaEntity(id = "x", category = "house", categoryOrder = 1, order = 1, name = "X", arabic = "x", translation = "x")))
        dao.delete("x")
        assertEquals(0, dao.count())
        assertNull(dao.getById("x"))
    }

    @Test
    fun upsert_insertsNewAndUpdatesExisting() = runTest {
        val dao = database.duaDao()
        val entry = DuaEntity(id = "y", category = "house", categoryOrder = 1, order = 1, name = "Y", arabic = "y", translation = "y")
        dao.upsert(entry)
        assertEquals("Y", dao.getById("y")?.name)
        dao.upsert(entry.copy(name = "Y2"))
        assertEquals(1, dao.count())
        assertEquals("Y2", dao.getById("y")?.name)
    }
}
