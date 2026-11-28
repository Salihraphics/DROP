package app.drop.ui.paper

import android.content.ActivityNotFoundException
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import app.drop.DropApplication
import app.drop.R
import app.drop.attach.FileImportSource
import app.drop.attach.ImportError
import app.drop.attach.UriImportSource
import app.drop.ui.attach.FilePreview
import app.drop.ui.icons.ActionIcons
import app.drop.ui.spaces.spacesFieldColors
import app.drop.ui.theme.DropTheme
import app.drop.ui.theme.accentTextButtonColors
import java.io.File
import java.util.UUID

/** The FileProvider that hands the camera app a file to write, and other apps a file to open. */
fun fileProviderAuthority(packageName: String) = "$packageName.files"

/**
 * Paper Capture: a photo taken now, an image from the gallery or a PDF becomes a note (or joins
 * one). [onSaved] gets the note and whether the file was attached to an existing note.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaperScreen(
    onBack: () -> Unit,
    onSaved: (noteId: Long, attached: Boolean) -> Unit,
    viewModel: PaperViewModel = viewModel(factory = PaperViewModel.Factory),
) {
    val c = DropTheme.colors
    val context = LocalContext.current
    val state = viewModel.state
    var noCamera by rememberSaveable { mutableStateOf(false) }
    (state as? PaperState.Saved)?.let { saved ->
        LaunchedEffect(saved) { onSaved(saved.noteId, viewModel.attachTo != null) }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.import(UriImportSource(context.contentResolver, uri))
    }
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.import(UriImportSource(context.contentResolver, uri))
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val file = viewModel.pendingCameraFile ?: return@rememberLauncherForActivityResult
        viewModel.pendingCameraFile = null
        if (taken && file.length() > 0) {
            viewModel.import(FileImportSource(file, "Photo.jpg"), afterwards = { file.delete() })
        } else {
            file.delete()
        }
    }
    val startCamera = {
        val file = File(File(context.cacheDir, "camera").apply { mkdirs() }, "${UUID.randomUUID()}.jpg")
        viewModel.pendingCameraFile = file
        try {
            takePhoto.launch(FileProvider.getUriForFile(context, fileProviderAuthority(context.packageName), file))
        } catch (e: ActivityNotFoundException) {
            viewModel.pendingCameraFile = null
            noCamera = true
        }
    }
    BackHandler(enabled = state is PaperState.Preview) { viewModel.cancelPreview() }

    Scaffold(
        modifier = Modifier.testTag("paper"),
        containerColor = c.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.paper_title)) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state is PaperState.Preview) viewModel.cancelPreview() else onBack()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = c.background,
                    titleContentColor = c.textPrimary,
                    navigationIconContentColor = c.textPrimary,
                ),
            )
        },
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                when (state) {
                    PaperState.Choose -> {
                        Text(stringResource(R.string.paper_intro), color = c.textSecondary, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(16.dp))
                        SourceOption(ActionIcons.Camera, stringResource(R.string.paper_take_photo), stringResource(R.string.paper_take_photo_body), "paper-camera", startCamera)
                        Spacer(Modifier.height(10.dp))
                        SourceOption(ActionIcons.Image, stringResource(R.string.paper_choose_image), stringResource(R.string.paper_choose_image_body), "paper-image") {
                            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                        Spacer(Modifier.height(10.dp))
                        SourceOption(ActionIcons.Pdf, stringResource(R.string.paper_choose_pdf), stringResource(R.string.paper_choose_pdf_body), "paper-pdf") {
                            pickPdf.launch(arrayOf("application/pdf"))
                        }
                    }
                    PaperState.Importing, PaperState.Saving, is PaperState.Saved -> {
                        Spacer(Modifier.height(32.dp))
                        Text(
                            stringResource(R.string.paper_importing),
                            color = c.textSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = c.accentContent, trackColor = c.border)
                    }
                    is PaperState.Preview -> Preview(state, attaching = viewModel.attachTo != null, onCancel = viewModel::cancelPreview, onSave = viewModel::save)
                }
                val problem = viewModel.error
                if (problem != null || viewModel.saveFailed || noCamera) {
                    Spacer(Modifier.height(16.dp))
                    val text = when {
                        noCamera -> stringResource(R.string.paper_no_camera)
                        viewModel.saveFailed -> stringResource(R.string.paper_error_save)
                        else -> importErrorText(problem!!)
                    }
                    ErrorCard(text) {
                        noCamera = false
                        viewModel.clearError()
                    }
                }
            }
        }
    }
}

@Composable
private fun importErrorText(error: ImportError): String {
    val context = LocalContext.current
    return when (error) {
        ImportError.Unsupported -> stringResource(R.string.paper_error_unsupported)
        is ImportError.TooLarge -> stringResource(R.string.paper_error_too_large, Formatter.formatShortFileSize(context, error.limitBytes))
        ImportError.UnreadablePdf -> stringResource(R.string.paper_error_pdf)
        ImportError.Empty -> stringResource(R.string.paper_error_empty)
        ImportError.LowStorage -> stringResource(R.string.paper_error_storage)
        ImportError.ReadFailed -> stringResource(R.string.paper_error_read)
    }
}

@Composable
private fun SourceOption(icon: ImageVector, title: String, body: String, tag: String, onClick: () -> Unit) {
    val c = DropTheme.colors
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button, onClick = onClick).testTag(tag),
        shape = RoundedCornerShape(16.dp),
        color = c.surface,
        border = BorderStroke(1.dp, c.border),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(44.dp), shape = RoundedCornerShape(12.dp), color = c.accent) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(24.dp)) }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = c.textPrimary, style = MaterialTheme.typography.titleSmall)
                Text(body, color = c.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun Preview(state: PaperState.Preview, attaching: Boolean, onCancel: () -> Unit, onSave: (String) -> Unit) {
    val c = DropTheme.colors
    val context = LocalContext.current
    val staged = state.staged
    val file = (context.applicationContext as DropApplication).attachments.store.file(staged.partName)
    var title by rememberSaveable { mutableStateOf("") }
    FilePreview(file, staged.kind, contentDescription = staged.displayName)
    Spacer(Modifier.height(10.dp))
    val details = buildList {
        add(staged.displayName)
        add(Formatter.formatShortFileSize(context, staged.sizeBytes))
        staged.pageCount?.let { add(pluralStringResource(R.plurals.paper_pages, it, it)) }
    }.joinToString(" · ")
    Text(details, color = c.textSecondary, style = MaterialTheme.typography.bodySmall)
    if (!attaching) {
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth().testTag("paper-title"),
            label = { Text(stringResource(R.string.paper_title_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = spacesFieldColors(),
        )
    }
    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.paper_ocr_note), color = c.textSecondary, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(20.dp))
    val defaultTitle = stringResource(R.string.paper_default_title)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onCancel, colors = accentTextButtonColors(), modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.dialog_cancel))
        }
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = { onSave(title.ifBlank { defaultTitle }) },
            modifier = Modifier.heightIn(min = 48.dp).testTag("paper-save"),
            colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
        ) { Text(stringResource(if (attaching) R.string.paper_attach else R.string.paper_save)) }
    }
}

@Composable
private fun ErrorCard(text: String, onDismiss: () -> Unit) {
    val c = DropTheme.colors
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("paper-error"),
        shape = RoundedCornerShape(12.dp),
        color = c.surface,
        border = BorderStroke(1.dp, c.error),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(text, color = c.textPrimary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            TextButton(onClick = onDismiss, colors = accentTextButtonColors(), modifier = Modifier.align(Alignment.End)) { Text(stringResource(android.R.string.ok)) }
        }
    }
}
