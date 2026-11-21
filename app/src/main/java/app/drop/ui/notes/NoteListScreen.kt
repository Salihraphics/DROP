package app.drop.ui.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.drop.DropApplication
import app.drop.R
import app.drop.data.Note
import app.drop.palette.CommandId
import app.drop.ui.icons.ActionIcons
import app.drop.ui.icons.PinIcons
import app.drop.ui.theme.DropSnackbarHost
import app.drop.ui.theme.DropTheme
import app.drop.ui.theme.accentTextButtonColors
import app.drop.ui.theme.destructiveTextButtonColors
import app.drop.ui.theme.dropTextFieldColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteListScreen(
    onOpen: (Long) -> Unit,
    onNew: (task: Boolean) -> Unit,
    onTrash: () -> Unit,
    onSettings: () -> Unit,
    /** Opens a space from the spaces menu; only spaces in [spaces] are offered. */
    onOpenSpace: (CommandId) -> Unit = {},
    spaces: Set<CommandId> = emptySet(),
    initialFilter: HomeFilter? = null,
    vm: NoteListViewModel = viewModel(factory = NoteListViewModel.Factory),
) {
    val c = DropTheme.colors
    val state by vm.state.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val selecting = selection.isNotEmpty()
    var searching by rememberSaveable { mutableStateOf(query.isNotEmpty()) }
    var showCompleted by rememberSaveable { mutableStateOf(true) }
    var menu by remember { mutableStateOf(false) }
    var confirmTrash by remember { mutableStateOf(false) }
    var bulkProject by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val formatter = rememberTimestampFormatter()

    LaunchedEffect(initialFilter) { initialFilter?.let(vm::onFilterChange) }
    BackHandler(enabled = selecting) { vm.clearSelection() }
    BackHandler(enabled = searching && !selecting) {
        searching = false
        vm.onQueryChange("")
    }

    Scaffold(
        modifier = Modifier.testTag("library"),
        containerColor = c.background,
        snackbarHost = { DropSnackbarHost(snackbar) },
        topBar = {
            if (selecting) {
                SelectionTopBar(
                    count = selection.size,
                    allPinned = vm.selectedNotes().let { it.isNotEmpty() && it.all { n -> n.pinned } },
                    onClear = vm::clearSelection,
                    onSelectAll = vm::selectAllVisible,
                    onPin = vm::setPinnedSelected,
                    onProject = { bulkProject = true },
                    onProperty = null,
                    onExport = null,
                    onTrash = { confirmTrash = true },
                )
            } else if (searching) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = c.background, titleContentColor = c.textPrimary),
                    navigationIcon = {
                        IconButton(onClick = {
                            searching = false
                            vm.onQueryChange("")
                        }) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.home_search_close), tint = c.textPrimary) }
                    },
                    title = {
                        OutlinedTextField(
                            value = query,
                            onValueChange = vm::onQueryChange,
                            placeholder = { Text(stringResource(R.string.home_search_hint)) },
                            singleLine = true,
                            colors = dropTextFieldColors(),
                            shape = RoundedCornerShape(24.dp),
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { vm.onQueryChange("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.home_search_clear), tint = c.textSecondary)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(end = 8.dp).testTag("search-field"),
                        )
                    },
                )
            } else {
                HomeTopBar(
                    onSearch = { searching = true },
                    onOpenSpace = onOpenSpace,
                    spaces = spaces,
                    onOpenTrash = onTrash,
                    onOpenSettings = onSettings,
                )
            }
        },
        floatingActionButton = {
            if (!selecting) {
                val task = filter == HomeFilter.Tasks
                FloatingActionButton(
                    onClick = { onNew(task) },
                    containerColor = c.accent,
                    contentColor = c.onAccent,
                    modifier = Modifier.testTag("new-note"),
                ) { Icon(Icons.Default.Add, contentDescription = stringResource(if (task) R.string.task_new else R.string.note_new)) }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(HomeFilter.entries) { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { vm.onFilterChange(f) },
                        label = {
                            Text(
                                stringResource(
                                    when (f) {
                                        HomeFilter.All -> R.string.filter_all
                                        HomeFilter.Tasks -> R.string.filter_tasks
                                        HomeFilter.Links -> R.string.filter_links
                                        HomeFilter.Pinned -> R.string.filter_pinned
                                    },
                                ),
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = c.accentSubtle,
                            selectedLabelColor = c.textPrimary,
                            labelColor = c.textSecondary,
                        ),
                        modifier = Modifier.testTag("filter-${f.name}"),
                    )
                }
            }
            val s = state ?: return@Column
            if (s.isEmpty) {
                EmptyLibrary(s, query)
                return@Column
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                fun cards(notes: List<Note>) = items(notes, key = { it.id }) { note ->
                    val hit = s.ocrHits[note.id]
                    NoteCard(
                        note = note,
                        meta = formatter.format(note.updatedAt),
                        query = s.query,
                        selected = if (selecting) note.id in selection else null,
                        onClick = { if (selecting) vm.toggleSelected(note.id) else onOpen(note.id) },
                        onLongClick = { vm.toggleSelected(note.id) },
                        onCompletedChange = { vm.setCompleted(note.id, it) },
                    ) {
                        if (hit != null) {
                            Text(stringResource(R.string.search_in_scanned_text), style = MaterialTheme.typography.labelSmall, color = c.accentContent)
                            Text(highlighted(hit.snippet.text, s.query), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 2)
                        }
                    }
                }
                if (s.pinned.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.home_section_pinned)) }
                    cards(s.pinned)
                    if (s.others.isNotEmpty()) item { SectionHeader(stringResource(R.string.home_section_others)) }
                }
                cards(s.others)
                if (s.completed.isNotEmpty()) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SectionHeader(stringResource(R.string.home_section_completed, s.completed.size), Modifier.weight(1f))
                            TextButton(onClick = { showCompleted = !showCompleted }, colors = accentTextButtonColors()) {
                                Text(stringResource(if (showCompleted) R.string.home_hide else R.string.home_show))
                            }
                        }
                    }
                    if (showCompleted) cards(s.completed)
                }
            }
        }
    }
    if (bulkProject) {
        val app = LocalContext.current.applicationContext as DropApplication
        val done = stringResource(R.string.bulk_done)
        val ids = selection.toList()
        BulkProjectDialog(
            count = ids.size,
            onAdd = { projectId, newName ->
                bulkProject = false
                scope.launch {
                    app.bulk.addToProject(ids, projectId, newName)
                    snackbar.showSnackbar(done)
                }
            },
            onRemove = { projectId ->
                bulkProject = false
                scope.launch {
                    app.bulk.removeFromProject(ids, projectId)
                    snackbar.showSnackbar(done)
                }
            },
            onDismiss = { bulkProject = false },
        )
    }
    if (confirmTrash) {
        val n = selection.size
        AlertDialog(
            onDismissRequest = { confirmTrash = false },
            title = { Text(pluralStringResource(R.plurals.bulk_trash_title, n, n)) },
            text = { Text(stringResource(R.string.bulk_trash_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmTrash = false
                    scope.launch {
                        val (ids, at) = vm.trashSelected() ?: return@launch
                        val result = snackbar.showSnackbar(
                            context.resources.getQuantityString(R.plurals.bulk_trashed, ids.size, ids.size),
                            actionLabel = context.getString(R.string.home_undo),
                            duration = SnackbarDuration.Long,
                        )
                        if (result == SnackbarResult.ActionPerformed) vm.untrash(ids, at)
                    }
                }, colors = destructiveTextButtonColors()) { Text(stringResource(R.string.home_trash)) }
            },
            dismissButton = { TextButton(onClick = { confirmTrash = false }, colors = accentTextButtonColors()) { Text(stringResource(R.string.dialog_cancel)) } },
        )
    }
}

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = DropTheme.colors.textSecondary, modifier = modifier.padding(top = 8.dp))
}

@Composable
private fun EmptyLibrary(s: HomeState, query: String) {
    val c = DropTheme.colors
    val (title, body) = when {
        query.isNotBlank() -> stringResource(R.string.home_search_no_results, query) to null
        s.filter == HomeFilter.Tasks -> stringResource(R.string.empty_tasks_title) to stringResource(R.string.empty_tasks_body)
        s.filter == HomeFilter.Links -> stringResource(R.string.empty_links_title) to stringResource(R.string.empty_links_body)
        s.filter == HomeFilter.Pinned -> stringResource(R.string.empty_pinned_title) to stringResource(R.string.empty_pinned_body)
        s.totalCount > 0 -> stringResource(R.string.empty_all_done_title) to stringResource(R.string.empty_all_done_body)
        else -> stringResource(R.string.home_empty_title) to stringResource(R.string.home_empty_body)
    }
    Box(Modifier.fillMaxSize().padding(32.dp).testTag("library-empty"), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = c.textPrimary, textAlign = TextAlign.Center)
            body?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, textAlign = TextAlign.Center) }
        }
    }
}

/** The Library's bar: DROP, search, the spaces menu, Trash and Settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    onSearch: () -> Unit,
    onOpenSpace: (CommandId) -> Unit,
    spaces: Set<CommandId>,
    onOpenTrash: () -> Unit,
    onOpenSettings: () -> Unit,
    showSearch: Boolean = true,
) {
    val c = DropTheme.colors
    var spacesOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium) },
        actions = {
            if (showSearch) {
                IconButton(onClick = onSearch, modifier = Modifier.testTag("search")) { Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.home_search)) }
            }
            Box {
                IconButton(onClick = { spacesOpen = true }, modifier = Modifier.testTag("spaces")) { Icon(ActionIcons.Spaces, contentDescription = stringResource(R.string.spaces_menu)) }
                DropdownMenu(expanded = spacesOpen, onDismissRequest = { spacesOpen = false }, containerColor = c.surfaceElevated) {
                    listOf(
                        CommandId.Projects to R.string.projects_title,
                        CommandId.DailyLog to R.string.daily_title,
                        CommandId.Canvas to R.string.canvas_title,
                        CommandId.Templates to R.string.templates_title,
                        CommandId.Collections to R.string.collections_title,
                        CommandId.Health to R.string.health_title,
                        CommandId.Portability to R.string.portability_title,
                    ).filter { it.first in spaces }.forEach { (id, label) ->
                        DropdownMenuItem(
                            text = { Text(stringResource(label), color = c.textPrimary) },
                            onClick = {
                                spacesOpen = false
                                onOpenSpace(id)
                            },
                            modifier = Modifier.testTag("space-${id.name}"),
                        )
                    }
                }
            }
            IconButton(onClick = onOpenTrash) { Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.home_trash)) }
            IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.home_settings)) }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = c.background,
            titleContentColor = c.textPrimary,
            actionIconContentColor = c.textPrimary,
        ),
    )
}
