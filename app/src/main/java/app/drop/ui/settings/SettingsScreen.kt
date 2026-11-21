package app.drop.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.drop.DropApplication
import app.drop.R
import app.drop.data.BackupError
import app.drop.data.NoteFont
import app.drop.data.ThemeMode
import app.drop.ui.notes.rememberTimestampFormatter
import app.drop.ui.theme.DropLogoTile
import app.drop.ui.theme.DropSnackbarHost
import app.drop.ui.theme.DropTheme
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val c = DropTheme.colors
    val app = LocalContext.current.applicationContext as DropApplication
    val settings = app.settings
    val theme by settings.themeMode.collectAsStateWithLifecycle()
    val font by settings.noteFont.collectAsStateWithLifecycle()
    val lastBackup by settings.lastBackupAt.collectAsStateWithLifecycle()
    val formatter = rememberTimestampFormatter()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(vm) {
        vm.messages.collect { message ->
            val text = when (message) {
                is BackupMessage.Exported -> context.resources.getQuantityString(R.plurals.backup_exported, message.count, message.count)
                BackupMessage.ExportFailed -> context.getString(R.string.backup_export_failed)
                is BackupMessage.Imported -> context.getString(R.string.backup_imported, message.added, message.skipped)
                is BackupMessage.ImportFailed -> when (val e = message.error) {
                    BackupError.NotADropBackup -> context.getString(R.string.backup_error_not_drop)
                    is BackupError.UnsupportedVersion -> context.getString(R.string.backup_error_version, e.version)
                    is BackupError.Malformed -> context.getString(R.string.backup_error_malformed)
                    BackupError.TooLarge -> context.getString(R.string.backup_error_too_large)
                    null -> context.getString(R.string.backup_error_read)
                }
            }
            snackbar.showSnackbar(text)
        }
    }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(vm::export) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::import) }

    Scaffold(
        modifier = Modifier.testTag("settings"),
        containerColor = c.background,
        snackbarHost = { DropSnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.background, titleContentColor = c.textPrimary),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back), tint = c.textPrimary)
                    }
                },
                title = { Text(stringResource(R.string.settings_title)) },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Section(stringResource(R.string.settings_appearance)) {
                Options(
                    ThemeMode.entries,
                    selected = theme,
                    label = {
                        when (it) {
                            ThemeMode.System -> stringResource(R.string.theme_system)
                            ThemeMode.Light -> stringResource(R.string.theme_light)
                            ThemeMode.Dark -> stringResource(R.string.theme_dark)
                        }
                    },
                    tag = { "theme-${it.name}" },
                    onSelect = vm::setThemeMode,
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.settings_note_font), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                Text(stringResource(R.string.settings_note_font_body), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                Options(
                    NoteFont.entries,
                    selected = font,
                    label = {
                        when (it) {
                            NoteFont.Default -> stringResource(R.string.note_font_default)
                            NoteFont.Serif -> stringResource(R.string.note_font_serif)
                            NoteFont.Mono -> stringResource(R.string.note_font_mono)
                        }
                    },
                    tag = { "note-font-${it.name}" },
                    onSelect = settings::setNoteFont,
                )
            }

            Section(stringResource(R.string.settings_backup)) {
                Text(stringResource(R.string.backup_explainer), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                Text(stringResource(R.string.settings_backup_attachments), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                Text(
                    stringResource(R.string.backup_last, lastBackup?.let { formatter.format(it) } ?: stringResource(R.string.backup_never)),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { exporter.launch("drop-backup-${LocalDate.now()}.json") },
                        enabled = !vm.busy,
                        colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                        modifier = Modifier.testTag("backup-export"),
                    ) { Text(stringResource(R.string.backup_export)) }
                    OutlinedButton(
                        onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                        enabled = !vm.busy,
                        modifier = Modifier.testTag("backup-import"),
                    ) { Text(stringResource(R.string.backup_import), color = c.textPrimary) }
                    if (vm.busy) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = c.accentContent, strokeWidth = 2.dp)
                        Text(stringResource(R.string.backup_working), color = c.textSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(stringResource(R.string.backup_import_explainer), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }

            Section(stringResource(R.string.settings_your_data)) {
                listOf(R.string.data_local, R.string.data_no_account, R.string.data_google_backup, R.string.data_device_transfer, R.string.data_loss, R.string.data_backup_private)
                    .forEach { Text("• " + stringResource(it), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary) }
            }

            Section(stringResource(R.string.settings_about)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DropLogoTile(40.dp)
                    Column {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                        Text(stringResource(R.string.about_version, vm.appVersion), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                }
                Text(stringResource(R.string.about_description), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
internal fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    val c = DropTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.accentContent)
        content()
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = c.border)
    }
}

@Composable
internal fun <T> Options(values: List<T>, selected: T, label: @Composable (T) -> String, tag: (T) -> String, onSelect: (T) -> Unit) {
    val c = DropTheme.colors
    Column(Modifier.selectableGroup()) {
        values.forEach { value ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(selected = value == selected, onClick = { onSelect(value) }, role = Role.RadioButton)
                    .padding(vertical = 6.dp)
                    .testTag(tag(value)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = value == selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = c.accentContent, unselectedColor = c.textSecondary))
                Text(label(value), modifier = Modifier.padding(start = 12.dp), color = c.textPrimary)
            }
        }
    }
}
