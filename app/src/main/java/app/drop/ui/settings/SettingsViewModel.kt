package app.drop.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.drop.DropApplication
import app.drop.data.AppSettings
import app.drop.data.BackupError
import app.drop.data.BackupManager
import app.drop.data.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** What happened to a backup, for a message on screen. */
sealed interface BackupMessage {
    data class Exported(val count: Int) : BackupMessage
    data object ExportFailed : BackupMessage
    data class Imported(val added: Int, val skipped: Int) : BackupMessage
    /** [error] is null when the file couldn't be read at all. */
    data class ImportFailed(val error: BackupError?) : BackupMessage
}

class SettingsViewModel(
    private val settings: AppSettings,
    private val backups: BackupManager,
    private val resolver: ContentResolver,
    val appVersion: String,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    var busy by mutableStateOf(false)
        private set

    private val _messages = Channel<BackupMessage>(Channel.BUFFERED)
    val messages: Flow<BackupMessage> = _messages.receiveAsFlow()

    fun setThemeMode(mode: ThemeMode) = settings.setThemeMode(mode)

    /**
     * Writes a backup to the file the person chose. Success is reported only after the file was
     * written, flushed and closed; anything else is reported as a failure.
     */
    fun export(uri: Uri) = runBusy {
        val now = clock()
        val message = try {
            val count = withContext(Dispatchers.IO) {
                // "wt" truncates: an older, longer file in the same place never leaves stale data behind.
                (resolver.openOutputStream(uri, "wt") ?: throw IOException("No output stream")).use { backups.export(it, now, appVersion) }
            }
            settings.recordBackup(now)
            BackupMessage.Exported(count)
        } catch (e: Exception) {
            BackupMessage.ExportFailed
        }
        _messages.send(message)
    }

    fun import(uri: Uri) = runBusy {
        val message = try {
            val result = withContext(Dispatchers.IO) {
                (resolver.openInputStream(uri) ?: throw IOException("No input stream")).use { backups.import(it) }
            }
            BackupMessage.Imported(result.added, result.skipped)
        } catch (e: BackupError) {
            BackupMessage.ImportFailed(e)
        } catch (e: Exception) {
            BackupMessage.ImportFailed(null)
        }
        _messages.send(message)
    }

    private fun runBusy(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                block()
            } finally {
                busy = false
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DropApplication
                SettingsViewModel(app.settings, app.backups, app.contentResolver, app.versionName)
            }
        }
    }
}
