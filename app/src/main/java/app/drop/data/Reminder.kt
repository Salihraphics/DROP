package app.drop.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * When a note comes back. [localTime] is a wall-clock time ("2026-10-24T09:00") so a reminder
 * keeps its local time when the phone changes time zone.
 */
@Entity(
    tableName = "reminders",
    foreignKeys = [ForeignKey(entity = Note::class, parentColumns = ["id"], childColumns = ["note_id"], onDelete = ForeignKey.CASCADE)],
)
data class Reminder(
    @PrimaryKey @ColumnInfo(name = "note_id") val noteId: Long,
    @ColumnInfo(name = "local_time") val localTime: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "fired_at") val firedAt: Long? = null,
)

data class ReminderWithNote(
    @ColumnInfo(name = "note_id") val noteId: Long,
    @ColumnInfo(name = "local_time") val localTime: String,
    @ColumnInfo(name = "fired_at") val firedAt: Long?,
    val title: String,
    val body: String,
    @ColumnInfo(name = "is_task") val isTask: Boolean,
    val format: Int = 0,
)

@Dao
interface ReminderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(reminder: Reminder)

    @Query("DELETE FROM reminders WHERE note_id = :noteId")
    suspend fun delete(noteId: Long)

    @Query("UPDATE reminders SET fired_at = :at WHERE note_id = :noteId AND fired_at IS NULL")
    suspend fun markFired(noteId: Long, at: Long): Int

    @Query("SELECT * FROM reminders WHERE note_id = :noteId")
    suspend fun get(noteId: Long): Reminder?

    @Query("SELECT * FROM reminders WHERE note_id = :noteId")
    fun observe(noteId: Long): Flow<Reminder?>

    @Query("SELECT * FROM reminders WHERE fired_at IS NULL")
    suspend fun pending(): List<Reminder>

    @Query("SELECT r.note_id, r.local_time, r.fired_at, n.title, n.body, n.is_task, n.format FROM reminders r JOIN notes n ON n.id = r.note_id WHERE n.deleted_at IS NULL ORDER BY r.local_time ASC")
    fun observeAll(): Flow<List<ReminderWithNote>>

    @Query("SELECT COUNT(*) FROM reminders r JOIN notes n ON n.id = r.note_id WHERE n.deleted_at IS NULL AND r.fired_at IS NOT NULL")
    fun observeDueCount(): Flow<Int>
}
