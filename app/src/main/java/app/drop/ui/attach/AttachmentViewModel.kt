package app.drop.ui.attach

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.drop.DropApplication
import app.drop.attach.AttachmentRepository
import app.drop.data.Attachment
import app.drop.data.AttachmentDao
import app.drop.data.Note
import app.drop.data.NoteDao
import app.drop.ocr.OcrProgress
import app.drop.ocr.OcrQueue
import app.drop.richtext.DropMarkdown
import app.drop.richtext.RichDoc
import app.drop.richtext.RichEdit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** An attachment with the note it belongs to. */
data class AttachmentView(val attachment: Attachment, val noteId: Long?, val noteTitle: String)

/** One attachment: its file, the text read from it, and what can be done with that text. */
@OptIn(ExperimentalCoroutinesApi::class)
class AttachmentViewModel(
    handle: SavedStateHandle,
    private val dao: AttachmentDao,
    private val notes: NoteDao,
    private val ocr: OcrQueue,
    private val repository: AttachmentRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Keeps the note's previous text in History before "Add to note" changes it. */
    private val beforeWrite: suspend (previous: Note, next: Note) -> Unit = { _, _ -> },
) : ViewModel() {
    private val id: Long = handle[ARG_ID] ?: -1L

    /** Null while loading; [Missing] once the attachment is gone. */
    val view: StateFlow<AttachmentView?> = dao.observe(id)
        .mapLatest { a ->
            if (a == null) {
                MISSING
            } else {
                val note = notes.getById(a.noteId)
                AttachmentView(a, note?.takeIf { it.deletedAt == null }?.id, note?.title.orEmpty())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val progress: StateFlow<OcrProgress?> = ocr.progress.map { it[id] }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var deleted by mutableStateOf(false)
        private set

    /** Keeps the person's corrections; marks the text as edited. */
    fun saveText(text: String) {
        viewModelScope.launch { dao.editOcrText(id, text.trim(), clock()) }
    }

    /** Appends [text] to the note as new paragraphs, after a blank line. */
    fun addToNote(text: String, then: () -> Unit) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val attachment = dao.getById(id) ?: return@launch
            val note = notes.getById(attachment.noteId) ?: return@launch
            val doc = DropMarkdown.docOf(note.body, note.format)
            val trimmedText = doc.text.trimEnd()
            val trimmed = RichDoc(
                trimmedText,
                doc.spans.mapNotNull { s -> if (s.start >= trimmedText.length) null else s.copy(end = minOf(s.end, trimmedText.length)) },
                doc.blocks.take(RichDoc.lineCount(trimmedText)),
            )
            val (body, format) = DropMarkdown.store(RichEdit.appendParagraphs(trimmed, clean, if (trimmed.text.isBlank()) "" else "\n\n"))
            val updated = note.copy(body = body, format = format, updatedAt = clock()).withDerivedFields()
            runCatching {
                runCatching { beforeWrite(note, updated) }
                notes.update(updated)
            }.onSuccess { then() }
        }
    }

    fun retry() {
        viewModelScope.launch { ocr.retry(id) }
    }

    fun delete() {
        viewModelScope.launch {
            repository.delete(id)
            deleted = true
        }
    }

    companion object {
        const val ARG_ID = "attachmentId"
        private val MISSING = AttachmentView(Attachment(id = -1, noteId = -1, kind = "", fileName = "", displayName = "", mimeType = "", sizeBytes = 0, createdAt = 0), null, "")

        fun isMissing(view: AttachmentView) = view === MISSING

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as DropApplication
                AttachmentViewModel(
                    createSavedStateHandle(),
                    app.database.attachmentDao(),
                    app.database.noteDao(),
                    app.ocr,
                    app.attachments,
                    beforeWrite = app.history::beforeWrite,
                )
            }
        }
    }
}
