package app.drop.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.drop.R

/** Text buttons in the accent, readable on both themes. */
@Composable
fun accentTextButtonColors(): ButtonColors =
    ButtonDefaults.textButtonColors(contentColor = DropTheme.colors.accentContent, disabledContentColor = DropTheme.colors.disabled)

@Composable
fun destructiveTextButtonColors(): ButtonColors = ButtonDefaults.textButtonColors(contentColor = DropTheme.colors.error)

/** A snackbar with a second action, for example "Undo" and "Open". */
data class TwoActionSnackbarVisuals(
    override val message: String,
    override val actionLabel: String?,
    val secondaryLabel: String,
    val onSecondary: () -> Unit,
    override val duration: SnackbarDuration = SnackbarDuration.Short,
) : SnackbarVisuals {
    override val withDismissAction: Boolean = false
}

@Composable
fun DropSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    val c = DropTheme.colors
    SnackbarHost(state, modifier) { data ->
        val visuals = data.visuals
        Snackbar(
            modifier = Modifier.padding(12.dp).testTag("snackbar"),
            containerColor = c.snackbarContainer,
            contentColor = c.snackbarContent,
            actionContentColor = c.accent,
            action = {
                Row {
                    if (visuals is TwoActionSnackbarVisuals) {
                        TextButton(onClick = {
                            visuals.onSecondary()
                            data.dismiss()
                        }) { Text(visuals.secondaryLabel, color = c.accent) }
                    }
                    visuals.actionLabel?.let { label ->
                        TextButton(onClick = { data.performAction() }) { Text(label, color = c.accent) }
                    }
                }
            },
        ) { Text(visuals.message) }
    }
}

/** DROP's logo on its dark tile, as on the launcher. */
@Composable
fun DropLogoTile(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(colorResource(R.color.ic_launcher_background)),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.requiredSize(size * 1.5f))
    }
}

/** Dialogs with text fields use this width instead of the platform's, so the keyboard never hides them. */
val DialogWidth: Modifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 560.dp).fillMaxWidth()

/** Text field colours that stay readable in both themes, including labels, hints and disabled text. */
@Composable
fun dropTextFieldColors(): TextFieldColors {
    val c = DropTheme.colors
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = c.textPrimary,
        unfocusedTextColor = c.textPrimary,
        disabledTextColor = c.textSecondary,
        errorTextColor = c.textPrimary,
        cursorColor = c.accentContent,
        focusedBorderColor = c.accentContent,
        unfocusedBorderColor = c.border,
        disabledBorderColor = c.border,
        focusedLabelColor = c.accentContent,
        unfocusedLabelColor = c.textSecondary,
        disabledLabelColor = c.textSecondary,
        focusedPlaceholderColor = c.textSecondary,
        unfocusedPlaceholderColor = c.textSecondary,
        disabledPlaceholderColor = c.textSecondary,
        focusedSupportingTextColor = c.textSecondary,
        unfocusedSupportingTextColor = c.textSecondary,
    )
}
