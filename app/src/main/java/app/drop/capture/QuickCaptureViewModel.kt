package app.drop.capture

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
import app.drop.data.Note
import app.drop.data.NoteDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

sealed interface CaptureState {
    data object Editing : CaptureState

    data object Saving : CaptureState

    data class Saved(val note: Note) : CaptureState

    data object Failed : CaptureState
}

/** Saves what was typed in the quick capture window, once, even if the save is retried. */
class QuickCaptureViewModel(
    private val handle: SavedStateHandle,
    private val dao: NoteDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    var state by mutableStateOf<CaptureState>(CaptureState.Editing)
        private set

    fun save(text: String, asTask: Boolean) {
        if (state == CaptureState.Saving || state is CaptureState.Saved) return
        if (text.isBlank()) return
        state = CaptureState.Saving
        viewModelScope.launch {
            state = try {
                CaptureState.Saved(withContext(NonCancellable + Dispatchers.IO) { persist(text, asTask) })
            } catch (e: Exception) {
                CaptureState.Failed
            }
        }
    }

    /** A task's first line is its title and the rest its notes; a note keeps everything as its body. */
    internal suspend fun persist(text: String, asTask: Boolean): Note {
        val uuid = handle.get<String>(KEY_UUID) ?: UUID.randomUUID().toString().also { handle[KEY_UUID] = it }
        dao.getByUuid(uuid)?.let { return it }
        val content = text.trim()
        val (title, body) = if (asTask) content.substringBefore('\n').trim() to content.substringAfter('\n', "").trim() else "" to content
        val now = clock()
        val note = Note(uuid = uuid, title = title, body = body, createdAt = now, updatedAt = now, isTask = asTask).withDerivedFields()
        val id = dao.insert(note)
        return note.copy(id = id)
    }

    companion object {
        const val KEY_UUID = "captureItemUuid"

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as DropApplication
                QuickCaptureViewModel(createSavedStateHandle(), app.database.noteDao())
            }
        }
    }
}
