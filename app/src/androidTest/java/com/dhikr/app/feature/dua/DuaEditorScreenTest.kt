package com.dhikr.app.feature.dua

import android.content.Context
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dhikr.app.core.database.AppDatabase
import com.dhikr.app.core.database.DuaRepository
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DuaEditorScreenTest {

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

    @Test
    fun saveButton_disabledUntilRequiredFieldsFilled() {
        val viewModel = DuaEditorViewModel(repository, duaId = null)
        composeTestRule.setContent {
            DuaEditorScreen(viewModel = viewModel, onBack = {})
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("dua_editor_save").assertIsNotEnabled()

        composeTestRule.onNodeWithTag("dua_editor_name").performTextInput("Name")
        composeTestRule.onNodeWithTag("dua_editor_arabic").performTextInput("دعاء")
        composeTestRule.onNodeWithTag("dua_editor_translation").performTextInput("Translation")

        composeTestRule.onNodeWithTag("dua_editor_save").assertIsEnabled()
    }
}
