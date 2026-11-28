package app.drop.capture

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.drop.DropApplication
import app.drop.R
import app.drop.data.Links
import app.drop.ui.icons.ActionIcons
import app.drop.ui.theme.DropLogoTile
import app.drop.ui.theme.DropTheme
import app.drop.ui.theme.isDark
import app.drop.ui.theme.noteTextStyle

/** A small window over whatever is on screen, for capturing a note or task without opening DROP. */
class QuickCaptureActivity : ComponentActivity() {
    private val viewModel: QuickCaptureViewModel by viewModels { QuickCaptureViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val startAsTask = intent.getBooleanExtra(EXTRA_TASK, false)
        val app = application as DropApplication
        setContent {
            val mode by app.settings.themeMode.collectAsStateWithLifecycle()
            DropTheme(mode.isDark()) {
                val state = viewModel.state
                LaunchedEffect(state) {
                    if (state is CaptureState.Saved) {
                        Toast.makeText(applicationContext, if (state.note.isTask) R.string.capture_saved_task else R.string.capture_saved, Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
                BackHandler { finish() }
                CaptureCard(state, startAsTask, onSave = viewModel::save, onDismiss = ::finish)
            }
        }
    }

    companion object {
        private const val EXTRA_TASK = "app.drop.extra.CAPTURE_TASK"

        fun intent(context: Context, task: Boolean): Intent = Intent(context, QuickCaptureActivity::class.java).putExtra(EXTRA_TASK, task)
    }
}

@Composable
internal fun CaptureCard(state: CaptureState, startAsTask: Boolean, onSave: (String, Boolean) -> Unit, onDismiss: () -> Unit) {
    val c = DropTheme.colors
    var text by rememberSaveable { mutableStateOf("") }
    var asTask by rememberSaveable { mutableStateOf(startAsTask) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    val busy = state == CaptureState.Saving || state is CaptureState.Saved
    // Tapping outside the card closes it; the card itself swallows taps.
    Box(
        Modifier
            .fillMaxSize()
            .background(c.snackbarContainer.copy(alpha = 0.32f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
            .testTag("quick-capture"),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .padding(12.dp)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            shape = RoundedCornerShape(24.dp),
            color = c.surfaceElevated,
        ) {
            Column(Modifier.padding(start = 20.dp, top = 18.dp, end = 12.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DropLogoTile(28.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.capture_title), color = c.textPrimary, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.size(12.dp))
                val style = noteTextStyle(MaterialTheme.typography.bodyLarge).copy(color = c.textPrimary)
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp).heightIn(min = 56.dp).focusRequester(focus).testTag("capture-text"),
                    enabled = !busy,
                    textStyle = style,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    maxLines = 8,
                    cursorBrush = SolidColor(c.accentContent),
                    decorationBox = { inner ->
                        Box {
                            if (text.isEmpty()) {
                                Text(stringResource(if (asTask) R.string.task_title_hint else R.string.capture_hint), style = style, color = c.textSecondary)
                            }
                            inner()
                        }
                    },
                )
                val host = remember(text) { Links.find(text)?.let(Links::host) }
                if (host != null) {
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(ActionIcons.Link, contentDescription = null, modifier = Modifier.size(14.dp), tint = c.textSecondary)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.capture_link_detected, host), color = c.textSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (state == CaptureState.Failed) {
                    Text(stringResource(R.string.capture_failed), color = c.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                }
                Spacer(Modifier.size(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(
                        Modifier
                            .heightIn(min = 48.dp)
                            .clickable(role = Role.Checkbox, enabled = !busy) { asTask = !asTask }
                            .padding(end = 8.dp)
                            .testTag("capture-as-task"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = asTask,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(checkedColor = c.accent, checkmarkColor = c.onAccent, uncheckedColor = c.textSecondary),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.capture_as_task), color = c.textPrimary, style = MaterialTheme.typography.bodyMedium)
                    }
                    Button(
                        onClick = { onSave(text, asTask) },
                        enabled = text.isNotBlank() && !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                        modifier = Modifier.testTag("capture-save"),
                    ) { Text(stringResource(R.string.capture_save)) }
                }
            }
        }
    }
}
