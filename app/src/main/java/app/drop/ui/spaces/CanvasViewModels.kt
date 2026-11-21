package app.drop.ui.spaces

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.drop.DropApplication
import app.drop.data.CanvasCard
import app.drop.data.CanvasDao
import app.drop.data.CanvasEdge
import app.drop.data.CanvasSummary
import app.drop.data.CardColor
import app.drop.data.CardShape
import app.drop.data.CardWithNote
import app.drop.data.IdeaCanvas
import app.drop.data.Note
import app.drop.data.NoteDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs

class CanvasListViewModel(private val dao: CanvasDao, private val clock: () -> Long = System::currentTimeMillis) : ViewModel() {
    /** Null until loaded. */
    val canvases: StateFlow<List<CanvasSummary>?> = dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun create(name: String, then: (Long) -> Unit) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val now = clock()
            then(dao.insert(IdeaCanvas(name = name.trim(), createdAt = now, updatedAt = now)))
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DropApplication
                CanvasListViewModel(app.database.canvasDao())
            }
        }
    }
}

/**
 * One Idea Canvas: notes laid out as cards that can be moved, styled and connected. Removing a
 * card never deletes its note; deleting the canvas keeps every note too.
 */
class CanvasViewModel(
    handle: SavedStateHandle,
    private val dao: CanvasDao,
    private val noteDao: NoteDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    val id: Long = handle.get<Long>(ARG_ID) ?: -1L
    val canvas: StateFlow<IdeaCanvas?> = dao.observe(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val cards: StateFlow<List<CardWithNote>?> = dao.observeCards(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val edges: StateFlow<List<CanvasEdge>> = dao.observeEdges(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val allNotes: StateFlow<List<Note>> = noteDao.observeActive().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Places the notes as new cards in free slots from ([originX], [originY]), never on top of another card. */
    fun add(noteIds: List<Long>, originX: Float = 0f, originY: Float = 0f) {
        viewModelScope.launch {
            val taken = dao.cardsOf(id).map { it.x to it.y }.toMutableList()
            var n = 0
            for (noteId in noteIds) {
                var spot = CanvasGeometry.slot(n++, originX, originY)
                while (n < MAX_SLOTS && taken.any { overlaps(it, spot) }) spot = CanvasGeometry.slot(n++, originX, originY)
                val (x, y) = CanvasGeometry.clamp(spot.first, spot.second)
                dao.addCard(CanvasCard(canvasId = id, noteId = noteId, x = x, y = y))
                taken.add(x to y)
            }
            touch()
        }
    }

    fun move(cardId: Long, x: Float, y: Float) {
        val (cx, cy) = CanvasGeometry.clamp(x, y)
        viewModelScope.launch { dao.moveCard(cardId, cx, cy) }
    }

    fun connect(a: Long, b: Long) {
        if (a == b) return
        viewModelScope.launch { dao.addEdge(CanvasEdge(minOf(a, b), maxOf(a, b))) }
    }

    /** Draws a line between two cards, or removes it if they are already connected. */
    fun toggleConnection(from: Long, to: Long) {
        if (from == to) return
        val a = minOf(from, to)
        val b = maxOf(from, to)
        val linked = edges.value.any { it.fromCard == a && it.toCard == b }
        viewModelScope.launch { if (linked) dao.removeEdge(a, b) else dao.addEdge(CanvasEdge(a, b)) }
    }

    fun style(cardId: Long, shape: CardShape, color: CardColor) {
        viewModelScope.launch { dao.styleCard(cardId, shape.id, color.id) }
    }

    /** Writes [text] as a new note (first line as its title) and puts it on the canvas as a sticky note. */
    fun addTextBlock(text: String, x: Float, y: Float) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val now = clock()
            val title = clean.lineSequence().first().take(200)
            val body = clean.substringAfter('\n', "").trim()
            val noteId = noteDao.insert(Note(title = title, body = body, createdAt = now, updatedAt = now).withDerivedFields())
            val (cx, cy) = CanvasGeometry.clamp(x, y)
            dao.addCard(CanvasCard(canvasId = id, noteId = noteId, x = cx, y = cy, shape = CardShape.Sticky.id, color = CardColor.Amber.id))
            touch()
        }
    }

    fun disconnect(cardId: Long) {
        viewModelScope.launch { dao.removeEdgesOf(cardId) }
    }

    /** Takes the card off the canvas; the note stays. */
    fun remove(cardId: Long) {
        viewModelScope.launch { dao.removeCard(cardId) }
    }

    fun rename(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { dao.rename(id, name.trim(), clock()) }
    }

    fun delete(then: () -> Unit) {
        viewModelScope.launch {
            dao.delete(id)
            then()
        }
    }

    private suspend fun touch() {
        val it = canvas.value ?: return
        dao.rename(id, it.name, clock())
    }

    private fun overlaps(a: Pair<Float, Float>, b: Pair<Float, Float>): Boolean =
        abs(a.first - b.first) < CanvasGeometry.CARD_W && abs(a.second - b.second) < CanvasGeometry.CARD_H

    companion object {
        const val ARG_ID = "canvasId"
        private const val MAX_SLOTS = 400

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DropApplication
                CanvasViewModel(createSavedStateHandle(), app.database.canvasDao(), app.database.noteDao())
            }
        }
    }
}
