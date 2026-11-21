package app.drop.ui.notes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.drop.R
import app.drop.data.Links
import app.drop.data.Note
import app.drop.richtext.DropMarkdown
import app.drop.search.FoldedText
import app.drop.search.SearchQuery
import app.drop.ui.icons.ActionIcons
import app.drop.ui.icons.PinIcons
import app.drop.ui.theme.DropTheme
import app.drop.ui.theme.noteTextStyle

/** What a card shows: the title, or the first line when there is none, and a preview. */
data class CardText(val title: String, val preview: String)

fun cardText(note: Note): CardText {
    val title = note.title.trim()
    val body = DropMarkdown.previewOf(note.body, note.format).trim()
    if (title.isNotEmpty()) return CardText(title, body)
    val lineEnd = body.indexOf('\n')
    return if (lineEnd < 0) CardText(body, "") else CardText(body.substring(0, lineEnd).trim(), body.substring(lineEnd + 1).trim())
}

fun cardTitle(note: Note): String = cardText(note).title

/** [text] with the search terms highlighted. */
@Composable
fun highlighted(text: String, query: SearchQuery?): AnnotatedString {
    if (query == null || query.isEmpty()) return AnnotatedString(text)
    val c = DropTheme.colors
    val ranges = query.highlights(FoldedText(text))
    return buildAnnotatedString {
        append(text)
        ranges.forEach { addStyle(SpanStyle(background = c.accent.copy(alpha = 0.45f), color = c.textPrimary), it.first, it.last + 1) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: Note,
    meta: String,
    modifier: Modifier = Modifier,
    showPin: Boolean = true,
    query: SearchQuery? = null,
    onClick: () -> Unit = {},
    onCompletedChange: ((Boolean) -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onPinClick: (() -> Unit)? = null,
    selected: Boolean? = null,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    val c = DropTheme.colors
    val text = cardText(note)
    val done = note.isCompletedTask
    val emptyLabel = stringResource(R.string.note_empty)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { if (selected != null) this.selected = selected }
            .testTag("note-card-${note.id}"),
        shape = RoundedCornerShape(16.dp),
        color = if (selected == true) c.accentSubtle else c.surface,
        border = BorderStroke(1.dp, if (selected == true) c.accentContent else c.border),
    ) {
        Row(
            Modifier
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (note.isTask && onCompletedChange != null) {
                val doneLabel = stringResource(R.string.task_done_toggle, text.title.ifEmpty { emptyLabel })
                Checkbox(
                    checked = done,
                    onCheckedChange = onCompletedChange,
                    colors = CheckboxDefaults.colors(checkedColor = c.accent, checkmarkColor = c.onAccent, uncheckedColor = c.textSecondary),
                    modifier = Modifier.size(32.dp).semantics { contentDescription = doneLabel },
                )
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    highlighted(text.title.ifEmpty { emptyLabel }, query),
                    style = noteTextStyle(MaterialTheme.typography.titleMedium).copy(textDecoration = if (done) TextDecoration.LineThrough else null),
                    color = if (done || text.title.isEmpty()) c.textSecondary else c.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (text.preview.isNotEmpty()) {
                    Text(
                        highlighted(text.preview, query),
                        style = noteTextStyle(MaterialTheme.typography.bodyMedium),
                        color = c.textSecondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                note.url?.let { url ->
                    Links.host(url)?.let { host ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(ActionIcons.Link, contentDescription = null, tint = c.accentContent, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(host, style = MaterialTheme.typography.labelMedium, color = c.accentContent, fontWeight = FontWeight.Medium, maxLines = 1)
                        }
                    }
                }
                extra()
                if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
            }
            if (showPin && note.pinned) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    PinIcons.Filled,
                    contentDescription = stringResource(R.string.note_pinned),
                    tint = c.accentContent,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
