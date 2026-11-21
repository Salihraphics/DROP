package app.drop.echo

import app.drop.search.FoldedText
import java.util.Locale
import kotlin.math.roundToInt

/**
 * ECHO: finds notes that belong together, on the phone, from the words they share and the
 * connections the person made (projects, canvas lines, [[links]]). Nothing leaves the device.
 */
object EchoText {
    const val MAX_CHARS = 20_000
    private const val MIN_LENGTH = 3
    /** Words are compared by their first letters, so "planning" and "planned" match. */
    const val STEM = 7

    private val url = Regex("""(?:https?://|www\.)\S+""", RegexOption.IGNORE_CASE)
    private val word = Regex("""[\p{L}\p{N}]+""")

    val STOP_WORDS: Set<String> = setOf(
        "the", "and", "for", "with", "that", "this", "these", "those", "from", "have", "has", "had", "are", "was", "were",
        "but", "not", "you", "your", "our", "ours", "they", "them", "their", "then", "than", "into", "onto", "about", "what",
        "when", "where", "which", "who", "why", "how", "will", "would", "can", "could", "should", "just", "also", "very",
        "more", "most", "some", "any", "all", "one", "two", "get", "got", "make", "made", "need", "like", "there", "here",
        "its", "out", "off", "too", "yes", "now", "new", "old", "did", "does", "done", "been", "being", "over", "under",
        "after", "before", "again", "only", "other", "each", "much", "many", "such", "own", "same", "via", "per", "etc",
        "note", "notes", "todo", "task", "tasks", "thing", "things", "stuff", "item", "items", "today", "tomorrow",
        "ve", "ile", "bir", "bu", "su", "icin", "gibi", "daha", "cok", "ama", "fakat", "veya", "yada", "her", "sey",
        "seyler", "not", "notu", "notlar", "notlari", "olan", "olarak", "kadar", "sonra", "once", "diye", "ben", "sen",
        "biz", "siz", "onlar", "var", "yok", "ise", "hem", "hep", "bazi", "gore", "yani", "simdi", "bugun", "yarin",
        "dun", "bunu", "buna", "bunun", "sunu", "sunlar", "onu", "ona", "onun", "nasil", "neden", "nerede", "hangi",
        "gibi", "ancak", "yine", "zaten", "artik", "bile", "gerek", "lazim", "olsun", "oldu", "olur", "olmak", "yapmak",
        "yap", "gorev", "gorevler", "madde", "http", "https", "www", "com", "org", "net", "html",
    )

    /** The meaningful words of [text]: stem to the word as first written (lower case). */
    fun keywords(text: String): Map<String, String> {
        val clean = url.replace(text.take(MAX_CHARS), " ")
        val out = LinkedHashMap<String, String>()
        for (m in word.findAll(clean)) {
            val raw = m.value
            if (raw.all { it.isDigit() }) continue
            val folded = FoldedText.fold(raw)
            if (folded.length < MIN_LENGTH || folded in STOP_WORDS) continue
            out.getOrPut(folded.take(STEM)) { raw.lowercase(Locale.getDefault()) }
        }
        return out
    }
}

/** A note as ECHO sees it: plain text only. */
data class EchoDoc(val id: Long, val title: String, val body: String, val updatedAt: Long, val isTask: Boolean = false)

/** How much each kind of evidence counts. */
data class EchoWeights(
    val titleWord: Double = 3.0,
    val titleToBodyWord: Double = 1.5,
    val bodyWord: Double = 1.0,
    val maxKeywords: Double = 8.0,
    val sameProject: Double = 3.0,
    val canvasConnection: Double = 4.0,
    val wikiLink: Double = 4.0,
    val minScore: Double = 3.0,
    /** A word in more than this share of notes says nothing... */
    val commonWordShare: Double = 0.3,
    /** ...once there are at least this many notes. */
    val commonWordMinDocs: Int = 8,
)

/** Why ECHO thinks two notes belong together, shown to the person. */
sealed interface EchoReason {
    data class SharedWords(val words: List<String>, val inTitles: Boolean) : EchoReason
    data class SameProject(val project: String) : EchoReason
    data class CanvasConnection(val canvas: String) : EchoReason
    data object WikiLink : EchoReason
}

data class EchoSuggestion(val noteId: Long, val score: Double, val reasons: List<EchoReason>, val points: List<Double> = emptyList())

/** An older note that fits something the person wrote recently ([seedId]). */
data class Rediscovery(val seedId: Long, val suggestion: EchoSuggestion)

object EchoStrength {
    const val STRONG = 6.0

    fun max(w: EchoWeights): Double = w.maxKeywords + w.sameProject + w.canvasConnection + w.wikiLink

    fun percent(score: Double, w: EchoWeights): Int = ((score / max(w)) * 100).roundToInt().coerceIn(0, 100)

    fun isStrong(score: Double): Boolean = score >= STRONG
}

/** Connections the person made: note → (project id → name), note → (note → canvas name), note → linked notes. */
data class EchoRelations(
    val projects: Map<Long, Map<Long, String>> = emptyMap(),
    val canvas: Map<Long, Map<Long, String>> = emptyMap(),
    val links: Map<Long, Set<Long>> = emptyMap(),
)

class EchoIndex(
    docs: List<EchoDoc>,
    private val relations: EchoRelations = EchoRelations(),
    private val weights: EchoWeights = EchoWeights(),
    keywordsOf: (EchoDoc) -> Pair<Map<String, String>, Map<String, String>> = { EchoText.keywords(it.title) to EchoText.keywords(it.body) },
) {
    private class Entry(val doc: EchoDoc, val title: Map<String, String>, val body: Map<String, String>) {
        val all: Set<String> = title.keys + body.keys
    }

    private val entries: Map<Long, Entry> = docs.associate { d ->
        val (title, body) = keywordsOf(d)
        d.id to Entry(d, title, body)
    }
    private val postings: Map<String, List<Long>>
    private val common: Set<String>

    init {
        val map = HashMap<String, MutableList<Long>>()
        entries.values.forEach { e -> e.all.forEach { k -> map.getOrPut(k) { ArrayList() }.add(e.doc.id) } }
        postings = map
        common = if (entries.size >= weights.commonWordMinDocs) {
            map.filterValues { it.size > entries.size * weights.commonWordShare }.keys
        } else {
            emptySet()
        }
    }

    val size: Int get() = entries.size

    fun doc(id: Long): EchoDoc? = entries[id]?.doc

    /** The notes that fit [sourceId] best, strongest first. */
    fun related(sourceId: Long, limit: Int = 3, exclude: Set<Long> = emptySet()): List<EchoSuggestion> {
        val source = entries[sourceId] ?: return emptyList()
        val candidates = HashSet<Long>()
        source.all.forEach { k -> if (k !in common) postings[k]?.let(candidates::addAll) }
        relations.projects[sourceId]?.keys?.let { mine ->
            for ((note, projects) in relations.projects) if (projects.keys.any { it in mine }) candidates.add(note)
        }
        relations.canvas[sourceId]?.keys?.let(candidates::addAll)
        relations.links[sourceId]?.let(candidates::addAll)
        candidates.remove(sourceId)
        candidates.removeAll(exclude)
        return candidates.mapNotNull { id -> entries[id]?.let { score(source, it) } }
            .filter { it.score >= weights.minScore }
            .sortedWith(
                compareByDescending<EchoSuggestion> { it.score }
                    .thenByDescending { entries.getValue(it.noteId).doc.updatedAt }
                    .thenBy { it.noteId },
            )
            .take(limit)
    }

    private fun score(a: Entry, b: Entry): EchoSuggestion {
        val reasons = ArrayList<EchoReason>()
        val points = ArrayList<Double>()
        var keywordScore = 0.0
        val shared = ArrayList<String>()
        var titlesShare = false
        for (k in a.all) {
            if (k !in b.all || k in common) continue
            val w = if (a.title.containsKey(k) && b.title.containsKey(k)) {
                titlesShare = true
                weights.titleWord
            } else if (a.title.containsKey(k) || b.title.containsKey(k)) {
                weights.titleToBodyWord
            } else {
                weights.bodyWord
            }
            keywordScore += w
            shared.add(b.title[k] ?: b.body[k] ?: a.title[k] ?: a.body.getValue(k))
        }
        var score = minOf(keywordScore, weights.maxKeywords)
        if (shared.isEmpty() || (shared.size < 2 && !titlesShare)) {
            score = 0.0
        } else {
            reasons.add(EchoReason.SharedWords(shared.sorted().take(3), titlesShare))
            points.add(score)
        }
        val mine = relations.projects[a.doc.id] ?: emptyMap()
        val theirs = relations.projects[b.doc.id] ?: emptyMap()
        theirs.entries.firstOrNull { mine.containsKey(it.key) }?.let {
            score += weights.sameProject
            reasons.add(EchoReason.SameProject(it.value))
            points.add(weights.sameProject)
        }
        relations.canvas[a.doc.id]?.get(b.doc.id)?.let {
            score += weights.canvasConnection
            reasons.add(EchoReason.CanvasConnection(it))
            points.add(weights.canvasConnection)
        }
        if (relations.links[a.doc.id]?.contains(b.doc.id) == true) {
            score += weights.wikiLink
            reasons.add(EchoReason.WikiLink)
            points.add(weights.wikiLink)
        }
        return EchoSuggestion(b.doc.id, score, reasons, points)
    }

    /**
     * Older notes that fit what the person wrote lately: for each of the [seeds] most recent
     * notes, the best match among notes not touched for [staleAfterMs]; each old note once.
     */
    fun rediscover(now: Long, staleAfterMs: Long, limit: Int, suppressed: Set<Long> = emptySet(), seeds: Int = 5): List<Rediscovery> {
        val recent = entries.values.filter { !it.doc.isTask && now - it.doc.updatedAt < staleAfterMs }
            .sortedByDescending { it.doc.updatedAt }.take(seeds)
        val fresh = entries.values.filter { now - it.doc.updatedAt < staleAfterMs }.mapTo(HashSet()) { it.doc.id }
        val taken = HashSet<Long>()
        val out = ArrayList<Rediscovery>()
        for (seed in recent) {
            val best = related(seed.doc.id, 3, fresh + suppressed + taken).firstOrNull() ?: continue
            taken.add(best.noteId)
            out.add(Rediscovery(seed.doc.id, best))
        }
        return out.sortedByDescending { it.suggestion.score }.take(limit)
    }
}
