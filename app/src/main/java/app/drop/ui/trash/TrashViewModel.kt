package app.drop.ui.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.drop.DropApplication
import app.drop.attach.AttachmentRepository
import app.drop.data.Note
import app.drop.data.NoteDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Trash keeps notes until the person deletes them; deleting also removes their files. */
class TrashViewModel(private val dao: NoteDao, private val attachments: AttachmentRepository) : ViewModel() {
    val notes: StateFlow<List<Note>?> = dao.observeTrash().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun restore(id: Long) {
        viewModelScope.launch { dao.setDeletedAt(id, null) }
    }

    fun deleteForever(id: Long) {
        viewModelScope.launch { attachments.deleteFromTrash(id) }
    }

    fun emptyTrash() {
        viewModelScope.launch { attachments.emptyTrash() }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DropApplication
                TrashViewModel(app.database.noteDao(), app.attachments)
            }
        }
    }
}
