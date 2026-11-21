package app.drop.ui

import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.drop.DropApplication
import app.drop.MainActivity
import app.drop.R
import app.drop.data.Note
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** ECHO on Home and under a note, and its switch in Settings. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class EchoUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val app get() = rule.activity.application as DropApplication

    @Before
    fun cleanMemory() {
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("echo_memory", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun settle() {
        rule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        Thread.sleep(150)
        rule.waitForIdle()
    }

    private fun waitForTag(tag: String, present: Boolean = true) {
        rule.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
            rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() == present
        }
    }

    private fun add(title: String, body: String, updated: Long) =
        runBlocking { app.database.noteDao().insert(Note(title = title, body = body, createdAt = updated, updatedAt = updated)) }

    @Test
    fun anOldMatchingNoteIsSuggestedOnHomeAndCanBeDismissed() {
        val now = System.currentTimeMillis()
        add("Marathon training", "interval running schedule, long runs on sunday", now)
        val old = add("Running shoes", "interval running needs light shoes, schedule a fitting", now - 30L * 24 * 3600 * 1000)
        settle()
        rule.onNodeWithTag("dashboard").assertIsDisplayed()
        waitForTag("echo-$old")
        rule.onNodeWithTag("echo-$old").assertIsDisplayed()

        rule.onNodeWithText(rule.activity.getString(R.string.echo_dismiss)).performClick()
        waitForTag("echo-$old", present = false)
        assertTrue(old in app.echoMemory.suppressed())
    }

    @Test
    fun relatedNotesAppearUnderANoteAndOpenThatNote() {
        val a = add("Kitchen renovation", "cabinets, countertop quotes and tiles", 1)
        val b = add("Renovation budget", "countertop quotes, cabinets and tiles cost", 2)
        settle()
        rule.onNodeWithTag("nav-library").performClick()
        settle()
        rule.onNodeWithText("Kitchen renovation").performClick()
        waitForTag("related-notes")
        rule.onNodeWithTag("echo-$b").performClick()
        settle()
        rule.onNodeWithText("Renovation budget").assertIsDisplayed()
        assertTrue(b in app.echoMemory.suppressed())
        assertFalse(a in app.echoMemory.suppressed())
    }

    @Test
    fun switchingEchoOffInSettingsRemovesSuggestions() {
        add("Kitchen renovation", "cabinets, countertop quotes and tiles", 1)
        add("Renovation budget", "countertop quotes, cabinets and tiles cost", 2)
        settle()
        rule.onNodeWithTag("nav-library").performClick()
        settle()
        rule.onNodeWithText("Kitchen renovation").performClick()
        waitForTag("related-notes")
        rule.activity.onBackPressedDispatcher.let { rule.runOnUiThread { it.onBackPressed() } }
        settle()
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.home_settings)).performClick()
        settle()
        rule.onNodeWithTag("echo-toggle").performScrollTo()
        rule.onNodeWithTag("echo-toggle").performClick()
        settle()
        assertFalse(app.settings.echoEnabled.value)
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
        rule.onNodeWithText("Kitchen renovation").performClick()
        settle()
        rule.onNodeWithTag("editor").assertIsDisplayed()
        assertTrue(rule.onAllNodesWithTag("related-notes").fetchSemanticsNodes().isEmpty())
    }
}
