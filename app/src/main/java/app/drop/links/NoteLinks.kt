package app.drop.links

import app.drop.data.Note
import app.drop.search.FoldedText

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
