package app.drop.ui

import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.drop.DropApplication
import app.drop.MainActivity
import app.drop.R
import app.drop.data.Note
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** The command palette from Home and the Library, and the Library requests it makes. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class PaletteUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val app get() = rule.activity.application as DropApplication

    private fun settle() {
        rule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        Thread.sleep(150)
        rule.waitForIdle()
    }

    private fun waitForTag(tag: String) {
        rule.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
            rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun typingFiltersCommandsAndRunningOneOpensIt() {
        runBlocking {
            app.database.noteDao().insert(Note(title = "Plain note", body = "", createdAt = 1, updatedAt = 1))
            app.database.noteDao().insert(Note(title = "Pay rent", body = "", isTask = true, createdAt = 2, updatedAt = 2))
        }
        settle()
        waitForTag("commands")
        rule.onNodeWithTag("commands").performClick()
        settle()
        rule.onNodeWithTag("palette").assertIsDisplayed()
        // Only rebuilt commands are offered.
        assertTrue(rule.onAllNodesWithTag("palette-Vault").fetchSemanticsNodes().isEmpty())
        rule.onNodeWithTag("palette-input").performTextInput("task")
        settle()
        rule.onNodeWithTag("palette-NewTask").assertIsSelected()
        rule.onNodeWithTag("palette-ShowTasks").performClick()
        settle()
        rule.onNodeWithTag("library").assertIsDisplayed()
        rule.onNodeWithText("Pay rent").assertIsDisplayed()
        assertTrue(rule.onAllNodesWithText("Plain note").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun aCommandThatMatchesNothingSaysSo() {
        settle()
        rule.onNodeWithTag("nav-library").performClick()
        settle()
        rule.onNodeWithTag("commands").performClick()
        settle()
        rule.onNodeWithTag("palette-input").performTextInput("zzqx")
        settle()
        rule.onNodeWithText(rule.activity.getString(R.string.palette_no_results, "zzqx")).assertIsDisplayed()
    }

    @Test
    fun theHomeSearchBoxOpensLibrarySearch() {
        settle()
        waitForTag("dashboard-search")
        rule.onNodeWithTag("dashboard-search").performClick()
        settle()
        rule.onNodeWithTag("search-field").assertIsDisplayed()
    }

    @Test
    fun newTaskFromThePaletteStartsATask() {
        settle()
        rule.onNodeWithTag("commands").performClick()
        settle()
        rule.onNodeWithTag("palette-NewTask").performClick()
        settle()
        rule.onNodeWithTag("task-done").assertIsDisplayed()
        assertEquals(0, runBlocking { app.database.noteDao().allNotes() }.size)
    }
}
