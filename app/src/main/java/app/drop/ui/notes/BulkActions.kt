package app.drop.ui.notes

import androidx.room.withTransaction
import app.drop.data.DropDatabase
import app.drop.data.Project
import app.drop.data.ProjectItem

/**
 * Changes to many notes at once. Each runs in one transaction: either every selected note
 * changes or none does.
 */
class BulkActions(
    private val database: DropDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Told about each trashed note, so its reminder stops. */
    private val onTrashed: suspend (Long) -> Unit = {},
) {
    private val notes get() = database.noteDao()

    suspend fun setPinned(ids: List<Long>, pinned: Boolean) = database.withTransaction {
        ids.chunked(CHUNK).forEach { notes.setPinned(it, pinned) }
    }

    /** Moves the notes to Trash, all with the same time [at], so [untrash] can undo exactly this. */
    suspend fun trash(ids: List<Long>, at: Long) {
        database.withTransaction { ids.chunked(CHUNK).forEach { notes.trashAll(it, at) } }
        ids.forEach { runCatching { onTrashed(it) } }
    }

    suspend fun untrash(ids: List<Long>, at: Long) = database.withTransaction {
        ids.chunked(CHUNK).forEach { notes.untrashAll(it, at) }
    }

    /** Adds the notes to a project, or to a new one called [newName]. */
    suspend fun addToProject(ids: List<Long>, projectId: Long?, newName: String?) {
        val projects = database.projectDao()
        val now = clock()
        database.withTransaction {
            val target = projectId ?: newName?.trim()?.takeIf { it.isNotEmpty() }?.let {
                projects.insert(Project(name = it, createdAt = now, updatedAt = now))
            } ?: return@withTransaction
            projects.addItems(ids.map { ProjectItem(target, it, now) })
        }
    }

    suspend fun removeFromProject(ids: List<Long>, projectId: Long) = database.withTransaction {
        ids.forEach { database.projectDao().removeItem(projectId, it) }
    }

    private companion object {
        const val CHUNK = 500
    }
}
