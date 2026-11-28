package app.drop.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import app.drop.DropApplication
import app.drop.MainActivity
import app.drop.R
import app.drop.attach.FileImportSource
import app.drop.attach.ImportError
import app.drop.data.Attachment
import app.drop.data.AttachmentKind
import app.drop.data.Note
import app.drop.data.OcrStatus
import app.drop.ui.paper.PaperState
import app.drop.ui.paper.PaperViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

/** Paper Capture and the attachment screen: import, save, read text, correct it and use it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
class PaperCaptureTest {
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

    private fun photo(): File {
        val bitmap = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(Color.WHITE)
        val file = File(rule.activity.cacheDir, "page.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }

    private fun waitForState(vm: PaperViewModel, done: (PaperState) -> Boolean) = rule.waitUntil(15_000) {
        shadowOf(Looper.getMainLooper()).idle()
        done(vm.state)
    }

    @Test
    fun aPhotoBecomesANoteAndIsQueuedForReading() {
        settle()
        var kicked = 0
        val vm = PaperViewModel(SavedStateHandle(), app.attachments, onSaved = { kicked++ }, io = Dispatchers.IO)
        vm.import(FileImportSource(photo(), "Page.png"))
        waitForState(vm) { it is PaperState.Preview }
        vm.save("Receipt from the market")
        waitForState(vm) { it is PaperState.Saved }
        val noteId = (vm.state as PaperState.Saved).noteId
        val note = runBlocking { app.database.noteDao().getById(noteId) }
        assertEquals("Receipt from the market", note!!.title)
        val attachments = runBlocking { app.database.attachmentDao().forNotes(listOf(noteId)) }
        assertEquals(1, attachments.size)
        assertEquals(AttachmentKind.Image, attachments[0].attachmentKind)
        // Reading starts after saving; on this test machine Tesseract isn't there, so it can't finish as "done".
        assertTrue(attachments[0].status in setOf(OcrStatus.Pending, OcrStatus.Running, OcrStatus.Failed))
        assertEquals(1, kicked)
    }

    @Test
    fun aScanJoinsAnExistingNoteButNotOneInTrash() {
        settle()
        val noteId = runBlocking { app.database.noteDao().insert(Note(title = "Trip", body = "", createdAt = 1, updatedAt = 1)) }
        val vm = PaperViewModel(SavedStateHandle(mapOf(PaperViewModel.ARG_NOTE_ID to noteId)), app.attachments, onSaved = {}, io = Dispatchers.IO)
        vm.import(FileImportSource(photo(), "Page.png"))
        waitForState(vm) { it is PaperState.Preview }
        vm.save("ignored")
        waitForState(vm) { it is PaperState.Saved }
        assertEquals(noteId, (vm.state as PaperState.Saved).noteId)
        assertEquals(1, runBlocking { app.database.attachmentDao().forNotes(listOf(noteId)) }.size)

        runBlocking { app.database.noteDao().setDeletedAt(noteId, 5) }
        val again = PaperViewModel(SavedStateHandle(mapOf(PaperViewModel.ARG_NOTE_ID to noteId)), app.attachments, onSaved = {}, io = Dispatchers.IO)
        again.import(FileImportSource(photo(), "Page.png"))
        waitForState(again) { it is PaperState.Preview }
        again.save("ignored")
        waitForState(again) { it == PaperState.Choose }
        assertEquals(ImportError.ReadFailed, again.error)
        assertEquals(1, runBlocking { app.database.attachmentDao().forNotes(listOf(noteId)) }.size)
    }

    @Test
    fun scanOnHomeOpensPaperCapture() {
        settle()
        waitForTag("dashboard-list")
        rule.onNodeWithTag("dashboard-list").performScrollToNode(hasTestTag("capture-scan"))
        rule.onNodeWithTag("capture-scan").performClick()
        settle()
        rule.onNodeWithTag("paper").assertIsDisplayed()
        rule.onNodeWithTag("paper-camera").assertIsDisplayed()
        rule.onNodeWithTag("paper-image").assertIsDisplayed()
        rule.onNodeWithTag("paper-pdf").assertIsDisplayed()
    }

    @Test
    fun textReadFromAScanCanBeCorrectedAndAddedToTheNote() {
        settle()
        val file = photo()
        val (noteId, attachmentId) = runBlocking {
            val noteId = app.database.noteDao().insert(Note(title = "Receipt", body = "Paid in cash.", createdAt = 1, updatedAt = 1))
            val name = "scan.png"
            file.copyTo(app.attachments.store.file(name), overwrite = true)
            val id = app.database.attachmentDao().insert(
                Attachment(
                    noteId = noteId, kind = AttachmentKind.Image.id, fileName = name, displayName = "Page.png", mimeType = "image/png",
                    sizeBytes = file.length(), createdAt = 1, ocrStatus = OcrStatus.Done.id, ocrText = "MARKET FİŞİ\nSüt 1L 91.52\nTOPLAM 572.79", ocrUpdatedAt = 2,
                ),
            )
            noteId to id
        }
        // Open the note from the Library, then its attachment.
        waitForTag("nav-library")
        rule.onNodeWithTag("nav-library").performClick()
        settle()
        rule.onNodeWithText("Receipt").performClick()
        settle()
        waitForTag("attachment-$attachmentId")
        rule.onNodeWithTag("attachment-$attachmentId").performScrollTo().performClick()
        settle()
        waitForTag("ocr-text")
        rule.onNodeWithTag("attachment").assertIsDisplayed()
        assertTrue(rule.onAllNodesWithText("MARKET FİŞİ\nSüt 1L 91.52\nTOPLAM 572.79").fetchSemanticsNodes().isNotEmpty())

        // Add to note: appended as new paragraphs after what was there.
        rule.onNodeWithTag("ocr-add").performScrollTo().performClick()
        settle()
        rule.waitUntil(10_000) { runBlocking { app.database.noteDao().getById(noteId) }!!.body.contains("TOPLAM") }
        assertEquals("Paid in cash.\n\nMARKET FİŞİ\nSüt 1L 91.52\nTOPLAM 572.79", runBlocking { app.database.noteDao().getById(noteId) }!!.body)

        // Correct a misread price and keep it.
        rule.onNodeWithTag("ocr-text").performScrollTo().performTextReplacement("MARKET FİŞİ\nSüt 1L 91.50\nTOPLAM 572.79")
        settle()
        rule.onNodeWithTag("ocr-save").performScrollTo().performClick()
        settle()
        rule.waitUntil(10_000) { runBlocking { app.database.attachmentDao().getById(attachmentId) }!!.ocrEdited }
        assertEquals("MARKET FİŞİ\nSüt 1L 91.50\nTOPLAM 572.79", runBlocking { app.database.attachmentDao().getById(attachmentId) }!!.ocrText)

        // Reading again would replace the correction, so it asks first.
        rule.onNodeWithTag("ocr-retry").performScrollTo().performClick()
        settle()
        rule.onNodeWithText(s(R.string.ocr_replace_title)).assertIsDisplayed()
        rule.onNodeWithTag("ocr-replace-confirm").performClick()
        settle()
        val after = runBlocking { app.database.attachmentDao().getById(attachmentId) }
        assertNotNull(after)
        assertTrue(after!!.status != OcrStatus.Done || !after.ocrEdited)
    }
}
