package app.drop.ui.vault

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.drop.R
import app.drop.data.Note
import app.drop.search.FoldedText
import app.drop.search.SearchQuery
import app.drop.ui.notes.NoteCard
import app.drop.ui.notes.rememberTimestampFormatter
import app.drop.ui.spaces.spacesFieldColors
import app.drop.ui.theme.DropSnackbarHost
import app.drop.ui.theme.DropTheme
import app.drop.ui.theme.accentTextButtonColors
import app.drop.ui.theme.destructiveTextButtonColors
import app.drop.vault.VaultListing
import app.drop.vault.VaultNote
import app.drop.vault.VaultState
import kotlinx.coroutines.launch

fun filterVaultNotes(notes: List<VaultNote>, query: SearchQuery): List<VaultNote> =
    if (query.isEmpty()) notes else notes.filter { query.matches(FoldedText(it.title), FoldedText(it.body)) }

/** Lets the Vault list reuse the regular note card. */
private fun VaultNote.asCardNote(): Note = Note(title = title, body = body, createdAt = createdAt, updatedAt = updatedAt)

/** The Vault: set up, unlock, then a list of encrypted notes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    onBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    onNewNote: () -> Unit,
    viewModel: VaultViewModel = viewModel(factory = VaultViewModel.Factory),
) {
    val c = DropTheme.colors
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listing by viewModel.listing.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var dialog by rememberSaveable { mutableStateOf<VaultDialog?>(null) }
    val unlocked = state is VaultState.Unlocked

    val closeSearch = {
        searching = false
        query = ""
    }
    val leave = {
        viewModel.lock()
        onBack()
    }
    BackHandler { if (searching) closeSearch() else leave() }
    LaunchedEffect(unlocked) {
        if (!unlocked) {
            closeSearch()
            if (dialog == VaultDialog.ChangePassphrase || dialog == VaultDialog.Delete) dialog = null
        }
    }
    val finish: (Int) -> Unit = { message ->
        dialog = null
        scope.launch { snackbar.showSnackbar(context.getString(message)) }
    }
    val dismiss = {
        dialog = null
        viewModel.clearError()
    }

    VaultSurface(onActivity = viewModel::touch) {
        Scaffold(
            modifier = Modifier.testTag("vault"),
            containerColor = c.background,
            topBar = {
                if (searching && unlocked) {
                    VaultSearchBar(query, onQueryChange = { query = it }, onClose = closeSearch)
                } else {
                    VaultTopBar(
                        unlocked = unlocked,
                        onBack = leave,
                        onSearch = { searching = true },
                        onLock = viewModel::lock,
                        onChangePassphrase = { dialog = VaultDialog.ChangePassphrase },
                        onDelete = { dialog = VaultDialog.Delete },
                    )
                }
            },
            snackbarHost = { DropSnackbarHost(snackbar) },
            floatingActionButton = {
                if (unlocked && listing != null && !searching) {
                    FloatingActionButton(
                        onClick = onNewNote,
                        containerColor = c.accent,
                        contentColor = c.onAccent,
                        modifier = Modifier.testTag("vault-new"),
                    ) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.vault_new_note)) }
                }
            },
        ) { innerPadding ->
            val modifier = Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding).imePadding()
            when (state) {
                VaultState.Unknown -> Unit
                VaultState.Unavailable -> CenteredMessage(
                    stringResource(R.string.vault_unavailable_title),
                    stringResource(R.string.vault_unavailable_body),
                    modifier,
                )
                VaultState.Absent -> SetupContent(viewModel, modifier)
                VaultState.Locked -> UnlockContent(viewModel, onForgot = { dialog = VaultDialog.Forgot }, modifier = modifier)
                is VaultState.Unlocked -> listing?.let {
                    VaultList(it, if (searching) query else "", onOpenNote, modifier)
                }
            }
        }
        when (dialog) {
            null -> Unit
            VaultDialog.ChangePassphrase -> ChangePassphraseDialog(viewModel, onDone = { finish(R.string.vault_changed) }, onDismiss = dismiss)
            VaultDialog.Delete -> DeleteVaultDialog(viewModel, onDone = { finish(R.string.vault_deleted) }, onDismiss = dismiss)
            VaultDialog.Forgot -> ForgotDialog(
                busy = viewModel.busy,
                onConfirm = { viewModel.eraseForgotten { finish(R.string.vault_erased) } },
                onDismiss = dismiss,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultTopBar(
    unlocked: Boolean,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onLock: () -> Unit,
    onChangePassphrase: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = DropTheme.colors
    TopAppBar(
        title = { Text(stringResource(R.string.vault_title)) },
        navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.testTag("vault-back")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.vault_back))
            }
        },
        actions = {
            if (unlocked) {
                IconButton(onClick = onSearch, modifier = Modifier.testTag("vault-search")) {
                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.vault_search))
                }
                IconButton(onClick = onLock, modifier = Modifier.testTag("vault-lock")) {
                    Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.vault_lock))
                }
                var open by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { open = true }, modifier = Modifier.testTag("vault-more")) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.vault_more))
                    }
                    DropdownMenu(
                        expanded = open,
                        onDismissRequest = { open = false },
                        properties = PopupProperties(focusable = true, securePolicy = SecureFlagPolicy.SecureOn),
                        containerColor = c.surfaceElevated,
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.vault_change_passphrase), color = c.textPrimary) },
                            onClick = {
                                open = false
                                onChangePassphrase()
                            },
                            modifier = Modifier.testTag("vault-change"),
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.vault_delete), color = c.error) },
                            onClick = {
                                open = false
                                onDelete()
                            },
                            modifier = Modifier.testTag("vault-delete"),
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = c.background,
            scrolledContainerColor = c.background,
            titleContentColor = c.textPrimary,
            navigationIconContentColor = c.textPrimary,
            actionIconContentColor = c.textPrimary,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultSearchBar(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val c = DropTheme.colors
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focus.requestFocus() }
    TopAppBar(
        title = {
            val style = MaterialTheme.typography.titleMedium.copy(color = c.textPrimary)
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("vault-search-field"),
                textStyle = style,
                singleLine = true,
                cursorBrush = SolidColor(c.accentContent),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) Text(stringResource(R.string.vault_search), style = style, color = c.textSecondary)
                        inner()
                    }
                },
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.home_search_close))
            }
        },
        actions = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.home_search_clear))
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = c.background,
            scrolledContainerColor = c.background,
            titleContentColor = c.textPrimary,
            navigationIconContentColor = c.textPrimary,
            actionIconContentColor = c.textPrimary,
        ),
    )
}

@Composable
private fun VaultList(listing: VaultListing, query: String, onOpenNote: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = DropTheme.colors
    val formatter = rememberTimestampFormatter()
    val search = remember(query) { SearchQuery(query) }
    val notes = remember(listing, search) { filterVaultNotes(listing.notes, search) }
    LazyColumn(
        modifier = modifier.testTag("vault-list"),
        contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (listing.unreadable > 0) {
            item("unreadable") {
                Notice(pluralStringResource(R.plurals.vault_unreadable, listing.unreadable, listing.unreadable), warning = true)
            }
        }
        if (search.isEmpty()) item("info") { Notice(stringResource(R.string.vault_lock_info)) }
        when {
            listing.notes.isEmpty() -> item("empty") {
                CenteredMessage(
                    stringResource(R.string.vault_empty_title),
                    stringResource(R.string.vault_empty_body),
                    Modifier.fillMaxWidth().padding(top = 48.dp),
                )
            }
            notes.isEmpty() -> item("no-results") {
                Text(
                    stringResource(R.string.vault_search_no_results, query.trim()),
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                    color = c.textSecondary,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            else -> items(notes, key = { it.id }) { note ->
                val cardNote = remember(note) { note.asCardNote() }
                NoteCard(
                    cardNote,
                    formatter.format(note.updatedAt),
                    modifier = Modifier.testTag("vault-note-${note.id}"),
                    showPin = false,
                    query = search.takeUnless { it.isEmpty() },
                    onClick = { onOpenNote(note.id) },
                )
            }
        }
    }
}

@Composable
private fun Notice(text: String, warning: Boolean = false) {
    val c = DropTheme.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (warning) c.surface else c.accentSubtle,
        border = BorderStroke(1.dp, c.border),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (warning) Icons.Filled.Warning else Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (warning) c.warning else c.textSecondary,
            )
            Spacer(Modifier.width(10.dp))
            Text(text, color = c.textPrimary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CenteredMessage(title: String, body: String, modifier: Modifier = Modifier) {
    val c = DropTheme.colors
    Box(modifier.padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = c.textPrimary, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(body, color = c.textSecondary, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun FormColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

@Composable
private fun LockBadge() {
    val c = DropTheme.colors
    Surface(Modifier.size(64.dp), shape = RoundedCornerShape(20.dp), color = c.accent) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(30.dp), tint = c.onAccent)
        }
    }
}

@Composable
private fun SetupContent(viewModel: VaultViewModel, modifier: Modifier = Modifier) {
    val c = DropTheme.colors
    var passphrase by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var acknowledged by rememberSaveable { mutableStateOf(false) }
    val submit = {
        if (acknowledged) viewModel.create(passphrase, confirmation)
    }
    FormColumn(modifier.testTag("vault-setup")) {
        Spacer(Modifier.height(24.dp))
        LockBadge()
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.vault_setup_title),
            modifier = Modifier.semantics { heading() },
            color = c.textPrimary,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.vault_setup_body),
            color = c.textSecondary,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(20.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = c.surface,
            border = BorderStroke(1.dp, c.border),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(20.dp), tint = c.warning)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.vault_setup_warning_title), color = c.textPrimary, style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.vault_setup_warning), color = c.textSecondary, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(value = acknowledged, role = Role.Checkbox, onValueChange = { acknowledged = it })
                        .testTag("vault-acknowledge"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = acknowledged,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(checkedColor = c.accent, uncheckedColor = c.textSecondary, checkmarkColor = c.onAccent),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.vault_setup_acknowledge), color = c.textPrimary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        PassphraseField(
            passphrase,
            { passphrase = it },
            stringResource(R.string.vault_passphrase),
            ImeAction.Next,
            modifier = Modifier.testTag("vault-passphrase"),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            pluralStringResource(R.plurals.vault_passphrase_hint, PassphrasePolicy.MIN_LENGTH, PassphrasePolicy.MIN_LENGTH),
            modifier = Modifier.fillMaxWidth(),
            color = c.textSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(12.dp))
        PassphraseField(
            confirmation,
            { confirmation = it },
            stringResource(R.string.vault_passphrase_confirm),
            ImeAction.Done,
            onDone = submit,
            modifier = Modifier.testTag("vault-confirm"),
        )
        ErrorLine(viewModel.error)
        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            stringResource(if (viewModel.busy) R.string.vault_working else R.string.vault_create),
            enabled = acknowledged && passphrase.isNotEmpty() && confirmation.isNotEmpty() && !viewModel.busy,
            onClick = submit,
            tag = "vault-create",
        )
    }
}

@Composable
private fun UnlockContent(viewModel: VaultViewModel, onForgot: () -> Unit, modifier: Modifier = Modifier) {
    val c = DropTheme.colors
    var passphrase by remember { mutableStateOf("") }
    val submit = {
        if (passphrase.isNotEmpty()) {
            viewModel.unlock(passphrase)
            passphrase = ""
        }
    }
    FormColumn(modifier.testTag("vault-locked")) {
        Spacer(Modifier.height(24.dp))
        LockBadge()
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.vault_locked_title),
            modifier = Modifier.semantics { heading() },
            color = c.textPrimary,
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.vault_locked_body),
            color = c.textSecondary,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
        PassphraseField(
            passphrase,
            {
                passphrase = it
                viewModel.clearError()
            },
            stringResource(R.string.vault_passphrase),
            ImeAction.Done,
            onDone = submit,
            autoFocus = true,
            modifier = Modifier.testTag("vault-passphrase"),
        )
        ErrorLine(viewModel.error)
        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            stringResource(if (viewModel.busy) R.string.vault_working else R.string.vault_unlock),
            enabled = passphrase.isNotEmpty() && !viewModel.busy,
            onClick = submit,
            tag = "vault-unlock",
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onForgot, colors = accentTextButtonColors(), modifier = Modifier.testTag("vault-forgot")) {
            Text(stringResource(R.string.vault_forgot))
        }
    }
}

@Composable
private fun PrimaryButton(text: String, enabled: Boolean, onClick: () -> Unit, tag: String) {
    val c = DropTheme.colors
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = c.accent,
            contentColor = c.onAccent,
            disabledContainerColor = c.surface,
            disabledContentColor = c.disabled,
        ),
    ) { Text(text) }
}

@Composable
private fun ErrorLine(error: VaultError?) {
    if (error == null) return
    val text = when (error) {
        VaultError.WrongPassphrase -> stringResource(R.string.vault_error_wrong)
        VaultError.TooManyAttempts -> stringResource(R.string.vault_error_cooldown)
        VaultError.Mismatch -> stringResource(R.string.vault_error_mismatch)
        VaultError.TooShort -> pluralStringResource(R.plurals.vault_error_short, PassphrasePolicy.MIN_LENGTH, PassphrasePolicy.MIN_LENGTH)
        VaultError.Failed -> stringResource(R.string.vault_error_failed)
    }
    Spacer(Modifier.height(8.dp))
    Text(
        text,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }.testTag("vault-error"),
        color = DropTheme.colors.error,
        style = MaterialTheme.typography.bodyMedium,
    )
}

/** A passphrase box: hidden by default, never autocorrected or suggested. */
@Composable
internal fun PassphraseField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    imeAction: ImeAction,
    onDone: () -> Unit = {},
    autoFocus: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    if (autoFocus) LaunchedEffect(Unit) { focus.requestFocus() }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().focusRequester(focus),
        label = { Text(label) },
        trailingIcon = {
            val description = stringResource(if (visible) R.string.vault_hide_passphrase else R.string.vault_show_passphrase)
            TextButton(
                onClick = { visible = !visible },
                colors = accentTextButtonColors(),
                modifier = Modifier.semantics { contentDescription = description },
            ) { Text(stringResource(if (visible) R.string.vault_hide else R.string.vault_show)) }
        },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        singleLine = true,
        colors = spacesFieldColors(),
    )
}

/** A dialog that keeps screenshots out, like the Vault screens. */
@Composable
internal fun VaultAlert(
    title: String,
    confirmLabel: String,
    confirmEnabled: Boolean,
    destructive: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = DropTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = confirmEnabled,
                colors = if (destructive) destructiveTextButtonColors() else accentTextButtonColors(),
                modifier = Modifier.testTag("vault-dialog-confirm"),
            ) { Text(confirmLabel) }
        },
        modifier = Modifier.padding(horizontal = 28.dp).widthIn(max = 560.dp),
        dismissButton = {
            TextButton(onClick = onDismiss, colors = accentTextButtonColors()) { Text(stringResource(R.string.dialog_cancel)) }
        },
        title = { Text(title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), content = content) },
        containerColor = c.surfaceElevated,
        titleContentColor = c.textPrimary,
        textContentColor = c.textSecondary,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn, usePlatformDefaultWidth = false),
    )
}

@Composable
private fun ChangePassphraseDialog(viewModel: VaultViewModel, onDone: () -> Unit, onDismiss: () -> Unit) {
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val submit = { viewModel.changePassphrase(current, new, confirmation, onDone) }
    VaultAlert(
        title = stringResource(R.string.vault_change_passphrase),
        confirmLabel = stringResource(if (viewModel.busy) R.string.vault_working else R.string.vault_change_confirm),
        confirmEnabled = current.isNotEmpty() && new.isNotEmpty() && confirmation.isNotEmpty() && !viewModel.busy,
        destructive = false,
        onConfirm = submit,
        onDismiss = onDismiss,
    ) {
        Text(stringResource(R.string.vault_change_body))
        Spacer(Modifier.height(12.dp))
        PassphraseField(current, { current = it }, stringResource(R.string.vault_current_passphrase), ImeAction.Next, autoFocus = true, modifier = Modifier.testTag("vault-current"))
        Spacer(Modifier.height(8.dp))
        PassphraseField(new, { new = it }, stringResource(R.string.vault_new_passphrase), ImeAction.Next, modifier = Modifier.testTag("vault-new-passphrase"))
        Spacer(Modifier.height(8.dp))
        PassphraseField(
            confirmation,
            { confirmation = it },
            stringResource(R.string.vault_repeat_new_passphrase),
            ImeAction.Done,
            onDone = submit,
            modifier = Modifier.testTag("vault-repeat-passphrase"),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            pluralStringResource(R.plurals.vault_passphrase_hint, PassphrasePolicy.MIN_LENGTH, PassphrasePolicy.MIN_LENGTH),
            style = MaterialTheme.typography.bodySmall,
        )
        ErrorLine(viewModel.error)
    }
}

@Composable
private fun DeleteVaultDialog(viewModel: VaultViewModel, onDone: () -> Unit, onDismiss: () -> Unit) {
    var passphrase by remember { mutableStateOf("") }
    val submit = {
        if (passphrase.isNotEmpty()) viewModel.deleteVault(passphrase, onDone)
    }
    VaultAlert(
        title = stringResource(R.string.vault_delete_title),
        confirmLabel = stringResource(if (viewModel.busy) R.string.vault_working else R.string.vault_delete_confirm),
        confirmEnabled = passphrase.isNotEmpty() && !viewModel.busy,
        destructive = true,
        onConfirm = submit,
        onDismiss = onDismiss,
    ) {
        Text(stringResource(R.string.vault_delete_body))
        Spacer(Modifier.height(12.dp))
        PassphraseField(
            passphrase,
            { passphrase = it },
            stringResource(R.string.vault_passphrase),
            ImeAction.Done,
            onDone = submit,
            autoFocus = true,
            modifier = Modifier.testTag("vault-delete-passphrase"),
        )
        ErrorLine(viewModel.error)
    }
}

@Composable
private fun ForgotDialog(busy: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val word = stringResource(R.string.vault_erase_word)
    var typed by remember { mutableStateOf("") }
    val matches = FoldedText.fold(typed.trim()) == FoldedText.fold(word)
    VaultAlert(
        title = stringResource(R.string.vault_forgot_title),
        confirmLabel = stringResource(if (busy) R.string.vault_working else R.string.vault_erase_confirm),
        confirmEnabled = matches && !busy,
        destructive = true,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    ) {
        Text(stringResource(R.string.vault_forgot_body))
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = typed,
            onValueChange = { typed = it },
            modifier = Modifier.fillMaxWidth().testTag("vault-erase-word"),
            label = { Text(stringResource(R.string.vault_forgot_type, word)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
            colors = spacesFieldColors(),
        )
    }
}
