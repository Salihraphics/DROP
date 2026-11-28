package app.drop.ui

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.os.Looper
import android.speech.RecognitionService
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import app.drop.DropApplication
import app.drop.MainActivity
import app.drop.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSpeechRecognizer
import java.time.Duration

/** Voice Dump on phones that can't transcribe: it says why and offers recording and the alternatives. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class VoiceUiTest {
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

    private fun s(id: Int) = rule.activity.getString(id)

    private fun openVoice() {
        // Both tests are about a phone without on-device recognition.
        ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(false)
        settle()
        waitForTag("dashboard-list")
        rule.onNodeWithTag("dashboard-list").performScrollToNode(hasTestTag("capture-voice"))
        rule.onNodeWithTag("capture-voice").performClick()
        settle()
        waitForTag("voice")
    }

    /** Segmented buttons don't take Robolectric's simulated touch; their click action is the same tap. */
    private fun chooseTranscribe() {
        rule.onNodeWithTag("voice-mode-Transcribe").performSemanticsAction(SemanticsActions.OnClick)
        settle()
    }

    @Test
    fun aPhoneWithoutSpeechRecognitionStillRecordsAndSaysWhy() {
        openVoice()
        // Nothing can transcribe here, so Voice Dump opens on recording rather than a dead button.
        rule.waitUntil(10_000) { rule.onAllNodesWithText(s(R.string.voice_record_hint)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("voice-start").assertIsEnabled()

        chooseTranscribe()
        rule.onNodeWithTag("voice-unavailable").assertIsDisplayed()
        assertTrue(rule.onAllNodesWithText(s(R.string.voice_support_none)).fetchSemanticsNodes().isNotEmpty())
        rule.onNodeWithTag("voice-start").performScrollTo().assertIsNotEnabled()
        // No speech service at all: nothing to allow.
        assertTrue(rule.onAllNodesWithTag("voice-allow-system").fetchSemanticsNodes().isEmpty())

        rule.onNodeWithTag("voice-record-instead").performClick()
        settle()
        rule.onNodeWithTag("voice-start").performScrollTo().assertIsEnabled()
    }

    @Test
    fun thePhonesSpeechServiceCanBeAllowedRightThere() {
        settle()
        // A phone with a speech service (Google's, say) but no on-device recognition.
        val info = ResolveInfo().apply {
            serviceInfo = ServiceInfo().apply {
                packageName = "com.example.speech"
                name = "com.example.speech.Recognizer"
                applicationInfo = ApplicationInfo().apply { packageName = "com.example.speech" }
            }
        }
        shadowOf(rule.activity.packageManager).addResolveInfoForIntent(Intent(RecognitionService.SERVICE_INTERFACE), info)
        openVoice()
        chooseTranscribe()
        waitForTag("voice-allow-system")
        rule.onNodeWithTag("voice-allow-system").performScrollTo().performClick()
        settle()
        // It says what allowing means before anything changes.
        assertTrue(rule.onAllNodesWithText(s(R.string.settings_voice_system_body)).fetchSemanticsNodes().isNotEmpty())
        rule.onNodeWithTag("voice-allow-confirm").performClick()
        settle()
        assertTrue(app.settings.allowSystemSpeech.value)
        rule.waitUntil(10_000) { rule.onAllNodesWithText(s(R.string.voice_support_system)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("voice-start").performScrollTo().assertIsEnabled()
        assertTrue(rule.onAllNodesWithTag("voice-unavailable").fetchSemanticsNodes().isEmpty())
    }
}
