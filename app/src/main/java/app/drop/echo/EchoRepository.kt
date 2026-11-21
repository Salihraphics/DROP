package app.drop.echo

import android.content.Context
import android.content.SharedPreferences
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import app.drop.richtext.DropMarkdown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** What ECHO reads: open notes and the connections the person made. Never the Vault. */
data class EchoNoteRow(
    val id: Long,
    val title: String,
    val body: String,
    @ColumnInfo(name = "is_task") val isTask: Boolean,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val format: Int = 0,
)

data class EchoProjectRow(
    @ColumnInfo(name = "note_id") val noteId: Long,
    @ColumnInfo(name = "project_id") val projectId: Long,
    val name: String,
)

/** Two notes the person connected; [name] is the canvas, or empty for a [[link]]. */
data class EchoPairRow(val a: Long, val b: Long, val name: String)

@Dao
interface EchoDao {
    @Query("SELECT id, title, body, is_task, updated_at, format FROM notes WHERE deleted_at IS NULL AND NOT (is_task = 1 AND completed_at IS NOT NULL)")
    fun observeNotes(): Flow<List<EchoNoteRow>>

    @Query("SELECT i.note_id, p.id AS project_id, p.name FROM project_items i JOIN projects p ON p.id = i.project_id")
    fun observeProjectMembers(): Flow<List<EchoProjectRow>>

    @Query(
        "SELECT ca.note_id AS a, cb.note_id AS b, c.name AS name FROM canvas_edges e " +
            "JOIN canvas_cards ca ON ca.id = e.from_card JOIN canvas_cards cb ON cb.id = e.to_card JOIN canvases c ON c.id = ca.canvas_id",
    )
    fun observeCanvasPairs(): Flow<List<EchoPairRow>>

    @Query("SELECT source_id AS a, target_id AS b, '' AS name FROM note_links")
    fun observeLinkPairs(): Flow<List<EchoPairRow>>
}

/**
 * Keeps an up-to-date [EchoIndex] while ECHO is switched on, rebuilt shortly after notes or
 * connections change. Keywords are cached per note until the note changes.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class EchoRepository(
    private val dao: EchoDao,
    enabled: StateFlow<Boolean>,
    scope: CoroutineScope,
    debounceMs: Long = 300,
) {
    class Inputs(val notes: List<EchoNoteRow>, val projects: List<EchoProjectRow>, val canvas: List<EchoPairRow>, val links: List<EchoPairRow>)

    private val cache = HashMap<Long, Pair<Long, Pair<Map<String, String>, Map<String, String>>>>()

    /** Null while ECHO is off or the first index is still being built. */
    val index: StateFlow<EchoIndex?> = enabled.flatMapLatest { on ->
        if (on) {
            combine(dao.observeNotes(), dao.observeProjectMembers(), dao.observeCanvasPairs(), dao.observeLinkPairs(), ::Inputs)
                .debounce(debounceMs)
                .map(::build)
                .flowOn(Dispatchers.Default)
        } else {
            flowOf(null)
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(10_000), null)

    fun build(i: Inputs): EchoIndex {
        val docs = i.notes.map { EchoDoc(it.id, it.title, DropMarkdown.plainOf(it.body, it.format), it.updatedAt, it.isTask) }
        val projects = i.projects.groupBy { it.noteId }.mapValues { (_, rows) -> rows.associate { it.projectId to it.name } }
        val canvas = both(i.canvas).mapValues { (_, rows) -> rows.associate { it.first to it.second } }
        val links = both(i.links).mapValues { (_, rows) -> rows.mapTo(HashSet()) { it.first } }
        val relations = EchoRelations(projects, canvas, links)
        synchronized(cache) {
            val live = docs.mapTo(HashSet()) { it.id }
            cache.keys.retainAll(live)
            return EchoIndex(docs, relations, keywordsOf = { d ->
                val hit = cache[d.id]
                if (hit != null && hit.first == d.updatedAt) {
                    hit.second
                } else {
                    (EchoText.keywords(d.title) to EchoText.keywords(d.body)).also { cache[d.id] = d.updatedAt to it }
                }
            })
        }
    }

    /** Connections work both ways; a note connected to itself is ignored. */
    private fun both(pairs: List<EchoPairRow>): Map<Long, List<Pair<Long, String>>> {
        val out = HashMap<Long, MutableList<Pair<Long, String>>>()
        for (p in pairs) {
            if (p.a == p.b) continue
            out.getOrPut(p.a) { ArrayList() }.add(p.b to p.name)
            out.getOrPut(p.b) { ArrayList() }.add(p.a to p.name)
        }
        return out
    }
}

/**
 * What ECHO should hold back for a while: a suggestion the person dismissed (90 days) or
 * already opened (14 days). Kept on the phone only, at most [MAX_ENTRIES] notes.
 */
class EchoMemory(context: Context, private val clock: () -> Long = System::currentTimeMillis) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val _suppressed = MutableStateFlow(read())
    val suppressedUntil: StateFlow<Map<Long, Long>> = _suppressed.asStateFlow()

    fun suppressed(now: Long = clock()): Set<Long> = _suppressed.value.filterValues { it > now }.keys

    fun dismiss(noteId: Long) = holdBack(noteId, DISMISS_MS)

    fun opened(noteId: Long) = holdBack(noteId, OPENED_MS)

    private fun holdBack(noteId: Long, forMs: Long) {
        val now = clock()
        val kept = (_suppressed.value.filterValues { it > now } + (noteId to now + forMs)).entries
            .sortedByDescending { it.value }.take(MAX_ENTRIES).associate { it.key to it.value }
        prefs.edit().putStringSet(KEY, kept.entries.mapTo(HashSet()) { "${it.key}:${it.value}" }).apply()
        _suppressed.value = kept
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
        _suppressed.value = emptyMap()
    }

    private fun read(): Map<Long, Long> = (prefs.getStringSet(KEY, emptySet()) ?: emptySet()).mapNotNull { e ->
        val id = e.substringBefore(':').toLongOrNull()
        val until = e.substringAfter(':').toLongOrNull()
        if (id != null && until != null) id to until else null
    }.toMap()

    companion object {
        private const val FILE = "echo_memory"
        private const val KEY = "suppressed"
        const val MAX_ENTRIES = 300
        const val DISMISS_MS = 90L * 24 * 60 * 60 * 1000
        const val OPENED_MS = 14L * 24 * 60 * 60 * 1000
    }
}
