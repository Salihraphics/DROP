package app.drop.ui.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.drop.DropApplication
import app.drop.R
import app.drop.ui.icons.ActionIcons
import app.drop.ui.icons.FormatIcons
import app.drop.ui.icons.PinIcons
import app.drop.ui.theme.DialogWidth
import app.drop.ui.theme.DropTheme
import app.drop.ui.theme.accentTextButtonColors
import app.drop.ui.theme.destructiveTextButtonColors
import app.drop.ui.theme.dropTextFieldColors

const val SELECTION_COUNT_TAG = "selection_count"

/**
 * The Library's bar while notes are selected. Property and export actions are offered only
 * when [onProperty] and [onExport] are given.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(
    count: Int,
    allPinned: Boolean,
    onClear: () -> Unit,
    onSelectAll: () -> Unit,
    onPin: (Boolean) -> Unit,
    onProject: () -> Unit,
    onProperty: (() -> Unit)?,
    onExport: (() -> Unit)?,
    onTrash: () -> Unit,
) {
    val c = DropTheme.colors
    var more by remember { mutableStateOf(false) }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClear) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.bulk_clear), tint = c.textPrimary) }
        },
        title = {
            Text(
                pluralStringResource(R.plurals.bulk_selected, count, count),
                modifier = Modifier.testTag(SELECTION_COUNT_TAG).semantics { liveRegion = LiveRegionMode.Polite },
                color = c.textPrimary,
            )
        },
        actions = {
            IconButton(onClick = onSelectAll) { Icon(FormatIcons.SelectAll, contentDescription = stringResource(R.string.bulk_select_all), tint = c.textPrimary) }
            IconButton(onClick = { onPin(!allPinned) }, enabled = count > 0) {
                Icon(
                    if (allPinned) PinIcons.Filled else PinIcons.Outlined,
                    contentDescription = stringResource(if (allPinned) R.string.bulk_unpin else R.string.bulk_pin),
                    tint = c.textPrimary,
                )
            }
            IconButton(onClick = onTrash, enabled = count > 0, modifier = Modifier.testTag("bulk-trash")) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.note_move_to_trash), tint = c.textPrimary)
            }
            Box {
                IconButton(onClick = { more = true }, enabled = count > 0, modifier = Modifier.testTag("bulk-more")) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.note_more), tint = c.textPrimary)
                }
                DropdownMenu(expanded = more, onDismissRequest = { more = false }, containerColor = c.surfaceElevated) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.bulk_project), color = c.textPrimary) },
                        leadingIcon = { Icon(ActionIcons.Spaces, contentDescription = null, tint = c.textPrimary) },
                        onClick = {
                            more = false
                            onProject()
                        },
                        modifier = Modifier.testTag("bulk-project"),
                    )
                    if (onProperty != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.bulk_property), color = c.textPrimary) },
                            leadingIcon = { Icon(FormatIcons.Label, contentDescription = null, tint = c.textPrimary) },
                            onClick = {
                                more = false
                                onProperty()
                            },
                        )
                    }
                    if (onExport != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.bulk_export), color = c.textPrimary) },
                            leadingIcon = { Icon(FormatIcons.Export, contentDescription = null, tint = c.textPrimary) },
                            onClick = {
                                more = false
                                onExport()
                            },
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = c.background),
    )
}

/**
 * Adds the selected notes to a project (an existing one or a new one), or removes them from one.
 * [onAdd] gets a project id, or null and the name of a project to create.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BulkProjectDialog(count: Int, onAdd: (projectId: Long?, newName: String?) -> Unit, onRemove: (Long) -> Unit, onDismiss: () -> Unit) {
    val c = DropTheme.colors
    val app = LocalContext.current.applicationContext as DropApplication
    val projects by remember { app.database.projectDao().observeAll() }.collectAsStateWithLifecycle(initialValue = emptyList())
    var remove by rememberSaveable { mutableStateOf(false) }
    var newName by rememberSaveable { mutableStateOf("") }
    val chip = FilterChipDefaults.filterChipColors(labelColor = c.textPrimary, selectedContainerColor = c.accentSubtle, selectedLabelColor = c.textPrimary)
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = DialogWidth,
        confirmButton = {
            if (!remove) {
                TextButton(onClick = { onAdd(null, newName) }, enabled = newName.isNotBlank(), colors = accentTextButtonColors()) {
                    Text(stringResource(R.string.bulk_project_create))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, colors = accentTextButtonColors()) { Text(stringResource(R.string.dialog_cancel)) } },
        title = { Text(pluralStringResource(R.plurals.bulk_project_title, count, count), color = c.textPrimary) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !remove, onClick = { remove = false }, label = { Text(stringResource(R.string.bulk_project_add)) }, colors = chip)
                    FilterChip(selected = remove, onClick = { remove = true }, label = { Text(stringResource(R.string.bulk_project_remove)) }, colors = chip)
                }
                if (projects.isEmpty()) Text(stringResource(R.string.collections_no_projects), color = c.textSecondary)
                projects.forEach { p ->
                    TextButton(
                        onClick = { if (remove) onRemove(p.id) else onAdd(p.id, null) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("bulk-project-${p.id}"),
                        colors = accentTextButtonColors(),
                    ) { Text(p.name, modifier = Modifier.fillMaxWidth()) }
                }
                if (!remove) {
                    Spacer(Modifier.padding(top = 4.dp))
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it.replace('\n', ' ') },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.bulk_project_new)) },
                        singleLine = true,
                        colors = dropTextFieldColors(),
                    )
                }
            }
        },
        containerColor = c.surfaceElevated,
    )
}

@Composable
fun BulkTrashDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val c = DropTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = DialogWidth,
        confirmButton = { TextButton(onClick = onConfirm, colors = destructiveTextButtonColors()) { Text(stringResource(R.string.note_move_to_trash)) } },
        dismissButton = { TextButton(onClick = onDismiss, colors = accentTextButtonColors()) { Text(stringResource(R.string.dialog_cancel)) } },
        title = { Text(pluralStringResource(R.plurals.bulk_trash_title, count, count), color = c.textPrimary) },
        text = { Text(stringResource(R.string.bulk_trash_body), color = c.textPrimary) },
        containerColor = c.surfaceElevated,
    )
}
