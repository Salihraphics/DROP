package app.drop.meta

import app.drop.links.WikiLinks
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Locale

/** The kinds of value a property holds. Stored by [id]. */
enum class PropertyType(val id: String) {
    Text("text"),
    Number("number"),
    Date("date"),
    Boolean("boolean");

    companion object {
        fun of(id: String): PropertyType? = entries.firstOrNull { it.id == id }
    }
}

enum class AliasResult { Added, Invalid, AlreadyThere, SameAsTitle, TooMany }

enum class PropertyResult { Saved, InvalidName, InvalidValue, TooMany }

/** The rules for property names and values. Values are stored in one canonical form. */
object Properties {
    const val MAX_NAME = 40
    const val MAX_TEXT = 500
    const val MAX_PER_NOTE = 30

    fun key(name: String): String = WikiLinks.key(name)

    /** The name with single spaces, or null when it is empty or too long. */
    fun cleanName(name: String): String? = Regex("\\s+").replace(name, " ").trim().takeIf { it.isNotEmpty() && it.length <= MAX_NAME }

    /** [raw] in its stored form, or null when it isn't a valid value of [type]. */
    fun canonical(type: PropertyType, raw: String): String? {
        val t = raw.trim()
        return when (type) {
            PropertyType.Text -> Regex("\\s*\\n\\s*").replace(t, " ").takeIf { it.isNotEmpty() && it.length <= MAX_TEXT }
            PropertyType.Number -> number(t)?.stripTrailingZeros()?.toPlainString()?.let { if (it == "-0") "0" else it }
            PropertyType.Date -> try {
                LocalDate.parse(t).toString()
            } catch (e: DateTimeParseException) {
                null
            }
            PropertyType.Boolean -> when (t.lowercase(Locale.ROOT)) {
                "true", "yes", "evet" -> "true"
                "false", "no", "hayır", "hayir" -> "false"
                else -> null
            }
        }
    }

    /** A number written with a dot or a comma, spaces allowed as thousands separators. */
    fun number(text: String): BigDecimal? {
        val t = text.replace(" ", "").replace(" ", "")
        return if (Regex("^-?\\d{1,15}([.,]\\d{1,10})?$").matches(t)) t.replace(',', '.').toBigDecimalOrNull() else null
    }
}
