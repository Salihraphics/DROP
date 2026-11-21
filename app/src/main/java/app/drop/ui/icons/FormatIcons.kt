package app.drop.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Icons for the formatting bar and the editor menu. */
object FormatIcons {
    val Bold: ImageVector by lazy { icon("Bold", "M15.6,10.79c0.97,-0.67 1.65,-1.77 1.65,-2.79 0,-2.26 -1.75,-4 -4,-4H7v14h7.04c2.09,0 3.71,-1.7 3.71,-3.79 0,-1.52 -0.86,-2.82 -2.15,-3.42zM10,6.5h3c0.83,0 1.5,0.67 1.5,1.5s-0.67,1.5 -1.5,1.5h-3v-3zM13.5,15.5H10v-3h3.5c0.83,0 1.5,0.67 1.5,1.5s-0.67,1.5 -1.5,1.5z") }
    val Italic: ImageVector by lazy { icon("Italic", "M10,4v3h2.21l-3.42,8H6v3h8v-3h-2.21l3.42,-8H18V4z") }
    val Strike: ImageVector by lazy { icon("Strike", "M10,19h4v-3h-4v3zM5,4v3h5v3h4V7h5V4H5zM3,14h18v-2H3v2z") }
    val Code: ImageVector by lazy { icon("Code", "M9.4,16.6L4.8,12l4.6,-4.6L8,6l-6,6 6,6 1.4,-1.4zM14.6,16.6l4.6,-4.6 -4.6,-4.6L16,6l6,6 -6,6 -1.4,-1.4z") }
    val CodeBlock: ImageVector by lazy { icon("CodeBlock", "M4,7v2c0,0.55 -0.45,1 -1,1H2v4h1c0.55,0 1,0.45 1,1v2c0,1.65 1.35,3 3,3h3v-2H7c-0.55,0 -1,-0.45 -1,-1v-2c0,-1.3 -0.84,-2.42 -2,-2.83v-0.34C5.16,11.42 6,10.3 6,9V7c0,-0.55 0.45,-1 1,-1h3V4H7C5.35,4 4,5.35 4,7zM21,10c-0.55,0 -1,-0.45 -1,-1V7c0,-1.65 -1.35,-3 -3,-3h-3v2h3c0.55,0 1,0.45 1,1v2c0,1.3 0.84,2.42 2,2.83v0.34c-1.16,0.41 -2,1.52 -2,2.83v2c0,0.55 -0.45,1 -1,1h-3v2h3c1.65,0 3,-1.35 3,-3v-2c0,-0.55 0.45,-1 1,-1h1v-4H21z") }
    val Quote: ImageVector by lazy { icon("Quote", "M6,17h3l2,-4V7H5v6h3zM14,17h3l2,-4V7h-6v6h3z") }
    val Bulleted: ImageVector by lazy { icon("Bulleted", "M4,10.5c-0.83,0 -1.5,0.67 -1.5,1.5s0.67,1.5 1.5,1.5 1.5,-0.67 1.5,-1.5 -0.67,-1.5 -1.5,-1.5zM4,4.5c-0.83,0 -1.5,0.67 -1.5,1.5S3.17,7.5 4,7.5 5.5,6.83 5.5,6 4.83,4.5 4,4.5zM4,16.67c-0.74,0 -1.33,0.6 -1.33,1.33s0.6,1.33 1.33,1.33 1.33,-0.6 1.33,-1.33 -0.59,-1.33 -1.33,-1.33zM7,19h14v-2H7v2zM7,13h14v-2H7v2zM7,5v2h14V5H7z") }
    val Numbered: ImageVector by lazy { icon("Numbered", "M2,17h2v0.5H3v1h1v0.5H2v1h3v-4H2v1zM3,8h1V4H2v1h1v3zM2,11h1.8L2,13.1v0.9h3v-1H3.2L5,10.9V10H2v1zM7,5v2h14V5H7zM7,19h14v-2H7v2zM7,13h14v-2H7v2z") }
    val Checklist: ImageVector by lazy { icon("Checklist", "M22,7h-9v2h9V7zM22,15h-9v2h9V15zM5.54,11L2,7.46l1.41,-1.41l2.12,2.12l4.24,-4.24l1.41,1.41L5.54,11zM5.54,19L2,15.46l1.41,-1.41l2.12,2.12l4.24,-4.24l1.41,1.41L5.54,19z") }
    val CheckBox: ImageVector by lazy { icon("CheckBox", "M19,3H5c-1.11,0 -2,0.9 -2,2v14c0,1.1 0.89,2 2,2h14c1.11,0 2,-0.9 2,-2V5c0,-1.1 -0.89,-2 -2,-2zM10,17l-5,-5 1.41,-1.41L10,14.17l7.59,-7.59L19,8l-9,9z") }
    val Title: ImageVector by lazy { icon("Title", "M5,4v3h5.5v12h3V7H19V4z") }
    val ClearFormat: ImageVector by lazy { icon("ClearFormat", "M3.27,5L2,6.27l6.97,6.97L6.5,19h3l1.57,-3.66L16.73,21 18,19.73 3.55,5.27 3.27,5zM6,5v0.18L8.82,8h2.4l-0.72,1.68 2.1,2.1L14.21,8H20V5H6z") }
    val Undo: ImageVector by lazy { icon("Undo", "M12.5,8c-2.65,0 -5.05,0.99 -6.9,2.6L2,7v9h9l-3.62,-3.62c1.39,-1.16 3.16,-1.88 5.12,-1.88 3.54,0 6.55,2.31 7.6,5.5l2.37,-0.78C21.08,11.03 17.15,8 12.5,8z") }
    val Redo: ImageVector by lazy { icon("Redo", "M18.4,10.6C16.55,8.99 14.15,8 11.5,8c-4.65,0 -8.58,3.03 -9.96,7.22L3.9,16c1.05,-3.19 4.05,-5.5 7.6,-5.5 1.95,0 3.73,0.72 5.12,1.88L13,16h9V7l-3.6,3.6z") }
    val History: ImageVector by lazy { icon("History", "M13,3c-4.97,0 -9,4.03 -9,9H1l3.89,3.89 0.07,0.14L9,12H6c0,-3.87 3.13,-7 7,-7s7,3.13 7,7 -3.13,7 -7,7c-1.93,0 -3.68,-0.79 -4.94,-2.06l-1.42,1.42C8.27,19.99 10.51,21 13,21c4.97,0 9,-4.03 9,-9s-4.03,-9 -9,-9zM12,8v5l4.28,2.54 0.72,-1.21 -3.5,-2.08V8H12z") }
    val Fullscreen: ImageVector by lazy { icon("Fullscreen", "M7,14H5v5h5v-2H7v-3zM5,10h2V7h3V5H5v5zM17,17h-3v2h5v-5h-2v3zM14,5v2h3v3h2V5h-5z") }
    val FullscreenExit: ImageVector by lazy { icon("FullscreenExit", "M5,16h3v3h2v-5H5v2zM8,8H5v2h5V5H8v3zM14,19h2v-3h3v-2h-5v5zM16,5v3h-3v2h5V5h-2z") }
    val Outline: ImageVector by lazy { icon("Outline", "M3,9h14V7H3v2zM3,13h14v-2H3v2zM3,17h14v-2H3v2zM19,17h2v-2h-2v2zM19,7v2h2V7h-2zM19,13h2v-2h-2v2z") }
    val Label: ImageVector by lazy { icon("Label", "M17.63,5.84C17.27,5.33 16.67,5 16,5L5,5.01C3.9,5.01 3,5.9 3,7v10c0,1.1 0.9,1.99 2,1.99L16,19c0.67,0 1.27,-0.33 1.63,-0.84L22,12l-4.37,-6.16z") }
    val Health: ImageVector by lazy { icon("Health", "M20,3H4C2.9,3 2,3.9 2,5v14c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V5C22,3.9 21.1,3 20,3zM10,17H5v-2h5V17zM10,13H5v-2h5V13zM10,9H5V7h5V9zM14.82,15L12,12.16l1.41,-1.41l1.41,1.42L17.99,9l1.42,1.42L14.82,15z") }
    val Filter: ImageVector by lazy { icon("Filter", "M10,18h4v-2h-4v2zM3,6v2h18V6H3zM6,13h12v-2H6v2z") }
    val Export: ImageVector by lazy { icon("Export", "M9,16h6v-6h4l-7,-7 -7,7h4zM5,18h14v2H5z") }
    val Import: ImageVector by lazy { icon("Import", "M19,9h-4V3H9v6H5l7,7 7,-7zM5,18v2h14v-2H5z") }
    val SelectAll: ImageVector by lazy { icon("SelectAll", "M3,5h2V3c-1.1,0 -2,0.9 -2,2zM3,13h2v-2H3v2zM7,21h2v-2H7v2zM3,9h2V7H3v2zM13,3h-2v2h2V3zM19,3v2h2c0,-1.1 -0.9,-2 -2,-2zM5,21v-2H3c0,1.1 0.9,2 2,2zM3,17h2v-2H3v2zM9,3H7v2h2V3zM11,21h2v-2h-2v2zM19,13h2v-2h-2v2zM19,21c1.1,0 2,-0.9 2,-2h-2v2zM19,9h2V7h-2v2zM19,17h2v-2h-2v2zM15,21h2v-2h-2v2zM15,5h2V3h-2v2zM7,17h10V7H7v10zM9,9h6v6H9V9z") }
    val Template: ImageVector by lazy { icon("Template", "M19,3H5C3.9,3 3,3.9 3,5v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V5C21,3.9 20.1,3 19,3zM14,17H7v-2h7V17zM17,13H7v-2h10V13zM17,9H7V7h10V9z") }
    val LinkOff: ImageVector by lazy { icon("LinkOff", "M17,7h-4v1.9h4c1.71,0 3.1,1.39 3.1,3.1 0,1.43 -0.98,2.63 -2.31,2.98l1.46,1.46C20.88,15.61 22,13.95 22,12c0,-2.76 -2.24,-5 -5,-5zM16,11h-2.19l2,2H16zM2,4.27l3.11,3.11C3.29,8.12 2,9.91 2,12c0,2.76 2.24,5 5,5h4v-1.9H7c-1.71,0 -3.1,-1.39 -3.1,-3.1 0,-1.59 1.21,-2.9 2.76,-3.07L8.73,11H8v2h2.73L13,15.27V17h1.73l4.01,4L20,19.74 3.27,3 2,4.27z") }
    val Compare: ImageVector by lazy { icon("Compare", "M9.01,14H2v2h7.01v3L13,15l-3.99,-4v3zM14.99,13v-3H22V8h-7.01V5L11,9l3.99,4z") }

    private fun icon(name: String, pathData: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(PathParser().parsePathString(pathData).toNodes(), fill = SolidColor(Color.Black))
            .build()
}
