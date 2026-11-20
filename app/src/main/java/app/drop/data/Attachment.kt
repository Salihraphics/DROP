package app.drop.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** What an attachment is. Stored by [id]. */
enum class AttachmentKind(val id: String) {
    Image("image"),
    Pdf("pdf"),
    Audio("audio");

    companion object {
        fun of(id: String): AttachmentKind? = entries.firstOrNull { it.id == id }
    }
}

/** Where text recognition stands for an attachment. Stored by [id]. */
enum class OcrStatus(val id: String) {
    None("none"),
    Pending("pending"),
    Running("running"),
    Done("done"),
    Failed("failed");

    companion object {
        fun of(id: String): OcrStatus = entries.firstOrNull { it.id == id } ?: None
    }
}

/** A file kept with a note: a photo, a PDF or a recording. The file itself is in app storage. */
@Entity(
    tableName = "attachments",
    foreignKeys = [ForeignKey(entity = Note::class, parentColumns = ["id"], childColumns = ["note_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["uuid"], unique = true), Index(value = ["note_id"])],
)
data class Attachment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "note_id") val noteId: Long,
    /** An [AttachmentKind] id. */
    val kind: String,
    /** The file's name inside app storage. */
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long? = null,
    @ColumnInfo(name = "page_count") val pageCount: Int? = null,
    /** An [OcrStatus] id. */
    @ColumnInfo(name = "ocr_status", defaultValue = "none") val ocrStatus: String = OcrStatus.None.id,
    @ColumnInfo(name = "ocr_text") val ocrText: String? = null,
    /** True once the person corrected the recognised text by hand. */
    @ColumnInfo(name = "ocr_edited", defaultValue = "0") val ocrEdited: Boolean = false,
    @ColumnInfo(name = "ocr_updated_at") val ocrUpdatedAt: Long? = null,
) {
    val attachmentKind: AttachmentKind? get() = AttachmentKind.of(kind)
    val status: OcrStatus get() = OcrStatus.of(ocrStatus)
}

data class AttachmentSummary(
    @ColumnInfo(name = "note_id") val noteId: Long,
    val kind: String,
    @ColumnInfo(name = "duration_ms") val durationMs: Long?,
    @ColumnInfo(name = "page_count") val pageCount: Int?,
)

data class OcrEntry(
    @ColumnInfo(name = "id") val attachmentId: Long,
    @ColumnInfo(name = "note_id") val noteId: Long,
    @ColumnInfo(name = "ocr_text") val text: String,
)

@Dao
interface AttachmentDao {
    @Insert
    suspend fun insert(attachment: Attachment): Long

    /** Recognition that was running when the app stopped starts again. */
    @Query("UPDATE attachments SET ocr_status = 'pending' WHERE ocr_status = 'running'")
    suspend fun requeueInterruptedOcr(): Int

    @Query("UPDATE attachments SET ocr_status = 'running' WHERE id = :id AND ocr_status = 'pending'")
    suspend fun claimOcr(id: Long): Int

    @Query("UPDATE attachments SET ocr_status = 'pending' WHERE id = :id AND ocr_status IN ('done', 'failed', 'none')")
    suspend fun requestOcr(id: Long): Int

    @Query("UPDATE attachments SET ocr_status = 'done', ocr_text = :text, ocr_edited = 0, ocr_updated_at = :at WHERE id = :id AND ocr_status = 'running'")
    suspend fun finishOcr(id: Long, text: String, at: Long): Int

    @Query("UPDATE attachments SET ocr_status = 'failed', ocr_updated_at = :at WHERE id = :id AND ocr_status = 'running'")
    suspend fun failOcr(id: Long, at: Long): Int

    @Query("UPDATE attachments SET ocr_text = :text, ocr_edited = 1, ocr_status = 'done', ocr_updated_at = :at WHERE id = :id")
    suspend fun editOcrText(id: Long, text: String, at: Long)

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun getById(id: Long): Attachment?

    @Query("SELECT * FROM attachments WHERE id = :id")
    fun observe(id: Long): Flow<Attachment?>

    @Query("SELECT * FROM attachments WHERE note_id = :noteId ORDER BY created_at ASC, id ASC")
    fun observeForNote(noteId: Long): Flow<List<Attachment>>

    @Query("SELECT COUNT(*) FROM attachments WHERE note_id = :noteId")
    suspend fun countForNote(noteId: Long): Int

    @Query("SELECT * FROM attachments WHERE note_id IN (:noteIds)")
    suspend fun forNotes(noteIds: List<Long>): List<Attachment>

    @Query("SELECT a.id, a.note_id, a.ocr_text FROM attachments a JOIN notes n ON n.id = a.note_id WHERE n.deleted_at IS NULL AND a.ocr_text IS NOT NULL AND a.ocr_text != ''")
    fun observeOcrText(): Flow<List<OcrEntry>>

    @Query("SELECT note_id, kind, duration_ms, page_count FROM attachments ORDER BY created_at ASC, id ASC")
    fun observeSummaries(): Flow<List<AttachmentSummary>>

    @Query("SELECT file_name FROM attachments")
    suspend fun allFileNames(): List<String>

    @Query("SELECT * FROM attachments WHERE ocr_status = 'pending' ORDER BY created_at ASC, id ASC LIMIT 1")
    suspend fun nextPendingOcr(): Attachment?
}
