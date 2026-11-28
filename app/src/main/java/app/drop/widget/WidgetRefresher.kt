package app.drop.widget

import android.content.Context
import androidx.room.InvalidationTracker
import app.drop.data.AppSettings
import app.drop.data.DropDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** Keeps widgets in step with notes and the theme; bursts of edits redraw once. */
class WidgetRefresher(
    private val context: Context,
    private val database: DropDatabase,
    private val settings: AppSettings,
    private val scope: CoroutineScope,
) {
    private var pending: Job? = null

    fun start() {
        database.invalidationTracker.addObserver(object : InvalidationTracker.Observer(arrayOf("notes")) {
            override fun onInvalidated(tables: Set<String>) = schedule()
        })
        scope.launch { settings.themeMode.drop(1).collect { schedule() } }
    }

    @Synchronized
    fun schedule() {
        pending?.cancel()
        pending = scope.launch {
            delay(DEBOUNCE_MS)
            runCatching { DropWidgets.updateAll(context) }
        }
    }

    companion object {
        const val DEBOUNCE_MS = 400L
    }
}
