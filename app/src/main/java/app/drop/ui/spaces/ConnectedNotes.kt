package app.drop.ui.spaces

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.drop.R
import app.drop.ui.theme.DropTheme
import app.drop.ui.theme.accentTextButtonColors
import app.drop.ui.theme.destructiveTextButtonColors

/** A yes-or-cancel question; [destructive] shows the confirm button in the warning colour. */
@Composable
fun Confirm(title: String, body: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit, destructive: Boolean = false) {
    val c = DropTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm, colors = if (destructive) destructiveTextButtonColors() else accentTextButtonColors()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss, colors = accentTextButtonColors()) { Text(stringResource(R.string.dialog_cancel)) } },
        title = { Text(title, color = c.textPrimary) },
        text = { Text(body, color = c.textSecondary) },
        containerColor = c.surfaceElevated,
    )
}
