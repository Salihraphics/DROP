package app.drop.ui.vault

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import app.drop.R
import app.drop.ui.theme.DropTheme
import app.drop.ui.theme.accentTextButtonColors
import app.drop.ui.theme.destructiveTextButtonColors

/** Edits one Vault note. It closes by itself when the Vault locks. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultEditorScreen(
    onClose: () -> Unit,
    viewModel: VaultEditorViewModel = viewModel(factory = VaultEditorViewModel.Factory),
) {
    val c = DropTheme.colors
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    if (viewModel.closed || viewModel.locked) LaunchedEffect(Unit) { onClose() }
    BackHandler(onBack = viewModel::close)
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.saveNow() }

    VaultSurface(onActivity = viewModel::touch) {
        Scaffold(
            modifier = Modifier.testTag("vault-editor"),
            containerColor = c.background,
            topBar = {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = viewModel::close) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.vault_back))
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { confirmDelete = true },
                            enabled = viewModel.loaded,
                            modifier = Modifier.testTag("vault-note-delete"),
                        ) { Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.vault_note_delete)) }
                        Button(
                            onClick = viewModel::close,
                            modifier = Modifier.padding(start = 4.dp, end = 8.dp).testTag("vault-done"),
                            enabled = viewModel.loaded,
                            colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                        ) { Text(stringResource(R.string.note_done)) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = c.background,
                        scrolledContainerColor = c.background,
                        navigationIconContentColor = c.textPrimary,
                        actionIconContentColor = c.textPrimary,
                    ),
                )
            },
        ) { padding ->
            if (viewModel.loaded) {
                Fields(viewModel, Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding())
            }
        }
        if (confirmDelete && viewModel.loaded) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmDelete = false
                            viewModel.delete()
                        },
                        colors = destructiveTextButtonColors(),
                        modifier = Modifier.testTag("vault-note-delete-confirm"),
                    ) { Text(stringResource(R.string.vault_delete_confirm)) }
                },
                modifier = Modifier.padding(horizontal = 28.dp).widthIn(max = 560.dp),
                dismissButton = {
                    TextButton(onClick = { confirmDelete = false }, colors = accentTextButtonColors()) {
                        Text(stringResource(R.string.dialog_cancel))
                    }
                },
                title = { Text(stringResource(R.string.vault_note_delete_title)) },
                text = { Text(stringResource(R.string.vault_note_delete_body)) },
                containerColor = c.surfaceElevated,
                titleContentColor = c.textPrimary,
                textContentColor = c.textSecondary,
                properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn, usePlatformDefaultWidth = false),
            )
        }
    }
}

@Composable
private fun Fields(viewModel: VaultEditorViewModel, modifier: Modifier = Modifier) {
    val c = DropTheme.colors
    val focusManager = LocalFocusManager.current
    val bodyFocus = remember { FocusRequester() }
    val cursor = SolidColor(c.accentContent)
    LaunchedEffect(Unit) {
        if (viewModel.title.isEmpty() && viewModel.body.isEmpty()) bodyFocus.requestFocus()
    }
    val keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
        val titleStyle = MaterialTheme.typography.headlineSmall.copy(color = c.textPrimary)
        BasicTextField(
            value = viewModel.title,
            onValueChange = viewModel::onTitleChange,
            modifier = Modifier.fillMaxWidth().testTag("vault-note-title"),
            textStyle = titleStyle,
            keyboardOptions = keyboard.copy(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            singleLine = true,
            cursorBrush = cursor,
            decorationBox = { inner ->
                Box {
                    Placeholder(viewModel.title.isEmpty(), stringResource(R.string.note_title_hint), titleStyle)
                    inner()
                }
            },
        )
        Spacer(Modifier.height(6.dp))
        Status(viewModel)
        Spacer(Modifier.height(16.dp))
        val bodyStyle = MaterialTheme.typography.bodyLarge.copy(color = c.textPrimary)
        BasicTextField(
            value = viewModel.body,
            onValueChange = viewModel::onBodyChange,
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 240.dp).focusRequester(bodyFocus).testTag("vault-note-body"),
            textStyle = bodyStyle,
            keyboardOptions = keyboard,
            cursorBrush = cursor,
            decorationBox = { inner ->
                Box {
                    Placeholder(viewModel.body.isEmpty(), stringResource(R.string.note_body_hint), bodyStyle)
                    inner()
                }
            },
        )
    }
}

/** "Saved, encrypted", or a warning when the last save failed. */
@Composable
private fun Status(viewModel: VaultEditorViewModel) {
    val c = DropTheme.colors
    val (text, color) = when {
        viewModel.saveFailed -> stringResource(R.string.vault_save_failed) to c.error
        viewModel.saved -> stringResource(R.string.vault_saved) to c.textSecondary
        else -> stringResource(R.string.vault_title) to c.textSecondary
    }
    Row(
        Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("vault-status"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(14.dp), tint = color)
        Spacer(Modifier.width(6.dp))
        Text(text, color = color, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun Placeholder(show: Boolean, text: String, style: TextStyle) {
    if (show) Text(text, style = style, color = DropTheme.colors.textSecondary)
}
