package app.drop.ui.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.drop.DropApplication
import app.drop.R
import app.drop.data.Project
import app.drop.data.ProjectItem
import app.drop.ui.attach.AttachmentList
import app.drop.ui.echo.RelatedNotesSection
import app.drop.ui.icons.ActionIcons
import app.drop.ui.icons.FormatIcons
import app.drop.ui.icons.PinIcons
import app.drop.ui.richtext.FormattingToolbar
import app.drop.ui.richtext.LinkBar
import app.drop.ui.richtext.LinkDialog
import app.drop.ui.richtext.RichTextField
import app.drop.ui.spaces.ProjectMembershipDialog
import app.drop.ui.theme.DropSnackbarHost
import app.drop.ui.theme.DropTheme
import app.drop.ui.theme.accentTextButtonColors
import app.drop.ui.theme.destructiveTextButtonColors
import app.drop.ui.theme.noteTextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Edits a note, task or link. Nothing needs saving by hand: the note is saved as the person
 * types, when the app goes to the background and when the screen closes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    onClose: (EditorResult) -> Unit,
    onSaveAsTemplate: ((noteId: Long) -> Unit)? = null,
    onOpenNote: (Long) -> Unit = {},
    onOpenAttachment: (Long) -> Unit = {},
    onAddScan: ((noteId: Long) -> Unit)? = null,
    vm: NoteEditorViewModel = viewModel(factory = NoteEditorViewModel.Factory),
) {
    val c = DropTheme.colors
    val result = vm.result
    LaunchedEffect(result) { result?.let(onClose) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.saveInBackground() }
    BackHandler { vm.saveAndClose() }

    var menu by remember { mutableStateOf(false) }
    var linkDialog by rememberSaveable { mutableStateOf(false) }
    var projectsOpen by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val noApp = stringResource(R.string.link_no_app)
    val formatter = rememberTimestampFormatter()

    Scaffold(
        modifier = Modifier.testTag("editor"),
        containerColor = c.background,
        snackbarHost = { DropSnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.background, titleContentColor = c.textPrimary),
                navigationIcon = {
                    IconButton(onClick = { vm.saveAndClose() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.note_back), tint = c.textPrimary)
                    }
                },
                title = {
                    val status = when {
                        vm.saveState == SaveState.Saving -> stringResource(R.string.save_saving)
                        vm.saveState == SaveState.Saved -> stringResource(R.string.save_saved)
                        else -> vm.editedAt?.let { stringResource(R.string.note_edited, formatter.format(it)) }.orEmpty()
                    }
                    Text(status, style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.testTag("save-state"))
                },
                actions = {
                    IconButton(onClick = { vm.onPinnedChange(!vm.pinned) }, modifier = Modifier.testTag("pin")) {
                        Icon(
                            if (vm.pinned) PinIcons.Filled else PinIcons.Outlined,
                            contentDescription = stringResource(if (vm.pinned) R.string.note_pinned else R.string.note_pin),
                            tint = if (vm.pinned) c.accentContent else c.textSecondary,
                        )
                    }
                    TextButton(onClick = { vm.saveAndClose() }, colors = accentTextButtonColors(), modifier = Modifier.testTag("done")) {
                        Text(stringResource(R.string.note_done))
                    }
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.note_more), tint = c.textPrimary)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = c.surfaceElevated) {
                        @Composable
                        fun Item(text: String, icon: ImageVector, tag: String, onClick: () -> Unit) = DropdownMenuItem(
                            text = { Text(text, color = c.textPrimary) },
                            leadingIcon = { Icon(icon, contentDescription = null, tint = c.textPrimary) },
                            onClick = {
                                menu = false
                                onClick()
                            },
                            modifier = Modifier.testTag(tag),
                        )
                        val noteId = vm.noteId
                        Item(stringResource(if (vm.isTask) R.string.convert_to_note else R.string.convert_to_task), Icons.Filled.CheckCircle, "convert") {
                            vm.onTaskChange(!vm.isTask)
                        }
                        if (noteId != null) {
                            Item(stringResource(R.string.projects_action), ActionIcons.Spaces, "note-projects") { projectsOpen = true }
                        }
                        if (onAddScan != null && noteId != null) {
                            Item(stringResource(R.string.attachment_add), ActionIcons.Attach, "add-scan") {
                                scope.launch {
                                    vm.save()
                                    withContext(Dispatchers.Main) { onAddScan(noteId) }
                                }
                            }
                        }
                        if (onSaveAsTemplate != null && noteId != null) {
                            Item(stringResource(R.string.save_as_template), FormatIcons.Template, "save-as-template") {
                                scope.launch {
                                    vm.save()
                                    withContext(Dispatchers.Main) { onSaveAsTemplate(noteId) }
                                }
                            }
                        }
                        Item(stringResource(R.string.note_move_to_trash), Icons.Outlined.Delete, "move-to-trash") { vm.moveToTrash() }
                    }
                },
            )
        },
        bottomBar = {
            Column(Modifier.navigationBarsPadding().imePadding()) {
                LinkBar(vm.editor, onEdit = { linkDialog = true }, onCannotOpen = { scope.launch { snackbar.showSnackbar(noApp) } })
                FormattingToolbar(vm.editor, onLink = { linkDialog = true })
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (vm.isTask) {
                    Checkbox(
                        checked = vm.completedAt != null,
                        onCheckedChange = { vm.onCompletedChange(it) },
                        colors = CheckboxDefaults.colors(checkedColor = c.accent, checkmarkColor = c.onAccent, uncheckedColor = c.textSecondary),
                        modifier = Modifier.testTag("task-done"),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                val titleStyle = noteTextStyle(MaterialTheme.typography.headlineMedium).copy(color = c.textPrimary)
                BasicTextField(
                    value = vm.title,
                    onValueChange = { vm.onTitleChange(it.replace('\n', ' ')) },
                    textStyle = titleStyle,
                    cursorBrush = SolidColor(c.accentContent),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f).testTag("note-title"),
                    decorationBox = { inner ->
                        if (vm.title.isEmpty()) {
                            Text(stringResource(if (vm.isTask) R.string.task_title_hint else R.string.note_title_hint), style = titleStyle, color = c.textSecondary)
                        }
                        inner()
                    },
                )
            }
            Spacer(Modifier.padding(top = 12.dp))
            RichTextField(
                model = vm.editor,
                textStyle = noteTextStyle(MaterialTheme.typography.bodyLarge),
                placeholder = stringResource(if (vm.isTask) R.string.task_body_hint else R.string.note_body_hint),
                modifier = Modifier.fillMaxWidth(),
            )
            vm.noteId?.let { id ->
                NoteAttachments(id, onOpenAttachment)
                RelatedNotesSection(id, onOpenNote)
            }
            Spacer(Modifier.padding(bottom = 96.dp))
        }
    }
    if (linkDialog) LinkDialog(vm.editor, onDismiss = { linkDialog = false })
    val noteId = vm.noteId
    if (projectsOpen && noteId != null) NoteProjectsDialog(noteId, onDismiss = { projectsOpen = false })
}

/** The photos, PDFs and recordings kept with this note. */
@Composable
private fun NoteAttachments(noteId: Long, onOpen: (Long) -> Unit) {
    val app = LocalContext.current.applicationContext as DropApplication
    val attachments by remember(noteId) { app.database.attachmentDao().observeForNote(noteId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    if (attachments.isEmpty()) return
    Spacer(Modifier.padding(top = 16.dp))
    AttachmentList(attachments, onOpen)
}

/** Which projects this note is in; changes apply at once. */
@Composable
private fun NoteProjectsDialog(noteId: Long, onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as DropApplication
    val dao = app.database.projectDao()
    val scope = rememberCoroutineScope()
    val projects by remember { dao.observeAll() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val memberOf by remember(noteId) { dao.observeProjectIdsFor(noteId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    ProjectMembershipDialog(
        projects = projects,
        memberOf = memberOf.toSet(),
        onToggle = { projectId, member ->
            scope.launch {
                if (member) dao.addItem(ProjectItem(projectId, noteId, System.currentTimeMillis())) else dao.removeItem(projectId, noteId)
            }
        },
        onCreate = { name ->
            scope.launch {
                val now = System.currentTimeMillis()
                val projectId = dao.insert(Project(name = name, createdAt = now, updatedAt = now))
                dao.addItem(ProjectItem(projectId, noteId, now))
            }
        },
        onDismiss = onDismiss,
    )
}
