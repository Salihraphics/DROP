package app.drop.ui.vault

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import java.util.WeakHashMap

/**
 * Keeps screenshots, screen recording and the recent-apps preview away from Vault screens.
 * Counted, so the flag stays on while any Vault screen is showing.
 */
object SecureWindow {
    private val holders = WeakHashMap<Window, Int>()

    fun acquire(window: Window) {
        val count = (holders[window] ?: 0) + 1
        holders[window] = count
        if (count == 1) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    fun release(window: Window) {
        val count = (holders[window] ?: 1) - 1
        if (count <= 0) {
            holders.remove(window)
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            holders[window] = count
        }
    }
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context !is Activity) {
        context = (context as? ContextWrapper)?.baseContext ?: return null
    }
    return context
}

/** Wraps a Vault screen: secures the window and counts every touch as activity. */
@Composable
fun VaultSurface(onActivity: () -> Unit, content: @Composable () -> Unit) {
    val window = LocalContext.current.findActivity()?.window
    if (window != null) {
        DisposableEffect(window) {
            SecureWindow.acquire(window)
            onDispose { SecureWindow.release(window) }
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        onActivity()
                    }
                }
            },
    ) { content() }
}
