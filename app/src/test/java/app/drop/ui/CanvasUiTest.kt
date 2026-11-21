package app.drop.ui

import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.drop.DropApplication
import app.drop.MainActivity
import app.drop.R
import app.drop.data.Note
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** Idea Canvas from the spaces menu: create, add notes, connect, zoom, open a note. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class CanvasUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val app get() = rule.activity.application as DropApplication

    private fun settle() {
        rule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        Thread.sleep(150)
        rule.waitForIdle()
    }

    private fun s(id: Int) = rule.activity.getString(id)

    @Test
    fun aCanvasIsCreatedFilledConnectedAndZoomed() {
        val a = runBlocking { app.database.noteDao().insert(Note(title = "Problem", body = "", createdAt = 1, updatedAt = 1)) }
        val b = runBlocking { app.database.noteDao().insert(Note(title = "Solution", body = "", createdAt = 1, updatedAt = 1)) }
        settle()
        rule.onNodeWithTag("nav-library").performClick()
        settle()
        rule.onNodeWithTag("spaces").performClick()
        settle()
        rule.onNodeWithTag("space-Canvas").performClick()
        settle()
        rule.onNodeWithText(s(R.string.canvas_empty_title)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.canvas_new), useUnmergedTree = true).performClick()
        settle()
        rule.onNodeWithTag("name-field").performTextInput("Launch")
        rule.onNodeWithText(s(R.string.spaces_save)).performClick()
        settle()
        rule.onNodeWithTag("canvas").assertIsDisplayed()

        rule.onNodeWithTag("canvas-add").performClick()
        settle()
        rule.onNodeWithText("Problem").performClick()
        rule.onNodeWithText("Solution").performClick()
        rule.onNodeWithText(s(R.string.spaces_add)).performClick()
        settle()
        rule.onNodeWithTag("canvas-card-$a").assertIsDisplayed()
        rule.onNodeWithTag("canvas-card-$b").assertIsDisplayed()

        // Connect the two cards from the first card's menu.
        rule.onNodeWithText("Problem").performClick()
        settle()
        rule.onNodeWithText(s(R.string.canvas_card_connect)).performClick()
        settle()
        rule.onNodeWithTag("canvas-connect-bar").assertIsDisplayed()
        rule.onNodeWithText("Solution").performClick()
        settle()
        val canvasId = runBlocking { app.database.canvasDao().all() }.single().id
        assertEquals(1, runBlocking { app.database.canvasDao().observeEdges(canvasId).first() }.size)
        rule.onNodeWithText(s(R.string.canvas_connect_done)).performClick()
        settle()
        assertEquals(0, rule.onAllNodesWithTag("canvas-connect-bar").fetchSemanticsNodes().size)

        rule.onNodeWithTag("canvas-zoom-level").assertTextEquals("100%")
        rule.onNodeWithTag("canvas-zoom-in").performClick()
        settle()
        rule.onNodeWithTag("canvas-zoom-level").assertTextEquals("125%")

        rule.onNodeWithText("Solution").performClick()
        settle()
        rule.onNodeWithText(s(R.string.canvas_card_open)).performClick()
        settle()
        rule.onNodeWithTag("editor").assertIsDisplayed()
    }
}
