package app.drop.ocr

import app.drop.attach.AttachmentStore
import app.drop.data.Attachment
import app.drop.data.AttachmentDao
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/** How far reading an attachment has got: [page] of [pages], counting from 1. */
data class OcrProgress(val page: Int, val pages: Int)

/**
 * Reads text from photos and PDFs in the background, one attachment at a time, oldest first.
 * Work survives the app closing: anything left running is queued again on the next start.
 */
class OcrQueue(
    private val dao: AttachmentDao,
    private val store: AttachmentStore,
    private val engineFactory: () -> OcrEngine,
    private val pages: PageRenderer,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val maxPages: Int = MAX_PAGES,
) {
    private val _progress = MutableStateFlow<Map<Long, OcrProgress>>(emptyMap())

    /** Attachments being read right now, by attachment id. */
    val progress: StateFlow<Map<Long, OcrProgress>> = _progress.asStateFlow()
    private val running = Mutex()

    /** On app start: requeues work a closed app interrupted, then reads what is waiting. */
    fun resume() {
        scope.launch(dispatcher) {
            dao.requeueInterruptedOcr()
            drain()
        }
    }

    /** Reads whatever is waiting; call after adding photos or PDFs. */
    fun kick() {
        scope.launch(dispatcher) { drain() }
    }

    /** Reads an attachment again, for "Read again". */
    suspend fun retry(attachmentId: Long) {
        dao.requestOcr(attachmentId)
        kick()
    }

    private suspend fun drain() {
        while (true) {
            if (!running.tryLock()) return
            var engine: OcrEngine? = null
            try {
                while (true) {
                    val next = dao.nextPendingOcr() ?: break
                    if (dao.claimOcr(next.id) == 0) continue
                    val ready = engine ?: try {
                        engineFactory().also { engine = it }
                    } catch (e: Exception) {
                        dao.failOcr(next.id, clock())
                        continue
                    } catch (e: LinkageError) {
                        // The native library is missing on this device.
                        dao.failOcr(next.id, clock())
                        continue
                    }
                    process(next, ready)
                }
            } finally {
                engine?.close()
                running.unlock()
            }
            // Something may have been queued while the lock was being released.
            if (dao.nextPendingOcr() == null) return
        }
    }

    private suspend fun process(attachment: Attachment, engine: OcrEngine) {
        try {
            val kind = attachment.attachmentKind ?: throw IllegalStateException("unknown kind")
            val file = store.file(attachment.fileName)
            val count = pages.pageCount(file, kind).coerceAtMost(maxPages)
            val reader = PageReader(engine)
            val texts = ArrayList<String>(count)
            for (index in 0 until count) {
                _progress.update { it + (attachment.id to OcrProgress(index + 1, count)) }
                texts += reader.read(pages.render(file, kind, index)).trim()
            }
            dao.finishOcr(attachment.id, texts.filter { it.isNotEmpty() }.joinToString("\n\n"), clock())
        } catch (e: Exception) {
            dao.failOcr(attachment.id, clock())
        } catch (e: LinkageError) {
            dao.failOcr(attachment.id, clock())
        } finally {
            _progress.update { it - attachment.id }
        }
    }

    companion object {
        const val MAX_PAGES = 30
    }
}
