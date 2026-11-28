package app.drop.ui

import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.drop.DropApplication
import app.drop.MainActivity
import app.drop.R
import app.drop.data.Note
import app.drop.data.NoteRevision
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

/** History from a note: see an earlier version, what changed, and put it back. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class HistoryUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val app get() = rule.activity.application as DropApplication

    private fun settle() {
        rule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        Thread.sleep(150)
        rule.waitForIdle()
    }

    private fun waitForTag(tag: String) = rule.waitUntil(15_000) {
        shadowOf(Looper.getMainLooper()).idle()
        rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }

    private fun s(id: Int, vararg args: Any) = rule.activity.getString(id, *args)

    @Test
    fun anEarlierVersionCanBeComparedAndRestored() {
        settle()
        val noteId = runBlocking {
            val id = app.database.noteDao().insert(Note(title = "Packing", body = "passport\ncharger\nsocks", createdAt = 1, updatedAt = 3))
            app.database.revisionDao().insert(NoteRevision(noteId = id, title = "Packing", body = "passport\ntickets\nsocks", editedAt = 2, createdAt = 2))
            id
        }
        waitForTag("nav-library")
        rule.onNodeWithTag("nav-library").performClick()
        settle()
        rule.onNodeWithText("Packing").performClick()
        settle()
        waitForTag("editor")
        rule.onNodeWithContentDescription(s(R.string.note_more)).performClick()
        settle()
        rule.onNodeWithTag("note-history").performClick()
        settle()
        waitForTag("history")
        val revision = runBlocking { app.database.revisionDao().latest(noteId) }!!
        rule.onNodeWithTag("revision-${revision.id}").performClick()
        settle()
        rule.onNodeWithTag("history-preview").assertIsDisplayed()
        rule.onNodeWithTag("history-show-changes").performClick()
        settle()
        // "tickets" only in this version, "charger" only in the current text.
        assertTrue(rule.onAllNodesWithContentDescriptionText(s(R.string.history_removed_line, "tickets")))
        assertTrue(rule.onAllNodesWithContentDescriptionText(s(R.string.history_added_line, "charger")))

        rule.onNodeWithTag("history-restore").performClick()
        settle()
        rule.onNodeWithText(s(R.string.history_restore_title)).assertIsDisplayed()
        rule.onNodeWithTag("history-restore-confirm").performClick()
        rule.waitUntil(10_000) { runBlocking { app.database.noteDao().getById(noteId) }!!.body == "passport\ntickets\nsocks" }
        // The text that was replaced is kept, so the restore can be undone.
        assertEquals("passport\ncharger\nsocks", runBlocking { app.database.revisionDao().latest(noteId) }!!.body)
        settle()
        assertTrue(rule.onAllNodesWithText(s(R.string.history_restored)).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun allHistoryCanBeClearedFromSettings() {
        settle()
        runBlocking {
            val id = app.database.noteDao().insert(Note(title = "A", body = "b", createdAt = 1, updatedAt = 1))
            app.database.revisionDao().insert(NoteRevision(noteId = id, title = "A", body = "old", editedAt = 1, createdAt = 1))
        }
        rule.onNodeWithContentDescription(s(R.string.home_settings)).performClick()
        settle()
        waitForTag("history-clear")
        rule.onNodeWithTag("history-clear").performScrollTo().performClick()
        settle()
        rule.onNodeWithTag("history-clear-confirm").performClick()
        rule.waitUntil(10_000) { runBlocking { app.database.revisionDao().countFor(1) } == 0 }
        assertEquals(0, runBlocking { app.database.revisionDao().countFor(1) })
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithContentDescriptionText(label: String): Boolean =
        onAllNodes(androidx.compose.ui.test.hasContentDescription(label), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
}
