package app.drop.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.drop.DropApplication
import app.drop.data.NoteFont

fun NoteFont.family(): FontFamily? = when (this) {
    NoteFont.Default -> null
    NoteFont.Serif -> FontFamily.Serif
    NoteFont.Mono -> FontFamily.Monospace
}

/** [style] in the note font chosen in Settings. */
@Composable
fun noteTextStyle(style: TextStyle): TextStyle {
    val app = LocalContext.current.applicationContext as? DropApplication ?: return style
    val font by app.settings.noteFont.collectAsStateWithLifecycle()
    return font.family()?.let { style.copy(fontFamily = it) } ?: style
}
