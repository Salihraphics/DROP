package app.drop.ui

import android.os.Looper
import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.Lifecycle
import app.drop.DropApplication
import app.drop.MainActivity
import app.drop.R
import app.drop.vault.VaultState
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
import java.io.File
import java.time.Duration

/** The Vault from Settings: create, write, lock, unlock, and erase after a forgotten passphrase. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class VaultUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val app get() = rule.activity.application as DropApplication

    private fun settle() {
        rule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        Thread.sleep(150)
        rule.waitForIdle()
    }

    /** Key derivation runs off the main thread and takes a moment, so wait for its result. */
    private fun waitForTag(tag: String, timeoutMs: Long = 30_000) {
        rule.waitUntil(timeoutMs) {
            shadowOf(Looper.getMainLooper()).idle()
            rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForText(text: String) {
        rule.waitUntil(30_000) {
            shadowOf(Looper.getMainLooper()).idle()
            rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun s(id: Int) = rule.activity.getString(id)

    private val secure get() = rule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0

    private fun openVault() {
        settle()
        rule.onNodeWithContentDescription(s(R.string.home_settings)).performClick()
        settle()
        rule.onNodeWithTag("open-vault").performScrollTo().performClick()
        settle()
        waitForTag("vault-setup")
    }

    private fun createVault(passphrase: String) {
        rule.onNodeWithTag("vault-passphrase").performTextReplacement(passphrase)
        rule.onNodeWithTag("vault-confirm").performTextReplacement(passphrase)
        // Nothing is created until the person accepts that there is no recovery.
        rule.onNodeWithTag("vault-create").assertIsNotEnabled()
        rule.onNodeWithTag("vault-acknowledge").performClick()
        settle()
        rule.onNodeWithTag("vault-create").assertIsEnabled().performClick()
        waitForTag("vault-list")
        settle()
    }

    private fun writeNote(title: String, body: String) {
        rule.onNodeWithTag("vault-new").performClick()
        settle()
        waitForTag("vault-note-body")
        rule.onNodeWithTag("vault-note-title").performTextReplacement(title)
        rule.onNodeWithTag("vault-note-body").performTextReplacement(body)
        settle()
        rule.onNodeWithTag("vault-done").performClick()
        waitForTag("vault-list")
        waitForText(title)
    }

    @Test
    fun aVaultNoteIsEncryptedAndComesBackAfterUnlocking() {
        openVault()
        assertTrue("Vault screens keep screenshots out", secure)

        // Too short a passphrase is refused.
        rule.onNodeWithTag("vault-passphrase").performTextReplacement("short")
        rule.onNodeWithTag("vault-confirm").performTextReplacement("short")
        rule.onNodeWithTag("vault-acknowledge").performClick()
        settle()
        rule.onNodeWithTag("vault-create").performClick()
        settle()
        rule.onNodeWithTag("vault-error").assertIsDisplayed()
        rule.onNodeWithTag("vault-acknowledge").performClick()
        settle()

        createVault("correct horse battery")
        rule.onAllNodesWithText(s(R.string.vault_empty_title)).fetchSemanticsNodes().let { assertEquals(1, it.size) }
        writeNote("Bank codes", "Locker 41, PIN in the green book")

        // The file on disk holds no plaintext.
        val files = File(app.getDatabasePath("vault.db").parent!!).listFiles { f -> f.name.startsWith("vault.db") }!!
        assertTrue(files.isNotEmpty())
        files.forEach { f ->
            val bytes = String(f.readBytes(), Charsets.ISO_8859_1)
            assertFalse("${f.name} leaks the title", bytes.contains("Bank codes"))
            assertFalse("${f.name} leaks the body", bytes.contains("green book"))
        }

        // Locking hides the notes; a wrong passphrase is refused.
        rule.onNodeWithTag("vault-lock").performClick()
        settle()
        waitForTag("vault-locked")
        assertTrue(rule.onAllNodesWithText("Bank codes").fetchSemanticsNodes().isEmpty())
        rule.onNodeWithTag("vault-passphrase").performTextReplacement("wrong horse battery")
        rule.onNodeWithTag("vault-unlock").performClick()
        waitForTag("vault-error")
        assertTrue(app.vault.state.value is VaultState.Locked)

        rule.onNodeWithTag("vault-passphrase").performTextReplacement("correct horse battery")
        rule.onNodeWithTag("vault-unlock").performClick()
        waitForTag("vault-list")
        waitForText("Bank codes")

        // Leaving the Vault locks it and lets screenshots back in.
        rule.onNodeWithTag("vault-back").performClick()
        settle()
        assertTrue(app.vault.state.value is VaultState.Locked)
        assertFalse(secure)
    }

    @Test
    fun theEditorClosesAndForgetsTheNoteWhenTheVaultLocks() {
        openVault()
        createVault("correct horse battery")
        rule.onNodeWithTag("vault-new").performClick()
        settle()
        waitForTag("vault-note-body")
        rule.onNodeWithTag("vault-note-body").performTextReplacement("Draft that must not linger")
        settle()

        // The screen turning off, DROP going to the background or the idle timer all call this.
        app.lockVault()
        settle()
        waitForTag("vault-locked")
        assertTrue(rule.onAllNodesWithTag("vault-editor").fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithText("Draft that must not linger").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun leavingTheAppLocksTheVault() {
        openVault()
        createVault("correct horse battery")
        assertTrue(app.vault.state.value is VaultState.Unlocked)
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        settle()
        assertTrue(app.vault.state.value is VaultState.Locked)
        waitForTag("vault-locked")
    }

    @Test
    fun aForgottenPassphraseErasesTheVaultOnlyAfterTypingTheWord() {
        openVault()
        createVault("correct horse battery")
        writeNote("Old secret", "gone soon")
        rule.onNodeWithTag("vault-lock").performClick()
        waitForTag("vault-locked")

        rule.onNodeWithTag("vault-forgot").performClick()
        settle()
        rule.onNodeWithTag("vault-dialog-confirm").assertIsNotEnabled()
        rule.onNodeWithTag("vault-erase-word").performTextReplacement("erase")
        settle()
        rule.onNodeWithTag("vault-dialog-confirm").assertIsEnabled().performClick()
        waitForTag("vault-setup")
        waitForText(s(R.string.vault_erased))
        assertFalse(runBlocking { app.vaultRepository.exists() })
    }
}
