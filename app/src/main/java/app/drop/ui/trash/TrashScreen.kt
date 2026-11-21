package app.drop.ui.trash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.drop.R
import app.drop.ui.notes.NoteCard
import app.drop.ui.notes.rememberTimestampFormatter
import app.drop.ui.theme.DropSnackbarHost
import app.drop.ui.theme.DropTheme
import app.drop.ui.theme.accentTextButtonColors
import app.drop.ui.theme.destructiveTextButtonColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(onBack: () -> Unit, vm: TrashViewModel = viewModel(factory = TrashViewModel.Factory)) {
    val c = DropTheme.colors
    val notes by vm.notes.collectAsStateWithLifecycle()
    val formatter = rememberTimestampFormatter()
    var confirmDelete by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmEmpty by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val restored = stringResource(R.string.trash_restored)
    Scaffold(
        modifier = Modifier.testTag("trash"),
        containerColor = c.background,
        snackbarHost = { DropSnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.background, titleContentColor = c.textPrimary),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.trash_back), tint = c.textPrimary)
                    }
                },
                title = { Text(stringResource(R.string.trash_title)) },
                actions = {
                    if (!notes.isNullOrEmpty()) {
                        TextButton(onClick = { confirmEmpty = true }, colors = destructiveTextButtonColors()) { Text(stringResource(R.string.trash_empty_action)) }
                    }
                },
            )
        },
    ) { padding ->
        val list = notes
        when {
            list == null -> Unit
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.trash_empty_state), color = c.textSecondary, style = MaterialTheme.typography.bodyLarge)
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { Text(stringResource(R.string.trash_explainer), color = c.textSecondary, style = MaterialTheme.typography.bodyMedium) }
                items(list, key = { it.id }) { note ->
                    NoteCard(
                        note = note,
                        meta = note.deletedAt?.let { stringResource(R.string.trash_deleted_on, formatter.format(it)) }.orEmpty(),
                        showPin = false,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                vm.restore(note.id)
                                scope.launch { snackbar.showSnackbar(restored) }
                            }, colors = accentTextButtonColors()) { Text(stringResource(R.string.trash_restore)) }
                            TextButton(onClick = { confirmDelete = note.id }, colors = destructiveTextButtonColors()) {
                                Text(stringResource(R.string.trash_delete_forever))
                            }
                        }
                    }
                }
            }
        }
    }
    confirmDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.trash_confirm_delete_title)) },
            text = { Text(stringResource(R.string.trash_confirm_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteForever(id)
                    confirmDelete = null
                }, colors = destructiveTextButtonColors()) { Text(stringResource(R.string.trash_delete_forever)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }, colors = accentTextButtonColors()) { Text(stringResource(R.string.dialog_cancel)) } },
        )
    }
    if (confirmEmpty) {
        val count = notes?.size ?: 0
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text(stringResource(R.string.trash_confirm_empty_title)) },
            text = { Text(pluralStringResource(R.plurals.trash_confirm_empty_body, count, count)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.emptyTrash()
                    confirmEmpty = false
                }, colors = destructiveTextButtonColors()) { Text(stringResource(R.string.trash_empty_action)) }
            },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }, colors = accentTextButtonColors()) { Text(stringResource(R.string.dialog_cancel)) } },
        )
    }
}
