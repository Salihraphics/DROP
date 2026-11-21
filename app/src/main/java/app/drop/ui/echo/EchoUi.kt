package app.drop.ui.echo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.drop.DropApplication
import app.drop.R
import app.drop.data.Note
import app.drop.echo.EchoDoc
import app.drop.echo.EchoIndex
import app.drop.echo.EchoReason
import app.drop.echo.EchoStrength
import app.drop.echo.EchoSuggestion
import app.drop.echo.EchoWeights
import app.drop.echo.Rediscovery
import app.drop.ui.notes.cardTitle
import app.drop.ui.theme.DropTheme
import app.drop.ui.theme.accentTextButtonColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.util.Locale

/** One ECHO suggestion, ready to show. [relatedTo] is the recent note it fits, for rediscoveries. */
data class EchoCard(
    val noteId: Long,
    val title: String,
    val preview: String,
    val reasons: List<EchoReason>,
    val relatedTo: String? = null,
    val score: Double = 0.0,
    val points: List<Double> = emptyList(),
)

private fun EchoDoc.titleText(): String = cardTitle(Note(title = title, body = body, createdAt = 0, updatedAt = 0))

private fun EchoDoc.previewText(): String =
    (body.takeIf { title.isNotBlank() } ?: "").lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().take(140)

fun EchoIndex.card(s: EchoSuggestion, relatedTo: Long? = null): EchoCard? {
    val doc = doc(s.noteId) ?: return null
    return EchoCard(doc.id, doc.titleText(), doc.previewText(), s.reasons, relatedTo?.let { doc(it)?.titleText() }, s.score, s.points)
}

fun EchoIndex.cards(found: List<Rediscovery>): List<EchoCard> = found.mapNotNull { card(it.suggestion, it.seedId) }

@Composable
fun reasonText(reason: EchoReason): String = when (reason) {
    is EchoReason.SharedWords -> stringResource(if (reason.inTitles) R.string.echo_reason_title else R.string.echo_reason_words, reason.words.joinToString(", "))
    is EchoReason.SameProject -> stringResource(R.string.echo_reason_project, reason.project)
    is EchoReason.CanvasConnection -> stringResource(R.string.echo_reason_canvas, reason.canvas)
    EchoReason.WikiLink -> stringResource(R.string.echo_reason_link)
}

/** Whole points without a decimal, others with one: "4", "1.5". */
fun formatPoints(p: Double): String = if (p == Math.floor(p)) p.toInt().toString() else String.format(Locale.ROOT, "%.1f", p)

/** A suggested note with why ECHO suggests it. [onDismiss] adds "How ECHO decided" and "Not useful". */
@Composable
fun EchoSuggestionRow(card: EchoCard, onOpen: () -> Unit, onDismiss: (() -> Unit)?, modifier: Modifier = Modifier) {
    val c = DropTheme.colors
    Surface(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth().testTag("echo-${card.noteId}"),
        shape = RoundedCornerShape(14.dp),
        color = c.surface,
        border = BorderStroke(1.dp, c.border),
    ) {
        Column(Modifier.padding(start = 14.dp, top = 12.dp, end = 4.dp, bottom = if (onDismiss == null) 12.dp else 0.dp)) {
            Text(
                card.title.ifBlank { stringResource(R.string.note_empty) },
                modifier = Modifier.padding(end = 10.dp),
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
            )
            if (card.preview.isNotEmpty()) {
                Text(card.preview, modifier = Modifier.padding(end = 10.dp), color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(4.dp))
            card.relatedTo?.let { related ->
                Text(
                    stringResource(R.string.echo_related_to, related.ifBlank { stringResource(R.string.note_empty) }),
                    modifier = Modifier.padding(top = 6.dp, end = 10.dp),
                    color = c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            card.reasons.forEachIndexed { i, r ->
                val pts = card.points.getOrNull(i)?.let { "  +" + formatPoints(it) } ?: ""
                Text(
                    reasonText(r) + pts,
                    modifier = Modifier.padding(top = 2.dp, end = 10.dp),
                    color = c.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            if (card.score > 0.0) {
                val strong = EchoStrength.isStrong(card.score)
                Text(
                    stringResource(if (strong) R.string.echo_strength_strong else R.string.echo_strength_possible, EchoStrength.percent(card.score, EchoWeights())),
                    modifier = Modifier.padding(top = 6.dp, end = 10.dp).testTag("echo-strength-${card.noteId}"),
                    color = if (strong) c.textPrimary else c.textSecondary,
                    fontWeight = if (strong) FontWeight.SemiBold else null,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            if (onDismiss != null) {
                var about by remember { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { about = true }, modifier = Modifier.heightIn(min = 48.dp), colors = accentTextButtonColors()) {
                        Text(stringResource(R.string.echo_how))
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp), colors = accentTextButtonColors()) {
                        Text(stringResource(R.string.echo_dismiss))
                    }
                }
                if (about) EchoExplanation(card, onDismiss = { about = false })
            }
        }
    }
}

/** Exactly how the score was reached, in plain words. */
@Composable
fun EchoExplanation(card: EchoCard, onDismiss: () -> Unit) {
    val c = DropTheme.colors
    val w = EchoWeights()
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss, colors = accentTextButtonColors()) { Text(stringResource(R.string.dialog_ok)) } },
        title = { Text(stringResource(R.string.echo_how_title), color = c.textPrimary) },
        text = {
            Text(
                stringResource(
                    R.string.echo_how_body,
                    formatPoints(card.score),
                    formatPoints(EchoStrength.max(w)),
                    EchoStrength.percent(card.score, w),
                    formatPoints(w.maxKeywords),
                    formatPoints(w.sameProject),
                    formatPoints(w.canvasConnection),
                    formatPoints(w.wikiLink),
                    formatPoints(w.minScore),
                    formatPoints(EchoStrength.STRONG),
                ),
                color = c.textPrimary,
            )
        },
        containerColor = c.surfaceElevated,
    )
}

/**
 * Under a note: up to three notes ECHO thinks belong with it. Notes already linked either way
 * are left out, since the person connected them already. Shows nothing while ECHO is off.
 */
@Composable
fun RelatedNotesSection(noteId: Long, onOpenNote: (Long) -> Unit) {
    val c = DropTheme.colors
    val app = LocalContext.current.applicationContext as DropApplication
    val index by app.echo.index.collectAsStateWithLifecycle()
    val linkedFlow = remember(noteId) {
        combine(app.database.linkDao().observeOutgoing(noteId), app.database.linkDao().observeBacklinks(noteId)) { out, back ->
            out.mapTo(HashSet()) { it.targetId } + back.map { it.id }
        }
    }
    val linked by linkedFlow.collectAsStateWithLifecycle(initialValue = emptySet())
    var cards by remember { mutableStateOf(emptyList<EchoCard>()) }
    LaunchedEffect(index, noteId, linked) {
        val idx = index
        cards = if (idx == null) emptyList() else withContext(Dispatchers.Default) { idx.related(noteId, 3, linked).mapNotNull { idx.card(it) } }
    }
    if (cards.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = 20.dp).testTag("related-notes")) {
        Text(stringResource(R.string.echo_note_section), modifier = Modifier.semantics { heading() }, color = c.textSecondary, style = MaterialTheme.typography.labelLarge)
        Text(stringResource(R.string.echo_note_hint), modifier = Modifier.padding(bottom = 6.dp), color = c.textSecondary, style = MaterialTheme.typography.labelSmall)
        cards.forEach { card ->
            EchoSuggestionRow(
                card,
                onOpen = {
                    app.echoMemory.opened(card.noteId)
                    onOpenNote(card.noteId)
                },
                onDismiss = null,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
    }
}
