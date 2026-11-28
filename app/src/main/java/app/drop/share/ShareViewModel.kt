package app.drop.share

import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.drop.data.Note
import app.drop.data.NoteDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

sealed interface ShareState {
    data object Saving : ShareState

    data class Saved(val note: Note, val truncated: Boolean, val alreadySaved: Boolean) : ShareState

    data object NothingToSave : ShareState

    data object Failed : ShareState
}

/** Saves a share as soon as it arrives: once per share, and not again if the same thing was just saved. */
class ShareViewModel(
    private val handle: SavedStateHandle,
    private val dao: NoteDao,
    supported: Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    var state by mutableStateOf<ShareState>(ShareState.Saving)
        private set

    init {
        val content = if (supported) SharedContentParser.parse(handle.get<CharSequence>(Intent.EXTRA_TEXT), handle.get<CharSequence>(Intent.EXTRA_SUBJECT)) else null
        if (content == null) {
            state = ShareState.NothingToSave
        } else {
            viewModelScope.launch {
                state = try {
                    withContext(NonCancellable + Dispatchers.IO) { save(content) }
                } catch (e: Exception) {
                    ShareState.Failed
                }
            }
        }
    }

    private suspend fun save(content: SharedContent): ShareState {
        // The same id when the screen is recreated, so rotating doesn't save again.
        val uuid = handle.get<String>(KEY_UUID) ?: UUID.randomUUID().toString().also { handle[KEY_UUID] = it }
        dao.getByUuid(uuid)?.let { return ShareState.Saved(it, content.truncated, alreadySaved = false) }
        val now = clock()
        // Sharing the same thing twice in a row (a double tap, say) keeps one note.
        dao.findRecentDuplicate(content.title, content.body, now - REPEAT_WINDOW_MS)?.let {
            return ShareState.Saved(it, content.truncated, alreadySaved = true)
        }
        val note = Note(uuid = uuid, title = content.title, body = content.body, createdAt = now, updatedAt = now).withDerivedFields()
        val id = dao.insert(note)
        return ShareState.Saved(note.copy(id = id), content.truncated, alreadySaved = false)
    }

    companion object {
        private const val KEY_UUID = "shareItemUuid"
        const val REPEAT_WINDOW_MS = 60_000L
    }
}
