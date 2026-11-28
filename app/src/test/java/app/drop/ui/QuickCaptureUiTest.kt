package app.drop.ui

import android.os.Looper
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import app.drop.DropApplication
import app.drop.R
import app.drop.capture.QuickCaptureActivity
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

/** The quick capture window the widgets open: type, optionally make it a task, save, gone. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class QuickCaptureUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<QuickCaptureActivity>()

    private fun settle() {
        rule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        rule.waitForIdle()
    }

    @Test
    fun aCapturedLinkBecomesATaskAndTheWindowCloses() {
        settle()
        rule.onNodeWithTag("capture-save").assertIsNotEnabled()
        rule.onNodeWithTag("capture-text").performTextReplacement("Read this before Friday\nhttps://example.com/guide")
        settle()
        assertTrue(rule.onAllNodesWithText(rule.activity.getString(R.string.capture_link_detected, "example.com")).fetchSemanticsNodes().isNotEmpty())
        rule.onNodeWithTag("capture-as-task").performClick()
        rule.onNodeWithTag("capture-save").performClick()
        val app = rule.activity.application as DropApplication
        rule.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            runBlocking { app.database.noteDao().allNotes() }.isNotEmpty() && rule.activity.isFinishing
        }
        val note = runBlocking { app.database.noteDao().allNotes() }.single()
        assertTrue(note.isTask)
        assertEquals("Read this before Friday", note.title)
        assertEquals("https://example.com/guide", note.url)
    }
}
