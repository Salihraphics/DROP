package app.drop.attach

import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import app.drop.data.AttachmentKind
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/** A file copied into DROP's storage but not yet part of a note. */
data class StagedFile(
    val partName: String,
    val kind: AttachmentKind,
    val mimeType: String,
    val sizeBytes: Long,
    val displayName: String,
    val pageCount: Int?,
)

/**
 * Attachment files in app-private storage. A file is first copied as "name.ext.part" and only
 * renamed to its final name once its note row exists, so a crash never leaves a note pointing at
 * a half-written file; leftovers are swept later. The type is decided by the file's own bytes,
 * never by its name.
 */
class AttachmentStore(filesDir: File, private val pdfPages: (File) -> Int = ::countPdfPages) {
    val dir: File = File(filesDir, DIR).apply { mkdirs() }

    fun file(name: String): File {
        require(name.isNotEmpty() && name == File(name).name && !name.startsWith(".")) { "invalid attachment name" }
        return File(dir, name)
    }

    fun newPart(extension: String): File = File(dir, "${UUID.randomUUID()}.$extension$PART")

    /** Copies [source] into storage and checks it. Throws [ImportError]; nothing is left behind on failure. */
    fun stage(source: ImportSource): StagedFile {
        source.declaredSize?.let { declared ->
            if (declared > MAX_PDF_BYTES) throw ImportError.TooLarge(MAX_PDF_BYTES)
            val free = dir.usableSpace
            if (free in 0 until declared + RESERVE_BYTES) throw ImportError.LowStorage
        }
        val part = newPart("tmp")
        try {
            val header = ByteArray(HEADER_BYTES)
            var headerLength = 0
            var total = 0L
            var limit = MAX_PDF_BYTES
            var type: SniffedType? = null
            source.open().use { input ->
                FileOutputStream(part).use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        if (headerLength < HEADER_BYTES) {
                            val take = minOf(n, HEADER_BYTES - headerLength)
                            System.arraycopy(buffer, 0, header, headerLength, take)
                            headerLength += take
                            if (type == null && headerLength >= MIN_SNIFF_BYTES) {
                                type = sniff(header, headerLength) ?: throw ImportError.Unsupported
                                limit = if (type!!.kind == AttachmentKind.Pdf) MAX_PDF_BYTES else MAX_IMAGE_BYTES
                            }
                        }
                        total += n
                        if (total > limit) throw ImportError.TooLarge(limit)
                        out.write(buffer, 0, n)
                    }
                    out.fd.sync()
                }
            }
            if (total == 0L) throw ImportError.Empty
            val sniffed = type ?: sniff(header, headerLength) ?: throw ImportError.Unsupported
            if (sniffed.kind == AttachmentKind.Pdf && total > MAX_PDF_BYTES) throw ImportError.TooLarge(MAX_PDF_BYTES)
            if (sniffed.kind == AttachmentKind.Image && total > MAX_IMAGE_BYTES) throw ImportError.TooLarge(MAX_IMAGE_BYTES)
            val pages = if (sniffed.kind == AttachmentKind.Pdf) {
                runCatching { pdfPages(part) }.getOrNull()?.takeIf { it > 0 } ?: throw ImportError.UnreadablePdf
            } else {
                checkDecodable(part)
                null
            }
            val staged = File(dir, part.name.removeSuffix(".tmp$PART") + "." + sniffed.extension + PART)
            if (!part.renameTo(staged)) throw ImportError.ReadFailed
            return StagedFile(staged.name, sniffed.kind, sniffed.mimeType, total, cleanDisplayName(source.displayName, sniffed.extension), pages)
        } catch (e: ImportError) {
            part.delete()
            throw e
        } catch (e: IOException) {
            part.delete()
            val free = dir.usableSpace
            throw if (free in 0 until RESERVE_BYTES) ImportError.LowStorage else ImportError.ReadFailed
        } catch (e: SecurityException) {
            part.delete()
            throw ImportError.ReadFailed
        }
    }

    /** Makes a staged file permanent and returns its final name. */
    fun commit(partName: String): String {
        require(partName.endsWith(PART)) { "not a staged file" }
        val finalName = partName.removeSuffix(PART)
        if (!file(partName).renameTo(File(dir, finalName))) throw IOException("could not store attachment")
        return finalName
    }

    fun discard(partName: String) {
        runCatching { file(partName).delete() }
    }

    fun delete(name: String) {
        runCatching { file(name).delete() }
    }

    /** Removes files no attachment refers to (and leftover parts), once they are at least [minAgeMs] old. */
    fun sweep(keep: Set<String>, now: Long, minAgeMs: Long = SWEEP_MIN_AGE_MS): Int {
        var removed = 0
        dir.listFiles()?.forEach { f ->
            if (f.isFile && f.name !in keep && now - f.lastModified() >= minAgeMs && f.delete()) removed++
        }
        return removed
    }

    private fun checkDecodable(file: File) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) throw ImportError.Unsupported
        if (options.outWidth.toLong() * options.outHeight > MAX_PIXELS) throw ImportError.TooLarge(MAX_IMAGE_BYTES)
    }

    data class SniffedType(val kind: AttachmentKind, val mimeType: String, val extension: String)

    companion object {
        const val DIR = "attachments"
        const val PART = ".part"
        const val MAX_IMAGE_BYTES = 30L * 1024 * 1024
        const val MAX_PDF_BYTES = 50L * 1024 * 1024
        const val MAX_PIXELS = 120_000_000L
        /** Space left free on the phone after an import. */
        const val RESERVE_BYTES = 50L * 1024 * 1024
        const val SWEEP_MIN_AGE_MS = 60 * 60 * 1000L
        private const val HEADER_BYTES = 16
        private const val MIN_SNIFF_BYTES = 12

        internal fun sniff(h: ByteArray, n: Int): SniffedType? {
            fun at(offset: Int, vararg bytes: Int) = n >= offset + bytes.size && bytes.indices.all { h[offset + it] == bytes[it].toByte() }
            fun ascii(offset: Int, text: String) = at(offset, *text.map { it.code }.toIntArray())
            return when {
                ascii(0, "%PDF-") -> SniffedType(AttachmentKind.Pdf, "application/pdf", "pdf")
                at(0, 0xFF, 0xD8, 0xFF) -> SniffedType(AttachmentKind.Image, "image/jpeg", "jpg")
                at(0, 137, 80, 78, 71, 13, 10, 26, 10) -> SniffedType(AttachmentKind.Image, "image/png", "png")
                ascii(0, "RIFF") && ascii(8, "WEBP") -> SniffedType(AttachmentKind.Image, "image/webp", "webp")
                ascii(0, "GIF8") -> SniffedType(AttachmentKind.Image, "image/gif", "gif")
                ascii(4, "ftyp") && (ascii(8, "heic") || ascii(8, "heix") || ascii(8, "mif1") || ascii(8, "msf1")) ->
                    SniffedType(AttachmentKind.Image, "image/heic", "heic")
                else -> null
            }
        }

        /** A name safe to show: no path, no control characters, at most 120 characters. */
        internal fun cleanDisplayName(raw: String?, extension: String): String {
            val base = (raw ?: "").substringAfterLast('/').substringAfterLast('\\').filterNot { it.isISOControl() }.trim().take(120)
            return base.ifEmpty { "file.$extension" }
        }

        fun countPdfPages(file: File): Int =
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use { it.pageCount } }
    }
}
