package app.drop.ui.templates

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.drop.DropApplication
import app.drop.R
import app.drop.richtext.DropMarkdown
import app.drop.templates.BuiltInTemplates
import app.drop.templates.Placeholders
import app.drop.templates.Template
import app.drop.templates.toTemplate
import app.drop.ui.icons.FormatIcons
import app.drop.ui.richtext.FormattingToolbar
import app.drop.ui.richtext.LinkDialog
import app.drop.ui.richtext.RichTextField
import app.drop.ui.theme.DropSnackbarHost
import app.drop.ui.theme.DropTheme
import app.drop.ui.theme.accentTextButtonColors
import app.drop.ui.theme.destructiveTextButtonColors
import app.drop.ui.theme.dropTextFieldColors
import app.drop.ui.theme.noteTextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import java.util.Locale

const val TEMPLATES_TAG = "templates"
const val TEMPLATE_PREVIEW_TAG = "template_preview"
const val TEMPLATE_NAME_TAG = "template_name"
const val TEMPLATE_BODY_TAG = "template_body"

/**
 * The built-in templates and the user's own. Tapping one shows what a note made from it would
 * look like; a note is only created when the user taps Use.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(onBack: () -> Unit, onCreated: (noteId: Long) -> Unit, onEdit: (templateId: Long) -> Unit, onNew: () -> Unit) {
    val c = DropTheme.colors
    val context = LocalContext.current
    val app = context.applicationContext as DropApplication
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val locale: Locale = LocalConfiguration.current.locales[0]
    val builtIn = remember(locale) { BuiltInTemplates.all(context.resources) }
    val store = app.templates
    val custom by remember(store) { store.observeCustom() }.collectAsStateWithLifecycle(initialValue = null)
    var previewKey by rememberSaveable { mutableStateOf<String?>(null) }
    val all = builtIn + custom.orEmpty().map { it.toTemplate() }
    val preview = previewKey?.let { key -> all.firstOrNull { it.key == key } }
    val duplicated = stringResource(R.string.template_duplicated)
    val deleted = stringResource(R.string.template_deleted)
    val unreadable = stringResource(R.string.template_unreadable)

    Scaffold(
        containerColor = c.background,
        snackbarHost = { DropSnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.templates_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.note_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.background, titleContentColor = c.textPrimary, navigationIconContentColor = c.textPrimary),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(stringResource(R.string.templates_new)) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = onNew,
                containerColor = c.accent,
                contentColor = c.onAccent,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).testTag(TEMPLATES_TAG),
            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(stringResource(R.string.templates_placeholders), color = c.textSecondary, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                SectionTitle(stringResource(R.string.templates_builtin))
            }
            items(builtIn, key = { it.key }) { t -> TemplateRow(t, locale) { previewKey = t.key } }
            item { SectionTitle(stringResource(R.string.templates_mine)) }
            val mine = custom
            if (mine != null && mine.isEmpty()) {
                item { Text(stringResource(R.string.templates_mine_empty), color = c.textSecondary, style = MaterialTheme.typography.bodyMedium) }
            }
            items(mine.orEmpty(), key = { "custom:${it.id}" }) { t ->
                val template = t.toTemplate()
                TemplateRow(template, locale) { previewKey = template.key }
            }
        }
    }

    if (preview != null) {
        val customId = preview.customId
        TemplatePreview(
            template = preview,
            onDismiss = { previewKey = null },
            onUse = {
                scope.launch {
                    val note = runCatching { app.templates.createNote(preview, ZonedDateTime.now(), locale) }.getOrNull()
                    if (note == null) {
                        snackbar.showSnackbar(unreadable)
                        return@launch
                    }
                    runCatching { app.links.sync(note) }
                    withContext(Dispatchers.Main) {
                        previewKey = null
                        onCreated(note.id)
                    }
                }
            },
            onDuplicate = {
                scope.launch {
                    app.templates.duplicate(preview, context.getString(R.string.template_copy_name, preview.name))
                    previewKey = null
                    snackbar.showSnackbar(duplicated)
                }
            },
            onEdit = customId?.let { id -> { previewKey = null; onEdit(id) } },
            onDelete = customId?.let { id ->
                {
                    scope.launch {
                        app.templates.delete(id)
                        previewKey = null
                        snackbar.showSnackbar(deleted)
                    }
                }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
        color = DropTheme.colors.textSecondary,
        style = MaterialTheme.typography.titleSmall,
    )
}

@Composable
private fun TemplateRow(template: Template, locale: Locale, onClick: () -> Unit) {
    val c = DropTheme.colors
    val firstLines = remember(template.key, template.body, template.format, locale) {
        val preview = runCatching { DropMarkdown.previewOf(template.body, template.format) }.getOrDefault("")
        preview.lines().filter { it.isNotBlank() }.take(3).joinToString(" · ")
    }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = c.surface,
        border = BorderStroke(1.dp, c.border),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(FormatIcons.Template, contentDescription = null, tint = c.textSecondary)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(template.name.ifBlank { stringResource(R.string.history_untitled) }, color = c.textPrimary, style = MaterialTheme.typography.titleMedium)
                if (firstLines.isNotEmpty()) {
                    Text(firstLines, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** What a note made from [template] now would look like. Edit and Delete are only offered for the user's own templates. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplatePreview(
    template: Template,
    onDismiss: () -> Unit,
    onUse: () -> Unit,
    onDuplicate: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    val c = DropTheme.colors
    val locale: Locale = LocalConfiguration.current.locales[0]
    val made = remember(template, locale) { Placeholders.tryInstantiate(template, ZonedDateTime.now(), locale) }
    val title = made?.first.orEmpty()
    val text = remember(made) { made?.let { runCatching { DropMarkdown.previewOf(it.second, it.third) }.getOrNull() } }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler(onBack = onDismiss)
        Scaffold(
            modifier = Modifier.testTag(TEMPLATE_PREVIEW_TAG),
            containerColor = c.background,
            topBar = {
                TopAppBar(
                    title = { Text(template.name, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.dialog_close), tint = c.textPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = c.background),
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                    if (text == null) {
                        Text(stringResource(R.string.template_unreadable), color = c.textPrimary, style = MaterialTheme.typography.bodyLarge)
                    } else {
                        Text(stringResource(R.string.template_preview_hint), color = c.textSecondary, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = c.surface,
                            border = BorderStroke(1.dp, c.border),
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(title.ifBlank { stringResource(R.string.history_untitled) }, color = c.textPrimary, style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.height(8.dp))
                                Text(text, color = c.textPrimary, style = noteTextStyle(MaterialTheme.typography.bodyLarge))
                            }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onDelete != null) {
                        TextButton(onClick = { confirmDelete = true }, colors = destructiveTextButtonColors()) { Text(stringResource(R.string.template_delete)) }
                    }
                    if (onEdit != null) {
                        TextButton(onClick = onEdit, colors = accentTextButtonColors()) { Text(stringResource(R.string.template_edit)) }
                    }
                    TextButton(onClick = onDuplicate, enabled = text != null, colors = accentTextButtonColors()) { Text(stringResource(R.string.template_duplicate)) }
                    Button(
                        onClick = onUse,
                        enabled = text != null,
                        colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                    ) { Text(stringResource(R.string.template_use)) }
                }
            }
        }
    }

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }, colors = destructiveTextButtonColors()) { Text(stringResource(R.string.template_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }, colors = accentTextButtonColors()) { Text(stringResource(R.string.dialog_cancel)) }
            },
            title = { Text(stringResource(R.string.template_delete_title), color = c.textPrimary) },
            text = { Text(stringResource(R.string.template_delete_body, template.name), color = c.textPrimary) },
            containerColor = c.surfaceElevated,
        )
    }
}

/** Creates or changes one of the user's own templates. Leaving with unsaved changes asks first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditorScreen(onClose: () -> Unit, viewModel: TemplateEditorViewModel = viewModel(factory = TemplateEditorViewModel.Factory)) {
    val c = DropTheme.colors
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var linkOpen by rememberSaveable { mutableStateOf(false) }
    var bodyFocused by remember { mutableStateOf(false) }
    val leave = remember(viewModel, onClose) { { if (viewModel.dirty) confirmDiscard = true else onClose() } }
    BackHandler(onBack = leave)
    LaunchedEffect(viewModel.saved) { if (viewModel.saved) onClose() }
    val fieldColors = dropTextFieldColors()

    Scaffold(
        containerColor = c.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (viewModel.isNew) R.string.template_editor_new else R.string.template_editor_edit), color = c.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = leave) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.note_back), tint = c.textPrimary)
                    }
                },
                actions = {
                    Button(
                        onClick = viewModel::save,
                        enabled = viewModel.loaded,
                        modifier = Modifier.padding(end = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                    ) { Text(stringResource(R.string.template_save)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.background),
            )
        },
    ) { padding ->
        if (!viewModel.loaded) return@Scaffold
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
                OutlinedTextField(
                    value = viewModel.name,
                    onValueChange = viewModel::onNameChange,
                    modifier = Modifier.fillMaxWidth().testTag(TEMPLATE_NAME_TAG),
                    label = { Text(stringResource(R.string.template_name)) },
                    supportingText = if (viewModel.nameError) ({ Text(stringResource(R.string.template_name_needed)) }) else null,
                    isError = viewModel.nameError,
                    singleLine = true,
                    colors = fieldColors,
                )
                OutlinedTextField(
                    value = viewModel.title,
                    onValueChange = viewModel::onTitleChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.template_title_field)) },
                    singleLine = true,
                    colors = fieldColors,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = viewModel.isTask,
                        onCheckedChange = viewModel::onTaskChange,
                        colors = CheckboxDefaults.colors(checkedColor = c.accent, uncheckedColor = c.textSecondary, checkmarkColor = c.onAccent),
                    )
                    Text(stringResource(R.string.template_as_task), color = c.textPrimary)
                }
                Text(stringResource(R.string.templates_placeholders), color = c.textSecondary, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                RichTextField(
                    model = viewModel.editor,
                    textStyle = noteTextStyle(MaterialTheme.typography.bodyLarge.copy(color = c.textPrimary)),
                    placeholder = stringResource(R.string.template_body_hint),
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 240.dp).onFocusChanged { bodyFocused = it.isFocused }.testTag(TEMPLATE_BODY_TAG),
                )
            }
            if (bodyFocused) FormattingToolbar(viewModel.editor, onLink = { linkOpen = true })
        }
    }

    if (linkOpen) LinkDialog(viewModel.editor, onDismiss = { linkOpen = false })
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; onClose() }, colors = destructiveTextButtonColors()) { Text(stringResource(R.string.template_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }, colors = accentTextButtonColors()) { Text(stringResource(R.string.template_keep_editing)) }
            },
            title = { Text(stringResource(R.string.template_discard_title), color = c.textPrimary) },
            text = { Text(stringResource(R.string.template_discard_body), color = c.textPrimary) },
            containerColor = c.surfaceElevated,
        )
    }
}
