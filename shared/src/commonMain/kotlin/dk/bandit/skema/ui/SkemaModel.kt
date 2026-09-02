package dk.bandit.skema.ui

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dk.bandit.skema.data.Diff
import dk.bandit.skema.data.Refresh
import dk.bandit.skema.data.Session
import dk.bandit.skema.data.SkemaRepository
import dk.bandit.skema.data.SkemaSettings
import dk.bandit.skema.data.Snapshot
import dk.bandit.skema.data.ViewMode
import dk.bandit.skema.ics.CalendarEvent
import dk.bandit.skema.ics.Dedup
import dk.bandit.skema.ics.Term
import dk.bandit.skema.ics.Terms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
class SkemaModel(
    private val repo: SkemaRepository,
    private val scope: CoroutineScope,
) {

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

    /**
     * The zoom is held here, not read back out of settings, because it changes
     * once per pointer event during a pinch. Routing that through `update`
     * encoded the whole settings object and wrote a file on the main thread
     * sixty times a second, which is what made pinching feel stuck rather
     * than smooth. It is still persisted, just not on the hot path.
     */
    var hourHeight by mutableStateOf(repo.settings().hourHeight)
        private set

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
    /**
     * The enrolment date, or null while it has not been set.
     *
     * Parsed leniently: a hand-edited settings file with a broken date should
     * show the whole feed, not crash on launch.
     */
    val enrolledFrom: LocalDate?
        get() = settings.enrolledFrom?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private val visibleState = derivedStateOf {
        val merged = if (settings.collapseDuplicates) Dedup.collapse(events) else events
        val from = enrolledFrom
        if (from == null) merged else merged.filter { it.start.date >= from }
    }
    val visible: List<CalendarEvent> get() = visibleState.value

    /** Every semester the visible feed touches, oldest first. */
    private val termsState = derivedStateOf {
        visible.map { Terms.of(it) }.distinct().sorted()
    }
    val terms: List<Term> get() = termsState.value

    fun eventsIn(term: Term): List<CalendarEvent> = visible.filter { Terms.of(it) == term }

    /** Course codes taught in a semester, in the order the palette assigns them. */
    fun coursesIn(term: Term): List<String> =
        eventsIn(term).mapNotNull { it.courseCode }.distinct().sorted()

    /**
     * What to offer as an enrolment date before one is set: the start of the
     * earliest semester DTU actually named. On the owner's feed that is Fall
     * 2025, which is right, and it beats asking someone to remember a date
     * the feed already knows.
     */
    val suggestedEnrolment: LocalDate?
        get() = events.mapNotNull { Terms.parse(it.location.courseName) }.minOrNull()?.startsOn

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

    /**
     * Whether today is already on screen, which decides if a "today" control
     * is worth offering.
     *
     * Asking "is the anchor today" was the wrong question: stepping one day
     * forward in the day view and switching to week left the anchor on
     * tomorrow, which is the same week, so the control appeared and pressing
     * it changed nothing. The honest test is whether the period being shown
     * already contains today.
     */
    val showingToday: Boolean
        get() {
            val now = today()
            return when (view) {
                ViewMode.Month -> anchor.year == now.year && anchor.month == now.month
                ViewMode.Week -> weekStart(anchor) == weekStart(now)
                ViewMode.Day -> selected == now
                ViewMode.Agenda -> true
            }
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
        if (next != hourHeight) {
            hourHeight = next
            persist()
        }
    }

    val atMinZoom: Boolean get() = hourHeight <= Grid.MIN_HOUR + 0.01f
    val atMaxZoom: Boolean get() = hourHeight >= Grid.MAX_HOUR - 0.01f

    fun setHue(code: String, hue: Float?) = update {
        val m = it.courseHues.toMutableMap()
        if (hue == null) m.remove(code) else m[code] = ((hue % 360f) + 360f) % 360f
        it.copy(courseHues = m)
    }

    /**
     * Put the user back where they were, if they were there recently.
     *
     * Called once at startup, before the first frame, so the calendar opens
     * on the right week rather than opening on today and then jumping.
     */
    fun restoreSession() {
        val saved = repo.session() ?: return
        if (!saved.isFreshAt(nowSeconds())) {
            // Stale: they closed it properly, or that was yesterday. A
            // calendar opening cold should open on today.
            repo.clearSession()
            return
        }
        settings = settings.copy(view = saved.view)
        anchor = LocalDate.fromEpochDays(saved.anchorEpochDay)
        selected = LocalDate.fromEpochDays(saved.selectedEpochDay)
        hourHeight = Grid.clampHour(saved.hourHeight)
    }

    private var sessionJob: Job? = null

    /**
     * Records the position, debounced. Every swipe between weeks calls this,
     * so it must not touch the disk on the gesture; the same reasoning as the
     * settings write, and a lost second of position costs nothing.
     */
    fun rememberPosition() {
        sessionJob?.cancel()
        sessionJob = scope.launch {
            delay(600)
            val snapshot = Session(
                view = view,
                anchorEpochDay = anchor.toEpochDays(),
                selectedEpochDay = selected.toEpochDays(),
                hourHeight = hourHeight,
                savedAtEpochSeconds = nowSeconds(),
            )
            withContext(Dispatchers.Default) { repo.saveSession(snapshot) }
        }
    }

    fun loadCache() {
        repo.cached()?.let(::adopt)
    }

    private var saveJob: Job? = null

    /**
     * Settings reach the disk once the user has stopped fiddling, and off the
     * main thread. Nothing here needs to survive a crash within half a second
     * of a gesture, and every writer of settings is a gesture.
     */
    private fun persist() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(400)
            val snapshot = settings.copy(hourHeight = hourHeight)
            withContext(Dispatchers.Default) { repo.saveSettings(snapshot) }
        }
    }

    fun update(change: (SkemaSettings) -> SkemaSettings) {
        settings = change(settings)
        persist()
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
