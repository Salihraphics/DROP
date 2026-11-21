package app.drop.palette

import app.drop.search.FoldedText

/** One palette entry: [label] is in the phone's language, [keywords] add English and local synonyms. */
data class Command(val id: CommandId, val label: String, val keywords: List<String>)

/** English words that find each command whatever the phone's language. */
val ENGLISH_KEYWORDS: Map<CommandId, List<String>> = mapOf(
    CommandId.NewNote to listOf("new note", "write", "create", "add"),
    CommandId.NewTask to listOf("new task", "todo", "to-do", "add task"),
    CommandId.VoiceDump to listOf("voice", "record", "dictate", "microphone", "speak"),
    CommandId.PaperCapture to listOf("scan", "paper", "photo", "camera", "pdf", "document", "ocr"),
    CommandId.Search to listOf("search", "find"),
    CommandId.ShowAll to listOf("all", "home", "everything"),
    CommandId.ShowTasks to listOf("tasks", "todos"),
    CommandId.ShowLinks to listOf("links", "bookmarks", "urls"),
    CommandId.ShowPinned to listOf("pinned", "pins"),
    CommandId.Resurface to listOf("resurface", "reminders", "later", "upcoming"),
    CommandId.Projects to listOf("projects", "spaces", "workspace"),
    CommandId.DailyLog to listOf("daily", "today", "journal", "log"),
    CommandId.Canvas to listOf("canvas", "board", "cards", "ideas"),
    CommandId.Trash to listOf("trash", "deleted", "bin"),
    CommandId.Settings to listOf("settings", "preferences", "backup", "theme"),
    CommandId.Vault to listOf("vault", "private", "secret", "locked"),
    CommandId.NewFromTemplate to listOf("template", "new from template", "meeting", "journal"),
    CommandId.Templates to listOf("templates", "template studio"),
    CommandId.Collections to listOf("collections", "smart collections", "filter", "rules", "saved search"),
    CommandId.Health to listOf("health", "knowledge health", "broken links", "duplicates", "check"),
    CommandId.Portability to listOf("export", "import", "markdown", "pdf", "archive", "zip"),
)

/**
 * The commands matching every word of [query] (in the label or a keyword, accents and case
 * ignored), best first: label starts with the first word, then a label word does, then a
 * keyword word does, then the rest. An empty query keeps every command in order.
 */
fun filterCommands(commands: List<Command>, query: String): List<Command> {
    val terms = Regex("\\s+").split(FoldedText.fold(query)).filter { it.isNotEmpty() }
    if (terms.isEmpty()) return commands
    return commands.mapNotNull { command ->
        val label = FoldedText.fold(command.label)
        val keywords = command.keywords.map(FoldedText::fold)
        val haystack = listOf(label) + keywords
        if (!terms.all { t -> haystack.any { it.contains(t) } }) return@mapNotNull null
        val first = terms.first()
        val score = when {
            label.startsWith(first) -> 0
            label.split(' ').any { it.startsWith(first) } -> 1
            keywords.any { k -> k.split(' ').any { it.startsWith(first) } } -> 2
            else -> 3
        }
        score to command
    }.sortedBy { it.first }.map { it.second }
}
