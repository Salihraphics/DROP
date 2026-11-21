package app.drop.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.drop.DropApplication
import app.drop.R
import app.drop.data.Note
import app.drop.data.ProjectSummary
import app.drop.data.TaskWithTime
import app.drop.experience.DashboardSection
import app.drop.experience.dashboardSections
import app.drop.richtext.DropMarkdown
import app.drop.ui.echo.EchoCard
import app.drop.ui.echo.EchoSuggestionRow
import app.drop.ui.icons.ActionIcons
import app.drop.ui.icons.FormatIcons
import app.drop.ui.notes.NoteCard
import app.drop.ui.notes.cardTitle
import app.drop.ui.notes.rememberTimestampFormatter
import app.drop.ui.theme.DropLogoTile
import app.drop.ui.theme.DropSnackbarHost
import app.drop.ui.theme.DropTheme
import app.drop.ui.theme.accentTextButtonColors
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** What the Dashboard can open. */
data class DashboardActions(
    val openNote: (Long) -> Unit,
    val newItem: (task: Boolean) -> Unit,
    val search: () -> Unit,
    val openTasks: () -> Unit,
    val openLibrary: () -> Unit,
    val openTrash: () -> Unit,
    val openSettings: () -> Unit,
    val openProject: (Long) -> Unit = {},
    /** Opens the project list; with true, straight into creating the first project. */
    val openProjects: (create: Boolean) -> Unit = {},
)

@Composable
fun DashboardScreen(actions: DashboardActions, vm: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory)) {
    val c = DropTheme.colors
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val formatter = rememberTimestampFormatter()
    val echo by vm.echo.collectAsStateWithLifecycle()
    val personalization by (context.applicationContext as DropApplication).settings.personalization.collectAsStateWithLifecycle()
    val echoHidden = stringResource(R.string.echo_dismissed)

    fun complete(id: Long) {
        vm.setCompleted(id, true)
        scope.launch {
            val result = snackbar.showSnackbar(
                context.getString(R.string.dashboard_task_done_where),
                actionLabel = context.getString(R.string.home_undo),
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) vm.setCompleted(id, false)
        }
    }

    Scaffold(modifier = Modifier.testTag("dashboard"), containerColor = c.background, snackbarHost = { DropSnackbarHost(snackbar) }) { padding ->
        val s = state ?: return@Scaffold
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DropLogoTile(36.dp)
                    Spacer(Modifier.width(12.dp))
                    Surface(
                        onClick = actions.search,
                        shape = RoundedCornerShape(24.dp),
                        color = c.surface,
                        border = BorderStroke(1.dp, c.border),
                        modifier = Modifier.weight(1f).testTag("dashboard-search"),
                    ) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = c.textSecondary)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.dashboard_search), color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    IconButton(onClick = actions.openSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.home_settings), tint = c.textPrimary)
                    }
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Capture(Icons.Default.Add, R.string.dashboard_capture_note, "capture-note") { actions.newItem(false) } }
                    item { Capture(FormatIcons.Checklist, R.string.dashboard_capture_task, "capture-task") { actions.newItem(true) } }
                }
            }
            if (s.isFirstUse) {
                item {
                    Section(stringResource(R.string.dashboard_first_action)) {
                        Text(stringResource(R.string.dashboard_first_more), color = c.textSecondary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                return@LazyColumn
            }
            item { TasksSection(s, actions, onComplete = ::complete, onReopen = { vm.setCompleted(it, false) }) }
            for (section in dashboardSections(personalization)) {
                when (section) {
                    DashboardSection.Daily -> item(key = "daily") { DailySection(s.daily) }
                    DashboardSection.Recent -> item(key = "recent") {
                Section(stringResource(R.string.dashboard_recent), action = stringResource(R.string.dashboard_view_all) to actions.openLibrary) {
                    if (s.recent.isEmpty()) Text(stringResource(R.string.dashboard_recent_empty), color = c.textSecondary)
                    s.recent.forEach { note ->
                        NoteCard(note, meta = formatter.format(note.updatedAt), onClick = { actions.openNote(note.id) })
                    }
                }
            }
                    DashboardSection.Projects -> item(key = "projects") { ProjectsSection(s.projects, s.shownProjects, actions) }
                    DashboardSection.Echo -> if (echo.isNotEmpty()) {
                        item(key = "echo") {
                            EchoSection(
                                echo,
                                onOpen = { id ->
                                    vm.openedEcho(id)
                                    actions.openNote(id)
                                },
                                onDismiss = { id ->
                                    vm.dismissEcho(id)
                                    scope.launch { snackbar.showSnackbar(echoHidden) }
                                },
                            )
                        }
                    }
                }
            }
            s.nextReminder?.let { next ->
                item {
                    Section(stringResource(R.string.dashboard_next_resurface)) {
                        val title = next.title.ifBlank { DropMarkdown.previewOf(next.body, next.format).lineSequence().firstOrNull().orEmpty() }
                        Text(title.ifBlank { stringResource(R.string.note_empty) }, color = c.textPrimary, modifier = Modifier.clickable { actions.openNote(next.noteId) })
                        Text(next.localTime.replace('T', ' '), color = c.textSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun Capture(icon: ImageVector, label: Int, tag: String, onClick: () -> Unit) {
    val c = DropTheme.colors
    AssistChip(
        onClick = onClick,
        label = { Text(stringResource(label)) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        colors = AssistChipDefaults.assistChipColors(containerColor = c.surface, labelColor = c.textPrimary, leadingIconContentColor = c.accentContent),
        border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = c.border),
        modifier = Modifier.testTag(tag),
    )
}

@Composable
private fun Section(
    title: String,
    action: Pair<String, () -> Unit>? = null,
    highlighted: Boolean = false,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = DropTheme.colors
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (highlighted) c.accentSubtle else c.surface,
        border = BorderStroke(1.dp, if (highlighted) c.accentContent.copy(alpha = 0.5f) else c.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
                action?.let { (label, onClick) -> TextButton(onClick = onClick, colors = accentTextButtonColors()) { Text(label) } }
            }
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.textSecondary) }
            content()
        }
    }
}

@Composable
private fun ProjectsSection(all: List<ProjectSummary>, shown: List<ProjectSummary>, actions: DashboardActions) {
    val c = DropTheme.colors
    Section(
        stringResource(R.string.projects_title),
        action = stringResource(if (all.isEmpty()) R.string.projects_new else R.string.dashboard_all) to { actions.openProjects(all.isEmpty()) },
    ) {
        if (all.isEmpty()) Text(stringResource(R.string.projects_empty_body), color = c.textSecondary, style = MaterialTheme.typography.bodyMedium)
        shown.forEach { p ->
            Surface(onClick = { actions.openProject(p.id) }, modifier = Modifier.fillMaxWidth().testTag("dashboard-project-${p.id}"), color = c.surface) {
                Row(Modifier.heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(ActionIcons.Spaces, contentDescription = null, modifier = Modifier.size(18.dp), tint = c.textSecondary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                        if (p.description.isNotBlank()) {
                            Text(p.description, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Text(pluralStringResource(R.plurals.projects_items, p.itemCount, p.itemCount), color = c.textSecondary, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** ECHO's rediscoveries: older notes that fit what was written lately. */
@Composable
private fun EchoSection(cards: List<EchoCard>, onOpen: (Long) -> Unit, onDismiss: (Long) -> Unit) {
    Section(stringResource(R.string.echo_title), highlighted = true, subtitle = stringResource(R.string.echo_subtitle)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            cards.forEach { card -> EchoSuggestionRow(card, onOpen = { onOpen(card.noteId) }, onDismiss = { onDismiss(card.noteId) }) }
        }
    }
}

/** Open tasks by day: due, today, tomorrow, later and undated, and what was completed today. */
@Composable
internal fun TasksSection(s: DashboardState, actions: DashboardActions, onComplete: (Long) -> Unit, onReopen: (Long) -> Unit) {
    val c = DropTheme.colors
    var showDone by rememberSaveable { mutableStateOf(false) }
    Section(stringResource(R.string.dashboard_tasks), action = stringResource(R.string.dashboard_view_all) to actions.openTasks) {
        Text(taskSummary(s), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.testTag("task-summary"))
        if (s.tasks.isEmpty()) {
            Text(
                stringResource(if (s.daily.completedTasks.isEmpty()) R.string.dashboard_no_tasks else R.string.dashboard_all_done),
                color = c.textSecondary,
            )
        }
        s.tasks.groupBy { it.whenDue }.toSortedMap().forEach { (whenDue, tasks) ->
            GroupLabel(groupTitle(whenDue))
            tasks.forEach { TaskRow(it, onOpen = actions.openNote, onComplete = onComplete) }
        }
        val done = s.daily.completedTasks
        if (done.isNotEmpty()) {
            TextButton(onClick = { showDone = !showDone }, colors = accentTextButtonColors(), modifier = Modifier.testTag("completed-today")) {
                Text(pluralStringResource(R.plurals.dashboard_completed_today, done.size, done.size))
            }
            if (showDone) done.forEach { CompletedRow(it, onOpen = actions.openNote, onReopen = onReopen) }
        }
    }
}

@Composable
private fun taskSummary(s: DashboardState): String {
    val k = s.counts
    val parts = buildList {
        add(pluralStringResource(R.plurals.dashboard_count_open, k.open, k.open))
        if (k.due > 0) add(pluralStringResource(R.plurals.dashboard_count_due, k.due, k.due))
        if (k.today > 0) add(pluralStringResource(R.plurals.dashboard_count_today, k.today, k.today))
        if (k.tomorrow > 0) add(pluralStringResource(R.plurals.dashboard_count_tomorrow, k.tomorrow, k.tomorrow))
        if (k.upcoming > 0) add(pluralStringResource(R.plurals.dashboard_count_upcoming, k.upcoming, k.upcoming))
    }
    return parts.joinToString(" · ")
}

@Composable
private fun groupTitle(whenDue: TaskWhen): String = stringResource(
    when (whenDue) {
        TaskWhen.Due -> R.string.dashboard_group_due
        TaskWhen.Today -> R.string.dashboard_group_today
        TaskWhen.Tomorrow -> R.string.dashboard_group_tomorrow
        TaskWhen.Upcoming -> R.string.dashboard_group_later
        TaskWhen.None -> R.string.dashboard_group_no_date
    },
)

@Composable
private fun GroupLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = DropTheme.colors.accentContent)
}

private fun taskTitle(task: TaskWithTime): String =
    task.title.ifBlank { DropMarkdown.previewOf(task.body, task.format).lineSequence().firstOrNull().orEmpty() }.trim()

@Composable
private fun TaskRow(task: DashboardTask, onOpen: (Long) -> Unit, onComplete: (Long) -> Unit) {
    val c = DropTheme.colors
    val title = taskTitle(task.task).ifEmpty { stringResource(R.string.note_empty) }
    val label = stringResource(R.string.task_done_toggle, title)
    Row(Modifier.fillMaxWidth().clickable { onOpen(task.task.id) }.testTag("task-${task.task.id}"), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = false,
            onCheckedChange = { if (it) onComplete(task.task.id) },
            colors = CheckboxDefaults.colors(uncheckedColor = c.textSecondary, checkedColor = c.accent, checkmarkColor = c.onAccent),
            modifier = Modifier.semantics { contentDescription = label },
        )
        Column(Modifier.weight(1f)) {
            Text(title, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            task.time?.let { time ->
                val text = if (task.whenDue == TaskWhen.Today || task.whenDue == TaskWhen.Due && time.toLocalDate() == java.time.LocalDate.now()) {
                    time.toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
                } else {
                    time.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
                }
                Text(text, style = MaterialTheme.typography.bodySmall, color = if (task.whenDue == TaskWhen.Due) c.warning else c.textSecondary)
            }
        }
    }
}

@Composable
private fun CompletedRow(note: Note, onOpen: (Long) -> Unit, onReopen: (Long) -> Unit) {
    val c = DropTheme.colors
    val title = cardTitle(note).ifEmpty { stringResource(R.string.note_empty) }
    val label = stringResource(R.string.task_reopen_toggle, title)
    Row(Modifier.fillMaxWidth().clickable { onOpen(note.id) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = true,
            onCheckedChange = { if (!it) onReopen(note.id) },
            colors = CheckboxDefaults.colors(checkedColor = c.accent, checkmarkColor = c.onAccent),
            modifier = Modifier.semantics { contentDescription = label },
        )
        Text(title, color = c.textSecondary, textDecoration = TextDecoration.LineThrough, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DailySection(day: DailySummary) {
    val c = DropTheme.colors
    Section(stringResource(R.string.daily_title)) {
        if (day.isEmpty) {
            Text(stringResource(R.string.dashboard_daily_empty), color = c.textSecondary)
        } else {
            day.entry?.takeIf { it.isNotBlank() }?.let { Text(it, color = c.textPrimary, maxLines = 3, overflow = TextOverflow.Ellipsis) }
            Text(
                listOf(
                    pluralStringResource(R.plurals.dashboard_daily_created, day.created, day.created),
                    pluralStringResource(R.plurals.dashboard_daily_completed, day.completed, day.completed),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
            )
        }
    }
}
