package app.drop.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** DROP's colours. Acid Green (#C4F879) is the brand accent in both themes. */
object DropPalette {
    val BrandAccent = Color(0xFFC4F879)
    val BrandAccentPressed = Color(0xFFA8DE5F)
    val BrandAccentSubtleDark = Color(0xFF293522)
    val BrandAccentSubtleLight = Color(0xFFEDF7E2)
    val OnAccent = Color(0xFF20241F)

    val DarkBackground = Color(0xFF151715)
    val DarkSurface = Color(0xFF20241F)
    val DarkSurfaceElevated = Color(0xFF282D26)
    val DarkTextPrimary = Color(0xFFF4F5EF)
    val DarkTextSecondary = Color(0xFFA6AD9F)
    val DarkBorder = Color(0xFF343A31)
    val DarkDisabled = Color(0xFF747B70)
    val DarkSuccess = Color(0xFFA9E879)
    val DarkWarning = Color(0xFFFFD166)
    val DarkError = Color(0xFFFF8A80)
    val DarkInfo = Color(0xFF91BFFF)

    val LightBackground = Color(0xFFF5F6F1)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceElevated = Color(0xFFFFFFFF)
    val LightTextPrimary = Color(0xFF20241F)
    val LightTextSecondary = Color(0xFF5F675A)
    val LightBorder = Color(0xFFDDE2D6)
    val LightDisabled = Color(0xFF777D72)
    val LightSuccess = Color(0xFF326B24)
    val LightWarning = Color(0xFF8A5A00)
    val LightError = Color(0xFFB3261E)
    val LightInfo = Color(0xFF245DB2)
}

/**
 * The colours screens use. [accentContent] is the accent where it is drawn as text or an icon on
 * the background: green on dark, dark text on light, so it always reads.
 */
@Immutable
data class DropColors(
    val accent: Color,
    val accentPressed: Color,
    val accentSubtle: Color,
    val onAccent: Color,
    val accentContent: Color,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val border: Color,
    val disabled: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val info: Color,
    val snackbarContainer: Color,
    val snackbarContent: Color,
)

val DarkDropColors = DropColors(
    accent = DropPalette.BrandAccent,
    accentPressed = DropPalette.BrandAccentPressed,
    accentSubtle = DropPalette.BrandAccentSubtleDark,
    onAccent = DropPalette.OnAccent,
    accentContent = DropPalette.BrandAccent,
    background = DropPalette.DarkBackground,
    surface = DropPalette.DarkSurface,
    surfaceElevated = DropPalette.DarkSurfaceElevated,
    textPrimary = DropPalette.DarkTextPrimary,
    textSecondary = DropPalette.DarkTextSecondary,
    border = DropPalette.DarkBorder,
    disabled = DropPalette.DarkDisabled,
    success = DropPalette.DarkSuccess,
    warning = DropPalette.DarkWarning,
    error = DropPalette.DarkError,
    info = DropPalette.DarkInfo,
    snackbarContainer = DropPalette.DarkSurfaceElevated,
    snackbarContent = DropPalette.DarkTextPrimary,
)

val LightDropColors = DropColors(
    accent = DropPalette.BrandAccent,
    accentPressed = DropPalette.BrandAccentPressed,
    accentSubtle = DropPalette.BrandAccentSubtleLight,
    onAccent = DropPalette.OnAccent,
    accentContent = DropPalette.LightTextPrimary,
    background = DropPalette.LightBackground,
    surface = DropPalette.LightSurface,
    surfaceElevated = DropPalette.LightSurfaceElevated,
    textPrimary = DropPalette.LightTextPrimary,
    textSecondary = DropPalette.LightTextSecondary,
    border = DropPalette.LightBorder,
    disabled = DropPalette.LightDisabled,
    success = DropPalette.LightSuccess,
    warning = DropPalette.LightWarning,
    error = DropPalette.LightError,
    info = DropPalette.LightInfo,
    snackbarContainer = DropPalette.LightTextPrimary,
    snackbarContent = DropPalette.LightBackground,
)
