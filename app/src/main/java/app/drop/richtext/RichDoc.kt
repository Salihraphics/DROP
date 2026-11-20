package app.drop.richtext

/** What a line is. */
enum class BlockType { Paragraph, H1, H2, H3, Quote, Bullet, Numbered, Check, Code }

/** Formatting on a run of characters. */
enum class InlineStyle { Bold, Italic, Strike, Code, Link }

/** One line's block formatting. */
data class Block(val type: BlockType = BlockType.Paragraph, val checked: Boolean = false) {
    val isList: Boolean get() = type == BlockType.Bullet || type == BlockType.Numbered || type == BlockType.Check
}

/** [style] on characters [start] until [end]. Links carry their [url]. */
data class Span(val start: Int, val end: Int, val style: InlineStyle, val url: String? = null)

data class EditResult(val doc: RichDoc, val selectionStart: Int, val selectionEnd: Int)

/**
 * A rich note: plain [text], inline [spans] and one [Block] per line. Immutable; every edit
 * makes a new document through [RichEdit].
 */
data class RichDoc(
    val text: String,
    val spans: List<Span> = emptyList(),
    val blocks: List<Block> = List(lineCount(text)) { Block() },
) {
    init {
        require(blocks.size == lineCount(text)) { "one block per line" }
    }

    val hasFormatting: Boolean get() = spans.isNotEmpty() || blocks.any { it.type != BlockType.Paragraph || it.checked }

    /** Where each line starts. */
    val lineStarts: IntArray by lazy {
        val out = IntArray(blocks.size)
        var line = 1
        for (i in text.indices) {
            if (text[i] == '\n') out[line++] = i + 1
        }
        out
    }

    fun lineOf(offset: Int): Int {
        var lo = 0
        var hi = lineStarts.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (lineStarts[mid] <= offset) lo = mid else hi = mid - 1
        }
        return lo
    }

    /** The offset just before the line break that ends [line], or the end of the text. */
    fun lineEnd(line: Int): Int = if (line + 1 < lineStarts.size) lineStarts[line + 1] - 1 else text.length

    fun lineText(line: Int): String = text.substring(lineStarts[line], lineEnd(line))

    /** The styles on the character at [offset]. */
    fun stylesAt(offset: Int): Set<InlineStyle> = spans.filter { offset >= it.start && offset < it.end }.mapTo(HashSet()) { it.style }

    /** Whether every character from [start] until [end] has [style]. */
    fun hasStyle(start: Int, end: Int, style: InlineStyle): Boolean {
        if (start >= end) return false
        var covered = start
        for (s in spans.filter { it.style == style }.sortedBy { it.start }) {
            if (s.start > covered) break
            if (s.end > covered) covered = s.end
            if (covered >= end) return true
        }
        return covered >= end
    }

    /** The link at [offset], or the one ending right there. */
    fun linkAt(offset: Int): Span? =
        spans.firstOrNull { it.style == InlineStyle.Link && offset >= it.start && offset < it.end }
            ?: spans.firstOrNull { it.style == InlineStyle.Link && offset == it.end && offset > it.start }

    /**
     * The one canonical form of this document: same-style spans merged, later links winning where
     * links overlap, and no inline formatting across line breaks or inside code blocks.
     */
    fun normalized(): RichDoc {
        val len = text.length
        val inCode = BooleanArray(len) { text[it] == '\n' }
        blocks.forEachIndexed { line, b ->
            if (b.type == BlockType.Code) for (i in lineStarts[line] until lineEnd(line)) inCode[i] = true
        }
        // A link replaces any other link it overlaps.
        val links = mutableListOf<Span>()
        for (l in spans.filter { it.style == InlineStyle.Link }) {
            val cut = links.flatMap { o ->
                if (o.url == l.url || o.end <= l.start || o.start >= l.end) {
                    listOf(o)
                } else {
                    listOfNotNull(o.copy(end = l.start).takeIf { o.start < l.start }, if (o.end > l.end) o.copy(start = l.end) else null)
                }
            }
            links.clear()
            links += cut
            links += l
        }
        val source = spans.filter { it.style != InlineStyle.Link } + links
        val out = mutableListOf<Span>()
        for ((key, group) in source.groupBy { it.style to (if (it.style == InlineStyle.Link) it.url else null) }) {
            val ranges = group.map { it.start.coerceIn(0, len) to it.end.coerceIn(0, len) }.filter { it.first < it.second }.sortedBy { it.first }
            var cur: Pair<Int, Int>? = null
            for (r in ranges) {
                val c = cur
                cur = if (c != null && r.first <= c.second) {
                    c.first to maxOf(c.second, r.second)
                } else {
                    if (c != null) out += Span(c.first, c.second, key.first, key.second)
                    r
                }
            }
            cur?.let { out += Span(it.first, it.second, key.first, key.second) }
        }
        val cleaned = if (inCode.none { it }) {
            out
        } else {
            out.flatMap { s ->
                val parts = mutableListOf<Span>()
                var start = -1
                for (i in s.start until s.end) {
                    if (!inCode[i] && start < 0) start = i
                    if (inCode[i] && start >= 0) {
                        parts += s.copy(start = start, end = i)
                        start = -1
                    }
                }
                if (start >= 0) parts += s.copy(start = start, end = s.end)
                parts
            }
        }
        return copy(spans = cleaned.sortedWith(compareBy({ it.start }, { it.style.ordinal }, { it.end })))
    }

    companion object {
        fun lineCount(text: String): Int = text.count { it == '\n' } + 1

        fun plain(text: String): RichDoc = RichDoc(text)
    }
}

/** Every change to a [RichDoc]. Each function returns a new, normalized document. */
object RichEdit {
    /**
     * Turns the editor's new text into a document, keeping formatting where the text didn't
     * change. Typed text takes [insertStyles], or the styles before it (never a link).
     */
    fun apply(old: RichDoc, newText: String, newSelStart: Int, newSelEnd: Int, insertStyles: Set<InlineStyle>? = null): EditResult {
        val o = old.text
        if (o == newText) return EditResult(old, newSelStart, newSelEnd)
        var p = 0
        val maxP = minOf(o.length, newText.length)
        while (p < maxP && o[p] == newText[p]) p++
        p = minOf(p, newSelStart.coerceAtLeast(0))
        var s = 0
        while (s < o.length - p && s < newText.length - p && o[o.length - 1 - s] == newText[newText.length - 1 - s]) s++
        val b = o.length - s
        val inserted = newText.substring(p, newText.length - s)
        val removed = o.substring(p, b)
        val line = old.lineOf(p)
        val block = old.blocks[line]

        // Enter on an empty list item or quote ends the list instead of adding a line.
        if (inserted == "\n" && removed.isEmpty() && (block.isList || block.type == BlockType.Quote) && old.lineText(line).isEmpty()) {
            val blocks = old.blocks.toMutableList()
            blocks[line] = Block()
            return EditResult(old.copy(blocks = blocks), p, p)
        }

        val delta = inserted.length - removed.length
        val spans = mutableListOf<Span>()
        for (sp in old.spans) {
            when {
                sp.end <= p -> spans += sp
                sp.start >= b -> spans += sp.copy(start = sp.start + delta, end = sp.end + delta)
                else -> {
                    val start = if (sp.start < p) sp.start else p + inserted.length
                    val end = if (sp.end <= b) p else sp.end + delta
                    if (start < end) spans += sp.copy(start = start, end = end)
                }
            }
        }
        if (inserted.isNotEmpty()) {
            val styles = insertStyles ?: if (p > 0) old.stylesAt(p - 1) - InlineStyle.Link else emptySet()
            styles.forEach { spans += Span(p, p + inserted.length, it) }
        }

        val removedLines = removed.count { it == '\n' }
        val insertedLines = inserted.count { it == '\n' }
        // New lines continue a list or quote; a new checklist item starts unchecked.
        val continuation = when (block.type) {
            BlockType.Bullet, BlockType.Numbered, BlockType.Quote, BlockType.Code -> block.copy(checked = false)
            BlockType.Check -> Block(BlockType.Check)
            else -> Block()
        }
        val blocks = ArrayList<Block>(old.blocks.size + insertedLines)
        blocks += old.blocks.subList(0, line)
        blocks += block
        repeat(insertedLines) { blocks += continuation }
        // Lines inserted at the start of a line push the line's own block down with its text.
        if (insertedLines > 0 && removedLines == 0 && p == old.lineStarts[line] && inserted.endsWith("\n")) {
            blocks[blocks.size - 1] = block
            blocks[line] = if (block.type == BlockType.Code) {
                block
            } else {
                (if (block.isList || block.type == BlockType.Quote) continuation else null) ?: Block()
            }
        }
        blocks += old.blocks.subList(line + removedLines + 1, old.blocks.size)
        return EditResult(RichDoc(newText, spans, blocks).normalized(), newSelStart, newSelEnd)
    }

    /** Adds [style] to the range, or removes it when the whole range already has it. */
    fun toggleInline(doc: RichDoc, start: Int, end: Int, style: InlineStyle): RichDoc {
        if (start >= end) return doc
        val has = doc.hasStyle(start, end, style)
        val rest = removeStyle(doc.spans, start, end, style)
        return doc.copy(spans = if (has) rest else rest + Span(start, end, style)).normalized()
    }

    fun setLink(doc: RichDoc, start: Int, end: Int, url: String): RichDoc {
        if (start >= end) return doc
        return doc.copy(spans = removeStyle(doc.spans, start, end, InlineStyle.Link) + Span(start, end, InlineStyle.Link, url)).normalized()
    }

    fun removeLink(doc: RichDoc, offset: Int): RichDoc {
        val link = doc.linkAt(offset) ?: return doc
        return doc.copy(spans = doc.spans - link)
    }

    private fun removeStyle(spans: List<Span>, start: Int, end: Int, style: InlineStyle): List<Span> = spans.flatMap { s ->
        if (s.style != style || s.end <= start || s.start >= end) {
            listOf(s)
        } else {
            listOfNotNull(s.copy(end = start).takeIf { s.start < start }, if (s.end > end) s.copy(start = end) else null)
        }
    }

    /** The lines a selection touches; a selection ending right after a line break doesn't include the next line. */
    fun linesOf(doc: RichDoc, start: Int, end: Int): IntRange = doc.lineOf(start)..doc.lineOf(maxOf(start, if (end > start) end - 1 else end))

    /** Gives every touched line [type], or makes them paragraphs again when they all have it. */
    fun toggleBlock(doc: RichDoc, start: Int, end: Int, type: BlockType): RichDoc {
        val lines = linesOf(doc, start, end)
        val all = lines.all { doc.blocks[it].type == type }
        val blocks = doc.blocks.toMutableList()
        for (l in lines) blocks[l] = if (all) Block() else Block(type)
        return doc.copy(blocks = blocks).normalized()
    }

    /**
     * Applies a block format to the selected text only. When the selection covers part of a line,
     * that part becomes its own line first, so text outside the selection keeps its format.
     */
    fun applyBlock(doc: RichDoc, start: Int, end: Int, type: BlockType): EditResult {
        if (start >= end) return EditResult(toggleBlock(doc, start, end, type), start, end)
        var d = doc
        var a = start
        var b = end
        while (b > a && d.text[b - 1] == '\n') b--
        if (a == b) return EditResult(toggleBlock(doc, start, start, type), start, start)
        val lastLine = d.lineOf(b - 1)
        if (d.text.substring(b, d.lineEnd(lastLine)).isNotBlank()) {
            val cut = splitAt(d, b)
            d = cut.doc
            b = cut.mapEnd(b)
        }
        val firstLine = d.lineOf(a)
        if (d.text.substring(d.lineStarts[firstLine], a).isNotBlank()) {
            val cut = splitAt(d, a)
            d = cut.doc
            a = cut.mapStart(a)
            b = cut.mapEnd(b)
        }
        while (a < b && d.text[a] == ' ') a++
        while (b > a && d.text[b - 1] == ' ') b--
        return EditResult(toggleBlock(d, a, maxOf(a, b), type), a, maxOf(a, b))
    }

    /** A document split by [splitAt], and how to move offsets from the old text into it. */
    class Split(val doc: RichDoc, val from: Int, val to: Int) {
        private val delta = 1 - (to - from)

        fun mapStart(x: Int): Int = when {
            x < from -> x
            x >= to -> x + delta
            else -> from + 1
        }

        fun mapEnd(x: Int): Int = if (x <= to) minOf(x, from) else x + delta
    }

    /** Breaks the line at [offset], dropping the spaces around it. Both halves keep the line's block. */
    internal fun splitAt(doc: RichDoc, offset: Int): Split {
        val text = doc.text
        var from = offset
        var to = offset
        while (from > 0 && text[from - 1] == ' ') from--
        while (to < text.length && text[to] == ' ') to++
        val line = doc.lineOf(offset)
        val newText = text.substring(0, from) + "\n" + text.substring(to)
        val delta = 1 - (to - from)
        val spans = doc.spans.mapNotNull { s ->
            val st = when {
                s.start <= from -> s.start
                s.start >= to -> s.start + delta
                else -> from + 1
            }
            val en = when {
                s.end <= from -> s.end
                s.end >= to -> s.end + delta
                else -> from
            }
            if (st < en) s.copy(start = st, end = en) else null
        }
        val blocks = doc.blocks.toMutableList().also { it.add(line + 1, doc.blocks[line]) }
        return Split(RichDoc(newText, spans, blocks).normalized(), from, to)
    }

    fun toggleChecked(doc: RichDoc, line: Int): RichDoc {
        val b = doc.blocks[line]
        if (b.type != BlockType.Check) return doc
        return doc.copy(blocks = doc.blocks.toMutableList().also { it[line] = b.copy(checked = !b.checked) })
    }

    /**
     * Removes inline formatting from the selection, and block formatting from lines the selection
     * covers completely (or the cursor's line when nothing is selected).
     */
    fun clearFormatting(doc: RichDoc, start: Int, end: Int): RichDoc {
        var spans = doc.spans
        if (start < end) InlineStyle.entries.forEach { spans = removeStyle(spans, start, end, it) }
        val blocks = doc.blocks.toMutableList()
        for (l in linesOf(doc, start, end)) {
            val whole = start == end || (
                doc.text.substring(doc.lineStarts[l], maxOf(doc.lineStarts[l], minOf(start, doc.lineEnd(l)))).isBlank() &&
                    doc.text.substring(minOf(doc.lineEnd(l), maxOf(end, doc.lineStarts[l])), doc.lineEnd(l)).isBlank()
                )
            if (whole) blocks[l] = Block()
        }
        return doc.copy(spans = spans, blocks = blocks).normalized()
    }

    /** Adds plain paragraphs at the end, after [separator]. */
    fun appendParagraphs(doc: RichDoc, plainText: String, separator: String = "\n"): RichDoc {
        if (plainText.isEmpty()) return doc
        val joined = if (doc.text.isEmpty()) plainText else doc.text + separator + plainText
        val added = RichDoc.lineCount(joined) - doc.blocks.size
        return RichDoc(joined, doc.spans, doc.blocks + List(added) { Block() }).normalized()
    }
}
