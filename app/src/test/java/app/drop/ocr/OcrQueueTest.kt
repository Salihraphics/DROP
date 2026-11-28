package app.drop.ocr

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.drop.attach.AttachmentStore
import app.drop.data.Attachment
import app.drop.data.AttachmentKind
import app.drop.data.DropDatabase
import app.drop.data.Note
import app.drop.data.OcrStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.Collections

/** The background OCR queue: reads waiting photos and PDFs, survives restarts, reports failures. */
@RunWith(RobolectricTestRunner::class)
class OcrQueueTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: DropDatabase
    private lateinit var store: AttachmentStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, DropDatabase::class.java).allowMainThreadQueries().build()
        store = AttachmentStore(File(context.filesDir, "queue-test"))
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    /** Pages are blank pictures whose width says which page they are; the engine "reads" that number. */
    private class Pages(private val counts: Map<String, Int>) : PageRenderer {
        override fun pageCount(file: File, kind: AttachmentKind) = counts.getValue(file.name)

        override fun render(file: File, kind: AttachmentKind, index: Int) =
            GrayImage(100 + index, 50).also { it.pixels.fill(255.toByte()) }
    }

    private class Engine(private val fail: Boolean = false) : OcrEngine {
        val seen: MutableList<Int> = Collections.synchronizedList(mutableListOf())
        var closed = false

        override fun recognize(page: GrayImage): Recognized {
            if (fail) throw IllegalStateException("broken page")
            seen += page.width
            return Recognized("Page ${page.width - 99} reads clearly with plenty of words here", 90)
        }

        override fun close() {
            closed = true
        }
    }

    private suspend fun attachment(name: String, kind: AttachmentKind = AttachmentKind.Pdf, status: OcrStatus = OcrStatus.Pending): Long {
        val noteId = db.noteDao().insert(Note(title = name, body = "", createdAt = 1, updatedAt = 1))
        store.file(name).apply { parentFile?.mkdirs() }.writeText("x")
        return db.attachmentDao().insert(
            Attachment(noteId = noteId, kind = kind.id, fileName = name, displayName = name, mimeType = "application/pdf", sizeBytes = 1, createdAt = 1, ocrStatus = status.id),
        )
    }

    private suspend fun waitFor(id: Long, status: OcrStatus): Attachment = withTimeout(10_000) {
        while (true) {
            val row = db.attachmentDao().getById(id)!!
            if (row.status == status) return@withTimeout row
            delay(20)
        }
        @Suppress("UNREACHABLE_CODE")
        error("unreachable")
    }

    @Test
    fun readsEveryPageAndStoresTheText(): Unit = runBlocking<Unit> {
        val engine = Engine()
        val queue = OcrQueue(db.attachmentDao(), store, { engine }, Pages(mapOf("a.pdf" to 3)), scope, clock = { 42 })
        val id = attachment("a.pdf")
        queue.kick()
        val done = waitFor(id, OcrStatus.Done)
        assertEquals(
            "Page 1 reads clearly with plenty of words here\n\nPage 2 reads clearly with plenty of words here\n\nPage 3 reads clearly with plenty of words here",
            done.ocrText,
        )
        assertEquals(42L, done.ocrUpdatedAt)
        assertEquals(listOf(100, 101, 102), engine.seen.toList())
        withTimeout(5_000) { while (!engine.closed) delay(10) }
        assertTrue(queue.progress.value.isEmpty())
    }

    @Test
    fun aLongPdfIsReadUpToTheLimit(): Unit = runBlocking<Unit> {
        val engine = Engine()
        val queue = OcrQueue(db.attachmentDao(), store, { engine }, Pages(mapOf("long.pdf" to 80)), scope, maxPages = 5)
        val id = attachment("long.pdf")
        queue.kick()
        waitFor(id, OcrStatus.Done)
        assertEquals(5, engine.seen.size)
    }

    @Test
    fun aBrokenFileFailsWithoutStoppingTheQueue(): Unit = runBlocking<Unit> {
        var calls = 0
        val good = Engine()
        val queue = OcrQueue(
            db.attachmentDao(), store,
            { if (calls++ == 0) Engine(fail = true) else good },
            Pages(mapOf("bad.pdf" to 1, "good.pdf" to 1)), scope,
        )
        val bad = attachment("bad.pdf")
        queue.kick()
        assertNull(waitFor(bad, OcrStatus.Failed).ocrText)
        val fine = attachment("good.pdf")
        queue.kick()
        waitFor(fine, OcrStatus.Done)
    }

    @Test
    fun anEngineThatCannotStartMarksTheFileFailed(): Unit = runBlocking<Unit> {
        val queue = OcrQueue(db.attachmentDao(), store, { throw UnsatisfiedLinkError("no libtesseract") }, Pages(mapOf("a.pdf" to 1)), scope)
        val id = attachment("a.pdf")
        queue.kick()
        waitFor(id, OcrStatus.Failed)
    }

    @Test
    fun aNativeLibraryErrorWhileReadingFailsTheFileInsteadOfCrashing(): Unit = runBlocking<Unit> {
        val broken = object : OcrEngine {
            override fun recognize(page: GrayImage): Recognized = throw UnsatisfiedLinkError("no jpeg")

            override fun close() = Unit
        }
        val queue = OcrQueue(db.attachmentDao(), store, { broken }, Pages(mapOf("a.pdf" to 1)), scope)
        val id = attachment("a.pdf")
        queue.kick()
        waitFor(id, OcrStatus.Failed)
    }

    @Test
    fun workInterruptedByClosingTheAppIsDoneOnTheNextStart(): Unit = runBlocking<Unit> {
        val engine = Engine()
        val id = attachment("a.pdf", status = OcrStatus.Running)
        val queue = OcrQueue(db.attachmentDao(), store, { engine }, Pages(mapOf("a.pdf" to 1)), scope)
        queue.resume()
        waitFor(id, OcrStatus.Done)
    }

    @Test
    fun readAgainReplacesTheText(): Unit = runBlocking<Unit> {
        val engine = Engine()
        val queue = OcrQueue(db.attachmentDao(), store, { engine }, Pages(mapOf("a.pdf" to 1)), scope)
        val id = attachment("a.pdf", status = OcrStatus.Done)
        db.attachmentDao().editOcrText(id, "my correction", 5)
        queue.retry(id)
        val done = waitFor(id, OcrStatus.Done).let { row ->
            withTimeout(10_000) {
                var r = row
                while (r.ocrEdited) {
                    delay(20)
                    r = db.attachmentDao().getById(id)!!
                }
                r
            }
        }
        assertEquals("Page 1 reads clearly with plenty of words here", done.ocrText)
        assertEquals(false, done.ocrEdited)
    }
}
