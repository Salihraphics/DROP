package app.drop.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.util.UUID

// Projects ----------------------------------------------------------------------------------

@Entity(tableName = "projects", indices = [Index(value = ["uuid"], unique = true)])
data class Project(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val name: String,
    @ColumnInfo(defaultValue = "") val description: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** A note in a project. A note can be in any number of projects. */
@Entity(
    tableName = "project_items",
    primaryKeys = ["project_id", "note_id"],
    foreignKeys = [
        ForeignKey(entity = Project::class, parentColumns = ["id"], childColumns = ["project_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Note::class, parentColumns = ["id"], childColumns = ["note_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["note_id"])],
)
data class ProjectItem(
    @ColumnInfo(name = "project_id") val projectId: Long,
    @ColumnInfo(name = "note_id") val noteId: Long,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)

data class ProjectSummary(
    val id: Long,
    val name: String,
    val description: String,
    @ColumnInfo(name = "item_count") val itemCount: Int,
)

@Dao
interface ProjectDao {
    @Insert
    suspend fun insert(project: Project): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(projects: List<Project>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addItem(item: ProjectItem)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addItems(items: List<ProjectItem>)

    @Query("UPDATE projects SET name = :name, description = :description, updated_at = :at WHERE id = :id")
    suspend fun rename(id: Long, name: String, description: String, at: Long)

    /** Deletes the project only; its notes stay in the library. */
    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM project_items WHERE project_id = :projectId AND note_id = :noteId")
    suspend fun removeItem(projectId: Long, noteId: Long)

    @Query("SELECT * FROM projects WHERE id = :id")
    fun observe(id: Long): Flow<Project?>

    @Query(
        "SELECT p.id, p.name, p.description, (SELECT COUNT(*) FROM project_items i JOIN notes n ON n.id = i.note_id " +
            "WHERE i.project_id = p.id AND n.deleted_at IS NULL) AS item_count FROM projects p ORDER BY p.name COLLATE NOCASE",
    )
    fun observeAll(): Flow<List<ProjectSummary>>

    @Query(
        "SELECT n.* FROM notes n JOIN project_items i ON i.note_id = n.id WHERE i.project_id = :projectId " +
            "AND n.deleted_at IS NULL ORDER BY n.pinned DESC, n.updated_at DESC, n.id DESC",
    )
    fun observeItems(projectId: Long): Flow<List<Note>>

    @Query("SELECT project_id FROM project_items WHERE note_id = :noteId")
    fun observeProjectIdsFor(noteId: Long): Flow<List<Long>>

    @Query("SELECT * FROM projects")
    suspend fun all(): List<Project>

    @Query("SELECT * FROM project_items")
    suspend fun allItems(): List<ProjectItem>

    @Query("SELECT uuid FROM projects")
    suspend fun allUuids(): List<String>

    @Query("SELECT * FROM projects WHERE uuid = :uuid")
    suspend fun getByUuid(uuid: String): Project?
}

// Connected notes ---------------------------------------------------------------------------

/** A resolved [[link]]: [label] in [sourceId] points at [targetId]. */
@Entity(
    tableName = "note_links",
    primaryKeys = ["source_id", "label"],
    foreignKeys = [
        ForeignKey(entity = Note::class, parentColumns = ["id"], childColumns = ["source_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Note::class, parentColumns = ["id"], childColumns = ["target_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["target_id"])],
)
data class NoteLink(
    @ColumnInfo(name = "source_id") val sourceId: Long,
    val label: String,
    @ColumnInfo(name = "target_id") val targetId: Long,
)

data class LinkTarget(
    val label: String,
    @ColumnInfo(name = "target_id") val targetId: Long,
    val title: String,
    val body: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long?,
    val format: Int = 0,
)

@Dao
interface LinkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(link: NoteLink)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(links: List<NoteLink>)

    @Query("DELETE FROM note_links WHERE source_id = :sourceId AND label = :label")
    suspend fun delete(sourceId: Long, label: String)

    @Query("SELECT * FROM note_links WHERE source_id = :sourceId")
    suspend fun forSource(sourceId: Long): List<NoteLink>

    @Query("SELECT l.label, l.target_id, n.title, n.body, n.deleted_at, n.format FROM note_links l JOIN notes n ON n.id = l.target_id WHERE l.source_id = :sourceId")
    fun observeOutgoing(sourceId: Long): Flow<List<LinkTarget>>

    @Query(
        "SELECT DISTINCT n.* FROM note_links l JOIN notes n ON n.id = l.source_id " +
            "WHERE l.target_id = :targetId AND l.source_id != :targetId AND n.deleted_at IS NULL ORDER BY n.updated_at DESC",
    )
    fun observeBacklinks(targetId: Long): Flow<List<Note>>

    @Query("SELECT * FROM note_links")
    suspend fun all(): List<NoteLink>
}

// Daily Log ---------------------------------------------------------------------------------

/** The day's own line in the Daily Log. [date] is "2026-10-24". */
@Entity(tableName = "daily_entries")
data class DailyEntry(
    @PrimaryKey val date: String,
    val text: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Dao
interface DailyDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<DailyEntry>)

    @Query("DELETE FROM daily_entries WHERE date = :date")
    suspend fun delete(date: String)

    @Upsert
    suspend fun put(entry: DailyEntry)

    @Query("SELECT * FROM daily_entries WHERE date = :date")
    fun observe(date: String): Flow<DailyEntry?>

    @Query("SELECT * FROM daily_entries WHERE date = :date")
    suspend fun get(date: String): DailyEntry?

    @Query("SELECT * FROM daily_entries ORDER BY date")
    suspend fun all(): List<DailyEntry>
}

// Idea Canvas -------------------------------------------------------------------------------

@Entity(tableName = "canvases", indices = [Index(value = ["uuid"], unique = true)])
data class IdeaCanvas(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** How a card is drawn. Stored by [id]; unknown values read as [Card]. */
enum class CardShape(val id: Int) {
    Card(0),
    Sticky(1),
    Pill(2),
    Frame(3);

    companion object {
        fun of(id: Int): CardShape = entries.firstOrNull { it.id == id } ?: Card
    }
}

/** A card's tint. Stored by [id]; unknown values read as [Default]. */
enum class CardColor(val id: Int) {
    Default(0),
    Lime(1),
    Blue(2),
    Amber(3),
    Rose(4);

    companion object {
        fun of(id: Int): CardColor = entries.firstOrNull { it.id == id } ?: Default
    }
}

/** A note placed on a canvas. A note appears at most once per canvas. */
@Entity(
    tableName = "canvas_cards",
    foreignKeys = [
        ForeignKey(entity = IdeaCanvas::class, parentColumns = ["id"], childColumns = ["canvas_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Note::class, parentColumns = ["id"], childColumns = ["note_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["canvas_id", "note_id"], unique = true), Index(value = ["note_id"])],
)
data class CanvasCard(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "canvas_id") val canvasId: Long,
    @ColumnInfo(name = "note_id") val noteId: Long,
    val x: Float,
    val y: Float,
    @ColumnInfo(defaultValue = "0") val shape: Int = 0,
    @ColumnInfo(defaultValue = "0") val color: Int = 0,
)

/** A line between two cards. Undirected: each pair is stored once. */
@Entity(
    tableName = "canvas_edges",
    primaryKeys = ["from_card", "to_card"],
    foreignKeys = [
        ForeignKey(entity = CanvasCard::class, parentColumns = ["id"], childColumns = ["from_card"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CanvasCard::class, parentColumns = ["id"], childColumns = ["to_card"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["to_card"])],
)
data class CanvasEdge(
    @ColumnInfo(name = "from_card") val fromCard: Long,
    @ColumnInfo(name = "to_card") val toCard: Long,
)

data class CanvasSummary(
    val id: Long,
    val name: String,
    @ColumnInfo(name = "card_count") val cardCount: Int,
)

data class CardWithNote(
    val id: Long,
    @ColumnInfo(name = "note_id") val noteId: Long,
    val x: Float,
    val y: Float,
    val title: String,
    val body: String,
    @ColumnInfo(name = "is_task") val isTask: Boolean,
    val format: Int = 0,
    @ColumnInfo(name = "completed_at") val completedAt: Long? = null,
    val url: String? = null,
    val shape: Int = 0,
    val color: Int = 0,
)

@Dao
interface CanvasDao {
    @Insert
    suspend fun insert(canvas: IdeaCanvas): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addCard(card: CanvasCard): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addEdge(edge: CanvasEdge)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(canvases: List<IdeaCanvas>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCards(cards: List<CanvasCard>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEdges(edges: List<CanvasEdge>)

    @Query("UPDATE canvases SET name = :name, updated_at = :at WHERE id = :id")
    suspend fun rename(id: Long, name: String, at: Long)

    @Query("DELETE FROM canvases WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE canvas_cards SET x = :x, y = :y WHERE id = :id")
    suspend fun moveCard(id: Long, x: Float, y: Float)

    @Query("UPDATE canvas_cards SET shape = :shape, color = :color WHERE id = :id")
    suspend fun styleCard(id: Long, shape: Int, color: Int)

    @Query("DELETE FROM canvas_cards WHERE id = :id")
    suspend fun removeCard(id: Long)

    @Query("DELETE FROM canvas_edges WHERE from_card = :cardId OR to_card = :cardId")
    suspend fun removeEdgesOf(cardId: Long)

    /** Removes the line between two cards whichever way it was stored. */
    @Query("DELETE FROM canvas_edges WHERE (from_card = :a AND to_card = :b) OR (from_card = :b AND to_card = :a)")
    suspend fun removeEdge(a: Long, b: Long)

    @Query("SELECT * FROM canvases WHERE id = :id")
    fun observe(id: Long): Flow<IdeaCanvas?>

    @Query(
        "SELECT c.id, c.name, (SELECT COUNT(*) FROM canvas_cards k JOIN notes n ON n.id = k.note_id " +
            "WHERE k.canvas_id = c.id AND n.deleted_at IS NULL) AS card_count FROM canvases c ORDER BY c.updated_at DESC",
    )
    fun observeAll(): Flow<List<CanvasSummary>>

    @Query(
        "SELECT k.id, k.note_id, k.x, k.y, n.title, n.body, n.is_task, n.format, n.completed_at, n.url, k.shape, k.color " +
            "FROM canvas_cards k JOIN notes n ON n.id = k.note_id WHERE k.canvas_id = :canvasId AND n.deleted_at IS NULL ORDER BY k.id",
    )
    fun observeCards(canvasId: Long): Flow<List<CardWithNote>>

    @Query("SELECT e.* FROM canvas_edges e JOIN canvas_cards k ON k.id = e.from_card WHERE k.canvas_id = :canvasId")
    fun observeEdges(canvasId: Long): Flow<List<CanvasEdge>>

    @Query("SELECT * FROM canvases")
    suspend fun all(): List<IdeaCanvas>

    @Query("SELECT * FROM canvas_cards")
    suspend fun allCards(): List<CanvasCard>

    @Query("SELECT * FROM canvas_edges")
    suspend fun allEdges(): List<CanvasEdge>

    @Query("SELECT * FROM canvases WHERE uuid = :uuid")
    suspend fun getByUuid(uuid: String): IdeaCanvas?

    @Query("SELECT * FROM canvas_cards WHERE canvas_id = :canvasId")
    suspend fun cardsOf(canvasId: Long): List<CanvasCard>
}
