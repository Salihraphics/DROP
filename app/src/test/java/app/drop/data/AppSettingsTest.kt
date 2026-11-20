package app.drop.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class AppSettingsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    /** The test application already ran its own first launch; each test starts from a fresh install. */
    @Before
    fun freshInstall() {
        context.getSharedPreferences("drop_settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun aNewInstallationGetsOnboardingAndNoWhatsNew() {
        val s = AppSettings(context)
        s.initLaunchState(databaseExisted = false, updatedInPlace = false, currentVersion = "1.0.0")
        assertFalse(s.onboardingDone.value)
        assertEquals("1.0.0", s.releaseNotesSeen.value)
    }

    @Test
    fun anyoneWhoUsedDropBeforeIsNeverSentThroughOnboardingAgain() {
        val s = AppSettings(context)
        s.initLaunchState(databaseExisted = true, updatedInPlace = false, currentVersion = "1.0.0")
        assertTrue(s.onboardingDone.value)
        assertNull(s.releaseNotesSeen.value)
        // Runs only once: a later call never resets the answer.
        AppSettings(context).initLaunchState(databaseExisted = false, updatedInPlace = false, currentVersion = "2.0.0")
        assertTrue(AppSettings(context).onboardingDone.value)
    }

    @Test
    fun everySettingSurvivesARestart() {
        val s = AppSettings(context)
        val p = Personalization(
            useCases = setOf(UseCase.Tasks, UseCase.Research), style = ExperienceStyle.Organized, familiarity = Familiarity.Experienced,
            where = CaptureWhere.Both, savesLinks = true, paper = false, dictation = true, journal = false, longWriting = true,
            repeatNotes = false, reminders = true, privateNotes = true,
        )
        s.setPersonalization(p)
        s.setThemeMode(ThemeMode.Dark)
        s.setNoteFont(NoteFont.Serif)
        s.setEchoEnabled(false)
        s.dismissHint("a")
        s.setSpeechLanguage("tr-TR")
        s.setAllowSystemSpeech(true)
        s.recordBackup(42)
        val again = AppSettings(context)
        assertEquals(p, again.personalization.value)
        assertEquals(ThemeMode.Dark, again.themeMode.value)
        assertEquals(NoteFont.Serif, again.noteFont.value)
        assertFalse(again.echoEnabled.value)
        assertEquals(setOf("a"), again.dismissedHints.value)
        assertEquals("tr-TR", again.speechLanguage.value)
        assertTrue(again.allowSystemSpeech.value)
        assertEquals(42L, again.lastBackupAt.value)
    }

    @Test
    fun defaultsAreSafe() {
        val s = AppSettings(context)
        assertEquals(ThemeMode.System, s.themeMode.value)
        assertEquals(NoteFont.Default, s.noteFont.value)
        assertTrue(s.echoEnabled.value)
        assertFalse(s.allowSystemSpeech.value)
        assertNull(s.lastBackupAt.value)
        assertEquals(Personalization(), s.personalization.value)
        assertEquals("tr-TR", AppSettings.defaultSpeechLanguage(Locale.forLanguageTag("tr-TR")))
        assertEquals("en-US", AppSettings.defaultSpeechLanguage(Locale.GERMANY))
    }
}
