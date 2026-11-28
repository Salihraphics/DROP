package app.drop.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.exifinterface.media.ExifInterface
import app.drop.data.AttachmentKind
import java.io.File
import java.io.IOException

/** Turns an attachment's pages into pictures for OCR. */
interface PageRenderer {
    fun pageCount(file: File, kind: AttachmentKind): Int

    fun render(file: File, kind: AttachmentKind, index: Int): GrayImage
}

/** Photos are decoded upright (EXIF) at most [maxSide] pixels long; PDF pages at about 200 dpi. */
class AndroidPageRenderer(private val maxSide: Int = MAX_SIDE) : PageRenderer {
    override fun pageCount(file: File, kind: AttachmentKind): Int {
        if (kind != AttachmentKind.Pdf) return 1
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { it.pageCount }
        }
    }

    override fun render(file: File, kind: AttachmentKind, index: Int): GrayImage {
        val bitmap = if (kind == AttachmentKind.Pdf) renderPdfPage(file, index) else decodeImage(file, maxSide)
        try {
            return bitmap.toGray()
        } finally {
            bitmap.recycle()
        }
    }

    private fun renderPdfPage(file: File, index: Int): Bitmap =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                renderer.openPage(index).use { page ->
                    // PDF units are 1/72 inch; 200/72 gives about 200 dpi, capped by maxSide.
                    val scale = minOf(PDF_SCALE, maxSide.toFloat() / maxOf(page.width, page.height))
                    val bitmap = Bitmap.createBitmap(
                        (page.width * scale).toInt().coerceAtLeast(1),
                        (page.height * scale).toInt().coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888,
                    )
                    Canvas(bitmap).drawColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }

    companion object {
        const val MAX_SIDE = 2600
        private const val PDF_SCALE = 200f / 72f

        fun decodeImage(file: File, maxSide: Int): Bitmap {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("not an image")
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }) ?: throw IOException("not an image")
            val longest = maxOf(decoded.width, decoded.height)
            val fitted = if (longest > maxSide) {
                val f = maxSide.toFloat() / longest
                Bitmap.createScaledBitmap(decoded, (decoded.width * f).toInt(), (decoded.height * f).toInt(), true).also {
                    if (it != decoded) decoded.recycle()
                }
            } else {
                decoded
            }
            val degrees = runCatching {
                when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }.getOrDefault(0f)
            if (degrees == 0f) return fitted
            val rotated = Bitmap.createBitmap(fitted, 0, 0, fitted.width, fitted.height, Matrix().apply { postRotate(degrees) }, true)
            if (rotated != fitted) fitted.recycle()
            return rotated
        }

        /** Grey, read one row at a time so a big photo never needs a second full-size copy in ints. */
        fun Bitmap.toGray(): GrayImage {
            val out = GrayImage(width, height)
            val row = IntArray(width)
            for (y in 0 until height) {
                getPixels(row, 0, width, 0, y, width, 1)
                val base = y * width
                for (x in 0 until width) out.pixels[base + x] = GrayImage.luminance(row[x]).toByte()
            }
            return out
        }
    }
}
