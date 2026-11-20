package app.drop.ui.notes

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.drop.DropApplication
import app.drop.data.Links
import app.drop.data.Note
import app.drop.data.NoteDao
import app.drop.richtext.DropMarkdown
import app.drop.richtext.RichEditorModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class SaveState { Idle, Saving, Saved }

/** A note the editor moved to Trash; [wasEmpty] when it was closed with nothing in it. */
data class TrashedNote(val id: Long, val wasEmpty: Boolean)

/** Set once the editor has finished: the screen closes. */
data class EditorResult(val trashed: TrashedNote?)

/**
 * The note being edited. Every change is saved automatically [autosaveDelayMs] after typing stops,
 * and again when the editor closes; a save that started always finishes, even if the screen goes.
 * A new note that stays empty is never stored.
 */
class NoteEditorViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val dao: NoteDao,
    private val clock: () -> Long = System::currentTimeMillis,
    private val autosaveDelayMs: Long = 800,
    private val attachmentCount: suspend (Long) -> Int = { 0 },
    private val onTrashed: suspend (Long) -> Unit = {},
    private val onSaved: suspend (Note) -> Unit = {},
    /** Called with the stored note and its replacement before an edit is written, for Time Machine. */
    private val beforeWrite: suspend (previous: Note, next: Note) -> Unit = { _, _ -> },
    /** Restores a revision of a note and returns the note as it is now, or null. */
    private val restoreRevision: suspend (noteId: Long, revisionId: Long) -> Note? = { _, _ -> null },
) : ViewModel() {
    var title by mutableStateOf("")
        private set
    val editor = RichEditorModel(clock = clock)
    val body: String get() = editor.doc.text
    var pinned by mutableStateOf(false)
        private set
    var isTask by mutableStateOf(false)
        private set
    var completedAt by mutableStateOf<Long?>(null)
        private set

    /** The first link in the note, shown as a card. */
    val url: String? by derivedStateOf { Links.find(title, body) }

    var saveState by mutableStateOf(SaveState.Idle)
        private set
    var editedAt by mutableStateOf<Long?>(null)
        private set
    var isLoaded by mutableStateOf(false)
        private set
    var noteId by mutableStateOf<Long?>(null)
        private set
    val isNewNote: Boolean
    var result by mutableStateOf<EditorResult?>(null)
        private set

    /** The note as last stored, or null before the first save of a new note. */
    private var saved: Note? = null
    private val saveMutex = Mutex()
    private var closeRequested = false
    private var autosaveJob: Job? = null

    init {
        // After process death the editor reopens the note it already created, never a second copy.
        val id = savedStateHandle.get<Long>(KEY_SAVED_ID)
            ?: savedStateHandle.get<Long>(ARG_NOTE_ID)?.takeIf { it != NEW_NOTE_ID }
        isNewNote = id == null
        if (id == null) isTask = savedStateHandle.get<Boolean>(ARG_TASK) ?: false
        editor.onContentChange = { if (isLoaded) scheduleAutosave() }
        viewModelScope.launch {
            if (id != null) {
                dao.getById(id)?.let { note ->
                    saved = note
                    noteId = note.id
                    editedAt = note.updatedAt
                    title = note.title
                    editor.replace(DropMarkdown.docOf(note.body, note.format), resetHistory = true)
                    pinned = note.pinned
                    isTask = note.isTask
                    completedAt = note.completedAt
                }
            }
            isLoaded = true
        }
    }

    fun onTitleChange(value: String) {
        if (!isLoaded) return
        title = value
        scheduleAutosave()
    }

    fun onBodyChange(value: String) {
        if (!isLoaded) return
        editor.onValueChange(value, value.length, value.length, null)
    }

    /** Adds a [[link]] at the end of the text. */
    fun appendWikiLink(label: String) {
        if (!isLoaded) return
        val text = body
        val separator = when {
            text.isEmpty() || text.endsWith("\n") -> ""
            text.endsWith(" ") -> ""
            else -> " "
        }
        editor.appendParagraphs("[[$label]]", separator)
    }

    fun onPinnedChange(value: Boolean) {
        if (!isLoaded) return
        pinned = value
        scheduleAutosave()
    }

    fun onTaskChange(value: Boolean) {
        if (!isLoaded) return
        isTask = value
        if (!value) completedAt = null
        scheduleAutosave()
    }

    fun onCompletedChange(done: Boolean) {
        if (!isLoaded || !isTask) return
        completedAt = if (done) clock() else null
        scheduleAutosave()
    }

    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(autosaveDelayMs)
            save()
        }
    }

    private val isBlank: Boolean get() = title.isBlank() && body.isBlank()

    private suspend fun hasAttachments(): Boolean = noteId?.let { attachmentCount(it) > 0 } ?: false

    /** Stores the note if anything changed. Safe to call any number of times. */
    suspend fun save() {
        saveMutex.withLock {
            if (!isLoaded) return
            if (isBlank && !hasAttachments()) return
            val (body, format) = DropMarkdown.store(editor.doc)
            val draft = Note(
                title = title, body = body, createdAt = 0, updatedAt = 0, pinned = pinned,
                isTask = isTask, completedAt = if (isTask) completedAt else null, format = format,
            )
            val current = saved
            if (current != null && !current.differsFrom(draft)) return
            saveState = SaveState.Saving
            withContext(NonCancellable) { write(current, draft) }
            editedAt = saved?.updatedAt
            saveState = SaveState.Saved
        }
    }

    private suspend fun write(current: Note?, draft: Note) {
        if (current != null) {
            // Pinning or completing doesn't count as editing: the note keeps its date.
            val edited = current.title != draft.title || current.body != draft.body || current.format != draft.format || current.isTask != draft.isTask
            val next = current.copy(
                title = draft.title, body = draft.body, format = draft.format,
                updatedAt = if (edited) clock() else current.updatedAt,
                pinned = draft.pinned, isTask = draft.isTask, completedAt = draft.completedAt,
            ).withDerivedFields()
            if (edited) runCatching { beforeWrite(current, next) }
            dao.update(next)
            saved = next
        } else {
            val now = clock()
            val note = draft.copy(createdAt = now, updatedAt = now).withDerivedFields()
            val id = dao.insert(note)
            saved = note.copy(id = id)
            noteId = id
            savedStateHandle[KEY_SAVED_ID] = id
        }
        saved?.let { runCatching { onSaved(it) } }
    }

    private fun Note.differsFrom(draft: Note): Boolean =
        title != draft.title || body != draft.body || format != draft.format || pinned != draft.pinned ||
            isTask != draft.isTask || completedAt != draft.completedAt

    /** Restores an earlier version from Time Machine. The current text is kept as a version first. */
    fun restore(revisionId: Long, onDone: (Boolean) -> Unit = {}) {
        val id = noteId ?: return
        if (!isLoaded || closeRequested) return
        autosaveJob?.cancel()
        viewModelScope.launch {
            save()
            val restored = saveMutex.withLock {
                val restored = withContext(NonCancellable) { runCatching { restoreRevision(id, revisionId) }.getOrNull() }
                if (restored != null) {
                    saved = restored
                    title = restored.title
                    editor.replace(DropMarkdown.docOf(restored.body, restored.format), resetHistory = false)
                    editedAt = restored.updatedAt
                }
                restored
            }
            if (restored != null) {
                // The restored text replaced the editor's, which scheduled a save; nothing changed since.
                autosaveJob?.cancel()
                runCatching { onSaved(restored) }
            }
            onDone(restored != null)
        }
    }

    /** Saves without closing, for example when the app goes to the background. */
    fun saveInBackground() {
        autosaveJob?.cancel()
        viewModelScope.launch { save() }
    }

    /** Saves and closes. A note that ended up empty goes to Trash, so it can be undone. */
    fun saveAndClose() = close {
        save()
        if (isBlank && !hasAttachments()) trashSaved(wasEmpty = true) else null
    }

    fun moveToTrash() = close {
        save()
        trashSaved(wasEmpty = false)
    }

    private fun close(action: suspend () -> TrashedNote?) {
        if (closeRequested) return
        closeRequested = true
        autosaveJob?.cancel()
        if (!isLoaded) {
            result = EditorResult(null)
            return
        }
        viewModelScope.launch {
            val trashed = runCatching { action() }.getOrNull()
            result = EditorResult(trashed)
        }
    }

    private suspend fun trashSaved(wasEmpty: Boolean): TrashedNote? = saveMutex.withLock {
        val current = saved ?: return null
        withContext(NonCancellable) {
            runCatching {
                dao.setDeletedAt(current.id, clock())
                onTrashed(current.id)
            }
        }
        TrashedNote(current.id, wasEmpty)
    }

    companion object {
        const val ARG_NOTE_ID = "noteId"
        const val ARG_TASK = "task"
        const val NEW_NOTE_ID = -1L
        private const val KEY_SAVED_ID = "savedNoteId"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DropApplication
                NoteEditorViewModel(
                    savedStateHandle = createSavedStateHandle(),
                    dao = app.database.noteDao(),
                    attachmentCount = { app.database.attachmentDao().countForNote(it) },
                )
            }
        }
    }
}
