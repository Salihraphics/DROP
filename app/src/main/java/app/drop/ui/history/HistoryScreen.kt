package app.drop.ui.history

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.drop.R
import app.drop.data.NoteRevision
import app.drop.data.RevisionSummary
import app.drop.history.DiffLine
import app.drop.history.LineDiff
import app.drop.history.NoteHistory
import app.drop.richtext.DropMarkdown
import app.drop.ui.theme.DropTheme
import app.drop.ui.theme.accentTextButtonColors
import app.drop.ui.theme.noteTextStyle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

const val HISTORY_TAG = "history"
const val PREVIEW_TAG = "history-preview"

/**
 * A note's earlier versions, full screen over the editor. Opening one shows it, or what changed
 * since; [onRestore] puts it back (the editor keeps the current text as a version first).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    history: NoteHistory,
    noteId: Long,
    currentTitle: String,
    currentText: String,
    onRestore: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = DropTheme.colors
    val context = LocalContext.current
    val locale = context.resources.configuration.locales[0]
    val formatter = remember(locale) {
        val skeleton = if (DateFormat.is24HourFormat(context)) "yMMMdHm" else "yMMMdhma"
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale).withZone(ZoneId.systemDefault())
    }
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    val revisions by remember(noteId) { history.observe(noteId) }.collectAsStateWithLifecycle(initialValue = null)
    val back = { if (selected != null) selected = null else onDismiss() }

    Dialog(onDismissRequest = back, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler(onBack = back)
        Scaffold(
            modifier = Modifier.fillMaxSize().testTag(HISTORY_TAG),
            containerColor = c.background,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.history_title)) },
                    navigationIcon = {
                        IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.note_back)) }
                    },
                    actions = {
                        if (selected != null) {
                            TextButton(onClick = { confirm = true }, colors = accentTextButtonColors(), modifier = Modifier.testTag("history-restore")) {
                                Text(stringResource(R.string.history_restore))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = c.background,
                        titleContentColor = c.textPrimary,
                        navigationIconContentColor = c.textPrimary,
                    ),
                )
            },
        ) { padding ->
            val id = selected
            if (id == null) {
                RevisionList(revisions, formatter, padding, onOpen = { selected = it })
            } else {
                RevisionDetail(history, noteId, id, currentTitle, currentText, formatter, onMissing = { selected = null }, modifier = Modifier.padding(padding))
            }
        }
        if (confirm) {
            AlertDialog(
                onDismissRequest = { confirm = false },
                confirmButton = {
                    TextButton(onClick = {
                        confirm = false
                        selected?.let(onRestore)
                    }, colors = accentTextButtonColors(), modifier = Modifier.testTag("history-restore-confirm")) { Text(stringResource(R.string.history_restore)) }
                },
                dismissButton = { TextButton(onClick = { confirm = false }, colors = accentTextButtonColors()) { Text(stringResource(R.string.dialog_cancel)) } },
                title = { Text(stringResource(R.string.history_restore_title)) },
                text = { Text(stringResource(R.string.history_restore_body)) },
                containerColor = c.surfaceElevated,
                titleContentColor = c.textPrimary,
                textContentColor = c.textSecondary,
            )
        }
    }
}

@Composable
private fun RevisionList(list: List<RevisionSummary>?, formatter: DateTimeFormatter, padding: PaddingValues, onOpen: (Long) -> Unit) {
    val c = DropTheme.colors
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        item("explain") {
            Text(
                stringResource(R.string.history_explain, (NoteHistory.INTERVAL_MS / 60_000).toInt(), NoteHistory.KEEP_PER_NOTE),
                modifier = Modifier.padding(bottom = 12.dp),
                color = c.textSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (list != null && list.isEmpty()) {
            item("empty") { Text(stringResource(R.string.history_empty), color = c.textSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("history-empty")) }
        }
        items(list.orEmpty(), key = { it.id }) { r ->
            val title = r.title.ifBlank { stringResource(R.string.history_untitled) }
            Column(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onOpen(r.id) }.padding(vertical = 10.dp).testTag("revision-${r.id}"),
            ) {
                Text(formatter.format(Instant.ofEpochMilli(r.editedAt)), color = c.textPrimary, style = MaterialTheme.typography.bodyLarge)
                Text(pluralStringResource(R.plurals.history_row, r.length, title, r.length), color = c.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            HorizontalDivider(color = c.border)
        }
    }
}

@Composable
private fun RevisionDetail(
    history: NoteHistory,
    noteId: Long,
    revisionId: Long,
    currentTitle: String,
    currentText: String,
    formatter: DateTimeFormatter,
    onMissing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = DropTheme.colors
    var revision by remember(revisionId) { mutableStateOf<NoteRevision?>(null) }
    LaunchedEffect(revisionId) {
        revision = history.get(noteId, revisionId)
        if (revision == null) onMissing()
    }
    var showChanges by rememberSaveable(revisionId) { mutableStateOf(false) }
    val r = revision ?: return
    val text = remember(r) { DropMarkdown.plainOf(r.body, r.format) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp).testTag(PREVIEW_TAG)) {
        Text(stringResource(R.string.history_version_of, formatter.format(Instant.ofEpochMilli(r.editedAt))), color = c.textSecondary, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            @Composable
            fun Chip(selected: Boolean, label: Int, tag: String, onClick: () -> Unit) = FilterChip(
                selected = selected,
                onClick = onClick,
                label = { Text(stringResource(label)) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = c.background,
                    labelColor = c.textSecondary,
                    selectedContainerColor = c.accentSubtle,
                    selectedLabelColor = c.textPrimary,
                ),
                modifier = Modifier.testTag(tag),
            )
            Chip(!showChanges, R.string.history_show_version, "history-show-version") { showChanges = false }
            Chip(showChanges, R.string.history_show_changes, "history-show-changes") { showChanges = true }
        }
        Spacer(Modifier.height(12.dp))
        if (!showChanges) {
            Text(r.title.ifBlank { stringResource(R.string.history_untitled) }, color = c.textPrimary, style = noteTextStyle(MaterialTheme.typography.headlineSmall))
            Spacer(Modifier.height(8.dp))
            Text(text, color = c.textPrimary, style = noteTextStyle(MaterialTheme.typography.bodyLarge))
        } else {
            val old = remember(r) { r.title + "\n" + text }
            val diff = remember(r, currentTitle, currentText) { LineDiff.of(old, currentTitle + "\n" + currentText) }
            when {
                diff == null -> Text(stringResource(R.string.history_too_long), color = c.textSecondary, style = MaterialTheme.typography.bodyMedium)
                diff.all { it.kind == DiffLine.Kind.Same } -> Text(stringResource(R.string.history_same), color = c.textSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("history-same"))
                else -> {
                    Text(stringResource(R.string.history_legend), color = c.textSecondary, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    diff.forEach { DiffRow(it) }
                }
            }
        }
    }
}

@Composable
private fun DiffRow(line: DiffLine) {
    val c = DropTheme.colors
    val (mark, label) = when (line.kind) {
        DiffLine.Kind.Same -> "  " to line.text
        DiffLine.Kind.Removed -> "− " to stringResource(R.string.history_removed_line, line.text)
        DiffLine.Kind.Added -> "+ " to stringResource(R.string.history_added_line, line.text)
    }
    val background = when (line.kind) {
        DiffLine.Kind.Same -> c.background
        DiffLine.Kind.Removed -> c.error.copy(alpha = 0.12f)
        DiffLine.Kind.Added -> c.success.copy(alpha = 0.14f)
    }
    Text(
        mark + line.text.ifEmpty { " " },
        modifier = Modifier.fillMaxWidth().background(background).padding(horizontal = 6.dp, vertical = 2.dp)
            .clearAndSetSemantics { contentDescription = label }
            .testTag("diff-${line.kind.name}"),
        color = c.textPrimary,
        style = noteTextStyle(MaterialTheme.typography.bodyMedium),
    )
}
