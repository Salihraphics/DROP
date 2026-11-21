package app.drop.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.drop.DropApplication
import app.drop.R
import app.drop.palette.CommandId
import app.drop.ui.home.DashboardActions
import app.drop.ui.home.DashboardScreen
import app.drop.ui.icons.ActionIcons
import app.drop.ui.notes.HomeFilter
import app.drop.ui.notes.NoteEditorScreen
import app.drop.ui.notes.NoteEditorViewModel
import app.drop.ui.notes.NoteListScreen
import app.drop.ui.settings.SettingsScreen
import app.drop.ui.spaces.CanvasListScreen
import app.drop.ui.spaces.CanvasScreen
import app.drop.ui.spaces.CanvasViewModel
import app.drop.ui.spaces.ProjectScreen
import app.drop.ui.spaces.ProjectViewModel
import app.drop.ui.spaces.ProjectsScreen
import app.drop.ui.templates.TemplateEditorScreen
import app.drop.ui.templates.TemplateEditorViewModel
import app.drop.ui.templates.TemplatesScreen
import app.drop.ui.theme.DropSnackbarHost
import app.drop.ui.theme.DropTheme
import app.drop.ui.trash.TrashScreen
import kotlinx.coroutines.launch

const val ROUTE_DASHBOARD = "dashboard"
const val ROUTE_NOTES = "notes"
const val ROUTE_TRASH = "trash"
const val ROUTE_SETTINGS = "settings"
const val ROUTE_TEMPLATES = "templates"
private const val ARG_CREATE = "create"
private const val ROUTE_PROJECTS = "projects?$ARG_CREATE={$ARG_CREATE}"
private const val ROUTE_PROJECT = "project/{${ProjectViewModel.ARG_ID}}"
private const val ROUTE_CANVASES = "canvases"
private const val ROUTE_CANVAS = "canvas/{${CanvasViewModel.ARG_ID}}"

private const val KEY_PENDING_FILTER = "pendingFilter"
private const val KEY_PENDING_SEARCH = "pendingSearch"

/** The spaces rebuilt so far; the others join the spaces menu as they return. */
private val AVAILABLE_SPACES = setOf(CommandId.Projects, CommandId.Canvas, CommandId.Templates)

/** The commands rebuilt so far; the others join the command palette as they return. */
private val AVAILABLE_COMMANDS = AVAILABLE_SPACES + setOf(
    CommandId.NewNote, CommandId.NewTask, CommandId.Search, CommandId.ShowAll, CommandId.ShowTasks,
    CommandId.ShowLinks, CommandId.ShowPinned, CommandId.Trash, CommandId.Settings, CommandId.NewFromTemplate,
)

/** Opens the Library, optionally with a filter or with search open, as 0.10.0 did. */
private fun NavHostController.showLibrary(filter: HomeFilter? = null, search: Boolean = false) {
    showSection(ROUTE_NOTES)
    val handle = getBackStackEntry(ROUTE_NOTES).savedStateHandle
    if (filter != null) handle[KEY_PENDING_FILTER] = filter
    if (search) handle[KEY_PENDING_SEARCH] = true
}

/** Everything the command palette, the spaces menu and Home's buttons can run. */
private fun NavHostController.runCommand(id: CommandId) {
    when (id) {
        CommandId.Projects -> navigate("projects?$ARG_CREATE=false")
        CommandId.Canvas -> navigate(ROUTE_CANVASES)
        CommandId.Templates, CommandId.NewFromTemplate -> navigate(ROUTE_TEMPLATES)
        CommandId.NewNote -> openEditor(NoteEditorViewModel.NEW_NOTE_ID)
        CommandId.NewTask -> openEditor(NoteEditorViewModel.NEW_NOTE_ID, task = true)
        CommandId.Search -> showLibrary(search = true)
        CommandId.ShowAll -> showLibrary(HomeFilter.All)
        CommandId.ShowTasks -> showLibrary(HomeFilter.Tasks)
        CommandId.ShowLinks -> showLibrary(HomeFilter.Links)
        CommandId.ShowPinned -> showLibrary(HomeFilter.Pinned)
        CommandId.Trash -> navigate(ROUTE_TRASH)
        CommandId.Settings -> navigate(ROUTE_SETTINGS)
        else -> Unit
    }
}
private const val ROUTE_TEMPLATE_EDIT = "template/{${TemplateEditorViewModel.ARG_ID}}?${TemplateEditorViewModel.ARG_FROM_NOTE}={${TemplateEditorViewModel.ARG_FROM_NOTE}}"
private const val ROUTE_EDITOR = "note/{${NoteEditorViewModel.ARG_NOTE_ID}}?${NoteEditorViewModel.ARG_TASK}={${NoteEditorViewModel.ARG_TASK}}"

fun editorRoute(noteId: Long, task: Boolean = false): String = "note/$noteId?${NoteEditorViewModel.ARG_TASK}=$task"

fun NavHostController.openEditor(noteId: Long, task: Boolean = false) = navigate(editorRoute(noteId, task))

/** Switches between Home and Library, keeping each section's state. */
fun NavHostController.showSection(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun DropNavHost(startAction: StartAction?, onStartActionHandled: () -> Unit) {
    val c = DropTheme.colors
    val nav = rememberNavController()
    val app = LocalContext.current.applicationContext as DropApplication
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    LaunchedEffect(startAction) {
        when (val action = startAction ?: return@LaunchedEffect) {
            StartAction.NewNote -> nav.openEditor(NoteEditorViewModel.NEW_NOTE_ID)
            is StartAction.OpenNote -> if (action.id > 0) nav.openEditor(action.id)
            StartAction.ShowTasks -> nav.showLibrary(HomeFilter.Tasks)
            StartAction.VoiceDump -> nav.openEditor(NoteEditorViewModel.NEW_NOTE_ID)
            is StartAction.OpenResurfaced -> {
                val note = app.database.noteDao().getById(action.id)
                // The reminder has done its job: clear it so it is not shown again.
                runCatching { app.resurfacer.cancel(action.id) }
                if (note != null && note.deletedAt == null) {
                    nav.openEditor(action.id)
                } else {
                    nav.popBackStack(ROUTE_DASHBOARD, inclusive = false)
                    Toast.makeText(context, R.string.resurface_gone, Toast.LENGTH_SHORT).show()
                }
            }
        }
        onStartActionHandled()
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            NavHost(nav, startDestination = ROUTE_DASHBOARD, modifier = Modifier.weight(1f)) {
                composable(ROUTE_DASHBOARD) {
                    Box {
                        DashboardScreen(
                            DashboardActions(
                                openNote = { nav.openEditor(it) },
                                newItem = { task -> nav.openEditor(NoteEditorViewModel.NEW_NOTE_ID, task) },
                                search = { nav.showLibrary(search = true) },
                                openTasks = { nav.showLibrary(HomeFilter.Tasks) },
                                openLibrary = { nav.showSection(ROUTE_NOTES) },
                                openTrash = { nav.navigate(ROUTE_TRASH) },
                                openSettings = { nav.navigate(ROUTE_SETTINGS) },
                                openProject = { nav.navigate("project/$it") },
                                openProjects = { create -> nav.navigate("projects?$ARG_CREATE=$create") },
                                command = { nav.runCommand(it) },
                                spaces = AVAILABLE_SPACES,
                                commands = AVAILABLE_COMMANDS,
                            ),
                        )
                    }
                }
                composable(ROUTE_NOTES) { notesEntry ->
                    val handle = notesEntry.savedStateHandle
                    val pendingFilter by handle.getStateFlow<HomeFilter?>(KEY_PENDING_FILTER, null).collectAsStateWithLifecycle()
                    val pendingSearch by handle.getStateFlow(KEY_PENDING_SEARCH, false).collectAsStateWithLifecycle()
                    NoteListScreen(
                        onOpen = { nav.openEditor(it) },
                        onNew = { task -> nav.openEditor(NoteEditorViewModel.NEW_NOTE_ID, task) },
                        onTrash = { nav.navigate(ROUTE_TRASH) },
                        onSettings = { nav.navigate(ROUTE_SETTINGS) },
                        onOpenSpace = { nav.runCommand(it) },
                        spaces = AVAILABLE_SPACES,
                        commands = AVAILABLE_COMMANDS,
                        initialFilter = pendingFilter,
                        onFilterConsumed = { handle[KEY_PENDING_FILTER] = null },
                        startSearch = pendingSearch,
                        onSearchConsumed = { handle[KEY_PENDING_SEARCH] = false },
                    )
                }
                composable(
                    ROUTE_EDITOR,
                    arguments = listOf(
                        navArgument(NoteEditorViewModel.ARG_NOTE_ID) { type = NavType.LongType },
                        navArgument(NoteEditorViewModel.ARG_TASK) {
                            type = NavType.BoolType
                            defaultValue = false
                        },
                    ),
                ) {
                    NoteEditorScreen(onOpenNote = { nav.openEditor(it) }, onSaveAsTemplate = { id -> nav.navigate("template/-1?${TemplateEditorViewModel.ARG_FROM_NOTE}=$id") }, onClose = { result ->
                        nav.popBackStack()
                        val trashed = result.trashed ?: return@NoteEditorScreen
                        scope.launch {
                            val message = context.getString(if (trashed.wasEmpty) R.string.home_empty_note_moved_to_trash else R.string.home_moved_to_trash)
                            val choice = snackbar.showSnackbar(message, actionLabel = context.getString(R.string.home_undo), duration = SnackbarDuration.Long)
                            if (choice == SnackbarResult.ActionPerformed) app.database.noteDao().setDeletedAt(trashed.id, null)
                        }
                    })
                }
                composable(ROUTE_TRASH) { TrashScreen(onBack = { nav.popBackStack() }) }
                composable(ROUTE_SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }) }
                composable(
                    ROUTE_PROJECTS,
                    arguments = listOf(
                        navArgument(ARG_CREATE) {
                            type = NavType.BoolType
                            defaultValue = false
                        },
                    ),
                ) { entry ->
                    ProjectsScreen(
                        onBack = { nav.popBackStack() },
                        onOpen = { nav.navigate("project/$it") },
                        startCreating = entry.arguments?.getBoolean(ARG_CREATE) ?: false,
                    )
                }
                composable(ROUTE_PROJECT, arguments = listOf(navArgument(ProjectViewModel.ARG_ID) { type = NavType.LongType })) {
                    ProjectScreen(onBack = { nav.popBackStack() }, onOpenNote = { nav.openEditor(it) })
                }
                composable(ROUTE_CANVASES) {
                    CanvasListScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate("canvas/$it") })
                }
                composable(ROUTE_CANVAS, arguments = listOf(navArgument(CanvasViewModel.ARG_ID) { type = NavType.LongType })) {
                    CanvasScreen(onBack = { nav.popBackStack() }, onOpenNote = { nav.openEditor(it) })
                }
                composable(ROUTE_TEMPLATES) {
                    TemplatesScreen(
                        onBack = { nav.popBackStack() },
                        onCreated = { id ->
                            nav.popBackStack()
                            nav.openEditor(id)
                        },
                        onEdit = { id -> nav.navigate("template/$id") },
                        onNew = { nav.navigate("template/-1") },
                    )
                }
                composable(
                    ROUTE_TEMPLATE_EDIT,
                    arguments = listOf(
                        navArgument(TemplateEditorViewModel.ARG_ID) { type = NavType.LongType },
                        navArgument(TemplateEditorViewModel.ARG_FROM_NOTE) {
                            type = NavType.LongType
                            defaultValue = -1L
                        },
                    ),
                ) { TemplateEditorScreen(onClose = { nav.popBackStack() }) }
            }
            if (route == ROUTE_DASHBOARD || route == ROUTE_NOTES) SectionBar(route) { nav.showSection(it) }
        }
        DropSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 80.dp))
    }
}

@Composable
private fun SectionBar(current: String?, onSelect: (String) -> Unit) {
    val c = DropTheme.colors
    val colors = NavigationBarItemDefaults.colors(
        selectedIconColor = c.onAccent,
        selectedTextColor = c.textPrimary,
        indicatorColor = c.accent,
        unselectedIconColor = c.textSecondary,
        unselectedTextColor = c.textSecondary,
    )
    NavigationBar(containerColor = c.surface, modifier = Modifier.testTag("section-bar")) {
        NavigationBarItem(
            selected = current == ROUTE_DASHBOARD,
            onClick = { onSelect(ROUTE_DASHBOARD) },
            icon = { Icon(Icons.Default.Home, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_home)) },
            colors = colors,
            modifier = Modifier.testTag("nav-home"),
        )
        NavigationBarItem(
            selected = current == ROUTE_NOTES,
            onClick = { onSelect(ROUTE_NOTES) },
            icon = { Icon(ActionIcons.Library, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_library)) },
            colors = colors,
            modifier = Modifier.testTag("nav-library"),
        )
    }
}
