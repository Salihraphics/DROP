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
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** How a note's body is stored. */
object NoteFormat {
    /** Plain text, as written before 0.9.0. */
    const val PLAIN = 0

    /** DROP's Markdown, which carries rich text. */
    const val MARKDOWN = 1
}

// Time Machine ------------------------------------------------------------------------------

/** An earlier version of a note. Revisions never leave the phone and never include the Vault. */
@Entity(
    tableName = "note_revisions",
    foreignKeys = [ForeignKey(entity = Note::class, parentColumns = ["id"], childColumns = ["note_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["note_id"])],
)
data class NoteRevision(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "note_id") val noteId: Long,
    val title: String,
    val body: String,
    @ColumnInfo(defaultValue = "0") val format: Int = 0,
    /** When the note had this text. */
    @ColumnInfo(name = "edited_at") val editedAt: Long,
    /** When this version was kept. */
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

data class RevisionSummary(
    val id: Long,
    val title: String,
    @ColumnInfo(name = "edited_at") val editedAt: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    val length: Int,
)

@Dao
interface RevisionDao {
    @Insert
    suspend fun insert(revision: NoteRevision): Long

    /** Keeps the newest [keep] versions of a note. */
    @Query("DELETE FROM note_revisions WHERE note_id = :noteId AND id NOT IN (SELECT id FROM note_revisions WHERE note_id = :noteId ORDER BY created_at DESC, id DESC LIMIT :keep)")
    suspend fun trimNote(noteId: Long, keep: Int)

    @Query("DELETE FROM note_revisions WHERE id NOT IN (SELECT id FROM note_revisions ORDER BY created_at DESC, id DESC LIMIT :keep)")
    suspend fun trimAll(keep: Int)

    @Query("DELETE FROM note_revisions WHERE note_id = :noteId")
    suspend fun deleteFor(noteId: Long)

    @Query("DELETE FROM note_revisions")
    suspend fun deleteAll()

    @Query("SELECT * FROM note_revisions WHERE note_id = :noteId ORDER BY created_at DESC, id DESC LIMIT 1")
    suspend fun latest(noteId: Long): NoteRevision?

    @Query("SELECT id, title, edited_at, created_at, LENGTH(title) + LENGTH(body) AS length FROM note_revisions WHERE note_id = :noteId ORDER BY created_at DESC, id DESC")
    fun observeFor(noteId: Long): Flow<List<RevisionSummary>>

    @Query("SELECT * FROM note_revisions WHERE id = :id")
    suspend fun get(id: Long): NoteRevision?

    @Query("SELECT COUNT(*) FROM note_revisions WHERE note_id = :noteId")
    suspend fun countFor(noteId: Long): Int

    @Query("SELECT COUNT(*) FROM note_revisions")
    fun observeCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(LENGTH(title) + LENGTH(body)), 0) FROM note_revisions")
    fun observeStoredChars(): Flow<Long>
}

// Templates ---------------------------------------------------------------------------------

/** One of the person's own templates. Built-in templates live in code, not here. */
@Entity(tableName = "templates", indices = [Index(value = ["uuid"], unique = true)])
data class NoteTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val name: String,
    val title: String,
    val body: String,
    @ColumnInfo(defaultValue = "0") val format: Int = 0,
    @ColumnInfo(name = "is_task", defaultValue = "0") val isTask: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Dao
interface TemplateDao {
    @Insert
    suspend fun insert(template: NoteTemplate): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(templates: List<NoteTemplate>)

    @Update
    suspend fun update(template: NoteTemplate)

    @Query("DELETE FROM templates WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM templates ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<NoteTemplate>>

    @Query("SELECT * FROM templates WHERE id = :id")
    suspend fun get(id: Long): NoteTemplate?

    @Query("SELECT * FROM templates")
    suspend fun all(): List<NoteTemplate>

    @Query("SELECT uuid FROM templates")
    suspend fun allUuids(): List<String>
}

// Smart Collections -------------------------------------------------------------------------

/** Saved rules; the matching notes are worked out each time, never stored. */
@Entity(tableName = "smart_collections", indices = [Index(value = ["uuid"], unique = true)])
data class SmartCollection(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val name: String,
    /** The rules as JSON. */
    val rules: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Dao
interface CollectionDao {
    @Insert
    suspend fun insert(collection: SmartCollection): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(collections: List<SmartCollection>)

    @Update
    suspend fun update(collection: SmartCollection)

    @Query("DELETE FROM smart_collections WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM smart_collections ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<SmartCollection>>

    @Query("SELECT * FROM smart_collections WHERE id = :id")
    fun observe(id: Long): Flow<SmartCollection?>

    @Query("SELECT * FROM smart_collections WHERE id = :id")
    suspend fun get(id: Long): SmartCollection?

    @Query("SELECT * FROM smart_collections")
    suspend fun all(): List<SmartCollection>
}

// Aliases and properties --------------------------------------------------------------------

/** Another name a note answers to in [[links]] and search. [key] is the normalised alias. */
@Entity(
    tableName = "note_aliases",
    primaryKeys = ["note_id", "key"],
    foreignKeys = [ForeignKey(entity = Note::class, parentColumns = ["id"], childColumns = ["note_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["key"])],
)
data class NoteAlias(
    @ColumnInfo(name = "note_id") val noteId: Long,
    val alias: String,
    val key: String,
)

/** A typed property such as a status or a due date. [key] is the normalised name. */
@Entity(
    tableName = "note_properties",
    primaryKeys = ["note_id", "key"],
    foreignKeys = [ForeignKey(entity = Note::class, parentColumns = ["id"], childColumns = ["note_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["key"])],
)
data class NoteProperty(
    @ColumnInfo(name = "note_id") val noteId: Long,
    val name: String,
    val key: String,
    val type: String,
    val value: String,
)

@Dao
interface MetaDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun putAlias(alias: NoteAlias): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAliases(aliases: List<NoteAlias>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putProperty(property: NoteProperty)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProperties(properties: List<NoteProperty>)

    @Query("DELETE FROM note_aliases WHERE note_id = :noteId AND `key` = :key")
    suspend fun deleteAlias(noteId: Long, key: String)

    @Query("DELETE FROM note_properties WHERE note_id = :noteId AND `key` = :key")
    suspend fun deleteProperty(noteId: Long, key: String)

    @Query("SELECT * FROM note_aliases WHERE note_id = :noteId ORDER BY alias COLLATE NOCASE")
    fun observeAliases(noteId: Long): Flow<List<NoteAlias>>

    @Query("SELECT a.* FROM note_aliases a JOIN notes n ON n.id = a.note_id WHERE n.deleted_at IS NULL")
    fun observeActiveAliases(): Flow<List<NoteAlias>>

    @Query("SELECT a.* FROM note_aliases a JOIN notes n ON n.id = a.note_id WHERE n.deleted_at IS NULL")
    suspend fun activeAliases(): List<NoteAlias>

    @Query("SELECT * FROM note_aliases")
    suspend fun allAliases(): List<NoteAlias>

    @Query("SELECT * FROM note_properties WHERE note_id = :noteId ORDER BY name COLLATE NOCASE")
    fun observeProperties(noteId: Long): Flow<List<NoteProperty>>

    @Query("SELECT p.* FROM note_properties p JOIN notes n ON n.id = p.note_id WHERE n.deleted_at IS NULL")
    fun observeActiveProperties(): Flow<List<NoteProperty>>

    @Query("SELECT * FROM note_properties")
    suspend fun allProperties(): List<NoteProperty>

    @Query("SELECT DISTINCT p.name FROM note_properties p JOIN notes n ON n.id = p.note_id WHERE n.deleted_at IS NULL ORDER BY p.name COLLATE NOCASE")
    fun observePropertyNames(): Flow<List<String>>
}

// Reads for collections and Knowledge Health ------------------------------------------------

data class ProjectRef(val uuid: String, val name: String)

data class MemberRow(
    @ColumnInfo(name = "note_id") val noteId: Long,
    @ColumnInfo(name = "project_uuid") val projectUuid: String,
    val name: String,
)

/** A note in Trash that a project or a canvas still uses. [placeId] is negative for canvases. */
data class TrashedUse(
    @ColumnInfo(name = "note_id") val noteId: Long,
    val title: String,
    val body: String,
    val format: Int,
    val place: String,
    @ColumnInfo(name = "place_id") val placeId: Long,
)

@Dao
interface InsightDao {
    @Query("SELECT uuid, name FROM projects ORDER BY name COLLATE NOCASE")
    fun observeProjects(): Flow<List<ProjectRef>>

    @Query("SELECT i.note_id, p.uuid AS project_uuid, p.name FROM project_items i JOIN projects p ON p.id = i.project_id")
    fun observeMembership(): Flow<List<MemberRow>>

    @Query("SELECT DISTINCT note_id FROM attachments")
    fun observeNotesWithAttachments(): Flow<List<Long>>

    @Query("SELECT note_id FROM reminders")
    fun observeNotesWithReminders(): Flow<List<Long>>

    @Query("SELECT DISTINCT note_id FROM canvas_cards")
    fun observeNotesOnCanvases(): Flow<List<Long>>

    @Query("SELECT * FROM note_links")
    fun observeLinks(): Flow<List<NoteLink>>

    @Query("SELECT * FROM note_properties")
    fun observeProperties(): Flow<List<NoteProperty>>

    @Query("SELECT * FROM note_aliases")
    fun observeAliases(): Flow<List<NoteAlias>>

    @Query(
        "SELECT n.id AS note_id, n.title, n.body, n.format, p.name AS place, p.id AS place_id FROM project_items i " +
            "JOIN notes n ON n.id = i.note_id JOIN projects p ON p.id = i.project_id WHERE n.deleted_at IS NOT NULL " +
            "UNION ALL " +
            "SELECT n.id AS note_id, n.title, n.body, n.format, c.name AS place, -c.id AS place_id FROM canvas_cards k " +
            "JOIN notes n ON n.id = k.note_id JOIN canvases c ON c.id = k.canvas_id WHERE n.deleted_at IS NOT NULL",
    )
    fun observeTrashedUses(): Flow<List<TrashedUse>>
}
