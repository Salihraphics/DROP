package app.drop.history

import androidx.room.withTransaction
import app.drop.data.DropDatabase
import app.drop.data.Note
import app.drop.data.NoteRevision
import app.drop.data.RevisionDao
import app.drop.data.RevisionSummary
import app.drop.richtext.DropMarkdown
import kotlinx.coroutines.flow.Flow

/**
 * Time Machine: earlier versions of notes. Before an edit is written, the previous text is kept,
 * at most once every [INTERVAL_MS], so a pause in writing leaves a version to go back to.
 * History stays on the phone: it is never in backups or exports, and Vault notes have none.
 */
class NoteHistory(private val database: DropDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    private val dao: RevisionDao get() = database.revisionDao()

    fun observe(noteId: Long): Flow<List<RevisionSummary>> = dao.observeFor(noteId)

    fun observeCount(): Flow<Int> = dao.observeCount()

    fun observeStoredChars(): Flow<Long> = dao.observeStoredChars()

    /** A version of [noteId], or null if it doesn't belong to that note. */
    suspend fun get(noteId: Long, revisionId: Long): NoteRevision? = dao.get(revisionId)?.takeIf { it.noteId == noteId }

    /** Called with the stored note and its replacement, before the replacement is written. */
    suspend fun beforeWrite(previous: Note, next: Note) {
        if (sameContent(previous, next.title, next.body, next.format)) return
        // An empty note has nothing worth going back to.
        if (previous.title.isBlank() && DropMarkdown.plainOf(previous.body, previous.format).isBlank()) return
        val latest = dao.latest(previous.id)
        val now = clock()
        if (latest != null && now - latest.createdAt < INTERVAL_MS) return
        record(previous, latest, now)
    }

    /**
     * Puts an earlier version back. The current text is kept as a version first, whatever the
     * interval, so a restore can always be undone. Only the title and text change.
     */
    suspend fun restore(noteId: Long, revisionId: Long): Note? = database.withTransaction {
        val note = database.noteDao().getById(noteId)?.takeIf { it.deletedAt == null } ?: return@withTransaction null
        val revision = dao.get(revisionId)?.takeIf { it.noteId == noteId } ?: return@withTransaction null
        if (sameContent(note, revision.title, revision.body, revision.format)) return@withTransaction note
        val now = clock()
        record(note, dao.latest(noteId), now)
        val updated = note.copy(title = revision.title, body = revision.body, format = revision.format, updatedAt = now).withDerivedFields()
        database.noteDao().update(updated)
        updated
    }

    suspend fun clearFor(noteId: Long) = dao.deleteFor(noteId)

    suspend fun clearAll() = dao.deleteAll()

    private suspend fun record(note: Note, latest: NoteRevision?, now: Long) {
        if (latest != null && sameContent(note, latest.title, latest.body, latest.format)) return
        dao.insert(NoteRevision(noteId = note.id, title = note.title, body = note.body, format = note.format, editedAt = note.updatedAt, createdAt = now))
        dao.trimNote(note.id, KEEP_PER_NOTE)
        dao.trimAll(KEEP_TOTAL)
    }

    private fun sameContent(note: Note, title: String, body: String, format: Int) =
        note.title == title && note.body == body && note.format == format

    companion object {
        const val INTERVAL_MS = 10 * 60 * 1000L
        const val KEEP_PER_NOTE = 50
        const val KEEP_TOTAL = 2000
    }
}
