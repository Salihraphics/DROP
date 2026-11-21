package app.drop.ui.notes

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.drop.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Today shows the time, yesterday says so, this year the day and month, otherwise the full date. */
class TimestampFormatter(locale: Locale, is24Hour: Boolean, private val yesterday: String, private val zone: ZoneId = ZoneId.systemDefault()) {
    private val time = pattern(locale, if (is24Hour) "Hm" else "hma")
    private val monthDay = pattern(locale, "MMMd")
    private val fullDate = pattern(locale, "yMMMd")

    fun format(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
        val moment = Instant.ofEpochMilli(epochMillis).atZone(zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val day = moment.toLocalDate()
        return when {
            day == today -> time.format(moment)
            day == today.minusDays(1) -> yesterday
            day.year == today.year -> monthDay.format(moment)
            else -> fullDate.format(moment)
        }
    }

    private fun pattern(locale: Locale, skeleton: String): DateTimeFormatter =
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
}

@Composable
fun rememberTimestampFormatter(): TimestampFormatter {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val yesterday = stringResource(R.string.time_yesterday)
    val is24Hour = DateFormat.is24HourFormat(context)
    return remember(locale, yesterday, is24Hour) { TimestampFormatter(locale, is24Hour, yesterday) }
}
