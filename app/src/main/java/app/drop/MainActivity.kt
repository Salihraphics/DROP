package app.drop

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.drop.ui.DropNavHost
import app.drop.ui.StartAction
import app.drop.ui.theme.DropTheme
import app.drop.ui.theme.SystemBarsEffect
import app.drop.ui.theme.isDark

class MainActivity : ComponentActivity() {
    /** What a widget, shortcut or notification asked for; cleared once handled. */
    var startAction by mutableStateOf<StartAction?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) startAction = startActionFor(intent)
        val app = application as DropApplication
        app.startBackgroundWorkOnce()
        setContent {
            val mode by app.settings.themeMode.collectAsStateWithLifecycle()
            val dark = mode.isDark()
            SystemBarsEffect(dark)
            DropTheme(dark) {
                DropNavHost(startAction, onStartActionHandled = { startAction = null })
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Leaving DROP locks the Vault; rotating the screen doesn't.
        if (!isChangingConfigurations) (application as DropApplication).lockVault()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        startActionFor(intent)?.let { startAction = it }
    }

    companion object {
        const val ACTION_NEW_NOTE = "app.drop.action.NEW_NOTE"
        const val ACTION_VOICE_DUMP = "app.drop.action.VOICE_DUMP"
        private const val EXTRA_OPEN_NOTE_ID = "app.drop.extra.OPEN_NOTE_ID"
        private const val EXTRA_RESURFACED_NOTE_ID = "app.drop.extra.RESURFACED_NOTE_ID"
        private const val EXTRA_SHOW_TASKS = "app.drop.extra.SHOW_TASKS"

        internal fun startActionFor(intent: Intent?): StartAction? {
            if (intent == null) return null
            return when {
                intent.action == ACTION_NEW_NOTE -> StartAction.NewNote
                intent.action == ACTION_VOICE_DUMP -> StartAction.VoiceDump
                intent.getBooleanExtra(EXTRA_SHOW_TASKS, false) -> StartAction.ShowTasks
                intent.hasExtra(EXTRA_RESURFACED_NOTE_ID) -> StartAction.OpenResurfaced(intent.getLongExtra(EXTRA_RESURFACED_NOTE_ID, -1))
                intent.hasExtra(EXTRA_OPEN_NOTE_ID) -> StartAction.OpenNote(intent.getLongExtra(EXTRA_OPEN_NOTE_ID, -1))
                else -> null
            }
        }

        private const val TOP = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP

        fun launchIntent(context: Context): Intent = Intent(context, MainActivity::class.java).addFlags(TOP)

        fun showTasksIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java).putExtra(EXTRA_SHOW_TASKS, true).addFlags(TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        fun resurfacedIntent(context: Context, noteId: Long): Intent =
            Intent(context, MainActivity::class.java).putExtra(EXTRA_RESURFACED_NOTE_ID, noteId).addFlags(TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        fun openNoteIntent(context: Context, noteId: Long): Intent =
            Intent(context, MainActivity::class.java).putExtra(EXTRA_OPEN_NOTE_ID, noteId).addFlags(TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
