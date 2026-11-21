package app.drop.ui

import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.drop.DropApplication
import app.drop.MainActivity
import app.drop.R
import app.drop.data.Note
import app.drop.data.NoteFormat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** The everyday path through DROP: write a note, find it, trash it, undo, restore. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class CoreFlowUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val app get() = rule.activity.application as DropApplication

    private fun settle() {
        rule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        Thread.sleep(150)
        rule.waitForIdle()
    }

    /** Waits until something tagged [tag] is on screen; loading from the database can take a moment on a busy machine. */
    private fun waitForTag(tag: String) {
        rule.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun notes() = runBlocking { app.database.noteDao().allNotes() }

    @Test
    fun aNoteWrittenOnHomeIsSavedAndFoundInTheLibrary() {
        settle()
        waitForTag("dashboard")
        rule.onNodeWithTag("dashboard").assertIsDisplayed()
        waitForTag("capture-note")
        rule.onNodeWithTag("capture-note").performClick()
        settle()
        rule.onNodeWithTag("note-title").performTextInput("Toplantı notları")
        rule.onNodeWithTag("note-body").performTextInput("Çarşamba saat 10'da")
        settle()
        rule.onNodeWithTag("done").performClick()
        settle()
        val note = notes().single()
        assertEquals("Toplantı notları", note.title)
        assertEquals("Çarşamba saat 10'da", note.body)
        assertEquals(NoteFormat.PLAIN, note.format)

        waitForTag("nav-library")

        rule.onNodeWithTag("nav-library").performClick()
        settle()
        rule.onNodeWithText("Toplantı notları").assertIsDisplayed()
    }

    @Test
    fun aNoteMovedToTrashCanBeUndoneAndRestoredFromTrash() {
        val id = runBlocking {
            app.database.noteDao().insert(Note(title = "Silinecek", body = "metin", createdAt = 1, updatedAt = 1))
        }
        settle()
        waitForTag("nav-library")
        rule.onNodeWithTag("nav-library").performClick()
        settle()
        rule.onNodeWithText("Silinecek").performClick()
        settle()
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.note_more)).performClick()
        settle()
        rule.onNodeWithTag("move-to-trash").performClick()
        settle()
        assertNotNull(runBlocking { app.database.noteDao().getById(id) }!!.deletedAt)
        rule.onNodeWithText(rule.activity.getString(R.string.home_moved_to_trash)).assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(R.string.home_undo)).performClick()
        settle()
        assertNull(runBlocking { app.database.noteDao().getById(id) }!!.deletedAt)

        // Trash it again and restore it from the Trash screen: the text is unchanged.
        runBlocking { app.database.noteDao().setDeletedAt(id, 5) }
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.note_more)).performClick()
        settle()
        rule.onNodeWithText(rule.activity.getString(R.string.home_trash)).performClick()
        settle()
        rule.onNodeWithTag("trash").assertIsDisplayed()
        rule.onNodeWithText("Silinecek").assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(R.string.trash_restore)).performClick()
        settle()
        val note = runBlocking { app.database.noteDao().getById(id) }!!
        assertNull(note.deletedAt)
        assertEquals("metin", note.body)
    }

    @Test
    fun anEmptyNoteIsNeverCreated() {
        settle()
        waitForTag("capture-note")
        rule.onNodeWithTag("capture-note").performClick()
        settle()
        rule.onNodeWithTag("done").performClick()
        settle()
        assertTrue(notes().isEmpty())
        waitForTag("dashboard")
        rule.onNodeWithTag("dashboard").assertIsDisplayed()
    }

    @Test
    fun aTaskCreatedFromHomeIsATask() {
        settle()
        waitForTag("capture-task")
        rule.onNodeWithTag("capture-task").performClick()
        settle()
        rule.onNodeWithTag("task-done").assertIsDisplayed()
        rule.onNodeWithTag("note-title").performTextInput("Faturayı öde")
        settle()
        rule.onNodeWithTag("done").performClick()
        settle()
        val task = notes().single()
        assertTrue(task.isTask)
        assertNull(task.completedAt)
        rule.onNodeWithText("Faturayı öde").assertIsDisplayed()
    }
}
