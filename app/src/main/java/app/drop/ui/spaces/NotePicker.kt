package app.drop.ui.spaces

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.drop.R
import app.drop.data.Note
import app.drop.richtext.DropMarkdown
import app.drop.search.FoldedText
import app.drop.search.SearchQuery
import app.drop.ui.notes.cardTitle
import app.drop.ui.theme.DropTheme
import app.drop.ui.theme.accentTextButtonColors

@Composable
internal fun spacesFieldColors(): TextFieldColors {
    val c = DropTheme.colors
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = c.textPrimary,
        unfocusedTextColor = c.textPrimary,
        cursorColor = c.accentContent,
        focusedBorderColor = c.accentContent,
        unfocusedBorderColor = c.border,
        focusedLabelColor = c.accentContent,
        unfocusedLabelColor = c.textSecondary,
    )
}

/**
 * Picks notes to add somewhere, with a search box. With [multiple] the person ticks several and
 * confirms; otherwise a tap picks one. Notes in [exclude] are not offered.
 */
@Composable
fun NotePicker(
    title: String,
    notes: List<Note>,
    multiple: Boolean,
    onPick: (List<Long>) -> Unit,
    onDismiss: () -> Unit,
    exclude: Set<Long> = emptySet(),
    confirmLabel: String = stringResource(R.string.spaces_add),
) {
    val c = DropTheme.colors
    var query by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf(emptySet<Long>()) }
    val search = remember(query) { SearchQuery(query) }
    val shown = remember(notes, search, exclude) {
        notes.filter { it.id !in exclude && (search.isEmpty() || search.matches(FoldedText(it.title), FoldedText(DropMarkdown.plainOf(it.body, it.format)))) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(horizontal = 28.dp).widthIn(max = 560.dp).testTag("note-picker"),
        confirmButton = {
            if (multiple) {
                TextButton(onClick = { onPick(chosen.toList()) }, enabled = chosen.isNotEmpty(), colors = accentTextButtonColors()) { Text(confirmLabel) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, colors = accentTextButtonColors()) { Text(stringResource(R.string.dialog_cancel)) } },
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.home_search_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    colors = spacesFieldColors(),
                )
                Spacer(Modifier.padding(top = 8.dp))
                if (shown.isEmpty()) Text(stringResource(R.string.spaces_picker_empty), color = c.textSecondary)
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(shown, key = { it.id }) { note ->
                        val label = cardTitle(note).ifBlank { stringResource(R.string.note_empty) }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .toggleable(
                                    value = note.id in chosen,
                                    role = if (multiple) Role.Checkbox else Role.Button,
                                    onValueChange = { on ->
                                        if (multiple) chosen = if (on) chosen + note.id else chosen - note.id
                                        else onPick(listOf(note.id))
                                    },
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (multiple) {
                                Checkbox(
                                    checked = note.id in chosen,
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(checkedColor = c.accent, uncheckedColor = c.textSecondary, checkmarkColor = c.onAccent),
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(label, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        containerColor = c.surfaceElevated,
    )
}

/** Asks for a name, and a description when [initialDescription] is not null. Both are trimmed. */
@Composable
fun NameDialog(
    title: String,
    initialName: String,
    onConfirm: (name: String, description: String) -> Unit,
    onDismiss: () -> Unit,
    initialDescription: String? = null,
) {
    val c = DropTheme.colors
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription.orEmpty()) }
    val fieldColors = spacesFieldColors()
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(horizontal = 28.dp).widthIn(max = 560.dp),
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim(), description.trim()) }, enabled = name.isNotBlank(), colors = accentTextButtonColors()) {
                Text(stringResource(R.string.spaces_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, colors = accentTextButtonColors()) { Text(stringResource(R.string.dialog_cancel)) } },
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth().testTag("name-field"),
                    label = { Text(stringResource(R.string.spaces_name)) },
                    singleLine = true,
                    colors = fieldColors,
                )
                if (initialDescription != null) {
                    Spacer(Modifier.padding(top = 8.dp))
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        modifier = Modifier.fillMaxWidth().testTag("description-field"),
                        label = { Text(stringResource(R.string.spaces_description)) },
                        colors = fieldColors,
                    )
                }
            }
        },
        containerColor = c.surfaceElevated,
    )
}
