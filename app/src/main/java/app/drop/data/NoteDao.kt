package app.drop.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE deleted_at IS NULL ORDER BY updated_at DESC, id DESC")
    fun observeActive(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC, id DESC")
    fun observeTrash(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE deleted_at IS NULL ORDER BY created_at ASC, id ASC")
    suspend fun getActive(): List<Note>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Long): Note?

    @Query("SELECT * FROM notes WHERE uuid = :uuid")
    suspend fun getByUuid(uuid: String): Note?

    @Query("SELECT * FROM notes WHERE uuid IN (:uuids)")
    suspend fun getByUuids(uuids: List<String>): List<Note>

    /** A note saved with the same text a moment ago: used to ignore double shares. */
    @Query("SELECT * FROM notes WHERE deleted_at IS NULL AND title = :title AND body = :body AND created_at >= :since ORDER BY created_at DESC LIMIT 1")
    suspend fun findRecentDuplicate(title: String, body: String, since: Long): Note?

    @Query("SELECT uuid FROM notes")
    suspend fun allUuids(): List<String>

    @Query("UPDATE notes SET pinned = :pinned WHERE id IN (:ids)")
    suspend fun setPinned(ids: List<Long>, pinned: Boolean)

    @Query("UPDATE notes SET deleted_at = :at WHERE id IN (:ids) AND deleted_at IS NULL")
    suspend fun trashAll(ids: List<Long>, at: Long)

    /** Restores only the notes that were trashed together at [at], so an undo never restores more. */
    @Query("UPDATE notes SET deleted_at = NULL WHERE id IN (:ids) AND deleted_at = :at")
    suspend fun untrashAll(ids: List<Long>, at: Long)

    @Query("SELECT * FROM notes")
    suspend fun allNotes(): List<Note>

    @Insert
    suspend fun insert(note: Note): Long

    @Insert
    suspend fun insertAll(notes: List<Note>)

    @Update
    suspend fun update(note: Note)

    @Query("UPDATE notes SET deleted_at = :deletedAt WHERE id = :id")
    suspend fun setDeletedAt(id: Long, deletedAt: Long?)

    @Query("SELECT * FROM notes WHERE deleted_at IS NULL AND is_task = 0 ORDER BY updated_at DESC, id DESC LIMIT :limit")
    suspend fun recentNotes(limit: Int): List<Note>

    @Query("SELECT * FROM notes WHERE deleted_at IS NULL AND is_task = 1 AND completed_at IS NULL ORDER BY pinned DESC, updated_at DESC, id DESC LIMIT :limit")
    suspend fun openTasks(limit: Int): List<Note>

    @Query("SELECT COUNT(*) FROM notes WHERE deleted_at IS NULL AND is_task = 1 AND completed_at IS NULL")
    suspend fun openTaskCount(): Int

    /** Completes a task only if it is still open, so a second tap (or a widget) never moves the time. */
    @Query("UPDATE notes SET completed_at = :completedAt WHERE id = :id AND is_task = 1 AND completed_at IS NULL AND deleted_at IS NULL")
    suspend fun completeIfOpen(id: Long, completedAt: Long): Int

    @Query("UPDATE notes SET completed_at = :completedAt WHERE id = :id AND is_task = 1")
    suspend fun setCompletedAt(id: Long, completedAt: Long?)

    @Query("DELETE FROM notes WHERE id = :id AND deleted_at IS NOT NULL")
    suspend fun deleteFromTrash(id: Long)

    @Query("SELECT * FROM notes WHERE deleted_at IS NULL AND created_at >= :start AND created_at < :end ORDER BY created_at")
    fun observeCreatedBetween(start: Long, end: Long): Flow<List<Note>>

    /** Notes edited in the range that were created before it. */
    @Query("SELECT * FROM notes WHERE deleted_at IS NULL AND updated_at >= :start AND updated_at < :end AND created_at < :start ORDER BY updated_at")
    fun observeLastEditedBetween(start: Long, end: Long): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE deleted_at IS NULL AND is_task = 1 AND completed_at >= :start AND completed_at < :end ORDER BY completed_at")
    fun observeCompletedBetween(start: Long, end: Long): Flow<List<Note>>

    @Query("SELECT id FROM notes WHERE deleted_at IS NOT NULL")
    suspend fun trashedIds(): List<Long>

    @Query("DELETE FROM notes WHERE deleted_at IS NOT NULL")
    suspend fun emptyTrash()
}
