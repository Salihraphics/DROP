package app.drop.data

import androidx.room.withTransaction
import app.drop.links.WikiLinks
import app.drop.meta.Properties
import app.drop.meta.PropertyType
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate

/** One note in a backup file. */
data class BackupItem(
    val uuid: String,
    val title: String,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean,
    val task: Boolean,
    val completedAt: Long?,
    val format: Int = 0,
)

/** Notes are referenced by uuid, so a backup restores correctly into any library. */
data class BackupProject(
    val uuid: String,
    val name: String,
    val description: String,
    val createdAt: Long,
    val updatedAt: Long,
    val items: List<String>,
)

data class BackupLink(val source: String, val label: String, val target: String)

data class BackupDailyEntry(val date: String, val text: String, val updatedAt: Long)

data class BackupCard(val note: String, val x: Float, val y: Float, val shape: Int = 0, val color: Int = 0)

data class BackupCanvas(
    val uuid: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val cards: List<BackupCard>,
    /** Pairs of note uuids. */
    val edges: List<Pair<String, String>>,
)

data class BackupAlias(val note: String, val alias: String)

data class BackupProperty(val note: String, val name: String, val type: String, val value: String)

data class BackupTemplate(
    val uuid: String,
    val name: String,
    val title: String,
    val body: String,
    val format: Int,
    val task: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

data class BackupCollection(val uuid: String, val name: String, val rules: String, val createdAt: Long, val updatedAt: Long)

/** Everything in a backup file. Version 1 files hold [items] only. */
data class BackupContents(
    val items: List<BackupItem>,
    val projects: List<BackupProject> = emptyList(),
    val links: List<BackupLink> = emptyList(),
    val dailyEntries: List<BackupDailyEntry> = emptyList(),
    val canvases: List<BackupCanvas> = emptyList(),
    val aliases: List<BackupAlias> = emptyList(),
    val properties: List<BackupProperty> = emptyList(),
    val templates: List<BackupTemplate> = emptyList(),
    val collections: List<BackupCollection> = emptyList(),
)

/** Why a file can't be restored. Nothing is changed when any of these is thrown. */
sealed class BackupError : Exception() {
    data object NotADropBackup : BackupError() {
        private fun readResolve(): Any = NotADropBackup
        override val message: String get() = "not a DROP backup"
    }

    data class UnsupportedVersion(val version: Int) : BackupError() {
        override val message: String get() = "unsupported backup version $version"
    }

    data class Malformed(val detail: String) : BackupError() {
        override val message: String get() = "malformed backup: $detail"
    }

    data object TooLarge : BackupError() {
        private fun readResolve(): Any = TooLarge
        override val message: String get() = "backup is too large"
    }
}

/** [added] notes were new, [skipped] were already in the library, [spaces] other things were added. */
data class ImportResult(val added: Int, val skipped: Int, val spaces: Int = 0)

/**
 * DROP's backup file: UTF-8 JSON, not encrypted. See docs/BACKUP_FORMAT.md.
 * Version 3 is written; versions 1 to 3 are read. Unknown fields are ignored and every value is
 * checked before anything is stored.
 */
object BackupFormat {
    const val FORMAT = "drop-backup"
    const val VERSION = 3
    val READABLE_VERSIONS = 1..3
    const val MAX_FILE_BYTES = 32L * 1024 * 1024
    const val MAX_ITEMS = 100_000
    const val MAX_TEXT_CHARS = 1_000_000
    private const val MAX_UUID_CHARS = 64
    private const val MAX_NAME_CHARS = 1000
    private val DATE = Regex("\\d{4}-\\d{2}-\\d{2}")

    fun encode(notes: List<Note>, exportedAt: Long, appVersion: String, spaces: BackupContents? = null): String {
        val items = JSONArray()
        notes.forEach { note ->
            items.put(
                JSONObject()
                    .put("uuid", note.uuid)
                    .put("title", note.title)
                    .put("body", note.body)
                    .put("createdAt", note.createdAt)
                    .put("updatedAt", note.updatedAt)
                    .put("pinned", note.pinned)
                    .put("task", note.isTask)
                    .put("completedAt", note.completedAt ?: JSONObject.NULL)
                    .put("format", note.format),
            )
        }
        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("exportedAt", exportedAt)
            .put("app", JSONObject().put("name", "DROP").put("version", appVersion))
            .put("items", items)
        val extra = spaces ?: BackupContents(emptyList())
        root.put(
            "projects",
            JSONArray(
                extra.projects.map {
                    JSONObject().put("uuid", it.uuid).put("name", it.name).put("description", it.description)
                        .put("createdAt", it.createdAt).put("updatedAt", it.updatedAt).put("items", JSONArray(it.items))
                },
            ),
        )
        root.put("links", JSONArray(extra.links.map { JSONObject().put("source", it.source).put("label", it.label).put("target", it.target) }))
        root.put("dailyEntries", JSONArray(extra.dailyEntries.map { JSONObject().put("date", it.date).put("text", it.text).put("updatedAt", it.updatedAt) }))
        root.put(
            "canvases",
            JSONArray(
                extra.canvases.map { c ->
                    JSONObject().put("uuid", c.uuid).put("name", c.name).put("createdAt", c.createdAt).put("updatedAt", c.updatedAt)
                        .put(
                            "cards",
                            JSONArray(
                                c.cards.map {
                                    JSONObject().put("note", it.note).put("x", it.x.toDouble()).put("y", it.y.toDouble())
                                        .put("shape", it.shape).put("color", it.color)
                                },
                            ),
                        )
                        .put("edges", JSONArray(c.edges.map { JSONObject().put("from", it.first).put("to", it.second) }))
                },
            ),
        )
        root.put("aliases", JSONArray(extra.aliases.map { JSONObject().put("note", it.note).put("alias", it.alias) }))
        root.put(
            "properties",
            JSONArray(extra.properties.map { JSONObject().put("note", it.note).put("name", it.name).put("type", it.type).put("value", it.value) }),
        )
        root.put(
            "templates",
            JSONArray(
                extra.templates.map {
                    JSONObject().put("uuid", it.uuid).put("name", it.name).put("title", it.title).put("body", it.body)
                        .put("format", it.format).put("task", it.task).put("createdAt", it.createdAt).put("updatedAt", it.updatedAt)
                },
            ),
        )
        root.put(
            "collections",
            JSONArray(
                extra.collections.map {
                    JSONObject().put("uuid", it.uuid).put("name", it.name).put("rules", it.rules)
                        .put("createdAt", it.createdAt).put("updatedAt", it.updatedAt)
                },
            ),
        )
        return root.toString(2)
    }

    fun decode(json: String): List<BackupItem> = decodeAll(json).items

    fun decodeAll(json: String): BackupContents {
        val root = try {
            JSONTokener(json).nextValue() as? JSONObject
        } catch (e: JSONException) {
            null
        } ?: throw BackupError.NotADropBackup
        if (root.optString("format") != FORMAT) throw BackupError.NotADropBackup
        val version = root.opt("version") as? Int ?: throw BackupError.Malformed("missing version")
        if (version !in READABLE_VERSIONS) throw BackupError.UnsupportedVersion(version)
        val items = root.opt("items") as? JSONArray ?: throw BackupError.Malformed("missing items")
        if (items.length() > MAX_ITEMS) throw BackupError.TooLarge
        val decoded = (0 until items.length()).map { i ->
            val item = items.opt(i) as? JSONObject ?: throw BackupError.Malformed("item $i is not an object")
            readItem(item, i, version)
        }
        if (version == 1) return BackupContents(decoded)
        val v3 = version >= 3

        val projects = objects(root, "projects").mapIndexed { i, o -> readProject(Fields(o, "project $i")) }
        val links = objects(root, "links").mapIndexed { i, o ->
            val f = Fields(o, "link $i")
            val source = f.uuid("source")
            val label = f.text("label", MAX_NAME_CHARS).also { if (it.isBlank()) f.fail("label is empty") }
            BackupLink(source, label, f.uuid("target"))
        }
        val days = objects(root, "dailyEntries").mapIndexed { i, o ->
            val f = Fields(o, "daily entry $i")
            val date = f.text("date", 10)
            if (!DATE.matches(date) || runCatching { LocalDate.parse(date) }.isFailure) f.fail("date is invalid")
            BackupDailyEntry(date, f.text("text", MAX_TEXT_CHARS), f.time("updatedAt"))
        }
        val canvases = objects(root, "canvases").mapIndexed { i, o -> readCanvas(Fields(o, "canvas $i")) }
        val aliases = if (!v3) emptyList() else objects(root, "aliases").mapIndexed { i, o ->
            val f = Fields(o, "alias $i")
            val note = f.uuid("note")
            val alias = f.text("alias", 120).also { if (it.isBlank()) f.fail("alias is empty") }
            BackupAlias(note, alias)
        }
        val properties = if (!v3) emptyList() else objects(root, "properties").mapIndexed { i, o ->
            val f = Fields(o, "property $i")
            val note = f.uuid("note")
            val name = f.text("name", Properties.MAX_NAME).also { if (it.isBlank()) f.fail("name is empty") }
            BackupProperty(note, name, f.text("type", 20), f.text("value", Properties.MAX_TEXT))
        }
        val templates = if (!v3) emptyList() else objects(root, "templates").mapIndexed { i, o ->
            val f = Fields(o, "template $i")
            val uuid = f.uuid("uuid")
            val name = f.text("name", MAX_NAME_CHARS).also { if (it.isBlank()) f.fail("name is empty") }
            BackupTemplate(
                uuid, name, f.text("title", MAX_NAME_CHARS), f.text("body", MAX_TEXT_CHARS), f.format("format"),
                f.flag("task"), f.time("createdAt"), f.time("updatedAt"),
            )
        }
        val collections = if (!v3) emptyList() else objects(root, "collections").mapIndexed { i, o ->
            val f = Fields(o, "collection $i")
            val uuid = f.uuid("uuid")
            val name = f.text("name", MAX_NAME_CHARS).also { if (it.isBlank()) f.fail("name is empty") }
            BackupCollection(uuid, name, f.text("rules", 100_000), f.time("createdAt"), f.time("updatedAt"))
        }
        return BackupContents(decoded, projects, links, days, canvases, aliases, properties, templates, collections)
    }

    /** The objects in an optional list; a missing list is empty. */
    private fun objects(root: JSONObject, key: String): List<JSONObject> {
        if (!root.has(key) || root.isNull(key)) return emptyList()
        val array = root.opt(key) as? JSONArray ?: throw BackupError.Malformed("$key is not a list")
        if (array.length() > MAX_ITEMS) throw BackupError.TooLarge
        return (0 until array.length()).map { i -> array.opt(i) as? JSONObject ?: throw BackupError.Malformed("$key $i is not an object") }
    }

    /** Typed, checked reads from one object; [where] names it in error messages. */
    private class Fields(val o: JSONObject, val where: String) {
        fun fail(what: String): Nothing = throw BackupError.Malformed("$where: $what")

        fun text(key: String, max: Int): String {
            if (!o.has(key) || o.isNull(key)) return ""
            val value = o.get(key) as? String ?: fail("$key is not text")
            if (value.length > max) fail("$key is too long")
            return value
        }

        fun uuid(key: String): String {
            val value = o.opt(key) as? String ?: fail("$key is missing")
            if (value.isBlank() || value.length > MAX_UUID_CHARS) fail("$key is invalid")
            return value
        }

        fun time(key: String): Long {
            val value = (o.opt(key) as? Number ?: fail("$key is missing")).toLong()
            if (value < 0) fail("$key is negative")
            return value
        }

        fun coordinate(key: String): Float {
            val value = (o.opt(key) as? Number ?: fail("$key is missing")).toDouble()
            if (value.isNaN() || value.isInfinite()) fail("$key is invalid")
            return value.toFloat()
        }

        fun format(key: String): Int {
            val value = o.opt(key)
            if (value == null || value == JSONObject.NULL) return NoteFormat.PLAIN
            if (value == NoteFormat.PLAIN || value == NoteFormat.MARKDOWN) return value as Int
            fail("$key is not a known format")
        }

        fun flag(key: String): Boolean {
            val value = o.opt(key)
            if (value == null || value == JSONObject.NULL) return false
            return value as? Boolean ?: fail("$key is not true/false")
        }

        fun array(key: String): JSONArray {
            if (!o.has(key) || o.isNull(key)) return JSONArray()
            val value = o.opt(key) as? JSONArray ?: fail("$key is not a list")
            if (value.length() > MAX_ITEMS) throw BackupError.TooLarge
            return value
        }
    }

    private fun readProject(f: Fields): BackupProject {
        val items = f.array("items")
        val uuid = f.uuid("uuid")
        val name = f.text("name", MAX_NAME_CHARS).also { if (it.isBlank()) f.fail("name is empty") }
        val description = f.text("description", MAX_TEXT_CHARS)
        val createdAt = f.time("createdAt")
        val updatedAt = f.time("updatedAt")
        val notes = (0 until items.length()).map { i ->
            val note = items.opt(i) as? String ?: f.fail("items $i is not a uuid")
            if (note.isBlank() || note.length > MAX_UUID_CHARS) f.fail("items $i is invalid")
            note
        }
        return BackupProject(uuid, name, description, createdAt, updatedAt, notes)
    }

    private fun readCanvas(f: Fields): BackupCanvas {
        val cards = f.array("cards")
        val edges = f.array("edges")
        val uuid = f.uuid("uuid")
        val name = f.text("name", MAX_NAME_CHARS).also { if (it.isBlank()) f.fail("name is empty") }
        val createdAt = f.time("createdAt")
        val updatedAt = f.time("updatedAt")
        fun obj(array: JSONArray, what: String, i: Int): JSONObject = array.opt(i) as? JSONObject ?: f.fail("$what $i is not an object")
        val readCards = (0 until cards.length()).map { i ->
            val c = Fields(obj(cards, "card", i), "${f.where} card $i")
            // Shape and colour are optional (0.10.0); an unknown value is drawn as the default.
            val shape = (c.o.opt("shape") as? Int)?.takeIf { s -> CardShape.entries.any { it.id == s } } ?: 0
            val color = (c.o.opt("color") as? Int)?.takeIf { k -> CardColor.entries.any { it.id == k } } ?: 0
            BackupCard(c.uuid("note"), c.coordinate("x"), c.coordinate("y"), shape, color)
        }
        val readEdges = (0 until edges.length()).map { i ->
            val e = Fields(obj(edges, "edge", i), "${f.where} edge $i")
            e.uuid("from") to e.uuid("to")
        }
        return BackupCanvas(uuid, name, createdAt, updatedAt, readCards, readEdges)
    }

    private fun readItem(o: JSONObject, i: Int, version: Int): BackupItem {
        fun fail(what: String): Nothing = throw BackupError.Malformed("item $i: $what")
        fun text(key: String): String {
            if (!o.has(key) || o.isNull(key)) return ""
            val value = o.get(key) as? String ?: fail("$key is not text")
            if (value.length > MAX_TEXT_CHARS) fail("$key is too long")
            return value
        }
        fun time(key: String): Long {
            val value = (o.opt(key) as? Number ?: fail("$key is missing")).toLong()
            if (value < 0) fail("$key is negative")
            return value
        }
        fun flag(key: String): Boolean {
            val value = o.opt(key)
            if (value == null || value == JSONObject.NULL) return false
            return value as? Boolean ?: fail("$key is not true/false")
        }

        val uuid = o.opt("uuid") as? String ?: fail("uuid is missing")
        if (uuid.isBlank() || uuid.length > MAX_UUID_CHARS) fail("uuid is invalid")
        val task = flag("task")
        val completedAt = when (val value = o.opt("completedAt")) {
            null, JSONObject.NULL -> null
            is Number -> value.toLong().also { if (it < 0) fail("completedAt is negative") }
            else -> fail("completedAt is not a time")
        }
        return BackupItem(
            uuid = uuid,
            title = text("title"),
            body = text("body"),
            createdAt = time("createdAt"),
            updatedAt = time("updatedAt"),
            pinned = flag("pinned"),
            task = task,
            completedAt = if (task) completedAt else null,
            format = if (version >= 3) Fields(o, "item $i").format("format") else NoteFormat.PLAIN,
        )
    }
}

/**
 * Exports and restores the main library. The Vault is never part of a backup.
 * A restore only adds: notes already in the library (by uuid) are left exactly as they are.
 */
class BackupManager(private val database: DropDatabase) {
    private val dao = database.noteDao()
    private val projects = database.projectDao()
    private val links = database.linkDao()
    private val daily = database.dailyDao()
    private val canvases = database.canvasDao()
    private val meta = database.metaDao()
    private val templates = database.templateDao()
    private val collections = database.collectionDao()

    /**
     * Writes every note that isn't in Trash, with everything that belongs to them, to [output].
     * The library is read in one transaction, so the file is a consistent snapshot. The stream is
     * flushed, not closed: the caller owns it. Returns the number of notes written.
     */
    suspend fun export(output: OutputStream, exportedAt: Long, appVersion: String): Int {
        val (notes, spaces) = database.withTransaction {
            val notes = dao.getActive()
            notes to spacesFor(notes)
        }
        val writer = output.writer(Charsets.UTF_8).buffered()
        writer.write(BackupFormat.encode(notes, exportedAt, appVersion, spaces))
        writer.flush()
        return notes.size
    }

    /** Everything besides notes, limited to what refers to [notes]. */
    private suspend fun spacesFor(notes: List<Note>): BackupContents {
        val uuidOf = notes.associate { it.id to it.uuid }
        val itemsByProject = projects.allItems().groupBy { it.projectId }
        val cardsByCanvas = canvases.allCards().groupBy { it.canvasId }
        val edges = canvases.allEdges()
        val backupProjects = projects.all().map { p ->
            BackupProject(p.uuid, p.name, p.description, p.createdAt, p.updatedAt, itemsByProject[p.id].orEmpty().mapNotNull { uuidOf[it.noteId] })
        }
        val backupLinks = links.all().mapNotNull { l ->
            val source = uuidOf[l.sourceId] ?: return@mapNotNull null
            val target = uuidOf[l.targetId] ?: return@mapNotNull null
            BackupLink(source, l.label, target)
        }
        val days = daily.all().map { BackupDailyEntry(it.date, it.text, it.updatedAt) }
        val backupCanvases = canvases.all().map { c ->
            val cards = cardsByCanvas[c.id].orEmpty().filter { uuidOf.containsKey(it.noteId) }
            val noteOfCard = cards.associate { it.id to uuidOf.getValue(it.noteId) }
            BackupCanvas(
                c.uuid, c.name, c.createdAt, c.updatedAt,
                cards.map { BackupCard(uuidOf.getValue(it.noteId), it.x, it.y, it.shape, it.color) },
                edges.mapNotNull { e ->
                    val from = noteOfCard[e.fromCard] ?: return@mapNotNull null
                    val to = noteOfCard[e.toCard] ?: return@mapNotNull null
                    from to to
                },
            )
        }
        val aliases = meta.allAliases().mapNotNull { a -> uuidOf[a.noteId]?.let { BackupAlias(it, a.alias) } }
        val properties = meta.allProperties().mapNotNull { p -> uuidOf[p.noteId]?.let { BackupProperty(it, p.name, p.type, p.value) } }
        val backupTemplates = templates.all().map { BackupTemplate(it.uuid, it.name, it.title, it.body, it.format, it.isTask, it.createdAt, it.updatedAt) }
        val backupCollections = collections.all().map { BackupCollection(it.uuid, it.name, it.rules, it.createdAt, it.updatedAt) }
        return BackupContents(
            emptyList(), backupProjects, backupLinks, days, backupCanvases, aliases, properties, backupTemplates, backupCollections,
        )
    }

    /**
     * Restores a backup. The whole file is read and checked first, then everything is added in
     * one transaction: on any error nothing at all is stored.
     */
    suspend fun import(input: InputStream): ImportResult {
        val contents = BackupFormat.decodeAll(readCapped(input))
        val items = contents.items
        return database.withTransaction {
            val known = dao.allUuids().toHashSet()
            val fresh = items.filter { known.add(it.uuid) }
            fresh.chunked(CHUNK).forEach { chunk ->
                dao.insertAll(
                    chunk.map {
                        Note(
                            uuid = it.uuid, title = it.title, body = it.body, createdAt = it.createdAt, updatedAt = it.updatedAt,
                            pinned = it.pinned, isTask = it.task, completedAt = it.completedAt, format = it.format,
                        ).withDerivedFields()
                    },
                )
            }
            val spaces = importSpaces(contents) + importWorkspace(contents, fresh.mapTo(HashSet()) { it.uuid })
            ImportResult(added = fresh.size, skipped = items.size - fresh.size, spaces = spaces)
        }
    }

    /** Notes' ids by uuid, for the uuids that exist. */
    private suspend fun idsOf(uuids: Collection<String>): Map<String, Long> =
        uuids.distinct().chunked(CHUNK).flatMap { dao.getByUuids(it) }.associate { it.uuid to it.id }

    /** Adds projects, links, Daily Log entries and canvases that aren't in the library yet. */
    private suspend fun importSpaces(contents: BackupContents): Int {
        val referenced = buildSet {
            contents.projects.forEach { addAll(it.items) }
            contents.links.forEach { add(it.source); add(it.target) }
            contents.canvases.forEach { c -> c.cards.forEach { add(it.note) } }
        }
        val idOf = idsOf(referenced)
        val now = System.currentTimeMillis()
        var added = 0

        val knownProjects = projects.allUuids().toHashSet()
        for (p in contents.projects) {
            if (!knownProjects.add(p.uuid)) continue
            added++
            val id = projects.insert(Project(uuid = p.uuid, name = p.name, description = p.description, createdAt = p.createdAt, updatedAt = p.updatedAt))
            projects.addItems(p.items.mapNotNull { idOf[it] }.distinct().map { ProjectItem(id, it, now) })
        }

        links.insertAll(
            contents.links.mapNotNull { l ->
                val source = idOf[l.source] ?: return@mapNotNull null
                val target = idOf[l.target] ?: return@mapNotNull null
                NoteLink(source, WikiLinks.key(l.label), target)
            },
        )

        val knownDays = daily.all().mapTo(HashSet()) { it.date }
        val days = contents.dailyEntries.filter { it.text.isNotBlank() && knownDays.add(it.date) }
        daily.insertAll(days.map { DailyEntry(it.date, it.text, it.updatedAt) })
        added += days.size

        val knownCanvases = canvases.all().mapTo(HashSet()) { it.uuid }
        for (c in contents.canvases) {
            if (!knownCanvases.add(c.uuid)) continue
            added++
            val id = canvases.insert(IdeaCanvas(uuid = c.uuid, name = c.name, createdAt = c.createdAt, updatedAt = c.updatedAt))
            val cards = c.cards.filter { idOf.containsKey(it.note) }.distinctBy { it.note }
            val cardIds = canvases.insertCards(cards.map { CanvasCard(canvasId = id, noteId = idOf.getValue(it.note), x = it.x, y = it.y, shape = it.shape, color = it.color) })
            val cardOf = cards.map { it.note }.zip(cardIds).toMap()
            canvases.insertEdges(
                c.edges.mapNotNull { (from, to) ->
                    val a = cardOf[from]?.takeIf { it > 0 } ?: return@mapNotNull null
                    val b = cardOf[to]?.takeIf { it > 0 } ?: return@mapNotNull null
                    if (a == b) null else CanvasEdge(minOf(a, b), maxOf(a, b))
                },
            )
        }
        return added
    }

    /**
     * Adds aliases and properties of the notes this restore added (an existing note's are never
     * touched), and templates and collections that aren't in the library yet.
     */
    private suspend fun importWorkspace(contents: BackupContents, fresh: Set<String>): Int {
        val wanted = (contents.aliases.map { it.note } + contents.properties.map { it.note }).filter { it in fresh }
        if (wanted.isNotEmpty()) {
            val idOf = idsOf(wanted)
            meta.insertAliases(
                contents.aliases.mapNotNull { a ->
                    val id = idOf[a.note] ?: return@mapNotNull null
                    val alias = WikiLinks.cleanLabel(a.alias).takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    NoteAlias(id, alias, WikiLinks.key(alias))
                },
            )
            meta.insertProperties(
                contents.properties.mapNotNull { p ->
                    val id = idOf[p.note] ?: return@mapNotNull null
                    val type = PropertyType.of(p.type) ?: return@mapNotNull null
                    val name = Properties.cleanName(p.name) ?: return@mapNotNull null
                    val value = Properties.canonical(type, p.value) ?: return@mapNotNull null
                    NoteProperty(id, name, Properties.key(name), type.id, value)
                },
            )
        }
        val knownTemplates = templates.allUuids().toHashSet()
        val newTemplates = contents.templates.filter { knownTemplates.add(it.uuid) }
        templates.insertAll(
            newTemplates.map {
                NoteTemplate(uuid = it.uuid, name = it.name, title = it.title, body = it.body, format = it.format, isTask = it.task, createdAt = it.createdAt, updatedAt = it.updatedAt)
            },
        )
        val knownCollections = collections.all().mapTo(HashSet()) { it.uuid }
        val newCollections = contents.collections.filter { knownCollections.add(it.uuid) }
        collections.insertAll(newCollections.map { SmartCollection(uuid = it.uuid, name = it.name, rules = it.rules, createdAt = it.createdAt, updatedAt = it.updatedAt) })
        return newTemplates.size + newCollections.size
    }

    /** Reads at most [BackupFormat.MAX_FILE_BYTES], so a huge file can't exhaust memory. */
    private fun readCapped(input: InputStream): String {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(chunk)
            if (n < 0) break
            total += n
            if (total > BackupFormat.MAX_FILE_BYTES) throw BackupError.TooLarge
            buffer.write(chunk, 0, n)
        }
        return buffer.toString(Charsets.UTF_8.name())
    }

    private companion object {
        /** Stays under SQLite's limit on query parameters. */
        const val CHUNK = 500
    }
}
