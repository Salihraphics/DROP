package app.drop.richtext

import java.util.Locale

/** A heading in a note, for Focus Writer's outline. [offset] is where its line starts. */
data class Heading(val level: Int, val text: String, val line: Int, val offset: Int)

object Outline {
    fun of(doc: RichDoc): List<Heading> = doc.blocks.mapIndexedNotNull { line, block ->
        val level = when (block.type) {
            BlockType.H1 -> 1
            BlockType.H2 -> 2
            BlockType.H3 -> 3
            else -> return@mapIndexedNotNull null
        }
        val text = doc.lineText(line).trim()
        if (text.isEmpty()) null else Heading(level, text, line, doc.lineStarts[line])
    }
}

/** Word and character counts, and reading time at [WORDS_PER_MINUTE]. */
data class TextStats(val words: Int, val characters: Int, val readingMinutes: Int) {
    companion object {
        const val WORDS_PER_MINUTE = 200
        private val WORD = Regex("[\\p{L}\\p{N}]+(?:['’-][\\p{L}\\p{N}]+)*")

        fun of(text: String): TextStats {
            val words = WORD.findAll(text).count()
            val chars = text.count { it != '\n' }
            val minutes = if (words == 0) 0 else Math.ceil(words / WORDS_PER_MINUTE.toDouble()).toInt()
            return TextStats(words, chars, minutes)
        }
    }
}

/** Which link targets a note may hold: web, mail and phone links only. */
object LinkUrls {
    private val SCHEME = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):")
    private val ALLOWED = setOf("http", "https", "mailto", "tel")

    /** The link to store for what the person typed, or null when it isn't a safe link. */
    fun normalize(input: String): String? {
        val t = input.trim()
        if (t.isEmpty() || t.any { it.isWhitespace() } || t.length > 2000) return null
        val scheme = SCHEME.find(t)?.groupValues?.get(1)?.lowercase(Locale.ROOT)
        if (scheme == null) {
            return if (!t.contains('.') || t.startsWith('.')) null else "https://$t"
        }
        if (scheme !in ALLOWED) return null
        if ((scheme == "http" || scheme == "https") && !t.substring(scheme.length + 1).startsWith("//")) return null
        if (t.length <= scheme.length + 3) return null
        return t
    }
}
