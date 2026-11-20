package app.drop.ui.theme

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import app.drop.data.ThemeMode

private val LightScrim = Color.argb(230, 255, 255, 255)
private val DarkScrim = Color.argb(128, 27, 27, 27)

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

/** Status and navigation bar icons follow DROP's theme, not only the system's. */
@Composable
fun ComponentActivity.SystemBarsEffect(dark: Boolean) {
    DisposableEffect(dark) {
        enableEdgeToEdge(
            statusBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = if (dark) SystemBarStyle.dark(DarkScrim) else SystemBarStyle.light(LightScrim, DarkScrim),
        )
        onDispose {}
    }
}
