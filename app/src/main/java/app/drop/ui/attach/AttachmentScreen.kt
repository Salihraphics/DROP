package app.drop.ui.attach

import android.content.ActivityNotFoundException
import android.content.Intent
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.drop.DropApplication
import app.drop.R
import app.drop.data.AttachmentKind
import app.drop.data.OcrStatus
import app.drop.ui.icons.ActionIcons
import app.drop.ui.paper.fileProviderAuthority
import app.drop.ui.spaces.spacesFieldColors
import app.drop.ui.theme.DropSnackbarHost
import app.drop.ui.theme.DropTheme
import app.drop.ui.theme.accentTextButtonColors
import app.drop.ui.theme.destructiveTextButtonColors
import kotlinx.coroutines.launch

/** One attachment: the file, the note it is in, and the text read from it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentScreen(
    onBack: () -> Unit,
    onOpenNote: (Long) -> Unit,
    viewModel: AttachmentViewModel = viewModel(factory = AttachmentViewModel.Factory),
) {
    val c = DropTheme.colors
    val context = LocalContext.current
    val view by viewModel.view.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val noApp = stringResource(R.string.attachment_no_app)
    if (viewModel.deleted) LaunchedEffect(Unit) { onBack() }

    val current = view?.takeUnless { AttachmentViewModel.isMissing(it) }
    Scaffold(
        modifier = Modifier.testTag("attachment"),
        containerColor = c.background,
        snackbarHost = { DropSnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.attachment_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.note_back))
                    }
                },
                actions = {
                    if (current != null) {
                        IconButton(onClick = {
                            val a = current.attachment
                            val file = (context.applicationContext as DropApplication).attachments.store.file(a.fileName)
                            try {
                                val uri = FileProvider.getUriForFile(context, fileProviderAuthority(context.packageName), file)
                                val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, a.mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                context.startActivity(Intent.createChooser(intent, null))
                            } catch (e: ActivityNotFoundException) {
                                scope.launch { snackbar.showSnackbar(noApp) }
                            } catch (e: IllegalArgumentException) {
                                scope.launch { snackbar.showSnackbar(noApp) }
                            }
                        }) { Icon(ActionIcons.OpenInNew, contentDescription = stringResource(R.string.attachment_open_with)) }
                        IconButton(onClick = { confirmDelete = true }, modifier = Modifier.testTag("attachment-delete")) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.attachment_delete))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = c.background,
                    titleContentColor = c.textPrimary,
                    navigationIconContentColor = c.textPrimary,
                    actionIconContentColor = c.textPrimary,
                ),
            )
        },
    ) { padding ->
        val v = view
        Box(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                when {
                    v == null -> Unit
                    current == null -> Text(stringResource(R.string.attachment_missing), color = c.textSecondary, modifier = Modifier.testTag("attachment-missing"))
                    else -> Content(current, progress, viewModel, onOpenNote, onAdded = {
                        scope.launch { snackbar.showSnackbar(context.getString(R.string.ocr_added_to_note)) }
                    }, onMessage = { message -> scope.launch { snackbar.showSnackbar(context.getString(message)) } })
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }, colors = destructiveTextButtonColors(), modifier = Modifier.testTag("attachment-delete-confirm")) {
                    Text(stringResource(R.string.attachment_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }, colors = accentTextButtonColors()) { Text(stringResource(R.string.dialog_cancel)) }
            },
            title = { Text(stringResource(R.string.attachment_delete_title)) },
            text = { Text(stringResource(R.string.attachment_delete_body)) },
            containerColor = c.surfaceElevated,
            titleContentColor = c.textPrimary,
            textContentColor = c.textSecondary,
        )
    }
}

@Composable
private fun Content(
    view: AttachmentView,
    progress: app.drop.ocr.OcrProgress?,
    viewModel: AttachmentViewModel,
    onOpenNote: (Long) -> Unit,
    onAdded: () -> Unit,
    onMessage: (Int) -> Unit,
) {
    val c = DropTheme.colors
    val context = LocalContext.current
    val a = view.attachment
    val kind = a.attachmentKind
    val file = remember(a.fileName) { (context.applicationContext as DropApplication).attachments.store.file(a.fileName) }
    var page by rememberSaveable(a.id) { mutableIntStateOf(0) }
    val pages = a.pageCount ?: 1

    if (kind == AttachmentKind.Audio) {
        Text(kind.label() + (a.durationMs?.let { " · " + formatDuration(it) } ?: ""), color = c.textPrimary, style = MaterialTheme.typography.titleMedium)
    } else if (kind != null) {
        FilePreview(file, kind, contentDescription = a.displayName, page = page)
        if (kind == AttachmentKind.Pdf && pages > 1) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                IconButton(onClick = { page-- }, enabled = page > 0) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.attachment_previous_page), tint = c.textPrimary)
                }
                Text(stringResource(R.string.attachment_page, page + 1, pages), color = c.textSecondary, style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = { page++ }, enabled = page < pages - 1) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.attachment_next_page), tint = c.textPrimary)
                }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    val details = buildList {
        add(a.displayName)
        add(Formatter.formatShortFileSize(context, a.sizeBytes))
        a.pageCount?.let { add(pluralStringResource(R.plurals.paper_pages, it, it)) }
    }.joinToString(" · ")
    Text(details, color = c.textSecondary, style = MaterialTheme.typography.bodySmall)
    view.noteId?.let { noteId ->
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.attachment_from_note, view.noteTitle.ifBlank { stringResource(R.string.note_empty) }),
                color = c.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onOpenNote(noteId) }, colors = accentTextButtonColors()) { Text(stringResource(R.string.attachment_open_note)) }
        }
    }
    if (kind != AttachmentKind.Audio) {
        Spacer(Modifier.height(16.dp))
        OcrSection(view, progress, viewModel, onAdded, onMessage)
    }
}

@Composable
private fun OcrSection(
    view: AttachmentView,
    progress: app.drop.ocr.OcrProgress?,
    viewModel: AttachmentViewModel,
    onAdded: () -> Unit,
    onMessage: (Int) -> Unit,
) {
    val c = DropTheme.colors
    val a = view.attachment
    val clipboard = LocalClipboardManager.current
    Text(stringResource(R.string.ocr_section), color = c.textPrimary, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    val status = a.status
    val busy = status == OcrStatus.Pending || status == OcrStatus.Running
    val live = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("ocr-status")
    when {
        busy -> {
            val text = when {
                progress != null && progress.pages > 1 -> stringResource(R.string.ocr_running_pages, progress.page, progress.pages)
                status == OcrStatus.Running -> stringResource(R.string.ocr_running)
                else -> stringResource(R.string.ocr_pending)
            }
            Text(text, color = c.textSecondary, style = MaterialTheme.typography.bodyMedium, modifier = live)
            Spacer(Modifier.height(8.dp))
            if (progress != null && progress.pages > 1) {
                LinearProgressIndicator(progress = { progress.page / progress.pages.toFloat() }, modifier = Modifier.fillMaxWidth(), color = c.accentContent, trackColor = c.border)
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = c.accentContent, trackColor = c.border)
            }
        }
        status == OcrStatus.Failed -> {
            Text(stringResource(R.string.ocr_failed), color = c.textSecondary, style = MaterialTheme.typography.bodyMedium, modifier = live)
            ReadAgain(edited = false, onRetry = viewModel::retry)
        }
        a.ocrText.isNullOrBlank() && status == OcrStatus.Done -> {
            Text(stringResource(R.string.ocr_empty_tips), color = c.textSecondary, style = MaterialTheme.typography.bodyMedium, modifier = live)
            ReadAgain(edited = false, onRetry = viewModel::retry)
        }
        else -> {
            var text by rememberSaveable(a.id, a.ocrUpdatedAt) { mutableStateOf(a.ocrText.orEmpty()) }
            val changed = text.trim() != a.ocrText.orEmpty().trim()
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().testTag("ocr-text"),
                textStyle = MaterialTheme.typography.bodyMedium,
                colors = spacesFieldColors(),
            )
            if (a.ocrEdited) {
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.ocr_edited_hint), color = c.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (changed) {
                    OutlinedButton(onClick = {
                        viewModel.saveText(text)
                        onMessage(R.string.ocr_saved)
                    }, modifier = Modifier.testTag("ocr-save")) { Text(stringResource(R.string.ocr_save), color = c.textPrimary) }
                }
                OutlinedButton(onClick = {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(text.trim()))
                    onMessage(R.string.ocr_copied)
                }, modifier = Modifier.testTag("ocr-copy")) {
                    Icon(ActionIcons.Copy, contentDescription = null, tint = c.textPrimary)
                    Spacer(Modifier.padding(start = 6.dp))
                    Text(stringResource(R.string.ocr_copy), color = c.textPrimary)
                }
                if (view.noteId != null) {
                    OutlinedButton(onClick = { viewModel.addToNote(text, onAdded) }, modifier = Modifier.testTag("ocr-add")) {
                        Text(stringResource(R.string.ocr_add_to_note), color = c.textPrimary)
                    }
                }
            }
            ReadAgain(edited = a.ocrEdited, onRetry = viewModel::retry)
        }
    }
    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.ocr_language_note), color = c.textSecondary, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun ReadAgain(edited: Boolean, onRetry: () -> Unit) {
    val c = DropTheme.colors
    var confirm by remember { mutableStateOf(false) }
    TextButton(onClick = { if (edited) confirm = true else onRetry() }, colors = accentTextButtonColors(), modifier = Modifier.testTag("ocr-retry")) {
        Text(stringResource(R.string.ocr_retry))
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onRetry()
                }, colors = destructiveTextButtonColors(), modifier = Modifier.testTag("ocr-replace-confirm")) { Text(stringResource(R.string.ocr_replace_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }, colors = accentTextButtonColors()) { Text(stringResource(R.string.dialog_cancel)) } },
            title = { Text(stringResource(R.string.ocr_replace_title)) },
            text = { Text(stringResource(R.string.ocr_replace_body)) },
            containerColor = c.surfaceElevated,
            titleContentColor = c.textPrimary,
            textContentColor = c.textSecondary,
        )
    }
}
