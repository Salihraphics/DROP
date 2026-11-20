package app.drop.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/** A note, task or saved link. Everything in the library is a note. */
@Entity(
    tableName = "notes",
    indices = [Index(value = ["uuid"], unique = true), Index(value = ["deleted_at"])],
)
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "") val uuid: String = UUID.randomUUID().toString(),
    val title: String,
    val body: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(defaultValue = "0") val pinned: Boolean = false,
    /** Set while the note is in Trash. */
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "is_task", defaultValue = "0") val isTask: Boolean = false,
    @ColumnInfo(name = "completed_at") val completedAt: Long? = null,
    /** The first link found in the title or body. */
    val url: String? = null,
    /** How [body] is stored: see [NoteFormat]. */
    @ColumnInfo(defaultValue = "0") val format: Int = 0,
) {
    val isCompletedTask: Boolean get() = isTask && completedAt != null

    /** Recomputes the fields derived from the others: a note that isn't a task is never completed. */
    fun withDerivedFields(): Note = copy(completedAt = if (isTask) completedAt else null, url = Links.find(title, body))
}
