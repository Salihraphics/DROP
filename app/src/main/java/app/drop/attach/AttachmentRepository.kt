package app.drop.attach

import androidx.room.withTransaction
import app.drop.data.Attachment
import app.drop.data.AttachmentKind
import app.drop.data.DropDatabase
import app.drop.data.Note
import app.drop.data.OcrStatus
import java.util.UUID

/**
 * Notes' files. The database and the files always agree: a file is made permanent only together
 * with its row, and a file is deleted only after its row is gone.
 */
class AttachmentRepository(
    private val database: DropDatabase,
    val store: AttachmentStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val attachments get() = database.attachmentDao()
    private val notes get() = database.noteDao()

    /** Creates a note holding [staged]. Returns the note's id. */
    suspend fun saveAsNewNote(
        noteUuid: String? = null,
        title: String = "",
        body: String = "",
        staged: StagedFile,
        durationMs: Long? = null,
    ): Long {
        val fileName = try {
            store.commit(staged.partName)
        } catch (e: Exception) {
            store.discard(staged.partName)
            throw e
        }
        val now = clock()
        return try {
            database.withTransaction {
                val noteId = notes.insert(
                    Note(uuid = noteUuid ?: UUID.randomUUID().toString(), title = title, body = body, createdAt = now, updatedAt = now).withDerivedFields(),
                )
                attachments.insert(staged.toAttachment(noteId, fileName, now, durationMs))
                noteId
            }
        } catch (e: Exception) {
            store.delete(fileName)
            throw e
        }
    }

    /** Adds [staged] to an existing note that isn't in Trash. Returns the attachment's id. */
    suspend fun attachTo(noteId: Long, staged: StagedFile): Long {
        val note = notes.getById(noteId)
        if (note == null || note.deletedAt != null) {
            store.discard(staged.partName)
            throw IllegalStateException("note is gone")
        }
        val fileName = try {
            store.commit(staged.partName)
        } catch (e: Exception) {
            store.discard(staged.partName)
            throw e
        }
        return try {
            attachments.insert(staged.toAttachment(noteId, fileName, clock(), null))
        } catch (e: Exception) {
            store.delete(fileName)
            throw e
        }
    }

    suspend fun delete(attachmentId: Long) {
        val row = attachments.getById(attachmentId) ?: return
        attachments.delete(attachmentId)
        store.delete(row.fileName)
    }

    /** Deletes a note in Trash for good, with its files. Notes outside Trash are never touched. */
    suspend fun deleteFromTrash(noteId: Long) {
        val note = notes.getById(noteId) ?: return
        if (note.deletedAt == null) return
        val files = attachments.forNotes(listOf(noteId)).map { it.fileName }
        notes.deleteFromTrash(noteId)
        files.forEach(store::delete)
    }

    suspend fun emptyTrash() {
        val ids = notes.trashedIds()
        val files = ids.chunked(500).flatMap { attachments.forNotes(it) }.map { it.fileName }
        notes.emptyTrash()
        // Only files no remaining attachment uses.
        val kept = attachments.allFileNames().toSet()
        files.filterNot { it in kept }.forEach(store::delete)
    }

    /** Removes files nothing refers to, such as copies left by an interrupted import. */
    suspend fun sweep(): Int = store.sweep(attachments.allFileNames().toSet(), clock())

    private fun StagedFile.toAttachment(noteId: Long, fileName: String, now: Long, durationMs: Long?): Attachment = Attachment(
        noteId = noteId,
        kind = kind.id,
        fileName = fileName,
        displayName = displayName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        createdAt = now,
        durationMs = durationMs,
        pageCount = pageCount,
        // Photos and PDFs are read for text in the background; recordings aren't.
        ocrStatus = if (kind == AttachmentKind.Audio) OcrStatus.None.id else OcrStatus.Pending.id,
    )
}
