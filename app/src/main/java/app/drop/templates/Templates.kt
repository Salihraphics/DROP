package app.drop.templates

import android.content.res.Resources
import app.drop.R
import app.drop.data.DropDatabase
import app.drop.data.Note
import app.drop.data.NoteFormat
import app.drop.data.NoteTemplate
import app.drop.richtext.DropMarkdown
import app.drop.richtext.InlineStyle
import app.drop.richtext.RichDoc
import app.drop.richtext.RichEdit
import kotlinx.coroutines.flow.Flow
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** A template, built in or the user's own, ready to preview or use. */
data class Template(
    /** "builtin:meeting" or "custom:12". */
    val key: String,
    val name: String,
    val title: String,
    val body: String,
    val format: Int,
    val isTask: Boolean,
    /** Set for the user's own templates. */
    val customId: Long? = null,
) {
    val isBuiltIn: Boolean get() = customId == null
}

/** The six templates that come with DROP, in the phone's language. They live in code, not the database. */
object BuiltInTemplates {
    private class Def(val id: String, val name: Int, val title: Int, val body: Int)

    private val defs = listOf(
        Def("meeting", R.string.tpl_meeting_name, R.string.tpl_meeting_title, R.string.tpl_meeting_body),
        Def("study", R.string.tpl_study_name, R.string.tpl_study_title, R.string.tpl_study_body),
        Def("project", R.string.tpl_project_name, R.string.tpl_project_title, R.string.tpl_project_body),
        Def("journal", R.string.tpl_journal_name, R.string.tpl_journal_title, R.string.tpl_journal_body),
        Def("research", R.string.tpl_research_name, R.string.tpl_research_title, R.string.tpl_research_body),
        Def("techdoc", R.string.tpl_techdoc_name, R.string.tpl_techdoc_title, R.string.tpl_techdoc_body),
    )

    fun all(res: Resources): List<Template> = defs.map { d ->
        Template("builtin:${d.id}", res.getString(d.name), res.getString(d.title), res.getString(d.body), NoteFormat.MARKDOWN, isTask = false)
    }
}

/**
 * The placeholders a template may contain, replaced when a note is created from it:
 * `{{date}}` (today, e.g. "24 Oct 2026"), `{{time}}` (now, e.g. "14:05") and, in the text,
 * `{{title}}` (the new note's title). Replacement is plain text: nothing in a template is
 * ever run, and an unknown `{{…}}` is left as it is.
 */
object Placeholders {
    // Every brace is escaped: Android's regex engine (ICU) rejects a bare "}", unlike the JVM's.
    private val TOKEN = Regex("""\{\{\s*(date|time|title)\s*\}\}""", RegexOption.IGNORE_CASE)

    fun values(now: ZonedDateTime, locale: Locale): Map<String, String> = mapOf(
        "date" to now.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)),
        "time" to now.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)),
    )

    fun expandTitle(title: String, values: Map<String, String>): String =
        TOKEN.replace(title) { m -> values[m.groupValues[1].lowercase()] ?: m.value }.replace('\n', ' ').trim()

    /** Replaces placeholders in [doc]'s text; a placeholder's formatting carries over to its value. */
    fun expandDoc(doc: RichDoc, values: Map<String, String>): RichDoc {
        var out = doc
        for (m in TOKEN.findAll(doc.text).toList().asReversed()) {
            val value = values[m.groupValues[1].lowercase()] ?: continue
            val start = m.range.first
            val text = out.text.replaceRange(m.range, value)
            val styles = out.stylesAt(start) - InlineStyle.Link
            out = RichEdit.apply(out, text, start + value.length, start + value.length, styles).doc
        }
        return out
    }

    /**
     * What a note made from [template] now would contain, or null if the template can't be read
     * (a damaged custom template must never take the app, or the other templates, down with it).
     */
    fun tryInstantiate(template: Template, now: ZonedDateTime, locale: Locale): Triple<String, String, Int>? =
        runCatching { instantiate(template, now, locale) }.getOrNull()

    /** What a note made from [template] now would contain: title, body and its format. */
    fun instantiate(template: Template, now: ZonedDateTime, locale: Locale): Triple<String, String, Int> {
        val values = values(now, locale)
        val title = expandTitle(template.title, values)
        val doc = expandDoc(DropMarkdown.docOf(template.body, template.format), values + ("title" to title))
        val (body, format) = DropMarkdown.store(doc)
        return Triple(title, body, format)
    }
}

/**
 * The user's own templates. Creating a note from any template only happens when the user
 * confirms it; templates never touch the Vault.
 */
class TemplateStore(private val database: DropDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    private val dao get() = database.templateDao()

    fun observeCustom(): Flow<List<NoteTemplate>> = dao.observeAll()

    suspend fun get(id: Long): NoteTemplate? = dao.get(id)

    suspend fun save(id: Long?, name: String, title: String, doc: RichDoc, isTask: Boolean): Long {
        val (body, format) = DropMarkdown.store(doc)
        val now = clock()
        val clean = name.replace('\n', ' ').trim().take(MAX_NAME)
        val existing = id?.let { dao.get(it) }
        return if (existing == null) {
            dao.insert(NoteTemplate(name = clean, title = title.replace('\n', ' ').trim(), body = body, format = format, isTask = isTask, createdAt = now, updatedAt = now))
        } else {
            dao.update(existing.copy(name = clean, title = title.replace('\n', ' ').trim(), body = body, format = format, isTask = isTask, updatedAt = now))
            existing.id
        }
    }

    /** A copy the user can change: of a built-in template or of one of their own. */
    suspend fun duplicate(template: Template, copyName: String): Long {
        val now = clock()
        return dao.insert(
            NoteTemplate(name = copyName.take(MAX_NAME), title = template.title, body = template.body, format = template.format, isTask = template.isTask, createdAt = now, updatedAt = now),
        )
    }

    suspend fun delete(id: Long) = dao.delete(id)

    /** Makes the note and returns it; call only after the user confirmed. */
    suspend fun createNote(template: Template, now: ZonedDateTime, locale: Locale): Note {
        val (title, body, format) = Placeholders.instantiate(template, now, locale)
        val time = clock()
        val note = Note(title = title, body = body, format = format, isTask = template.isTask, createdAt = time, updatedAt = time).withDerivedFields()
        val id = database.noteDao().insert(note)
        return note.copy(id = id)
    }

    companion object {
        const val MAX_NAME = 60
    }
}

fun NoteTemplate.toTemplate() = Template("custom:$id", name, title, body, format, isTask, id)
