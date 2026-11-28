package app.drop.ui

import android.content.Intent
import android.os.Looper
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import app.drop.DropApplication
import app.drop.MainActivity
import app.drop.R
import app.drop.share.ShareActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Sharing a link from another app: saved at once, with a way to open it. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ShareUiTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    @Test
    fun aSharedLinkIsSavedAndCanBeOpened() {
        val app = ApplicationProvider.getApplicationContext<DropApplication>()
        val intent = Intent(app, ShareActivity::class.java)
            .setAction(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "https://example.com/recipe")
            .putExtra(Intent.EXTRA_SUBJECT, "Lemon cake recipe")
        ActivityScenario.launch<ShareActivity>(intent).use { scenario ->
            rule.waitUntil(10_000) {
                shadowOf(Looper.getMainLooper()).idle()
                rule.onAllNodesWithText(app.getString(R.string.share_saved)).fetchSemanticsNodes().isNotEmpty()
            }
            val note = runBlocking { app.database.noteDao().allNotes() }.single()
            assertEquals("Lemon cake recipe", note.title)
            assertEquals("https://example.com/recipe", note.url)

            rule.onNodeWithTag("share-open").performClick()
            rule.waitForIdle()
            scenario.onActivity { activity ->
                val started = shadowOf(activity).nextStartedActivity
                assertEquals(MainActivity::class.java.name, started.component?.className)
                assertEquals(MainActivity.openNoteIntent(activity, note.id).extras?.keySet(), started.extras?.keySet())
                assertTrue(activity.isFinishing)
            }
        }
    }
}
