package app.drop.attach

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.InputStream

/** Where an attachment comes from: a picked file, a camera photo or a recording. */
interface ImportSource {
    val displayName: String?
    val declaredSize: Long?
    fun open(): InputStream
}

class FileImportSource(private val file: File, override val displayName: String?) : ImportSource {
    override val declaredSize: Long? get() = file.length().takeIf { it > 0 }
    override fun open(): InputStream = file.inputStream()
}

/** A file chosen with the system picker. Its name and size come from the provider, if it says. */
class UriImportSource(private val resolver: ContentResolver, private val uri: Uri) : ImportSource {
    private val meta: Pair<String?, Long?> by lazy {
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use null
                val name = c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { if (c.isNull(it)) null else c.getString(it) }
                val size = c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { if (c.isNull(it)) null else c.getLong(it) }
                name to size
            }
        }.getOrNull() ?: (null to null)
    }

    override val displayName: String? get() = meta.first
    override val declaredSize: Long? get() = meta.second?.takeIf { it > 0 }
    override fun open(): InputStream = resolver.openInputStream(uri) ?: throw ImportError.ReadFailed
}

/** Why a file can't be attached. */
sealed class ImportError : Exception() {
    data object Unsupported : ImportError() {
        private fun readResolve(): Any = Unsupported
    }

    data class TooLarge(val limitBytes: Long) : ImportError()

    data object UnreadablePdf : ImportError() {
        private fun readResolve(): Any = UnreadablePdf
    }

    data object Empty : ImportError() {
        private fun readResolve(): Any = Empty
    }

    data object LowStorage : ImportError() {
        private fun readResolve(): Any = LowStorage
    }

    data object ReadFailed : ImportError() {
        private fun readResolve(): Any = ReadFailed
    }
}
