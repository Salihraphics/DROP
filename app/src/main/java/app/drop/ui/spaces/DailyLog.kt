package app.drop.ui.spaces

import java.time.LocalDate
import java.time.ZoneId

/** The start of [date] and of the next day, in this zone's time. */
fun dayBounds(date: LocalDate, zone: ZoneId): Pair<Long, Long> =
    date.atStartOfDay(zone).toInstant().toEpochMilli() to date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
