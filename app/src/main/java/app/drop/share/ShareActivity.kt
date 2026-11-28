package app.drop.share

import android.os.Bundle
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.drop.DropApplication
import app.drop.MainActivity
import app.drop.R
import app.drop.ui.notes.cardTitle
import app.drop.ui.theme.DropLogoTile
import app.drop.ui.theme.DropTheme
import app.drop.ui.theme.accentTextButtonColors
import app.drop.ui.theme.isDark

/** "Share to DROP" from other apps: saves text and links straight away and says so. */
class ShareActivity : ComponentActivity() {
    private val viewModel: ShareViewModel by viewModels {
        val supported = intent.type?.startsWith("text/") == true
        viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as DropApplication
                ShareViewModel(createSavedStateHandle(), app.database.noteDao(), supported)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as DropApplication
        setContent {
            val mode by app.settings.themeMode.collectAsStateWithLifecycle()
            DropTheme(mode.isDark()) {
                BackHandler { finish() }
                ShareSheet(
                    state = viewModel.state,
                    onOpen = { id ->
                        startActivity(MainActivity.openNoteIntent(this, id))
                        finish()
                    },
                    onDone = ::finish,
                )
            }
        }
    }
}

@Composable
internal fun ShareSheet(state: ShareState, onOpen: (Long) -> Unit, onDone: () -> Unit) {
    val c = DropTheme.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(c.snackbarContainer.copy(alpha = 0.32f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDone)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .testTag("share"),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier.padding(12.dp).widthIn(max = 560.dp).fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            shape = RoundedCornerShape(24.dp),
            color = c.surfaceElevated,
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DropLogoTile(40.dp)
                    Spacer(Modifier.width(12.dp))
                    val title = when (state) {
                        ShareState.Saving -> R.string.share_saving
                        is ShareState.Saved -> if (state.alreadySaved) R.string.share_already_saved else R.string.share_saved
                        ShareState.NothingToSave -> R.string.share_nothing_title
                        ShareState.Failed -> R.string.share_failed_title
                    }
                    Text(
                        stringResource(title),
                        color = c.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("share-status"),
                    )
                }
                Spacer(Modifier.height(12.dp))
                when (state) {
                    ShareState.Saving -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = c.accentContent, trackColor = c.border)
                    is ShareState.Saved -> {
                        Text(cardTitle(state.note), color = c.textPrimary, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (state.truncated) {
                            Spacer(Modifier.height(6.dp))
                            Text(stringResource(R.string.share_truncated), color = c.warning, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    ShareState.NothingToSave -> Text(stringResource(R.string.share_nothing_body), color = c.textSecondary, style = MaterialTheme.typography.bodyMedium)
                    ShareState.Failed -> Text(stringResource(R.string.share_failed_body), color = c.textSecondary, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    if (state is ShareState.Saved) {
                        TextButton(onClick = { onOpen(state.note.id) }, colors = accentTextButtonColors(), modifier = Modifier.testTag("share-open")) {
                            Text(stringResource(R.string.share_open))
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Button(
                        onClick = onDone,
                        enabled = state != ShareState.Saving,
                        colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                        modifier = Modifier.testTag("share-done"),
                    ) { Text(stringResource(if (state is ShareState.Saved) R.string.share_done else R.string.share_close)) }
                }
            }
        }
    }
}
