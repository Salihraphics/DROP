package app.drop.widget

import android.content.BroadcastReceiver
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "DropWidget"

/** Runs [block] off the main thread and keeps the receiver alive until it finishes. A failure never crashes the launcher. */
fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try {
            block()
        } catch (e: Exception) {
            Log.w(TAG, "Widget update failed: ${e.javaClass.simpleName}")
        } finally {
            pending?.finish()
        }
    }
}
