package app.drop.ocr

import android.content.Context
import app.drop.attach.AttachmentStore
import java.io.File
import java.io.IOException

/** Copies the bundled Tesseract language files out of the APK once, where Tesseract can open them. */
object OcrData {
    const val LANGUAGES = "eng+tur"
    private const val VERSION = "tessdata_fast-4.1.0-eng-tur-1"
    private val FILES = listOf("eng.traineddata", "tur.traineddata")

    /** Returns the folder holding `tessdata/`. */
    @Synchronized
    fun install(context: Context): File {
        val root = File(context.noBackupFilesDir, "ocr")
        val dir = File(root, "tessdata").apply { mkdirs() }
        val marker = File(root, "version")
        if (marker.exists() && marker.readText() == VERSION && FILES.all { File(dir, it).length() > 0 }) return root
        for (name in FILES) {
            val part = File(dir, name + AttachmentStore.PART)
            context.assets.open("tessdata/$name").use { input -> part.outputStream().use { input.copyTo(it) } }
            if (!part.renameTo(File(dir, name))) throw IOException("could not install OCR data")
        }
        marker.writeText(VERSION)
        return root
    }
}
