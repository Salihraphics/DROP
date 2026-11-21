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
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import app.drop.R
import app.drop.ui.icons.FormatIcons
import app.drop.ui.icons.PinIcons
import app.drop.ui.richtext.FormattingToolbar
import app.drop.ui.richtext.LinkBar
import app.drop.ui.richtext.LinkDialog
import app.drop.ui.richtext.RichTextField
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
    vm: NoteEditorViewModel = viewModel(factory = NoteEditorViewModel.Factory),
) {
    val c = DropTheme.colors
    val result = vm.result
    LaunchedEffect(result) { result?.let(onClose) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.saveInBackground() }
    BackHandler { vm.saveAndClose() }

    var menu by remember { mutableStateOf(false) }
    var linkDialog by rememberSaveable { mutableStateOf(false) }
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
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(if (vm.isTask) R.string.convert_to_note else R.string.convert_to_task), color = c.textPrimary) },
                            onClick = {
                                menu = false
                                vm.onTaskChange(!vm.isTask)
                            },
                            modifier = Modifier.testTag("convert"),
                        )
                        val noteId = vm.noteId
                        if (onSaveAsTemplate != null && noteId != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.save_as_template), color = c.textPrimary) },
                                leadingIcon = { Icon(FormatIcons.Template, contentDescription = null, tint = c.textSecondary) },
                                onClick = {
                                    menu = false
                                    scope.launch {
                                        vm.save()
                                        withContext(Dispatchers.Main) { onSaveAsTemplate(noteId) }
                                    }
                                },
                                modifier = Modifier.testTag("save-as-template"),
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.note_move_to_trash), color = c.error) },
                            onClick = {
                                menu = false
                                vm.moveToTrash()
                            },
                            modifier = Modifier.testTag("move-to-trash"),
                        )
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
                modifier = Modifier.fillMaxWidth().padding(bottom = 96.dp),
            )
        }
    }
    if (linkDialog) LinkDialog(vm.editor, onDismiss = { linkDialog = false })
}
