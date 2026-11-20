package app.drop.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

internal val LocalDropColors = staticCompositionLocalOf { DarkDropColors }

@Composable
fun DropTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkDropColors else LightDropColors
    // The selected text stays readable: the highlight is light enough under both text colours.
    val selection = TextSelectionColors(handleColor = colors.accentContent, backgroundColor = colors.accent.copy(alpha = selectionAlpha(darkTheme)))
    CompositionLocalProvider(LocalDropColors provides colors, LocalTextSelectionColors provides selection) {
        MaterialTheme(colorScheme = colors.toColorScheme(darkTheme), typography = DropTypography, content = content)
    }
}

/** How strongly selected text is highlighted. */
internal fun selectionAlpha(dark: Boolean): Float = if (dark) 0.32f else 0.5f

object DropTheme {
    val colors: DropColors
        @Composable
        @ReadOnlyComposable
        get() = LocalDropColors.current
}

/** Every Material colour role mapped to DROP's, so no component falls back to a default colour. */
fun DropColors.toColorScheme(dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentSubtle,
        onPrimaryContainer = textPrimary,
        inversePrimary = accent,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = accentSubtle,
        onSecondaryContainer = textPrimary,
        tertiary = info,
        onTertiary = background,
        tertiaryContainer = surfaceElevated,
        onTertiaryContainer = textPrimary,
        background = background,
        onBackground = textPrimary,
        surface = background,
        onSurface = textPrimary,
        surfaceVariant = surfaceElevated,
        onSurfaceVariant = textSecondary,
        surfaceTint = Color.Transparent,
        inverseSurface = textPrimary,
        inverseOnSurface = background,
        error = error,
        onError = background,
        errorContainer = surfaceElevated,
        onErrorContainer = error,
        outline = border,
        outlineVariant = border,
        surfaceBright = surfaceElevated,
        surfaceContainer = background,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = surfaceElevated,
        surfaceContainerLow = surfaceElevated,
        surfaceContainerLowest = surface,
        surfaceDim = background,
    )
}
