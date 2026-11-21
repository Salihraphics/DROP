package app.drop.links

import app.drop.data.LinkDao
import app.drop.data.Note
import app.drop.data.NoteAlias
import app.drop.data.NoteDao
import app.drop.data.NoteLink
import app.drop.richtext.DropMarkdown
import app.drop.search.FoldedText
import app.drop.ui.notes.cardTitle

/** [[Note title]] links inside note text. */
object WikiLinks {
    private val PATTERN = Regex("\\[\\[([^\\[\\]\\n]{1,120})]]")

    /** The distinct link labels in [text], in order; labels differing only in case or accents count once. */
    fun labels(text: String): List<String> =
        PATTERN.findAll(text).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.distinctBy(::key).toList()

    /** How labels are compared: folded, with runs of whitespace as one space. */
    fun key(label: String): String = Regex("\\s+").replace(FoldedText.fold(label.trim()), " ")

    /** A title that can be used inside [[ ]]: no brackets or line breaks, at most 120 characters. */
    fun cleanLabel(title: String): String = Regex("[\\[\\]\\n]").replace(title, " ").trim().take(120)

    /** Where the links are in [text], brackets included. */
    fun ranges(text: String): List<IntRange> = PATTERN.findAll(text).map { it.range }.toList()
}

/** What a [[link]] points at. */
sealed interface LinkState {
    val label: String

    data class Linked(override val label: String, val targetId: Long, val title: String) : LinkState

    data class InTrash(override val label: String, val targetId: Long, val title: String) : LinkState

    /** Several notes have this title: the person picks one. */
    data class Ambiguous(override val label: String, val candidates: List<Note>) : LinkState

    data class Missing(override val label: String) : LinkState
}

/**
 * Keeps the stored [[link]] targets of a note in step with its text. A link is only stored when
 * exactly one note has that title (or alias); otherwise the person chooses, see [choose].
 */
class LinkResolver(
    private val notes: NoteDao,
    private val links: LinkDao,
    private val aliases: suspend () -> List<NoteAlias> = { emptyList() },
) {
    /** Call after [source] was saved: drops links whose text is gone and resolves new ones. */
    suspend fun sync(source: Note) {
        val wanted = WikiLinks.labels(textOf(source)).associateBy { WikiLinks.key(it) }
        val existing = links.forSource(source.id)
        existing.filter { !wanted.containsKey(it.label) }.forEach { links.delete(source.id, it.label) }
        val linked = existing.map { it.label }.toSet()
        val unlinked = wanted.keys - linked
        if (unlinked.isEmpty()) return
        val active = activeByKey(source.id)
        for (key in unlinked) {
            val match = active[key].orEmpty()
            if (match.size == 1) links.put(NoteLink(source.id, key, match.single().id))
        }
    }

    /** What each [[link]] in [source] points at, in the order the links appear. */
    suspend fun states(source: Note): List<LinkState> {
        val stored = links.forSource(source.id).associateBy { it.label }
        val active = activeByKey(source.id)
        return WikiLinks.labels(textOf(source)).map { label ->
            val key = WikiLinks.key(label)
            val target = stored[key]?.let { notes.getById(it.targetId) }
            if (target != null) {
                if (target.deletedAt == null) LinkState.Linked(label, target.id, cardTitle(target))
                else LinkState.InTrash(label, target.id, cardTitle(target))
            } else {
                val candidates = active[key].orEmpty()
                if (candidates.isEmpty()) LinkState.Missing(label) else LinkState.Ambiguous(label, candidates)
            }
        }
    }

    /** The person picked [targetId] for [label] in note [sourceId]. */
    suspend fun choose(sourceId: Long, label: String, targetId: Long) {
        links.put(NoteLink(sourceId, WikiLinks.key(label), targetId))
    }

    private fun textOf(note: Note) = note.title + "\n" + DropMarkdown.plainOf(note.body, note.format)

    /** Active notes other than [excluding], by link key of their title and of each alias. */
    private suspend fun activeByKey(excluding: Long): Map<String, List<Note>> {
        val active = notes.getActive().filter { it.id != excluding }
        val out = HashMap<String, MutableList<Note>>()
        active.forEach { out.getOrPut(WikiLinks.key(cardTitle(it))) { ArrayList() }.add(it) }
        val byId = active.associateBy { it.id }
        for (alias in aliases()) {
            val note = byId[alias.noteId] ?: continue
            val list = out.getOrPut(alias.key) { ArrayList() }
            if (list.none { it.id == note.id }) list.add(note)
        }
        return out
    }
}
