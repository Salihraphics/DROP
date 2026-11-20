package app.drop.richtext

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** What the editor shows: the document, the selection and the keyboard's composing range. */
data class EditorState(
    val doc: RichDoc,
    val selStart: Int = doc.text.length,
    val selEnd: Int = selStart,
    val composition: IntRange? = null,
)

/**
 * The editor's state with undo and redo. Typing within a second is one undo step; a new line
 * always starts a new one.
 */
class RichEditorModel(initial: RichDoc = RichDoc.plain(""), private val clock: () -> Long = System::currentTimeMillis) {
    var state by mutableStateOf(EditorState(initial))
        private set

    /** Styles for the next typed text when they were toggled with nothing selected. */
    var typingStyles by mutableStateOf<Set<InlineStyle>?>(null)
        private set

    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    /** Counts content changes, so the screen knows when to save. */
    var revision by mutableIntStateOf(0)
        private set

    var onContentChange: () -> Unit = {}

    private val undoStack = ArrayDeque<EditorState>()
    private val redoStack = ArrayDeque<EditorState>()
    private var lastTypingAt = 0L
    private var lastWasTyping = false

    val doc: RichDoc get() = state.doc

    /** Shows [doc], for example a restored version. Without [resetHistory] the change can be undone. */
    fun replace(doc: RichDoc, resetHistory: Boolean) {
        if (resetHistory) {
            undoStack.clear()
            redoStack.clear()
        } else {
            push(state)
            redoStack.clear()
        }
        state = EditorState(doc.normalized(), doc.text.length)
        typingStyles = null
        changed(!resetHistory)
    }

    fun onValueChange(text: String, selStart: Int, selEnd: Int, composition: IntRange?) {
        val old = state
        if (text == old.doc.text) {
            if (selStart != old.selStart || selEnd != old.selEnd) typingStyles = null
            state = old.copy(selStart = selStart, selEnd = selEnd, composition = composition)
            return
        }
        val now = clock()
        val newline = text.length > old.doc.text.length && text.count { it == '\n' } > old.doc.text.count { it == '\n' }
        if (!lastWasTyping || now - lastTypingAt > TYPING_GROUP_MS || newline) push(old)
        redoStack.clear()
        lastWasTyping = !newline
        lastTypingAt = now
        val result = RichEdit.apply(old.doc, text, selStart, selEnd, typingStyles)
        val len = result.doc.text.length
        state = EditorState(
            result.doc,
            result.selectionStart.coerceAtMost(len),
            result.selectionEnd.coerceAtMost(len),
            if (result.doc.text == text) composition else null,
        )
        changed(true)
    }

    /** With a selection, formats it; with only a cursor, applies to what is typed next. */
    fun toggleInline(style: InlineStyle) {
        val s = state
        if (s.selStart == s.selEnd) {
            val current = typingStyles ?: currentInlineStyles()
            typingStyles = if (style in current) current - style else current + style
            return
        }
        format { RichEdit.toggleInline(it.doc, minOf(it.selStart, it.selEnd), maxOf(it.selStart, it.selEnd), style) }
    }

    /** Applies a block format to the selection only, keeping the selection on the same text. */
    fun toggleBlock(type: BlockType) {
        val s = state
        val result = RichEdit.applyBlock(s.doc, minOf(s.selStart, s.selEnd), maxOf(s.selStart, s.selEnd), type)
        if (result.doc == s.doc) return
        push(s)
        redoStack.clear()
        val reversed = s.selStart > s.selEnd
        state = EditorState(
            result.doc,
            if (reversed) result.selectionEnd else result.selectionStart,
            if (reversed) result.selectionStart else result.selectionEnd,
        )
        changed(true)
    }

    fun toggleChecked() = format { RichEdit.toggleChecked(it.doc, it.doc.lineOf(minOf(it.selStart, it.selEnd))) }

    /** Links the selection, or inserts [label] (or the url) as a link at the cursor. */
    fun setLink(url: String, label: String?) {
        val s = state
        val a = minOf(s.selStart, s.selEnd)
        val b = maxOf(s.selStart, s.selEnd)
        if (a < b) {
            format { RichEdit.setLink(it.doc, a, b, url) }
            return
        }
        val insert = label?.takeIf { it.isNotBlank() } ?: url
        push(s)
        redoStack.clear()
        val text = s.doc.text.substring(0, a) + insert + s.doc.text.substring(a)
        val edited = RichEdit.apply(s.doc, text, a + insert.length, a + insert.length, emptySet()).doc
        state = EditorState(RichEdit.setLink(edited, a, a + insert.length, url), a + insert.length)
        changed(true)
    }

    fun removeLink() = format { RichEdit.removeLink(it.doc, minOf(it.selStart, it.selEnd)) }

    fun clearFormatting() = format { RichEdit.clearFormatting(it.doc, minOf(it.selStart, it.selEnd), maxOf(it.selStart, it.selEnd)) }

    fun appendParagraphs(text: String, separator: String) = format { RichEdit.appendParagraphs(it.doc, text, separator) }

    fun selectRange(start: Int, end: Int) {
        val len = state.doc.text.length
        state = state.copy(selStart = start.coerceIn(0, len), selEnd = end.coerceIn(0, len))
        typingStyles = null
    }

    fun moveCursor(offset: Int) {
        val o = offset.coerceIn(0, state.doc.text.length)
        state = state.copy(selStart = o, selEnd = o)
        typingStyles = null
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(state)
        state = previous.copy(composition = null)
        changed(true)
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(state)
        state = next.copy(composition = null)
        changed(true)
    }

    /** The styles the format bar shows as on. */
    fun currentInlineStyles(): Set<InlineStyle> {
        val s = state
        val typing = typingStyles
        if (typing != null && s.selStart == s.selEnd) return typing
        val a = minOf(s.selStart, s.selEnd)
        val b = maxOf(s.selStart, s.selEnd)
        if (a == b) return if (a == 0) emptySet() else s.doc.stylesAt(a - 1) - InlineStyle.Link
        return InlineStyle.entries.filterTo(HashSet()) { s.doc.hasStyle(a, b, it) }
    }

    fun currentBlock(): Block = state.doc.blocks[state.doc.lineOf(minOf(state.selStart, state.selEnd))]

    fun currentLink(): Span? = state.doc.linkAt(minOf(state.selStart, state.selEnd))

    private fun format(change: (EditorState) -> RichDoc) {
        val s = state
        val doc = change(s)
        if (doc == s.doc) return
        push(s)
        redoStack.clear()
        state = s.copy(doc = doc, composition = null)
        changed(true)
    }

    private fun push(s: EditorState) {
        undoStack.addLast(s.copy(composition = null))
        while (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
    }

    private fun changed(contentChanged: Boolean) {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
        if (contentChanged) {
            revision++
            onContentChange()
        }
    }

    companion object {
        const val MAX_HISTORY = 200
        const val TYPING_GROUP_MS = 1000L
    }
}
