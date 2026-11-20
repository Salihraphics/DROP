package app.drop.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Open tasks by when they're due. Times are local ("2026-10-24T09:00"), so they compare as text. */
data class TaskCounts(
    val open: Int,
    val due: Int,
    val today: Int,
    val upcoming: Int,
    val tomorrow: Int = 0,
)

/** An open task with its reminder time, if it has one. */
data class TaskWithTime(
    val id: Long,
    val title: String,
    val body: String,
    val pinned: Boolean,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "local_time") val localTime: String?,
    @ColumnInfo(name = "fired_at") val firedAt: Long?,
    val format: Int = 0,
)

/** The Dashboard's reads. Each one counts only what is really there: nothing from Trash. */
@Dao
interface DashboardDao {
    @Query("SELECT COUNT(*) FROM notes WHERE deleted_at IS NULL")
    fun observeActiveCount(): Flow<Int>

    @Query(
        "SELECT n.id, n.title, n.body, n.pinned, n.updated_at, r.local_time, r.fired_at, n.format FROM notes n " +
            "LEFT JOIN reminders r ON r.note_id = n.id " +
            "WHERE n.deleted_at IS NULL AND n.is_task = 1 AND n.completed_at IS NULL " +
            "ORDER BY (r.local_time IS NULL), r.local_time, n.pinned DESC, n.updated_at DESC, n.id DESC LIMIT :limit",
    )
    suspend fun openTasks(limit: Int): List<TaskWithTime>

    /**
     * [now] is the current local time, [tomorrow] the start of tomorrow and [dayAfter] the start of
     * the day after. A task is due once its reminder fired or its time passed.
     */
    @Query(
        "SELECT COUNT(*) AS open, " +
            "COALESCE(SUM(CASE WHEN r.note_id IS NOT NULL AND (r.fired_at IS NOT NULL OR r.local_time <= :now) THEN 1 ELSE 0 END), 0) AS due, " +
            "COALESCE(SUM(CASE WHEN r.fired_at IS NULL AND r.local_time > :now AND r.local_time < :tomorrow THEN 1 ELSE 0 END), 0) AS today, " +
            "COALESCE(SUM(CASE WHEN r.fired_at IS NULL AND r.local_time >= :tomorrow AND r.local_time < :dayAfter AND r.local_time > :now THEN 1 ELSE 0 END), 0) AS tomorrow, " +
            "COALESCE(SUM(CASE WHEN r.fired_at IS NULL AND r.local_time >= :dayAfter AND r.local_time > :now THEN 1 ELSE 0 END), 0) AS upcoming " +
            "FROM notes n LEFT JOIN reminders r ON r.note_id = n.id " +
            "WHERE n.deleted_at IS NULL AND n.is_task = 1 AND n.completed_at IS NULL",
    )
    fun observeTaskCounts(now: String, tomorrow: String, dayAfter: String): Flow<TaskCounts>

    @Query("SELECT * FROM notes WHERE deleted_at IS NULL AND is_task = 1 AND completed_at >= :start AND completed_at < :end ORDER BY completed_at DESC, id DESC")
    fun observeCompletedTasks(start: Long, end: Long): Flow<List<Note>>

    @Query(
        "SELECT n.id, n.title, n.body, n.pinned, n.updated_at, r.local_time, r.fired_at, n.format FROM notes n " +
            "JOIN reminders r ON r.note_id = n.id " +
            "WHERE n.deleted_at IS NULL AND n.is_task = 1 AND n.completed_at IS NULL ORDER BY r.local_time, n.id",
    )
    fun observeScheduledOpenTasks(): Flow<List<TaskWithTime>>

    @Query("SELECT * FROM notes WHERE deleted_at IS NULL AND is_task = 0 ORDER BY updated_at DESC, id DESC LIMIT :limit")
    fun observeRecentNotes(limit: Int): Flow<List<Note>>

    @Query("SELECT COUNT(*) FROM notes WHERE deleted_at IS NULL AND created_at >= :start AND created_at < :end")
    fun observeCreatedCount(start: Long, end: Long): Flow<Int>

    @Query(
        "SELECT r.note_id, r.local_time, r.fired_at, n.title, n.body, n.is_task, n.format FROM reminders r " +
            "JOIN notes n ON n.id = r.note_id WHERE n.deleted_at IS NULL AND r.fired_at IS NULL ORDER BY r.local_time ASC LIMIT 1",
    )
    fun observeNextReminder(): Flow<ReminderWithNote?>
}
