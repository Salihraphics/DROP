package app.drop.richtext

import app.drop.data.NoteFormat

/**
 * How rich notes are stored: a small, readable Markdown that any Markdown app can open.
 * [serialize] and [parse] round-trip every [RichDoc] exactly. A note with no formatting is
 * stored as plain text, as before 0.9.0.
 */
object DropMarkdown {
    private const val PUNCTUATION = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~"
    private val wiki = Regex("\\[\\[([^\\[\\]\\n]{1,120})]]")

    /** The order formatting opens in, outermost first. */
    private val ordered = listOf(InlineStyle.Link, InlineStyle.Bold, InlineStyle.Italic, InlineStyle.Strike, InlineStyle.Code)
    private val heading = Regex("^(#{1,6})(?: (.*))?$")
    private val check = Regex("^[-*+] \\[([ xX])](?: (.*))?$")
    private val bullet = Regex("^[-*+](?: (.*))?$")
    private val numbered = Regex("^\\d{1,9}[.)](?: (.*))?$")
    private val fenceOpen = Regex("^(`{3,})[^`]*$")

    fun docOf(body: String, format: Int): RichDoc = if (format == NoteFormat.MARKDOWN) parse(body) else RichDoc.plain(body)

    /** The body to store and its format. */
    fun store(doc: RichDoc): Pair<String, Int> =
        if (doc.hasFormatting) serialize(doc) to NoteFormat.MARKDOWN else doc.text to NoteFormat.PLAIN

    /** The text without any formatting marks. */
    fun plainOf(body: String, format: Int): String = if (format == NoteFormat.MARKDOWN) parse(body).text else body

    /** Text for previews: list bullets and checkboxes shown as characters, no Markdown marks. */
    fun previewOf(body: String, format: Int): String = if (format == NoteFormat.MARKDOWN) toPlainText(parse(body), decorated = true) else body

    fun toPlainText(doc: RichDoc, decorated: Boolean): String {
        if (!decorated) return doc.text
        val numbers = numbering(doc)
        return (0 until doc.blocks.size).joinToString("\n") { line -> prefix(doc.blocks[line], numbers[line]) + doc.lineText(line) }
    }

    /** Each numbered line's number; numbering restarts after any other line. */
    fun numbering(doc: RichDoc): IntArray {
        val out = IntArray(doc.blocks.size)
        var n = 0
        doc.blocks.forEachIndexed { i, b ->
            if (b.type == BlockType.Numbered) out[i] = ++n else n = 0
        }
        return out
    }

    fun prefix(block: Block, number: Int): String = when (block.type) {
        BlockType.Bullet -> "• "
        BlockType.Numbered -> "$number. "
        BlockType.Check -> if (block.checked) "☑ " else "☐ "
        BlockType.Quote -> "│ "
        else -> ""
    }

    fun serialize(doc: RichDoc): String {
        val d = doc.normalized()
        val numbers = numbering(d)
        val out = mutableListOf<String>()
        var line = 0
        while (line < d.blocks.size) {
            val block = d.blocks[line]
            if (block.type == BlockType.Code) {
                var end = line
                while (end + 1 < d.blocks.size && d.blocks[end + 1].type == BlockType.Code) end++
                val lines = (line..end).map { d.lineText(it) }
                val fence = "`".repeat(maxOf(3, lines.maxOf { longestRun(it, '`') } + 1))
                out += fence
                out += lines
                out += fence
                line = end + 1
            } else {
                val content = inline(d, d.lineStarts[line], d.lineEnd(line))
                out += when (block.type) {
                    BlockType.Bullet -> "- $content"
                    BlockType.Numbered -> "${numbers[line]}. $content"
                    BlockType.Check -> (if (block.checked) "- [x] " else "- [ ] ") + content
                    BlockType.Quote -> "> $content"
                    BlockType.H1 -> "# $content"
                    BlockType.H2 -> "## $content"
                    BlockType.H3 -> "### $content"
                    else -> escapeLineStart(content)
                }
                line++
            }
        }
        return out.joinToString("\n")
    }

    private fun longestRun(s: String, c: Char): Int {
        var best = 0
        var cur = 0
        for (ch in s) {
            cur = if (ch == c) cur + 1 else 0
            best = maxOf(best, cur)
        }
        return best
    }

    /** Keeps a paragraph that starts like a heading, quote or list from being read as one. */
    private fun escapeLineStart(content: String): String {
        if (content.isEmpty()) return content
        val c = content[0]
        if (c == '#' || c == '>') return "\\" + content
        if ((c == '-' || c == '+') && (content.length == 1 || content[1] == ' ')) return "\\" + content
        val digits = content.takeWhile { it.isDigit() }
        if (digits.isNotEmpty() && digits.length < content.length) {
            val after = content[digits.length]
            if ((after == '.' || after == ')') && (content.length == digits.length + 1 || content[digits.length + 1] == ' ')) {
                return digits + "\\" + content.substring(digits.length)
            }
        }
        return content
    }

    private class Open(val style: InlineStyle, val span: Span, val closer: String)

    /** One line's text with its inline formatting as Markdown. */
    private fun inline(doc: RichDoc, start: Int, end: Int): String {
        val text = doc.text
        val spans = doc.spans.filter { it.end > start && it.start < end }
        val cuts = sortedSetOf(start, end)
        spans.forEach {
            cuts += it.start.coerceIn(start, end)
            cuts += it.end.coerceIn(start, end)
        }
        // [[links]] that no formatting cuts through are written as they are, without escapes.
        val raw = BooleanArray(end - start)
        wiki.findAll(text.substring(start, end)).forEach { m ->
            val from = start + m.range.first
            val to = start + m.range.last + 1
            if (cuts.none { it in from + 1 until to }) for (i in m.range) raw[i] = true
        }
        val points = cuts.toList()
        val out = StringBuilder()
        val stack = mutableListOf<Open>()
        for (k in 0 until points.size - 1) {
            val a = points[k]
            val b = points[k + 1]
            if (a >= b) continue
            val covering = spans.filter { it.start <= a && it.end >= b }
            val target = ordered.mapNotNull { st -> covering.firstOrNull { it.style == st } }
            var common = 0
            while (common < stack.size && common < target.size && stack[common].style == target[common].style &&
                (stack[common].style != InlineStyle.Link || stack[common].span == target[common])
            ) {
                common++
            }
            while (stack.size > common) out.append(stack.removeAt(stack.size - 1).closer)
            val segment = text.substring(a, b)
            for (t in target.drop(common)) {
                when (t.style) {
                    InlineStyle.Link -> {
                        out.append('[')
                        stack += Open(t.style, t, "](" + encodeUrl(t.url ?: "") + ")")
                    }
                    InlineStyle.Bold -> {
                        out.append("**")
                        stack += Open(t.style, t, "**")
                    }
                    InlineStyle.Italic -> {
                        out.append('_')
                        stack += Open(t.style, t, "_")
                    }
                    InlineStyle.Strike -> {
                        out.append("~~")
                        stack += Open(t.style, t, "~~")
                    }
                    InlineStyle.Code -> {
                        val fence = "`".repeat(longestRun(segment, '`') + 1)
                        val pad = segment.startsWith('`') || segment.endsWith('`') ||
                            (segment.length > 1 && segment.startsWith(' ') && segment.endsWith(' ') && !segment.isBlank())
                        out.append(fence)
                        if (pad) out.append(' ')
                        stack += Open(t.style, t, (if (pad) " " else "") + fence)
                    }
                }
            }
            if (stack.lastOrNull()?.style == InlineStyle.Code) {
                out.append(segment)
            } else {
                val inLink = stack.any { it.style == InlineStyle.Link }
                for (i in a until b) {
                    val ch = text[i]
                    if (inLink || !raw[i - start]) out.append(escape(ch)) else out.append(ch)
                }
            }
        }
        while (stack.isNotEmpty()) out.append(stack.removeAt(stack.size - 1).closer)
        return out.toString()
    }

    private fun escape(c: Char): String = if (c in "\\*_~`[]") "\\" + c else c.toString()

    private fun encodeUrl(url: String): String = url.replace("%", "%25").replace(" ", "%20").replace(")", "%29").replace("(", "%28")

    private fun decodeUrl(url: String): String = url.replace("%28", "(").replace("%29", ")").replace("%20", " ").replace("%25", "%")

    /**
     * Reads DROP's Markdown. [lenient] accepts Markdown from other apps (indented lines, deeper
     * headings), as used by import.
     */
    fun parse(markdown: String, lenient: Boolean = false): RichDoc {
        val text = StringBuilder()
        val spans = ArrayList<Span>()
        val blocks = ArrayList<Block>()
        fun addLine(block: Block, content: String, parseInline: Boolean) {
            if (blocks.isNotEmpty()) text.append('\n')
            val base = text.length
            if (!parseInline) {
                text.append(content)
            } else {
                val (plain, inline) = parseInline(content)
                text.append(plain)
                inline.forEach { spans += it.copy(start = it.start + base, end = it.end + base) }
            }
            blocks += block
        }

        val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        var i = 0
        while (i < lines.size) {
            val raw = lines[i]
            val line = if (lenient) raw.trimStart(' ', '\t') else raw
            val fence = fenceOpen.matchEntire(line)
            if (fence != null) {
                val n = fence.groupValues[1].length
                var j = i + 1
                while (j < lines.size) {
                    val t = lines[j].trim()
                    if (t.all { it == '`' } && t.length >= n) break
                    j++
                }
                for (k in i + 1 until minOf(j, lines.size)) addLine(Block(BlockType.Code), lines[k], parseInline = false)
                // An empty code block still has its one line.
                if (j == i + 1 && j < lines.size) addLine(Block(BlockType.Code), "", parseInline = false)
                i = j + 1
                continue
            }
            val h = heading.matchEntire(line)?.takeIf { it.groupValues[1].length <= 3 || lenient }
            val c = check.matchEntire(line)
            when {
                h != null -> {
                    val type = when (h.groupValues[1].length) {
                        1 -> BlockType.H1
                        2 -> BlockType.H2
                        else -> BlockType.H3
                    }
                    addLine(Block(type), h.groupValues[2], parseInline = true)
                }
                line.startsWith(">") -> addLine(Block(BlockType.Quote), line.removePrefix(">").removePrefix(" "), parseInline = true)
                c != null -> addLine(Block(BlockType.Check, checked = c.groupValues[1] != " "), c.groupValues[2], parseInline = true)
                bullet.matches(line) -> addLine(Block(BlockType.Bullet), bullet.matchEntire(line)!!.groupValues[1], parseInline = true)
                numbered.matches(line) -> addLine(Block(BlockType.Numbered), numbered.matchEntire(line)!!.groupValues[1], parseInline = true)
                else -> addLine(Block(), raw, parseInline = true)
            }
            i++
        }
        if (blocks.isEmpty()) blocks += Block()
        return RichDoc(text.toString(), spans, blocks).normalized()
    }

    private sealed interface Token

    private class TextToken(val text: String) : Token

    private class Marker(val style: InlineStyle, val raw: String) : Token

    private class CodeToken(val text: String) : Token

    private class LinkToken(val inner: List<Token>, val url: String) : Token

    /** One line of Markdown as plain text and spans. */
    fun parseInline(content: String): Pair<String, List<Span>> {
        val out = StringBuilder()
        val spans = ArrayList<Span>()
        build(tokenize(content), out, spans)
        return out.toString() to spans
    }

    private fun tokenize(s: String): List<Token> {
        val tokens = ArrayList<Token>()
        val buf = StringBuilder()
        fun flush() {
            if (buf.isNotEmpty()) {
                tokens += TextToken(buf.toString())
                buf.clear()
            }
        }
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length && s[i + 1] in PUNCTUATION) {
                buf.append(s[i + 1])
                i += 2
                continue
            }
            if (c == '[' && s.startsWith("[[", i)) {
                val m = wiki.matchAt(s, i)
                if (m != null) {
                    buf.append(m.value)
                    i = m.range.last + 1
                    continue
                }
            }
            when {
                c == '`' -> {
                    var n = 0
                    while (i + n < s.length && s[i + n] == '`') n++
                    val close = findBacktickRun(s, i + n, n)
                    if (close < 0) {
                        buf.append("`".repeat(n))
                        i += n
                    } else {
                        var code = s.substring(i + n, close)
                        if (code.length > 1 && code.startsWith(' ') && code.endsWith(' ') && !code.isBlank()) code = code.substring(1, code.length - 1)
                        flush()
                        tokens += CodeToken(code)
                        i = close + n
                    }
                }
                c == '*' || c == '_' -> {
                    var n = 0
                    while (i + n < s.length && s[i + n] == c) n++
                    flush()
                    when (n) {
                        1 -> tokens += Marker(InlineStyle.Italic, c.toString())
                        2 -> tokens += Marker(InlineStyle.Bold, "$c$c")
                        3 -> {
                            tokens += Marker(InlineStyle.Bold, "$c$c")
                            tokens += Marker(InlineStyle.Italic, c.toString())
                        }
                        else -> buf.append(c.toString().repeat(n))
                    }
                    i += n
                }
                c == '~' && s.startsWith("~~", i) -> {
                    flush()
                    tokens += Marker(InlineStyle.Strike, "~~")
                    i += 2
                }
                c == '[' -> {
                    val link = matchLink(s, i)
                    if (link == null) {
                        buf.append(c)
                        i++
                    } else {
                        flush()
                        tokens += LinkToken(tokenize(link.first), link.second)
                        i = link.third
                    }
                }
                else -> {
                    buf.append(c)
                    i++
                }
            }
        }
        flush()
        return tokens
    }

    /** Where a run of exactly [n] backticks starts at or after [from], or -1. */
    private fun findBacktickRun(s: String, from: Int, n: Int): Int {
        var i = from
        while (i < s.length) {
            if (s[i] == '`') {
                var m = 0
                while (i + m < s.length && s[i + m] == '`') m++
                if (m == n) return i
                i += m
            } else {
                i++
            }
        }
        return -1
    }

    /** A [text](url) link at [start]: its text, url and the index after it. */
    private fun matchLink(s: String, start: Int): Triple<String, String, Int>? {
        var i = start + 1
        while (i < s.length) {
            val c = s[i]
            when {
                c == '\\' && i + 1 < s.length -> i += 2
                c == '`' -> {
                    var n = 0
                    while (i + n < s.length && s[i + n] == '`') n++
                    val close = findBacktickRun(s, i + n, n)
                    i = if (close < 0) i + n else close + n
                }
                c == '[' -> return null
                c == ']' -> break
                else -> i++
            }
        }
        if (i >= s.length || i + 1 >= s.length || s[i + 1] != '(') return null
        val close = s.indexOf(')', i + 2)
        if (close < 0) return null
        val url = s.substring(i + 2, close)
        if (url.isEmpty() || url.any { it.isWhitespace() }) return null
        return Triple(s.substring(start + 1, i), decodeUrl(url), close + 1)
    }

    private fun build(tokens: List<Token>, out: StringBuilder, spans: MutableList<Span>) {
        // Markers only count in pairs; an odd one out is ordinary text.
        val paired = HashSet<Token>()
        for (style in listOf(InlineStyle.Bold, InlineStyle.Italic, InlineStyle.Strike)) {
            val ms = tokens.filterIsInstance<Marker>().filter { it.style == style }
            for (k in 0 until ms.size / 2 * 2) paired += ms[k]
        }
        val open = HashMap<InlineStyle, Int>()
        for (t in tokens) {
            when (t) {
                is TextToken -> out.append(t.text)
                is CodeToken -> {
                    val s = out.length
                    out.append(t.text)
                    if (t.text.isNotEmpty()) spans += Span(s, out.length, InlineStyle.Code)
                }
                is LinkToken -> {
                    val s = out.length
                    build(t.inner, out, spans)
                    if (out.length > s) spans += Span(s, out.length, InlineStyle.Link, t.url)
                }
                is Marker -> {
                    if (t in paired) {
                        val s = open.remove(t.style)
                        if (s == null) {
                            open[t.style] = out.length
                        } else if (out.length > s) {
                            spans += Span(s, out.length, t.style)
                        }
                    } else {
                        out.append(t.raw)
                    }
                }
            }
        }
    }
}
