package app.drop.ui.paper

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
import app.drop.attach.ImportError
import app.drop.attach.ImportSource
import app.drop.attach.StagedFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

sealed interface PaperState {
    data object Choose : PaperState

    data object Importing : PaperState

    data class Preview(val staged: StagedFile) : PaperState

    data object Saving : PaperState

    data class Saved(val noteId: Long) : PaperState
}

/**
 * Paper Capture: copies a photo, image or PDF into DROP, shows it, and saves it as a new note
 * or onto an existing one. Text is read afterwards, in the background.
 */
class PaperViewModel(
    private val handle: SavedStateHandle,
    private val attachments: AttachmentRepository,
    private val onSaved: () -> Unit,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    /** The note to attach to, or null for a new note. */
    val attachTo: Long? = handle.get<Long>(ARG_NOTE_ID)?.takeIf { it > 0 }

    var state by mutableStateOf<PaperState>(PaperState.Choose)
        private set
    var error by mutableStateOf<ImportError?>(null)
        private set
    var saveFailed by mutableStateOf(false)
        private set

    /** The camera's output file, kept across the camera app taking over the screen. */
    var pendingCameraFile: File?
        get() = handle.get<String>(KEY_CAMERA_FILE)?.let(::File)
        set(value) {
            handle[KEY_CAMERA_FILE] = value?.path
        }

    fun clearError() {
        error = null
        saveFailed = false
    }

    fun import(source: ImportSource, afterwards: () -> Unit = {}) {
        if (state != PaperState.Choose) return
        error = null
        state = PaperState.Importing
        viewModelScope.launch {
            val result = withContext(io) {
                try {
                    runCatching { attachments.store.stage(source) }
                } finally {
                    afterwards()
                }
            }
            result.fold(
                onSuccess = { state = PaperState.Preview(it) },
                onFailure = {
                    state = PaperState.Choose
                    error = it as? ImportError ?: ImportError.ReadFailed
                },
            )
        }
    }

    /** Back from the preview: the copied file is thrown away. */
    fun cancelPreview() {
        val s = state as? PaperState.Preview ?: return
        attachments.store.discard(s.staged.partName)
        handle.remove<String>(KEY_UUID)
        state = PaperState.Choose
    }

    fun save(title: String) {
        val s = state as? PaperState.Preview ?: return
        saveFailed = false
        // The same id on a retry, so a save that half-worked can't make two notes.
        val uuid = handle.get<String>(KEY_UUID) ?: UUID.randomUUID().toString().also { handle[KEY_UUID] = it }
        state = PaperState.Saving
        viewModelScope.launch {
            try {
                val noteId = withContext(NonCancellable + io) {
                    val target = attachTo
                    if (target == null) {
                        attachments.saveAsNewNote(uuid, title.trim(), "", s.staged)
                    } else {
                        runCatching { attachments.attachTo(target, s.staged) }.getOrNull()?.let { target }
                    }
                }
                if (noteId == null) {
                    // The note went away (to Trash, say) while the file was being looked at.
                    state = PaperState.Choose
                    error = ImportError.ReadFailed
                } else {
                    onSaved()
                    state = PaperState.Saved(noteId)
                }
            } catch (e: Exception) {
                state = s
                saveFailed = true
            }
        }
    }

    override fun onCleared() {
        (state as? PaperState.Preview)?.let { attachments.store.discard(it.staged.partName) }
        pendingCameraFile?.delete()
    }

    companion object {
        const val ARG_NOTE_ID = "attachTo"
        private const val KEY_CAMERA_FILE = "cameraFile"
        private const val KEY_UUID = "paperItemUuid"

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as DropApplication
                PaperViewModel(createSavedStateHandle(), app.attachments, onSaved = app.ocr::kick)
            }
        }
    }
}
