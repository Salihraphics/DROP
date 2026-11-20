package app.drop.search

import java.text.Normalizer
import java.util.Locale

/**
 * Text folded for matching: lower case, accents removed, and Turkish dotted and dotless I both
 * read as "i", so "Istanbul", "İstanbul" and "istanbul" all match. Keeps a map back to the
 * original text so matches can be highlighted where they really are.
 */
class FoldedText(val original: String) {
    val folded: String
    private val starts: IntArray
    private val ends: IntArray

    init {
        val out = StringBuilder(original.length)
        val starts = ArrayList<Int>(original.length)
        val ends = ArrayList<Int>(original.length)
        var i = 0
        while (i < original.length) {
            val cp = original.codePointAt(i)
            val next = i + Character.charCount(cp)
            for (c in foldCodePoint(cp)) {
                out.append(c)
                starts += i
                ends += next
            }
            i = next
        }
        folded = out.toString()
        this.starts = starts.toIntArray()
        this.ends = ends.toIntArray()
    }

    /** The original characters behind folded positions [start] until [endExclusive]. */
    fun originalRange(start: Int, endExclusive: Int): IntRange = starts[start] until ends[endExclusive - 1]

    companion object {
        private val marks = Regex("\\p{Mn}+")

        fun fold(text: String): String = FoldedText(text).folded

        fun foldCodePoint(cp: Int): String = when (cp) {
            0x130, 0x131 -> "i" // İ and ı
            else -> marks.replace(Normalizer.normalize(String(Character.toChars(cp)), Normalizer.Form.NFD), "").lowercase(Locale.ROOT)
        }
    }
}

/** What the person typed, split into terms. A note matches when it contains every term. */
class SearchQuery(raw: String) {
    val terms: List<String> = Regex("\\s+").split(FoldedText.fold(raw)).filter { it.isNotEmpty() }.distinct()

    fun isEmpty(): Boolean = terms.isEmpty()

    fun matches(vararg texts: FoldedText): Boolean = terms.all { term -> texts.any { it.folded.contains(term) } }

    /** Where the terms appear in the original text, merged where they touch. */
    fun highlights(text: FoldedText): List<IntRange> {
        if (isEmpty() || text.folded.isEmpty()) return emptyList()
        val hits = mutableListOf<IntRange>()
        for (term in terms) {
            var from = 0
            while (true) {
                val at = text.folded.indexOf(term, from)
                if (at < 0) break
                hits += text.originalRange(at, at + term.length)
                from = at + term.length
            }
        }
        if (hits.isEmpty()) return emptyList()
        hits.sortBy { it.first }
        val merged = mutableListOf(hits.first())
        for (r in hits.drop(1)) {
            val last = merged.last()
            if (r.first <= last.last + 1) merged[merged.lastIndex] = last.first..maxOf(last.last, r.last) else merged += r
        }
        return merged
    }
}

/** Text to show for a search result, with the ranges to highlight. */
data class Snippet(val text: String, val highlights: List<IntRange>)

/**
 * Shows [text] from shortly before the first match, so a match deep in a long note is visible.
 * When the first match is within [startWithin] characters the text is shown from the start.
 */
fun snippet(text: String, query: SearchQuery, lead: Int = 40, startWithin: Int = 80): Snippet {
    if (query.isEmpty()) return Snippet(text, emptyList())
    val highlights = query.highlights(FoldedText(text))
    val first = highlights.firstOrNull() ?: return Snippet(text, emptyList())
    if (first.first <= startWithin) return Snippet(text, highlights)
    var cut = first.first - lead
    // Start at a word, never after the match.
    while (cut < first.first && !text[cut - 1].isWhitespace()) cut++
    val ellipsis = "…"
    val shifted = highlights.filter { it.first >= cut }.map { (it.first - cut + ellipsis.length)..(it.last - cut + ellipsis.length) }
    val rest = text.substring(cut)
    return Snippet(ellipsis + rest.trimStart(), adjustForTrim(shifted, rest, ellipsis.length))
}

private fun adjustForTrim(ranges: List<IntRange>, afterCut: String, prefixLength: Int): List<IntRange> {
    val trimmed = afterCut.length - afterCut.trimStart().length
    if (trimmed == 0) return ranges
    return ranges.map { (it.first - trimmed)..(it.last - trimmed) }.filter { it.first >= prefixLength }
}
