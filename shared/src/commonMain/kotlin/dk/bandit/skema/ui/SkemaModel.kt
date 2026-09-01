package dk.bandit.skema.ui

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dk.bandit.skema.data.Diff
import dk.bandit.skema.data.Refresh
import dk.bandit.skema.data.SkemaRepository
import dk.bandit.skema.data.SkemaSettings
import dk.bandit.skema.data.Snapshot
import dk.bandit.skema.data.ViewMode
import dk.bandit.skema.ics.CalendarEvent
import dk.bandit.skema.ics.Dedup
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

enum class Screen { Calendar, Settings }

/** Monday, because that is where a Danish week starts and where the feed's weeks start. */
fun weekStart(d: LocalDate): LocalDate = d.minus(d.dayOfWeek.ordinal, DateTimeUnit.DAY)

fun LocalDate.shift(days: Int): LocalDate = plus(days, DateTimeUnit.DAY)

/** Minutes from midnight. The one number the time grid actually positions against. */
fun CalendarEvent.startMinute(): Int = start.hour * 60 + start.minute

/**
 * An event with no DTEND is treated as an hour long. DTU sends DTEND on
 * everything with a time, so this only catches the whole day markers, which
 * the grid does not draw anyway.
 */
fun CalendarEvent.endMinute(): Int {
    val e = end ?: return startMinute() + 60
    val raw = e.hour * 60 + e.minute
    // An event that ends past midnight reports a smaller number than it
    // started at. Clamp to the end of the day rather than drawing it upwards.
    return if (raw <= startMinute()) 24 * 60 else raw
}

/**
 * All the app's state in one object, held by remember{} at the root. No
 * ViewModel, because that is an Android class and this has to run on the
 * desktop too.
 *
 * Everything about what is on screen lives here rather than inside a view, so
 * that folding the phone changes the layout and nothing else: the anchor date,
 * the zoom and the selection survive because they were never owned by the
 * composable that got resized.
 */
class SkemaModel(private val repo: SkemaRepository) {

    var screen by mutableStateOf(Screen.Calendar)

    var settings by mutableStateOf(repo.settings())
        private set

    var events by mutableStateOf<List<CalendarEvent>>(emptyList())
        private set

    var fetchedAt by mutableStateOf<Long?>(null)
        private set

    var feedBytes by mutableStateOf(0)
        private set

    var busy by mutableStateOf(false)
        private set

    var status by mutableStateOf("")
        private set

    var failed by mutableStateOf(false)
        private set

    /** The day the current view is built around. Week and month derive from it. */
    var anchor by mutableStateOf(today())

    /** The day the day pane is showing. On a folded screen it tracks the anchor. */
    var selected by mutableStateOf(today())

    /** The event whose detail sheet is open, if any. */
    var picked by mutableStateOf<CalendarEvent?>(null)

    val view: ViewMode get() = settings.view
    val hourHeight: Float get() = settings.hourHeight

    val storageLocation: String get() = repo.storageLocation
    val hasUrl: Boolean get() = settings.feedUrl.isNotBlank()

    // These four are derived rather than computed on read. Merging 736 events
    // is not free, and a plain `get()` would rerun it for every one of these
    // on every frame the grid scrolls. derivedStateOf recomputes only when
    // `events` or the merge setting actually changes, which is on sync.

    /**
     * What the calendar actually draws: merged when the setting is on, raw
     * when it is off, so the toggle shows you the feed's own redundancy.
     */
    private val visibleState = derivedStateOf {
        if (settings.collapseDuplicates) Dedup.collapse(events) else events
    }
    val visible: List<CalendarEvent> get() = visibleState.value

    private val byDayState = derivedStateOf {
        visible.groupBy { it.start.date }.mapValues { (_, v) -> v.sortedBy { it.startMinute() } }
    }
    val byDay: Map<LocalDate, List<CalendarEvent>> get() = byDayState.value

    /**
     * Start times where two different courses genuinely collide. Computed off
     * the raw feed, because merging is what removes the false positives and a
     * clash between two real courses survives it.
     */
    private val conflictState = derivedStateOf {
        Dedup.slots(events).filter { it.isConflict }.map { it.startEpochSeconds }.toSet()
    }
    val conflicts: Set<Long> get() = conflictState.value

    private val courseCodeState = derivedStateOf {
        visible.mapNotNull { it.courseCode }.distinct().sorted()
    }
    val courseCodes: List<String> get() = courseCodeState.value

    val courseCount: Int get() = courseCodes.size

    fun eventsOn(date: LocalDate): List<CalendarEvent> = byDay[date].orEmpty()

    // Navigation. One step means one of whatever the current view shows.

    fun step(direction: Int) {
        anchor = when (view) {
            ViewMode.Month -> anchor.plus(direction, DateTimeUnit.MONTH)
            ViewMode.Week -> anchor.shift(direction * 7)
            ViewMode.Day, ViewMode.Agenda -> anchor.shift(direction)
        }
        if (view != ViewMode.Month) selected = anchor
    }

    fun goToday() {
        anchor = today()
        selected = today()
    }

    fun select(date: LocalDate) {
        selected = date
        anchor = date
    }

    fun setView(next: ViewMode) {
        if (next == view) return
        update { it.copy(view = next) }
    }

    /**
     * Pinching scales the hour rather than setting it, so the gesture reads as
     * continuous and a slow pinch is not quantised into steps.
     */
    fun zoomBy(factor: Float) {
        val next = Grid.clampHour(hourHeight * factor)
        if (next != hourHeight) update { it.copy(hourHeight = next) }
    }

    fun setHue(code: String, hue: Float?) = update {
        val m = it.courseHues.toMutableMap()
        if (hue == null) m.remove(code) else m[code] = ((hue % 360f) + 360f) % 360f
        it.copy(courseHues = m)
    }

    fun loadCache() {
        repo.cached()?.let(::adopt)
    }

    fun update(change: (SkemaSettings) -> SkemaSettings) {
        settings = change(settings)
        repo.saveSettings(settings)
    }

    suspend fun refresh() {
        if (busy) return
        busy = true
        failed = false
        status = "Fetching"
        when (val result = repo.refresh(settings.feedUrl)) {
            is Refresh.NoUrl -> {
                status = "No feed link yet"
                failed = true
            }
            is Refresh.Failed -> {
                status = result.message
                failed = true
            }
            is Refresh.Ok -> {
                adopt(result.snapshot)
                status = describe(result.diff, result.snapshot.events.size)
            }
        }
        busy = false
    }

    /** Drops a settled sync message. A failure is cleared by the next attempt. */
    fun clearStatus() {
        if (!failed) status = ""
    }

    fun forget() {
        repo.clearCache()
        events = emptyList()
        fetchedAt = null
        feedBytes = 0
        status = ""
        failed = false
    }

    private fun adopt(snapshot: Snapshot) {
        events = snapshot.events
        fetchedAt = snapshot.fetchedAtEpochSeconds
        feedBytes = snapshot.bytes
    }

    private fun describe(diff: Diff, total: Int): String {
        if (diff.isEmpty) return "Nothing moved, $total entries"
        val parts = buildList {
            if (diff.added > 0) add("${diff.added} new")
            if (diff.changed > 0) add("${diff.changed} moved")
            if (diff.removed > 0) add("${diff.removed} gone")
        }
        return parts.joinToString(", ")
    }
}
