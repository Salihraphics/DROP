package app.drop.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.drop.DropApplication
import app.drop.data.DailyDao
import app.drop.data.DashboardDao
import app.drop.data.Note
import app.drop.data.NoteDao
import app.drop.data.ProjectDao
import app.drop.data.ProjectSummary
import app.drop.data.ReminderDao
import app.drop.data.ReminderWithNote
import app.drop.data.TaskCounts
import app.drop.data.TaskWithTime
import app.drop.echo.EchoIndex
import app.drop.resurface.ResurfaceTime
import app.drop.ui.echo.EchoCard
import app.drop.ui.echo.cards
import app.drop.ui.spaces.dayBounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** When an open task is planned, relative to now. */
enum class TaskWhen { Due, Today, Tomorrow, Upcoming, None }

data class DashboardTask(val task: TaskWithTime, val whenDue: TaskWhen, val time: LocalDateTime?)

/** Today in the Daily Log: the day's line, notes created and tasks completed today. */
data class DailySummary(
    val date: String,
    val entry: String?,
    val created: Int,
    val completed: Int,
    val completedTasks: List<Note> = emptyList(),
) {
    val isEmpty: Boolean get() = entry.isNullOrBlank() && created == 0 && completed == 0
}

data class DashboardState(
    val now: LocalDateTime,
    val activeCount: Int,
    val counts: TaskCounts,
    val tasks: List<DashboardTask>,
    val recent: List<Note>,
    val projects: List<ProjectSummary>,
    val daily: DailySummary,
    val dueReminders: Int,
    val nextReminder: ReminderWithNote?,
) {
    /** Nothing written yet: the Dashboard shows how to start instead of empty sections. */
    val isFirstUse: Boolean get() = activeCount == 0 && projects.isEmpty() && daily.entry.isNullOrBlank()
    val shownProjects: List<ProjectSummary> get() = projects.take(MAX_PROJECTS)

    companion object {
        const val MAX_TASKS = 8
        const val MAX_RECENT = 5
        const val MAX_PROJECTS = 4
    }
}

/** Times are compared as stored text ("2026-11-21T09:00"), the same way the database does. */
fun taskWhen(task: TaskWithTime, now: LocalDateTime): TaskWhen {
    val stored = task.localTime ?: return TaskWhen.None
    val nowText = ResurfaceTime.format(now)
    val tomorrow = ResurfaceTime.format(now.toLocalDate().plusDays(1).atStartOfDay())
    val dayAfter = ResurfaceTime.format(now.toLocalDate().plusDays(2).atStartOfDay())
    return when {
        task.firedAt != null || stored <= nowText -> TaskWhen.Due
        stored < tomorrow -> TaskWhen.Today
        stored < dayAfter -> TaskWhen.Tomorrow
        else -> TaskWhen.Upcoming
    }
}

/** The day a task is planned for, from its reminder. */
fun plannedDate(task: TaskWithTime): LocalDate? = task.localTime?.let { ResurfaceTime.parse(it) }?.toLocalDate()

/** The time now, and again at the start of every minute. */
fun minuteTicks(clock: () -> Long): Flow<Long> = flow {
    while (true) {
        val now = clock()
        emit(now)
        delay(60_000 - now % 60_000)
    }
}.flowOn(Dispatchers.Default)

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    private val dashboard: DashboardDao,
    private val notes: NoteDao,
    projects: ProjectDao,
    private val daily: DailyDao,
    reminders: ReminderDao,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    ticks: Flow<Long> = minuteTicks(clock),
    echoIndex: Flow<EchoIndex?> = flowOf(null),
    echoSuppressed: Flow<Map<Long, Long>> = flowOf(emptyMap()),
    private val onEchoDismissed: (Long) -> Unit = {},
    private val onEchoOpened: (Long) -> Unit = {},
) : ViewModel() {
    private val minute: Flow<LocalDateTime> = ticks.map { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone()) }.distinctUntilChanged()

    /** Counts and the tasks to show, regrouped when a day passes. */
    private val taskPart = minute.flatMapLatest { now ->
        val nowText = ResurfaceTime.format(now)
        val tomorrow = ResurfaceTime.format(now.toLocalDate().plusDays(1).atStartOfDay())
        val dayAfter = ResurfaceTime.format(now.toLocalDate().plusDays(2).atStartOfDay())
        dashboard.observeTaskCounts(nowText, tomorrow, dayAfter).mapLatest { counts ->
            counts to dashboard.openTasks(DashboardState.MAX_TASKS).map {
                DashboardTask(it, taskWhen(it, now), it.localTime?.let(ResurfaceTime::parse))
            }
        }
    }

    private val dailyPart: Flow<DailySummary> = minute.map { it.toLocalDate() }.distinctUntilChanged().flatMapLatest { date ->
        val (start, end) = dayBounds(date, zone())
        combine(daily.observe(date.toString()), dashboard.observeCreatedCount(start, end), dashboard.observeCompletedTasks(start, end)) { entry, created, done ->
            DailySummary(date.toString(), entry?.text, created, done.size, done)
        }
    }

    private val reminderPart = combine(reminders.observeDueCount(), dashboard.observeNextReminder()) { due, next -> due to next }

    /** Null until the first read completes, so the first-use state never flashes. */
    val state: StateFlow<DashboardState?> = combine(
        combine(minute, dashboard.observeActiveCount()) { now, count -> now to count },
        taskPart,
        dashboard.observeRecentNotes(DashboardState.MAX_RECENT),
        combine(projects.observeAll(), dailyPart) { p, d -> p to d },
        reminderPart,
    ) { (now, active), (counts, tasks), recent, (projectList, day), (due, next) ->
        DashboardState(now, active, counts, tasks, recent, projectList, day, due, next)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Older notes that fit what was written lately, worked out again when notes change, when
     * the person dismisses one, and every hour.
     */
    val echo: StateFlow<List<EchoCard>> = combine(echoIndex, echoSuppressed, ticks.map { it / ECHO_REFRESH_MS }.distinctUntilChanged()) { index, held, _ ->
        index to held
    }.mapLatest { (index, held) ->
        if (index == null) {
            emptyList()
        } else {
            withContext(Dispatchers.Default) {
                val now = clock()
                index.cards(index.rediscover(now, STALE_AFTER_MS, ECHO_LIMIT, held.filterValues { it > now }.keys))
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun dismissEcho(noteId: Long) = onEchoDismissed(noteId)

    fun openedEcho(noteId: Long) = onEchoOpened(noteId)

    /** Completing never moves an already completed task's time; reopening clears it. */
    fun setCompleted(id: Long, done: Boolean) {
        viewModelScope.launch { if (done) notes.completeIfOpen(id, clock()) else notes.setCompletedAt(id, null) }
    }

    fun restore(id: Long) {
        viewModelScope.launch { notes.setDeletedAt(id, null) }
    }

    companion object {
        /** Notes untouched for a week can be rediscovered. */
        const val STALE_AFTER_MS = 7L * 24 * 60 * 60 * 1000
        const val ECHO_LIMIT = 3
        private const val ECHO_REFRESH_MS = 60L * 60 * 1000

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DropApplication
                val db = app.database
                DashboardViewModel(
                    db.dashboardDao(), db.noteDao(), db.projectDao(), db.dailyDao(), db.reminderDao(),
                    echoIndex = app.echo.index,
                    echoSuppressed = app.echoMemory.suppressedUntil,
                    onEchoDismissed = app.echoMemory::dismiss,
                    onEchoOpened = app.echoMemory::opened,
                )
            }
        }
    }
}
