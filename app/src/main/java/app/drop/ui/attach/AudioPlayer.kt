package app.drop.ui.attach

import android.media.MediaPlayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.drop.R
import app.drop.ui.icons.ActionIcons
import app.drop.ui.theme.DropTheme
import kotlinx.coroutines.delay
import java.io.File

/** Plays a recording kept in DROP. It pauses when DROP leaves the screen. */
@Composable
fun AudioPlayer(file: File, durationMs: Long?, modifier: Modifier = Modifier) {
    val c = DropTheme.colors
    var player by remember(file) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(file) { mutableStateOf(false) }
    var failed by remember(file) { mutableStateOf(false) }
    var position by remember(file) { mutableLongStateOf(0L) }
    var total by remember(file) { mutableLongStateOf(durationMs ?: 0L) }

    DisposableEffect(file) {
        onDispose {
            player?.release()
            player = null
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        player?.takeIf { it.isPlaying }?.pause()
        playing = false
    }
    LaunchedEffect(playing) {
        while (playing) {
            player?.let { position = it.currentPosition.toLong() }
            delay(200)
        }
    }

    fun toggle() {
        val p = player ?: runCatching {
            MediaPlayer().apply {
                setDataSource(file.path)
                prepare()
                setOnCompletionListener {
                    playing = false
                    position = 0
                }
            }
        }.getOrElse {
            failed = true
            return
        }.also {
            player = it
            if (it.duration > 0) total = it.duration.toLong()
        }
        if (p.isPlaying) {
            p.pause()
            playing = false
        } else {
            p.start()
            playing = true
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth().testTag("audio-player"),
        shape = RoundedCornerShape(16.dp),
        color = c.surface,
        border = BorderStroke(1.dp, c.border),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (failed) {
                Text(stringResource(R.string.attachment_audio_unplayable), color = c.textSecondary, style = MaterialTheme.typography.bodyMedium)
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledIconButton(
                    onClick = ::toggle,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = c.accent, contentColor = c.onAccent),
                    modifier = Modifier.testTag("audio-play"),
                ) {
                    Icon(
                        if (playing) ActionIcons.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(if (playing) R.string.voice_pause else R.string.voice_play),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    LinearProgressIndicator(
                        progress = { if (total > 0) (position.toFloat() / total).coerceIn(0f, 1f) else 0f },
                        modifier = Modifier.fillMaxWidth(),
                        color = c.accentContent,
                        trackColor = c.border,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("${formatDuration(position)} / ${formatDuration(total)}", color = c.textSecondary, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
