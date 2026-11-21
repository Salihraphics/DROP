package app.drop.ui

import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import app.drop.DropApplication
import app.drop.MainActivity
import app.drop.R
import app.drop.data.Note
import app.drop.data.NoteFormat
import app.drop.ui.templates.TEMPLATES_TAG
import app.drop.ui.templates.TEMPLATE_NAME_TAG
import app.drop.ui.templates.TEMPLATE_PREVIEW_TAG
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** Template Studio from the Library: preview, use, and save a note as a template. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class TemplatesUiTest {
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

    private fun s(id: Int) = rule.activity.getString(id)

    private fun openTemplates() {
        settle()
        waitForTag("nav-library")
        rule.onNodeWithTag("nav-library").performClick()
        settle()
        rule.onNodeWithContentDescription(s(R.string.note_more)).performClick()
        settle()
        rule.onNodeWithTag("open-templates").performClick()
        settle()
        rule.onNodeWithTag(TEMPLATES_TAG).assertIsDisplayed()
    }

    @Test
    fun usingABuiltInTemplateOpensTheNewNote() {
        openTemplates()
        rule.onNodeWithText(s(R.string.tpl_meeting_name)).performClick()
        settle()
        rule.onNodeWithTag(TEMPLATE_PREVIEW_TAG).assertIsDisplayed()
        // Previewing creates nothing.
        assertTrue(runBlocking { app.database.noteDao().allNotes() }.isEmpty())
        rule.onNodeWithText(s(R.string.template_use)).performClick()
        settle()
        rule.onNodeWithTag("editor").assertIsDisplayed()
        val note = runBlocking { app.database.noteDao().allNotes() }.single()
        assertEquals(NoteFormat.MARKDOWN, note.format)
        assertFalse(note.title.contains("{{"))
        assertFalse(note.body.contains("{{"))
    }

    @Test
    fun aNoteSavedAsATemplateAppearsUnderMyTemplates() {
        runBlocking { app.database.noteDao().insert(Note(title = "Weekly review", body = "- wins\n- blockers", createdAt = 1, updatedAt = 1)) }
        settle()
        waitForTag("nav-library")
        rule.onNodeWithTag("nav-library").performClick()
        settle()
        rule.onNodeWithText("Weekly review").performClick()
        settle()
        rule.onNodeWithContentDescription(s(R.string.note_more)).performClick()
        settle()
        rule.onNodeWithTag("save-as-template").performClick()
        settle()
        rule.onNodeWithTag(TEMPLATE_NAME_TAG).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.template_save)).performClick()
        settle()
        val saved = runBlocking { app.templates.observeCustom().first() }.single()
        assertEquals("Weekly review", saved.name)
        assertEquals("- wins\n- blockers", saved.body)
        // Back in the editor; the note itself is unchanged.
        rule.onNodeWithTag("editor").assertIsDisplayed()
        assertEquals(1, runBlocking { app.database.noteDao().allNotes() }.size)
    }

    @Test
    fun aNewTemplateNeedsAName() {
        openTemplates()
        rule.onNodeWithText(s(R.string.templates_new), useUnmergedTree = true).performClick()
        settle()
        rule.onNodeWithText(s(R.string.template_save)).performClick()
        settle()
        rule.onNodeWithText(s(R.string.template_name_needed)).assertIsDisplayed()
        rule.onNodeWithTag(TEMPLATE_NAME_TAG).performTextInput("Daily plan")
        rule.onNodeWithText(s(R.string.template_save)).performClick()
        settle()
        rule.onNodeWithTag(TEMPLATES_TAG).performScrollToNode(hasText("Daily plan"))
        rule.onNodeWithText("Daily plan").assertIsDisplayed()
    }
}
