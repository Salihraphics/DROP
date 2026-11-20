package app.drop.ui.notes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.drop.DropApplication
import app.drop.data.AttachmentDao
import app.drop.data.AttachmentSummary
import app.drop.data.Note
import app.drop.data.NoteAlias
import app.drop.data.NoteDao
import app.drop.data.OcrEntry
import app.drop.richtext.DropMarkdown
import app.drop.search.FoldedText
import app.drop.search.SearchQuery
import app.drop.search.Snippet
import app.drop.search.snippet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class HomeFilter { All, Tasks, Links, Pinned }

/** A note found only through text read from one of its scans. */
data class OcrHit(val attachmentId: Long, val snippet: Snippet)

data class HomeState(
    val filter: HomeFilter,
    val query: SearchQuery,
    val pinned: List<Note>,
    val others: List<Note>,
    val completed: List<Note>,
    val totalCount: Int,
    val filterCount: Int,
    val ocrHits: Map<Long, OcrHit> = emptyMap(),
    val attachments: Map<Long, List<AttachmentSummary>> = emptyMap(),
) {
    val isEmpty: Boolean get() = pinned.isEmpty() && others.isEmpty() && completed.isEmpty()
}

/** Folded text of each note, kept between searches and refreshed only when a note changes. */
class SearchIndex {
    private class Entry(
        val title: String,
        val body: String,
        val format: Int,
        val aliases: String,
        val foldedTitle: FoldedText,
        val foldedBody: FoldedText,
        val foldedAliases: FoldedText,
    )

    private val entries = HashMap<Long, Entry>()
    private val ocrEntries = HashMap<Long, Pair<String, FoldedText>>()

    /** Whether the note's title, text or aliases contain every term. */
    fun matches(note: Note, query: SearchQuery, aliases: String = ""): Boolean {
        if (query.isEmpty()) return true
        val entry = entry(note, aliases)
        return query.matches(entry.foldedTitle, entry.foldedBody, entry.foldedAliases)
    }

    private fun entry(note: Note, aliases: String): Entry {
        val cached = entries[note.id]
        if (cached != null && cached.title == note.title && cached.body == note.body && cached.format == note.format && cached.aliases == aliases) return cached
        val text = DropMarkdown.plainOf(note.body, note.format)
        return Entry(note.title, note.body, note.format, aliases, FoldedText(note.title), FoldedText(text), FoldedText(aliases)).also { entries[note.id] = it }
    }

    fun retainOnly(ids: Set<Long>) {
        entries.keys.retainAll(ids)
    }

    fun retainScans(attachmentIds: Set<Long>) {
        ocrEntries.keys.retainAll(attachmentIds)
    }

    private fun folded(entry: OcrEntry): FoldedText {
        val cached = ocrEntries[entry.attachmentId]
        if (cached != null && cached.first == entry.text) return cached.second
        return FoldedText(entry.text).also { ocrEntries[entry.attachmentId] = entry.text to it }
    }

    /**
     * When the note matches only with the text of its scans, the scan that matches best and a
     * snippet to show where.
     */
    fun ocrHit(note: Note, scans: List<OcrEntry>, query: SearchQuery, aliases: String = ""): OcrHit? {
        if (query.isEmpty() || scans.isEmpty() || matches(note, query, aliases)) return null
        val own = entries[note.id] ?: return null
        val foldedScans = scans.map { it to folded(it) }
        val all = arrayOf(own.foldedTitle, own.foldedBody, own.foldedAliases) + foldedScans.map { it.second }
        if (!query.matches(*all)) return null
        val best = foldedScans.maxBy { (_, f) -> query.terms.count { f.folded.contains(it) } }.first
        return OcrHit(best.attachmentId, snippet(Regex("\\s+").replace(best.text, " "), query))
    }
}

/** Every note's aliases as one searchable line. */
fun aliasText(aliases: List<NoteAlias>): Map<Long, String> =
    aliases.groupBy { it.noteId }.mapValues { (_, list) -> list.joinToString(" ") { it.alias } }

/**
 * What the Library shows. Completed tasks are left out of "All" unless the person searches;
 * the Tasks filter and search always show them, newest first, after the open ones.
 */
fun buildState(
    notes: List<Note>,
    filter: HomeFilter,
    query: SearchQuery,
    index: SearchIndex,
    ocr: List<OcrEntry>,
    summaries: List<AttachmentSummary>,
    aliases: Map<Long, String>,
): HomeState {
    index.retainOnly(notes.mapTo(HashSet()) { it.id })
    index.retainScans(ocr.mapTo(HashSet()) { it.attachmentId })
    val scansByNote = ocr.groupBy { it.noteId }
    val hits = HashMap<Long, OcrHit>()
    val filtered = when (filter) {
        HomeFilter.All -> notes
        HomeFilter.Tasks -> notes.filter { it.isTask }
        HomeFilter.Links -> notes.filter { it.url != null }
        HomeFilter.Pinned -> notes.filter { it.pinned }
    }
    val showCompleted = filter != HomeFilter.All || !query.isEmpty()
    val visible = filtered.filter { note ->
        if (!showCompleted && note.isCompletedTask) return@filter false
        val a = aliases[note.id].orEmpty()
        if (index.matches(note, query, a)) return@filter true
        val hit = index.ocrHit(note, scansByNote[note.id].orEmpty(), query, a) ?: return@filter false
        hits[note.id] = hit
        true
    }
    val (completed, active) = visible.partition { it.isCompletedTask }
    return HomeState(
        filter = filter,
        query = query,
        pinned = active.filter { it.pinned },
        others = active.filter { !it.pinned },
        completed = completed.sortedByDescending { it.completedAt },
        totalCount = notes.size,
        filterCount = if (showCompleted) filtered.size else filtered.count { !it.isCompletedTask },
        ocrHits = hits,
        attachments = summaries.groupBy { it.noteId },
    )
}

private data class Inputs(
    val notes: List<Note>,
    val ocr: List<OcrEntry>,
    val summaries: List<AttachmentSummary>,
    val filter: HomeFilter,
    val query: String,
    val aliases: Map<Long, String>,
)

/** The Library: search, filters and selection survive rotation and process death. */
class NoteListViewModel(
    private val dao: NoteDao,
    private val attachmentDao: AttachmentDao?,
    private val savedStateHandle: SavedStateHandle,
    private val clock: () -> Long = System::currentTimeMillis,
    aliases: Flow<List<NoteAlias>> = flowOf(emptyList()),
    private val bulk: BulkActions? = null,
) : ViewModel() {
    val selection: StateFlow<Set<Long>> = savedStateHandle.getStateFlow(KEY_SELECTION, LongArray(0))
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val query: StateFlow<String> = savedStateHandle.getStateFlow(KEY_QUERY, "")
    val filter: StateFlow<HomeFilter> = savedStateHandle.getStateFlow(KEY_FILTER, HomeFilter.All)
    private val index = SearchIndex()

    val state: StateFlow<HomeState?> = combine(
        combine(dao.observeActive(), aliases) { notes, a -> notes to aliasText(a) },
        attachmentDao?.observeOcrText() ?: flowOf(emptyList()),
        attachmentDao?.observeSummaries() ?: flowOf(emptyList()),
        filter,
        query,
    ) { notes, ocr, summaries, filter, query -> Inputs(notes.first, ocr, summaries, filter, query, notes.second) }
        .map { i -> buildState(i.notes, i.filter, SearchQuery(i.query), index, i.ocr, i.summaries, i.aliases) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onQueryChange(value: String) {
        savedStateHandle[KEY_QUERY] = value
    }

    fun onFilterChange(value: HomeFilter) {
        savedStateHandle[KEY_FILTER] = value
    }

    private fun setSelection(ids: Set<Long>) {
        savedStateHandle[KEY_SELECTION] = ids.toLongArray()
    }

    fun toggleSelected(id: Long) {
        val current = selection.value
        setSelection(if (id in current) current - id else current + id)
    }

    fun selectAllVisible() {
        val s = state.value ?: return
        setSelection((s.pinned + s.others + s.completed).mapTo(HashSet()) { it.id })
    }

    fun clearSelection() = setSelection(emptySet())

    fun selectedNotes(): List<Note> {
        val s = state.value ?: return emptyList()
        val ids = selection.value
        return (s.pinned + s.others + s.completed).filter { it.id in ids }
    }

    fun setPinnedSelected(pinned: Boolean) {
        val ids = selectedNotes().map { it.id }
        viewModelScope.launch { bulk?.setPinned(ids, pinned) }
    }

    /** Moves the selection to Trash and returns what [untrash] needs to undo it. */
    suspend fun trashSelected(): Pair<List<Long>, Long>? {
        val ids = selectedNotes().map { it.id }
        if (ids.isEmpty()) return null
        val at = clock()
        if (bulk != null) bulk.trash(ids, at) else dao.trashAll(ids, at)
        clearSelection()
        return ids to at
    }

    fun untrash(ids: List<Long>, at: Long) {
        viewModelScope.launch { if (bulk != null) bulk.untrash(ids, at) else dao.untrashAll(ids, at) }
    }

    /** Undo for a single note moved to Trash. */
    fun restore(id: Long) {
        viewModelScope.launch { dao.setDeletedAt(id, null) }
    }

    fun setCompleted(id: Long, done: Boolean) {
        viewModelScope.launch { dao.setCompletedAt(id, if (done) clock() else null) }
    }

    companion object {
        private const val KEY_QUERY = "query"
        private const val KEY_FILTER = "filter"
        private const val KEY_SELECTION = "selection"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DropApplication
                NoteListViewModel(
                    dao = app.database.noteDao(),
                    attachmentDao = app.database.attachmentDao(),
                    savedStateHandle = createSavedStateHandle(),
                    aliases = app.database.metaDao().observeActiveAliases(),
                    bulk = app.bulk,
                )
            }
        }
    }
}
