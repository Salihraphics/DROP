package app.drop.resurface

import app.drop.data.NoteDao
import app.drop.data.Reminder
import app.drop.data.ReminderDao
import app.drop.richtext.DropMarkdown
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Quick choices for when a note comes back. */
enum class Preset { LaterToday, Tomorrow, Weekend, NextWeek }

/** Reminder times are stored as local wall-clock text, to the minute. */
object ResurfaceTime {
    val MORNING: LocalTime = LocalTime.of(9, 0)
    private val LATEST_LATER_TODAY: LocalTime = LocalTime.of(21, 0)

    fun triggerAt(local: LocalDateTime, zone: ZoneId): Long = ZonedDateTime.of(local, zone).toInstant().toEpochMilli()

    fun parse(stored: String): LocalDateTime? = try {
        LocalDateTime.parse(stored)
    } catch (e: DateTimeParseException) {
        null
    }

    fun format(local: LocalDateTime): String = local.truncatedTo(ChronoUnit.MINUTES).toString()

    /** The time for a preset, or null when "later today" is no longer possible. */
    fun presetTime(preset: Preset, now: LocalDateTime): LocalDateTime? = when (preset) {
        Preset.LaterToday -> now.plusHours(3).truncatedTo(ChronoUnit.HOURS).plusHours(1)
            .takeIf { it.toLocalDate() == now.toLocalDate() && !it.toLocalTime().isAfter(LATEST_LATER_TODAY) }
        Preset.Tomorrow -> now.toLocalDate().plusDays(1).atTime(MORNING)
        Preset.Weekend -> now.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.SATURDAY)).atTime(MORNING)
        Preset.NextWeek -> now.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atTime(MORNING)
    }
}

interface AlarmScheduler {
    fun set(atMillis: Long)
    fun cancel()
}

interface ResurfaceNotifier {
    fun canNotify(): Boolean
    fun show(noteId: Long, title: String)
    fun cancel(noteId: Long)
}

/**
 * Brings notes back at the chosen time. One alarm is kept for the soonest reminder; each sync
 * fires what is due exactly once and drops reminders of notes that are gone or in Trash.
 */
class Resurfacer(
    private val reminders: ReminderDao,
    private val notes: NoteDao,
    private val alarms: AlarmScheduler,
    private val notifier: ResurfaceNotifier,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    private val mutex = Mutex()

    suspend fun schedule(noteId: Long, at: LocalDateTime) {
        reminders.put(Reminder(noteId, ResurfaceTime.format(at), clock()))
        notifier.cancel(noteId)
        sync()
    }

    suspend fun cancel(noteId: Long) {
        reminders.delete(noteId)
        notifier.cancel(noteId)
        sync()
    }

    suspend fun onTrashed(noteId: Long) = cancel(noteId)

    suspend fun sync() = mutex.withLock {
        val now = clock()
        val zoneId = zone()
        var next: Long? = null
        for (r in reminders.pending()) {
            val local = ResurfaceTime.parse(r.localTime)
            if (local == null) {
                reminders.delete(r.noteId)
                continue
            }
            val at = ResurfaceTime.triggerAt(local, zoneId)
            if (at > now) {
                next = next?.let { minOf(it, at) } ?: at
                continue
            }
            val note = notes.getById(r.noteId)
            if (note == null || note.deletedAt != null) {
                reminders.delete(r.noteId)
                continue
            }
            if (reminders.markFired(r.noteId, now) == 1) {
                val title = note.title.ifBlank { DropMarkdown.previewOf(note.body, note.format).lineSequence().firstOrNull().orEmpty() }
                notifier.show(note.id, title.trim())
            }
        }
        val soonest = next
        if (soonest != null) alarms.set(soonest) else alarms.cancel()
    }
}
