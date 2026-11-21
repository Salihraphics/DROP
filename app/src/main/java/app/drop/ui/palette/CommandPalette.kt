package app.drop.ui.palette

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.drop.R
import app.drop.palette.Command
import app.drop.palette.CommandId
import app.drop.palette.ENGLISH_KEYWORDS
import app.drop.palette.filterCommands
import app.drop.ui.icons.ActionIcons
import app.drop.ui.icons.FormatIcons
import app.drop.ui.icons.PinIcons
import app.drop.ui.theme.DropTheme

private data class CommandText(val label: Int, val keywords: Int, val hint: Int? = null)

private val TEXT: Map<CommandId, CommandText> = mapOf(
    CommandId.NewNote to CommandText(R.string.cmd_new_note, R.string.cmd_new_note_keywords),
    CommandId.NewTask to CommandText(R.string.cmd_new_task, R.string.cmd_new_task_keywords),
    CommandId.VoiceDump to CommandText(R.string.cmd_voice, R.string.cmd_voice_keywords),
    CommandId.PaperCapture to CommandText(R.string.cmd_paper, R.string.cmd_paper_keywords),
    CommandId.Search to CommandText(R.string.cmd_search, R.string.cmd_search_keywords),
    CommandId.ShowAll to CommandText(R.string.cmd_show_all, R.string.cmd_show_all_keywords),
    CommandId.ShowTasks to CommandText(R.string.cmd_show_tasks, R.string.cmd_show_tasks_keywords),
    CommandId.ShowLinks to CommandText(R.string.cmd_show_links, R.string.cmd_show_links_keywords),
    CommandId.ShowPinned to CommandText(R.string.cmd_show_pinned, R.string.cmd_show_pinned_keywords),
    CommandId.Resurface to CommandText(R.string.cmd_resurface, R.string.cmd_resurface_keywords),
    CommandId.Projects to CommandText(R.string.cmd_projects, R.string.cmd_projects_keywords),
    CommandId.DailyLog to CommandText(R.string.cmd_daily, R.string.cmd_daily_keywords),
    CommandId.Canvas to CommandText(R.string.cmd_canvas, R.string.cmd_canvas_keywords),
    CommandId.Trash to CommandText(R.string.cmd_trash, R.string.cmd_trash_keywords),
    CommandId.Settings to CommandText(R.string.cmd_settings, R.string.cmd_settings_keywords),
    CommandId.Vault to CommandText(R.string.cmd_vault, R.string.cmd_vault_keywords, R.string.cmd_vault_hint),
    CommandId.NewFromTemplate to CommandText(R.string.new_from_template, R.string.cmd_new_from_template_keywords),
    CommandId.Templates to CommandText(R.string.templates_title, R.string.cmd_templates_keywords),
    CommandId.Collections to CommandText(R.string.collections_title, R.string.cmd_collections_keywords),
    CommandId.Health to CommandText(R.string.health_title, R.string.cmd_health_keywords),
    CommandId.Portability to CommandText(R.string.portability_title, R.string.cmd_portability_keywords),
)

fun CommandId.icon(): ImageVector = when (this) {
    CommandId.NewNote -> Icons.Filled.Add
    CommandId.NewTask, CommandId.ShowTasks -> Icons.Filled.CheckCircle
    CommandId.VoiceDump -> ActionIcons.Mic
    CommandId.PaperCapture -> ActionIcons.Document
    CommandId.Search -> Icons.Filled.Search
    CommandId.ShowAll -> Icons.Filled.Home
    CommandId.ShowLinks -> ActionIcons.Link
    CommandId.ShowPinned -> PinIcons.Outlined
    CommandId.Resurface -> ActionIcons.Schedule
    CommandId.Projects, CommandId.Canvas -> ActionIcons.Spaces
    CommandId.DailyLog -> ActionIcons.Schedule
    CommandId.Trash -> Icons.Outlined.Delete
    CommandId.Settings -> Icons.Filled.Settings
    CommandId.Vault -> Icons.Filled.Lock
    CommandId.NewFromTemplate, CommandId.Templates -> FormatIcons.Template
    CommandId.Collections -> FormatIcons.Filter
    CommandId.Health -> FormatIcons.Health
    CommandId.Portability -> FormatIcons.Export
}

/** Every command in the phone's language, with its local and English keywords. */
@Composable
private fun rememberCommands(available: Set<CommandId>): List<Command> {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(configuration, available) {
        CommandId.entries.filter { it in available }.map { id ->
            val text = TEXT.getValue(id)
            val local = context.getString(text.keywords).split(',').map { it.trim() }
            Command(id, context.getString(text.label), local + ENGLISH_KEYWORDS[id].orEmpty())
        }
    }
}

/**
 * Type to find any action or place in DROP. Arrow keys move, Enter runs, Esc closes. Only the
 * commands in [available] are offered.
 */
@Composable
fun CommandPalette(onRun: (CommandId) -> Unit, onDismiss: () -> Unit, available: Set<CommandId> = CommandId.entries.toSet()) {
    val c = DropTheme.colors
    val commands = rememberCommands(available)
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableIntStateOf(0) }
    val results = remember(commands, query) { filterCommands(commands, query) }
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(query) { selected = 0 }
    LaunchedEffect(selected, results) {
        if (results.isNotEmpty()) listState.animateScrollToItem(selected.coerceIn(results.indices))
    }
    val run: (Command) -> Unit = { command ->
        onDismiss()
        onRun(command.id)
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LaunchedEffect(Unit) { focus.requestFocus() }
        Box(Modifier.fillMaxSize().imePadding().padding(16.dp), contentAlignment = Alignment.TopCenter) {
            Surface(
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(top = 48.dp).testTag("palette"),
                shape = RoundedCornerShape(20.dp),
                color = c.surfaceElevated,
                border = BorderStroke(1.dp, c.border),
            ) {
                Column {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(ActionIcons.Bolt, contentDescription = null, modifier = Modifier.size(20.dp), tint = c.textSecondary)
                        Spacer(Modifier.width(12.dp))
                        val style = MaterialTheme.typography.titleMedium.copy(color = c.textPrimary)
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focus)
                                .testTag("palette-input")
                                .onPreviewKeyEvent { e ->
                                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    when (e.key) {
                                        Key.DirectionDown -> {
                                            if (results.isNotEmpty()) selected = (selected + 1).coerceAtMost(results.lastIndex)
                                            true
                                        }
                                        Key.DirectionUp -> {
                                            selected = (selected - 1).coerceAtLeast(0)
                                            true
                                        }
                                        Key.Enter, Key.NumPadEnter -> {
                                            results.getOrNull(selected)?.let(run)
                                            true
                                        }
                                        Key.Escape -> {
                                            onDismiss()
                                            true
                                        }
                                        else -> false
                                    }
                                },
                            textStyle = style,
                            singleLine = true,
                            cursorBrush = SolidColor(c.accentContent),
                            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { results.getOrNull(selected)?.let(run) }),
                            decorationBox = { inner ->
                                Box {
                                    if (query.isEmpty()) Text(stringResource(R.string.palette_hint), style = style, color = c.textSecondary)
                                    inner()
                                }
                            },
                        )
                    }
                    HorizontalDivider(color = c.border)
                    if (results.isEmpty()) {
                        Text(stringResource(R.string.palette_no_results, query.trim()), modifier = Modifier.padding(16.dp), color = c.textSecondary)
                    }
                    LazyColumn(Modifier.heightIn(max = 440.dp), state = listState) {
                        itemsIndexed(results, key = { _, it -> it.id }) { i, command -> CommandRow(command, highlighted = i == selected) { run(command) } }
                    }
                    HorizontalDivider(color = c.border)
                    Text(
                        stringResource(R.string.palette_shortcut_hint),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        color = c.textSecondary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun CommandRow(command: Command, highlighted: Boolean, onClick: () -> Unit) {
    val c = DropTheme.colors
    val hint = TEXT.getValue(command.id).hint
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics {
                selected = highlighted
                role = Role.Button
            }
            .testTag("palette-${command.id.name}"),
        color = if (highlighted) c.accentSubtle else c.surfaceElevated,
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(command.id.icon(), contentDescription = null, modifier = Modifier.size(22.dp), tint = c.textSecondary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(command.label, color = c.textPrimary, style = MaterialTheme.typography.bodyLarge)
                if (hint != null) Text(stringResource(hint), color = c.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
