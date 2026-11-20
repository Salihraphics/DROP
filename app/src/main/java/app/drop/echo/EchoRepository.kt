package app.drop.echo

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** What ECHO reads: open notes and the connections the person made. Never the Vault. */
data class EchoNoteRow(
    val id: Long,
    val title: String,
    val body: String,
    @ColumnInfo(name = "is_task") val isTask: Boolean,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val format: Int = 0,
)

data class EchoProjectRow(
    @ColumnInfo(name = "note_id") val noteId: Long,
    @ColumnInfo(name = "project_id") val projectId: Long,
    val name: String,
)

/** Two notes the person connected; [name] is the canvas, or empty for a [[link]]. */
data class EchoPairRow(val a: Long, val b: Long, val name: String)

@Dao
interface EchoDao {
    @Query("SELECT id, title, body, is_task, updated_at, format FROM notes WHERE deleted_at IS NULL AND NOT (is_task = 1 AND completed_at IS NOT NULL)")
    fun observeNotes(): Flow<List<EchoNoteRow>>

    @Query("SELECT i.note_id, p.id AS project_id, p.name FROM project_items i JOIN projects p ON p.id = i.project_id")
    fun observeProjectMembers(): Flow<List<EchoProjectRow>>

    @Query(
        "SELECT ca.note_id AS a, cb.note_id AS b, c.name AS name FROM canvas_edges e " +
            "JOIN canvas_cards ca ON ca.id = e.from_card JOIN canvas_cards cb ON cb.id = e.to_card JOIN canvases c ON c.id = ca.canvas_id",
    )
    fun observeCanvasPairs(): Flow<List<EchoPairRow>>

    @Query("SELECT source_id AS a, target_id AS b, '' AS name FROM note_links")
    fun observeLinkPairs(): Flow<List<EchoPairRow>>
}
