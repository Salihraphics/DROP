package app.drop.ui.attach

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.drop.R
import app.drop.data.AttachmentKind
import app.drop.ocr.AndroidPageRenderer
import app.drop.ui.theme.DropTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val PREVIEW_MAX_SIDE = 1600

internal sealed interface Loaded {
    data object Loading : Loaded

    data object Failed : Loaded

    class Ready(val bitmap: Bitmap) : Loaded
}

/** Shows a photo, or one page of a PDF, fitted to the width. */
@Composable
fun FilePreview(
    file: File,
    kind: AttachmentKind,
    contentDescription: String?,
    page: Int = 0,
    maxHeight: Dp = 420.dp,
    modifier: Modifier = Modifier,
) {
    val c = DropTheme.colors
    var loaded by remember(file, kind, page) { mutableStateOf<Loaded>(Loaded.Loading) }
    LaunchedEffect(file, kind, page) {
        loaded = withContext(Dispatchers.IO) {
            runCatching { Loaded.Ready(load(file, kind, page)) }.getOrDefault(Loaded.Failed)
        }
    }
    Surface(
        modifier = modifier.fillMaxWidth().testTag("file-preview"),
        shape = RoundedCornerShape(16.dp),
        color = c.surface,
        border = BorderStroke(1.dp, c.border),
    ) {
        when (val l = loaded) {
            Loaded.Loading -> Box(Modifier.fillMaxWidth().heightIn(min = 160.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(32.dp), color = c.accentContent)
            }
            Loaded.Failed -> Text(
                stringResource(R.string.attachment_preview_failed),
                modifier = Modifier.padding(16.dp),
                color = c.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
            is Loaded.Ready -> Image(
                l.bitmap.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight).clip(RoundedCornerShape(16.dp)).background(c.surface),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

private fun load(file: File, kind: AttachmentKind, page: Int): Bitmap {
    if (kind != AttachmentKind.Pdf) return AndroidPageRenderer.decodeImage(file, PREVIEW_MAX_SIDE)
    return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
        PdfRenderer(fd).use { renderer ->
            renderer.openPage(page.coerceIn(0, renderer.pageCount - 1)).use { p ->
                val scale = PREVIEW_MAX_SIDE.toFloat() / maxOf(p.width, p.height)
                Bitmap.createBitmap((p.width * scale).toInt().coerceAtLeast(1), (p.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888).also {
                    Canvas(it).drawColor(Color.WHITE)
                    p.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        }
    }
}
