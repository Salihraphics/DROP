package app.drop.share

import app.drop.data.Links

/** What another app shared, ready to become a note. */
data class SharedContent(val title: String, val body: String, val truncated: Boolean)

object SharedContentParser {
    const val MAX_CHARS = 100_000
    private const val MAX_TITLE_CHARS = 200

    /**
     * The shared text becomes the body. A subject (a page title, say) becomes the note's title,
     * unless it is already in the text or is itself a link, which is added under the text.
     * Null when nothing usable was shared.
     */
    fun parse(text: CharSequence?, subject: CharSequence?): SharedContent? {
        val rawText = text?.toString()?.trim().orEmpty()
        val rawSubject = subject?.toString()?.let { Regex("\\s+").replace(it, " ").trim() }.orEmpty()
        if (rawText.isEmpty() && rawSubject.isEmpty()) return null
        val subjectIsLink = rawSubject.isNotEmpty() && Links.find(rawSubject) == rawSubject
        var (title, body) = when {
            rawText.isEmpty() -> "" to rawSubject
            rawSubject.isEmpty() || rawText.contains(rawSubject) -> "" to rawText
            subjectIsLink -> "" to "$rawText\n$rawSubject"
            else -> rawSubject.take(MAX_TITLE_CHARS) to rawText
        }
        val truncated = body.length > MAX_CHARS
        if (truncated) {
            // Never cut an emoji or other surrogate pair in half.
            val end = if (Character.isHighSurrogate(body[MAX_CHARS - 1])) MAX_CHARS - 1 else MAX_CHARS
            body = body.substring(0, end)
        }
        return SharedContent(title, body, truncated)
    }
}
