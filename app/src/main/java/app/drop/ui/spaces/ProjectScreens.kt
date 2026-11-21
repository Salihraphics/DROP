package app.drop.ui.spaces

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.drop.DropApplication
import app.drop.R
import app.drop.data.Note
import app.drop.data.NoteDao
import app.drop.data.Project
import app.drop.data.ProjectDao
import app.drop.data.ProjectItem
import app.drop.data.ProjectSummary
import app.drop.ui.notes.NoteCard
import app.drop.ui.notes.rememberTimestampFormatter
import app.drop.ui.theme.DropTheme
import app.drop.ui.theme.accentTextButtonColors
import app.drop.ui.theme.destructiveTextButtonColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProjectsViewModel(private val dao: ProjectDao, private val clock: () -> Long = System::currentTimeMillis) : ViewModel() {
    /** Null until loaded. */
    val projects: StateFlow<List<ProjectSummary>?> = dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun create(name: String, description: String, then: (Long) -> Unit = {}) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val now = clock()
            then(dao.insert(Project(name = name.trim(), description = description.trim(), createdAt = now, updatedAt = now)))
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DropApplication
                ProjectsViewModel(app.database.projectDao())
            }
        }
    }
}

/** One project: its notes, renaming, adding and removing notes, deleting it (the notes stay). */
class ProjectViewModel(
    handle: SavedStateHandle,
    private val dao: ProjectDao,
    notes: NoteDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    val id: Long = handle.get<Long>(ARG_ID) ?: -1L
    val project: StateFlow<Project?> = dao.observe(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val items: StateFlow<List<Note>?> = dao.observeItems(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val allNotes: StateFlow<List<Note>> = notes.observeActive().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun rename(name: String, description: String) {
        if (name.isBlank()) return
        viewModelScope.launch { dao.rename(id, name.trim(), description.trim(), clock()) }
    }

    fun add(noteIds: List<Long>) {
        viewModelScope.launch { dao.addItems(noteIds.map { ProjectItem(id, it, clock()) }) }
    }

    fun remove(noteId: Long) {
        viewModelScope.launch { dao.removeItem(id, noteId) }
    }

    fun delete(then: () -> Unit) {
        viewModelScope.launch {
            dao.delete(id)
            then()
        }
    }

    companion object {
        const val ARG_ID = "projectId"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DropApplication
                ProjectViewModel(createSavedStateHandle(), app.database.projectDao(), app.database.noteDao())
            }
        }
    }
}

/** The top bar shared by Projects, Daily Log and Idea Canvas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpacesTopBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    val c = DropTheme.colors
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back)) }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = c.background,
            titleContentColor = c.textPrimary,
            navigationIconContentColor = c.textPrimary,
            actionIconContentColor = c.textPrimary,
        ),
    )
}

@Composable
fun EmptyMessage(title: String, body: String, modifier: Modifier = Modifier) {
    val c = DropTheme.colors
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = c.textPrimary, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
            Text(body, modifier = Modifier.padding(top = 6.dp), color = c.textSecondary, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ProjectsScreen(
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    startCreating: Boolean = false,
    viewModel: ProjectsViewModel = viewModel(factory = ProjectsViewModel.Factory),
) {
    val c = DropTheme.colors
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(startCreating) }
    Scaffold(
        modifier = Modifier.testTag("projects"),
        topBar = { SpacesTopBar(stringResource(R.string.projects_title), onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(stringResource(R.string.projects_new)) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = { creating = true },
                containerColor = c.accent,
                contentColor = c.onAccent,
            )
        },
        containerColor = c.background,
    ) { padding ->
        val list = projects ?: return@Scaffold
        if (list.isEmpty()) {
            EmptyMessage(stringResource(R.string.projects_empty_title), stringResource(R.string.projects_empty_body), Modifier.padding(padding))
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, top = padding.calculateTopPadding() + 4.dp, end = 16.dp, bottom = padding.calculateBottomPadding() + 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(list, key = { it.id }) { p ->
                    Surface(
                        onClick = { onOpen(p.id) },
                        modifier = Modifier.fillMaxWidth().testTag("project-${p.id}"),
                        shape = RoundedCornerShape(16.dp),
                        color = c.surface,
                        border = BorderStroke(1.dp, c.border),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(p.name, color = c.textPrimary, style = MaterialTheme.typography.titleMedium)
                            if (p.description.isNotBlank()) {
                                Text(p.description, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(
                                pluralStringResource(R.plurals.projects_items, p.itemCount, p.itemCount),
                                modifier = Modifier.padding(top = 8.dp),
                                color = c.textSecondary,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }
    }
    if (creating) {
        NameDialog(
            stringResource(R.string.projects_new),
            initialName = "",
            onConfirm = { name, description ->
                creating = false
                viewModel.create(name, description, onOpen)
            },
            onDismiss = { creating = false },
            initialDescription = "",
        )
    }
}

@Composable
fun ProjectScreen(
    onBack: () -> Unit,
    onOpenNote: (Long) -> Unit,
    onExport: ((Project, List<Note>) -> Unit)? = null,
    viewModel: ProjectViewModel = viewModel(factory = ProjectViewModel.Factory),
) {
    val c = DropTheme.colors
    val project by viewModel.project.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val allNotes by viewModel.allNotes.collectAsStateWithLifecycle()
    val formatter = rememberTimestampFormatter()
    var menu by remember { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.testTag("project"),
        topBar = {
            SpacesTopBar(project?.name.orEmpty(), onBack) {
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.testTag("project-menu")) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.note_more))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = c.surfaceElevated) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.spaces_rename), color = c.textPrimary) }, onClick = {
                            menu = false
                            editing = true
                        })
                        if (onExport != null) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.project_export), color = c.textPrimary) }, onClick = {
                                menu = false
                                project?.let { onExport(it, items.orEmpty()) }
                            })
                        }
                        DropdownMenuItem(text = { Text(stringResource(R.string.projects_delete), color = c.error) }, onClick = {
                            menu = false
                            deleting = true
                        })
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(stringResource(R.string.projects_add_items)) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = { adding = true },
                containerColor = c.accent,
                contentColor = c.onAccent,
                modifier = Modifier.testTag("project-add"),
            )
        },
        containerColor = c.background,
    ) { padding ->
        val list = items ?: return@Scaffold
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, top = padding.calculateTopPadding() + 4.dp, end = 16.dp, bottom = padding.calculateBottomPadding() + 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            project?.description?.takeIf { it.isNotBlank() }?.let { d ->
                item(key = "description") { Text(d, modifier = Modifier.padding(4.dp), color = c.textSecondary, style = MaterialTheme.typography.bodyMedium) }
            }
            if (list.isEmpty()) {
                item(key = "empty") { EmptyMessage(stringResource(R.string.project_empty_title), stringResource(R.string.project_empty_body)) }
            }
            items(list, key = { it.id }) { note ->
                NoteCard(note, meta = formatter.format(note.updatedAt), showPin = note.pinned, onClick = { onOpenNote(note.id) }) {
                    TextButton(
                        onClick = { viewModel.remove(note.id) },
                        modifier = Modifier.align(Alignment.End),
                        colors = accentTextButtonColors(),
                    ) { Text(stringResource(R.string.projects_remove_item)) }
                }
            }
        }
    }

    if (editing) {
        NameDialog(
            stringResource(R.string.spaces_rename),
            initialName = project?.name.orEmpty(),
            onConfirm = { name, description ->
                editing = false
                viewModel.rename(name, description)
            },
            onDismiss = { editing = false },
            initialDescription = project?.description.orEmpty(),
        )
    }
    if (adding) {
        NotePicker(
            stringResource(R.string.projects_add_items),
            allNotes,
            multiple = true,
            onPick = { ids ->
                adding = false
                viewModel.add(ids)
            },
            onDismiss = { adding = false },
            exclude = items.orEmpty().mapTo(HashSet()) { it.id },
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.padding(horizontal = 28.dp),
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    viewModel.delete(onBack)
                }, colors = destructiveTextButtonColors(), modifier = Modifier.testTag("confirm-delete-project")) { Text(stringResource(R.string.projects_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }, colors = accentTextButtonColors()) { Text(stringResource(R.string.dialog_cancel)) } },
            title = { Text(stringResource(R.string.projects_delete_title)) },
            text = { Text(stringResource(R.string.projects_delete_body)) },
            containerColor = c.surfaceElevated,
            titleContentColor = c.textPrimary,
            textContentColor = c.textSecondary,
        )
    }
}

/** Which projects a note (or several) belongs to, with a quick way to start a new project. */
@Composable
fun ProjectMembershipDialog(
    projects: List<ProjectSummary>,
    memberOf: Set<Long>,
    onToggle: (projectId: Long, member: Boolean) -> Unit,
    onCreate: (name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = DropTheme.colors
    var creating by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss, colors = accentTextButtonColors()) { Text(stringResource(R.string.resurface_done)) } },
        dismissButton = { TextButton(onClick = { creating = true }, colors = accentTextButtonColors()) { Text(stringResource(R.string.projects_new)) } },
        title = { Text(stringResource(R.string.projects_for_item)) },
        text = {
            LazyColumn(Modifier.fillMaxWidth()) {
                if (projects.isEmpty()) item { Text(stringResource(R.string.projects_empty_title), color = c.textSecondary) }
                items(projects, key = { it.id }) { p ->
                    val checked = p.id in memberOf
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle(p.id, it) })
                            .testTag("membership-${p.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(checkedColor = c.accent, uncheckedColor = c.textSecondary, checkmarkColor = c.onAccent),
                        )
                        Text(p.name, modifier = Modifier.padding(start = 8.dp), color = c.textPrimary)
                    }
                }
            }
        },
        containerColor = c.surfaceElevated,
    )
    if (creating) {
        NameDialog(stringResource(R.string.projects_new), "", onConfirm = { name, _ ->
            creating = false
            onCreate(name)
        }, onDismiss = { creating = false })
    }
}
