package app.drop.ui.templates

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
import app.drop.data.NoteDao
import app.drop.richtext.DropMarkdown
import app.drop.richtext.RichEditorModel
import app.drop.templates.TemplateStore
import app.drop.ui.notes.cardTitle
import kotlinx.coroutines.launch

/**
 * Edits one of the user's own templates, a new one, or a new one made from a note. The draft
 * survives the app being closed by the system; nothing is saved until the user taps Save.
 */
class TemplateEditorViewModel(
    private val handle: SavedStateHandle,
    private val store: TemplateStore,
    private val notes: NoteDao,
) : ViewModel() {
    private val id: Long? = handle.get<Long>(ARG_ID)?.takeIf { it >= 0 }
    private val fromNote: Long? = handle.get<Long>(ARG_FROM_NOTE)?.takeIf { it >= 0 }

    val isNew: Boolean get() = id == null

    var name by mutableStateOf("")
        private set
    var title by mutableStateOf("")
        private set
    var isTask by mutableStateOf(false)
        private set
    val editor = RichEditorModel()
    var loaded by mutableStateOf(false)
        private set
    var dirty by mutableStateOf(false)
        private set
    var nameError by mutableStateOf(false)
        private set
    var saved by mutableStateOf(false)
        private set

    init {
        editor.onContentChange = { changed() }
        viewModelScope.launch {
            val draftBody = handle.get<String>(KEY_BODY)
            if (draftBody != null) {
                name = handle.get<String>(KEY_NAME) ?: ""
                title = handle.get<String>(KEY_TITLE) ?: ""
                isTask = handle.get<Boolean>(KEY_TASK) ?: false
                editor.replace(DropMarkdown.docOf(draftBody, handle.get<Int>(KEY_FORMAT) ?: 0), resetHistory = true)
                dirty = true
            } else if (id != null) {
                store.get(id)?.let { t ->
                    name = t.name
                    title = t.title
                    isTask = t.isTask
                    editor.replace(DropMarkdown.docOf(t.body, t.format), resetHistory = true)
                }
            } else if (fromNote != null) {
                notes.getById(fromNote)?.let { n ->
                    name = cardTitle(n).take(TemplateStore.MAX_NAME)
                    title = n.title
                    isTask = n.isTask
                    editor.replace(DropMarkdown.docOf(n.body, n.format), resetHistory = true)
                    dirty = true
                }
            }
            loaded = true
        }
    }

    fun onNameChange(value: String) {
        name = value.replace('\n', ' ')
        nameError = false
        changed()
    }

    fun onTitleChange(value: String) {
        title = value.replace('\n', ' ')
        changed()
    }

    fun onTaskChange(value: Boolean) {
        isTask = value
        changed()
    }

    private fun changed() {
        if (!loaded) return
        dirty = true
        val (body, format) = DropMarkdown.store(editor.doc)
        handle[KEY_NAME] = name
        handle[KEY_TITLE] = title
        handle[KEY_TASK] = isTask
        handle[KEY_BODY] = body
        handle[KEY_FORMAT] = format
    }

    fun save() {
        if (!loaded || saved) return
        if (name.isBlank()) {
            nameError = true
            return
        }
        viewModelScope.launch {
            store.save(id, name, title, editor.doc, isTask)
            dirty = false
            saved = true
        }
    }

    companion object {
        const val ARG_ID = "templateId"
        const val ARG_FROM_NOTE = "fromNote"
        private const val KEY_NAME = "draftName"
        private const val KEY_TITLE = "draftTitle"
        private const val KEY_TASK = "draftTask"
        private const val KEY_BODY = "draftBody"
        private const val KEY_FORMAT = "draftFormat"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DropApplication
                TemplateEditorViewModel(createSavedStateHandle(), app.templates, app.database.noteDao())
            }
        }
    }
}
