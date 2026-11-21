package app.drop.ui.richtext

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.window.DialogProperties
import app.drop.R
import app.drop.links.WikiLinks
import app.drop.richtext.Block
import app.drop.richtext.BlockType
import app.drop.richtext.DropMarkdown
import app.drop.richtext.InlineStyle
import app.drop.richtext.LinkUrls
import app.drop.richtext.RichDoc
import app.drop.richtext.RichEditorModel
import app.drop.ui.icons.ActionIcons
import app.drop.ui.icons.FormatIcons
import app.drop.ui.theme.DialogWidth
import app.drop.ui.theme.DropTheme
import app.drop.ui.theme.accentTextButtonColors
import app.drop.ui.theme.dropTextFieldColors

data class RichColors(val accent: Color, val muted: Color, val codeBackground: Color)

@Composable
fun rememberRichColors(): RichColors {
    val c = DropTheme.colors
    return remember(c) { RichColors(accent = c.accentContent, muted = c.textSecondary, codeBackground = c.surfaceElevated) }
}

/**
 * Maps between the stored text and what is shown, which adds list bullets, numbers, checkboxes
 * and the quote bar in front of lines. Those marks are drawn, never stored in the note.
 */
class PrefixMapping(private val doc: RichDoc, private val prefixes: IntArray) : OffsetMapping {
    private val shiftBefore = IntArray(prefixes.size).also { out ->
        for (i in 1 until prefixes.size) out[i] = out[i - 1] + prefixes[i - 1]
    }
    private val shownStarts = IntArray(prefixes.size) { doc.lineStarts[it] + shiftBefore[it] }

    override fun originalToTransformed(offset: Int): Int {
        val o = offset.coerceIn(0, doc.text.length)
        val line = doc.lineOf(o)
        return shiftBefore[line] + o + prefixes[line]
    }

    override fun transformedToOriginal(offset: Int): Int {
        var lo = 0
        var hi = shownStarts.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (shownStarts[mid] <= offset) lo = mid else hi = mid - 1
        }
        val contentStart = shownStarts[lo] + prefixes[lo]
        return if (offset <= contentStart) doc.lineStarts[lo] else (offset - shiftBefore[lo] - prefixes[lo]).coerceAtMost(doc.lineEnd(lo))
    }

    companion object {
        fun of(doc: RichDoc): PrefixMapping {
            val numbers = DropMarkdown.numbering(doc)
            return PrefixMapping(doc, IntArray(doc.blocks.size) { DropMarkdown.prefix(doc.blocks[it], numbers[it]).length })
        }
    }
}

/** Draws the document's formatting on the editor's plain text. */
class RichTransformation(private val doc: RichDoc, private val colors: RichColors) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        // While the keyboard is mid-edit the text can be ahead of the document: show it plainly.
        if (text.text != doc.text) return TransformedText(text, OffsetMapping.Identity)
        val numbers = DropMarkdown.numbering(doc)
        val mapping = PrefixMapping.of(doc)
        val shown = AnnotatedString.Builder().apply {
            for (line in doc.blocks.indices) {
                val block = doc.blocks[line]
                val prefix = DropMarkdown.prefix(block, numbers[line])
                if (prefix.isNotEmpty()) {
                    val start = length
                    append(prefix)
                    addStyle(SpanStyle(color = colors.accent, fontWeight = FontWeight.Medium), start, length)
                }
                val start = length
                append(doc.lineText(line))
                lineStyle(block)?.let { if (length > start) addStyle(it, start, length) }
                if (line < doc.blocks.size - 1) append('\n')
            }
            for (span in doc.spans) {
                val a = mapping.originalToTransformed(span.start)
                val b = mapping.originalToTransformed(span.end)
                if (a < b) addStyle(inlineStyle(span.style), a, b)
            }
            for (range in WikiLinks.ranges(doc.text)) {
                val a = mapping.originalToTransformed(range.first)
                val b = mapping.originalToTransformed(range.last + 1)
                if (a < b) addStyle(SpanStyle(color = colors.accent, fontWeight = FontWeight.Medium), a, b)
            }
        }.toAnnotatedString()
        return TransformedText(shown, mapping)
    }

    private fun lineStyle(block: Block): SpanStyle? = when (block.type) {
        BlockType.H1 -> SpanStyle(fontSize = 1.5.em, fontWeight = FontWeight.Bold)
        BlockType.H2 -> SpanStyle(fontSize = 1.3.em, fontWeight = FontWeight.Bold)
        BlockType.H3 -> SpanStyle(fontSize = 1.15.em, fontWeight = FontWeight.SemiBold)
        BlockType.Quote -> SpanStyle(color = colors.muted, fontStyle = FontStyle.Italic)
        BlockType.Code -> SpanStyle(fontFamily = FontFamily.Monospace, background = colors.codeBackground)
        BlockType.Check -> if (block.checked) SpanStyle(color = colors.muted, textDecoration = TextDecoration.LineThrough) else null
        else -> null
    }

    private fun inlineStyle(style: InlineStyle): SpanStyle = when (style) {
        InlineStyle.Bold -> SpanStyle(fontWeight = FontWeight.Bold)
        InlineStyle.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
        InlineStyle.Strike -> SpanStyle(textDecoration = TextDecoration.LineThrough)
        InlineStyle.Code -> SpanStyle(fontFamily = FontFamily.Monospace, background = colors.codeBackground)
        InlineStyle.Link -> SpanStyle(color = colors.accent, textDecoration = TextDecoration.Underline)
    }
}

/** The note's text with its formatting, edited through [model]. */
@Composable
fun RichTextField(
    model: RichEditorModel,
    textStyle: TextStyle,
    placeholder: String,
    modifier: Modifier = Modifier,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    val colors = rememberRichColors()
    val state = model.state
    val value = TextFieldValue(
        text = state.doc.text,
        selection = TextRange(state.selStart, state.selEnd),
        composition = state.composition?.let { TextRange(it.first, it.last + 1) },
    )
    val transformation = remember(state.doc, colors) { RichTransformation(state.doc, colors) }
    BasicTextField(
        value = value,
        onValueChange = { v ->
            model.onValueChange(v.text, v.selection.start, v.selection.end, v.composition?.let { it.min until it.max })
        },
        modifier = modifier.testTag("note-body"),
        textStyle = textStyle.copy(color = DropTheme.colors.textPrimary),
        cursorBrush = SolidColor(DropTheme.colors.accentContent),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        visualTransformation = transformation,
        onTextLayout = onTextLayout,
        decorationBox = { inner ->
            Box {
                if (state.doc.text.isEmpty()) Text(placeholder, style = textStyle, color = DropTheme.colors.textSecondary)
                inner()
            }
        },
    )
}

/** The formatting bar above the keyboard. Every button applies to the selected text only. */
@Composable
fun FormattingToolbar(model: RichEditorModel, onLink: () -> Unit, modifier: Modifier = Modifier) {
    val c = DropTheme.colors
    val inline = model.currentInlineStyles()
    val block = model.currentBlock()
    val label = stringResource(R.string.fmt_toolbar)
    Row(
        modifier
            .fillMaxWidth()
            .background(c.surface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp)
            .semantics { contentDescription = label }
            .testTag("format-bar"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolButton(FormatIcons.Undo, R.string.fmt_undo, model.canUndo) { model.undo() }
        ToolButton(FormatIcons.Redo, R.string.fmt_redo, model.canRedo) { model.redo() }
        Separator()
        ToolToggle(FormatIcons.Bold, R.string.fmt_bold, InlineStyle.Bold in inline) { model.toggleInline(InlineStyle.Bold) }
        ToolToggle(FormatIcons.Italic, R.string.fmt_italic, InlineStyle.Italic in inline) { model.toggleInline(InlineStyle.Italic) }
        ToolToggle(FormatIcons.Strike, R.string.fmt_strike, InlineStyle.Strike in inline) { model.toggleInline(InlineStyle.Strike) }
        ToolToggle(FormatIcons.Code, R.string.fmt_code, InlineStyle.Code in inline) { model.toggleInline(InlineStyle.Code) }
        ToolToggle(ActionIcons.Link, R.string.fmt_link, model.currentLink() != null) { onLink() }
        Separator()
        HeadingToggle("H1", R.string.fmt_h1, block.type == BlockType.H1) { model.toggleBlock(BlockType.H1) }
        HeadingToggle("H2", R.string.fmt_h2, block.type == BlockType.H2) { model.toggleBlock(BlockType.H2) }
        HeadingToggle("H3", R.string.fmt_h3, block.type == BlockType.H3) { model.toggleBlock(BlockType.H3) }
        Separator()
        ToolToggle(FormatIcons.Bulleted, R.string.fmt_bullets, block.type == BlockType.Bullet) { model.toggleBlock(BlockType.Bullet) }
        ToolToggle(FormatIcons.Numbered, R.string.fmt_numbers, block.type == BlockType.Numbered) { model.toggleBlock(BlockType.Numbered) }
        ToolToggle(FormatIcons.Checklist, R.string.fmt_checklist, block.type == BlockType.Check) { model.toggleBlock(BlockType.Check) }
        if (block.type == BlockType.Check) {
            ToolToggle(FormatIcons.CheckBox, R.string.fmt_check_toggle, block.checked) { model.toggleChecked() }
        }
        ToolToggle(FormatIcons.Quote, R.string.fmt_quote, block.type == BlockType.Quote) { model.toggleBlock(BlockType.Quote) }
        ToolToggle(FormatIcons.CodeBlock, R.string.fmt_code_block, block.type == BlockType.Code) { model.toggleBlock(BlockType.Code) }
        Separator()
        ToolButton(FormatIcons.ClearFormat, R.string.fmt_clear, true) { model.clearFormatting() }
    }
}

@Composable
private fun Separator() {
    Spacer(Modifier.padding(horizontal = 4.dp).width(1.dp).height(24.dp).background(DropTheme.colors.border))
}

@Composable
private fun ToolButton(icon: ImageVector, label: Int, enabled: Boolean, onClick: () -> Unit) {
    val description = stringResource(label)
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(icon, contentDescription = description, tint = if (enabled) DropTheme.colors.textPrimary else DropTheme.colors.disabled)
    }
}

@Composable
private fun ToolToggle(icon: ImageVector, label: Int, checked: Boolean, onClick: () -> Unit) {
    val c = DropTheme.colors
    IconToggleButton(
        checked = checked,
        onCheckedChange = { onClick() },
        colors = IconButtonDefaults.iconToggleButtonColors(
            contentColor = c.textPrimary,
            checkedContainerColor = c.accentSubtle,
            checkedContentColor = c.textPrimary,
        ),
    ) {
        Icon(icon, contentDescription = stringResource(label))
    }
}

@Composable
private fun HeadingToggle(text: String, label: Int, checked: Boolean, onClick: () -> Unit) {
    val c = DropTheme.colors
    val description = stringResource(label)
    IconToggleButton(
        checked = checked,
        onCheckedChange = { onClick() },
        modifier = Modifier.semantics { contentDescription = description },
        colors = IconButtonDefaults.iconToggleButtonColors(contentColor = c.textPrimary, checkedContainerColor = c.accentSubtle, checkedContentColor = c.textPrimary),
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

/** Shown when the cursor is in a link: open, edit or remove it. */
@Composable
fun LinkBar(model: RichEditorModel, onEdit: () -> Unit, onCannotOpen: () -> Unit, modifier: Modifier = Modifier) {
    val link = model.currentLink() ?: return
    val c = DropTheme.colors
    val uri = LocalUriHandler.current
    Row(modifier.fillMaxWidth().background(c.surfaceElevated).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(link.url.orEmpty(), color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        TextButton(onClick = {
            runCatching { uri.openUri(link.url.orEmpty()) }.onFailure { onCannotOpen() }
        }, colors = accentTextButtonColors()) { Text(stringResource(R.string.link_open)) }
        TextButton(onClick = onEdit, colors = accentTextButtonColors()) { Text(stringResource(R.string.link_bar_edit)) }
        TextButton(onClick = { model.removeLink() }, colors = accentTextButtonColors()) { Text(stringResource(R.string.link_bar_remove)) }
    }
}

/** Adds or edits a link. Only web, mail and phone links are accepted. */
@Composable
fun LinkDialog(model: RichEditorModel, onDismiss: () -> Unit) {
    val existing = model.currentLink()
    val s = model.state
    val hasSelection = s.selStart != s.selEnd
    var url by rememberSaveable { mutableStateOf(existing?.url.orEmpty()) }
    var label by rememberSaveable { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = DialogWidth,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text(stringResource(if (existing != null) R.string.link_dialog_edit else R.string.link_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        invalid = false
                    },
                    label = { Text(stringResource(R.string.link_dialog_url)) },
                    singleLine = true,
                    isError = invalid,
                    supportingText = if (invalid) ({ Text(stringResource(R.string.link_dialog_invalid)) }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    colors = dropTextFieldColors(),
                    modifier = Modifier.fillMaxWidth().testTag("link-url"),
                )
                if (!hasSelection && existing == null) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text(stringResource(R.string.link_dialog_label)) },
                        singleLine = true,
                        colors = dropTextFieldColors(),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val normalized = LinkUrls.normalize(url)
                if (normalized == null) {
                    invalid = true
                } else {
                    if (existing != null && !hasSelection) model.selectRange(existing.start, existing.end)
                    model.setLink(normalized, label.ifBlank { null })
                    onDismiss()
                }
            }, colors = accentTextButtonColors()) { Text(stringResource(R.string.link_dialog_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, colors = accentTextButtonColors()) { Text(stringResource(R.string.dialog_cancel)) } },
    )
}
