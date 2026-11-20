package app.drop.data

import java.util.Locale

/** Finds web links in text, the way a person would read them. */
object Links {
    private const val TRAILING = ".,;:!?'\"*"
    private val candidate = Regex("(?i)(?<![\\w@.])(?:https?://|www\\.)[^\\s<>\"'`]+")
    private val pairs = mapOf(')' to '(', ']' to '[', '}' to '{')

    /** The first link in [text] with a plausible host, without trailing punctuation. */
    fun find(text: String): String? =
        candidate.findAll(text).map { trimTrailing(it.value) }.firstOrNull { host(it) != null }

    /** The first link in the title, else in the body. */
    fun find(title: String, body: String): String? = find(title) ?: find(body)

    /** The link's host without "www.", or null when it isn't an http(s) link with a valid host. */
    fun host(url: String): String? {
        val openable = openable(url)
        val scheme = openable.indexOf("://")
        if (scheme <= 0) return null
        val protocol = openable.substring(0, scheme).lowercase(Locale.ROOT)
        if (protocol != "http" && protocol != "https") return null
        var rest = openable.substring(scheme + 3)
        val end = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }
        if (end >= 0) rest = rest.substring(0, end)
        val host = rest.substringAfterLast('@').substringBefore(':').trimEnd('.').lowercase(Locale.ROOT)
        val labels = host.split('.')
        val valid = labels.size >= 2 && labels.all { label ->
            label.isNotEmpty() && !label.startsWith('-') && !label.endsWith('-') &&
                label.all { it.isLetterOrDigit() || it == '-' }
        }
        return if (valid) host.removePrefix("www.") else null
    }

    /** A link that can be opened: "www." links get https. */
    fun openable(url: String): String = if (url.regionMatches(0, "www.", 0, 4, ignoreCase = true)) "https://$url" else url

    /** Drops sentence punctuation and unbalanced closing brackets from the end of a link. */
    internal fun trimTrailing(raw: String): String {
        var url = raw
        while (url.isNotEmpty()) {
            val last = url.last()
            val opener = pairs[last]
            url = when {
                last in TRAILING -> url.dropLast(1)
                opener != null && url.count { it == last } > url.count { it == opener } -> url.dropLast(1)
                else -> return url
            }
        }
        return url
    }
}
