package com.dhikr.app.feature.dua

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dhikr.app.core.database.AppDatabase
import com.dhikr.app.core.database.DuaRepository
import com.dhikr.app.core.database.entity.DuaEntity
import com.dhikr.app.core.database.seed.DuaSeedData
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DuaScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var database: AppDatabase
    private lateinit var repository: DuaRepository

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = DuaRepository(database.duaDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun seed(entries: List<DuaEntity>) = runTest {
        entries.forEach { repository.upsert(it) }
    }

    @Test
    fun entryWithRepeatCountOne_showsNoCounterPillAndTapOpensEditor() {
        seed(listOf(
            DuaEntity(
                id = "b",
                category = "house",
                categoryOrder = 1,
                order = 1,
                name = "B",
                arabic = "ب",
                translation = "translation b",
                repeatCount = 1,
            ),
        ))
        var editedId: String? = null
        val viewModel = DuaViewModel(repository)
        composeTestRule.setContent {
            DuaScreen(viewModel = viewModel, onBack = {}, onAddDua = {}, onEditDua = { editedId = it })
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("B").assertExists()
        composeTestRule.onAllNodesWithTag("dua_counter_pill").assertCountEquals(0)
        composeTestRule.onNodeWithTag("dua_row_b").performClick()
        assertEquals("b", editedId)
    }

    @Test
    fun entryWithRepeatCountGreaterThanOne_tapIncrementsLocalCounterWithoutOpeningEditor() {
        seed(listOf(
            DuaEntity(
                id = "c",
                category = "house",
                categoryOrder = 1,
                order = 1,
                name = "C",
                arabic = "ج",
                translation = "translation c",
                repeatCount = 7,
            ),
        ))
        var editedId: String? = null
        val viewModel = DuaViewModel(repository)
        composeTestRule.setContent {
            DuaScreen(viewModel = viewModel, onBack = {}, onAddDua = {}, onEditDua = { editedId = it })
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("dua_counter_pill").assertTextEquals("0/7")
        composeTestRule.onNodeWithTag("dua_counter_pill").performClick()
        composeTestRule.onNodeWithTag("dua_counter_pill").assertTextEquals("1/7")
        assertNull(editedId)
    }

    @Test
    fun addButton_invokesOnAddDua() {
        var added = false
        val viewModel = DuaViewModel(repository)
        composeTestRule.setContent {
            DuaScreen(viewModel = viewModel, onBack = {}, onAddDua = { added = true }, onEditDua = {})
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("dua_add_button").performClick()
        assertTrue(added)
    }

    @Test
    fun translationText_hasNoMaxLinesOrEllipsis() {
        val longest = DuaSeedData.all.first { it.id == "marriage_wedding" }
        seed(listOf(longest))
        val viewModel = DuaViewModel(repository)
        composeTestRule.setContent {
            DuaScreen(viewModel = viewModel, onBack = {}, onAddDua = {}, onEditDua = {})
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("dua_translation_marriage_wedding", useUnmergedTree = true).performScrollTo()
        composeTestRule.onNodeWithTag("dua_translation_marriage_wedding", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("dua_translation_marriage_wedding", useUnmergedTree = true)
            .assertTextEquals(longest.translation)
    }

    @Test
    fun categoryFilter_selectingCategory_showsOnlyThatCategorysEntries() {
        seed(listOf(
            DuaEntity(
                id = "house_entry",
                category = "house",
                categoryOrder = 1,
                order = 1,
                name = "House Entry",
                arabic = "ب",
                translation = "translation house",
            ),
            DuaEntity(
                id = "travel_entry",
                category = "travel",
                categoryOrder = 2,
                order = 1,
                name = "Travel Entry",
                arabic = "ج",
                translation = "translation travel",
            ),
        ))
        val viewModel = DuaViewModel(repository)
        composeTestRule.setContent {
            DuaScreen(viewModel = viewModel, onBack = {}, onAddDua = {}, onEditDua = {})
        }
        composeTestRule.waitForIdle()

        // Both visible with no filter applied.
        composeTestRule.onNodeWithText("House Entry").assertExists()
        composeTestRule.onNodeWithText("Travel Entry").assertExists()

        // Open the filter dropdown and select the "Travel" category (exact match,
        // to avoid colliding with "Travel Entry"'s row text which also contains "Travel").
        composeTestRule.onNodeWithTag("dua_filter_button").performClick()
        composeTestRule.onNodeWithText("Travel", substring = false).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Travel Entry").assertExists()
        composeTestRule.onAllNodesWithTag("dua_row_house_entry").assertCountEquals(0)

        // Selecting "All" again restores both.
        composeTestRule.onNodeWithTag("dua_filter_button").performClick()
        composeTestRule.onNodeWithText("All", substring = false).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("House Entry").assertExists()
        composeTestRule.onNodeWithText("Travel Entry").assertExists()
    }
}
