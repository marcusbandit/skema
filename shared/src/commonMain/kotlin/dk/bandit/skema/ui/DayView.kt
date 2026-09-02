package dk.bandit.skema.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dk.bandit.skema.data.ViewMode
import dk.bandit.skema.ics.CalendarEvent
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/**
 * One day, on the same grid as the week.
 *
 * The day view is the week view with the column count set to one, on purpose:
 * the window, the lanes, the gutter, the rules and the now line are all the
 * shared primitives, so a day reads as the week zoomed in rather than as a
 * second app with its own conventions. The only thing that changes is what an
 * event can afford to say, because one column of 360dp holds four lines of
 * text where a 70dp week column holds a clipped title.
 */

/**
 * Pages either side of the day the view opens on. Roughly thirteen years each
 * way, which is past the end of any degree.
 */
private const val DAY_SPAN = 5_000
private const val DAY_PAGES = DAY_SPAN * 2 + 1

/**
 * How wide the event column is allowed to get.
 *
 * Unfolded, the app has 841dp and the day has one column to spend it on. A
 * block stretched the whole way is a 780dp banner holding "10:00 - 12:00" and
 * a course name, which reads as a layout bug rather than as a big day. So the
 * grid is capped at a comfortable measure and centred, and the spare width is
 * left as margin.
 *
 * The alternative, filling the spare width with a second pane, is already
 * taken: the shell puts the event detail and the day summary in a rail at
 * this size, so a day view that grew its own side panel would be competing
 * with it. Note also that this is a constraint and not a branch on [Frame]:
 * the day view does not know whether it is short of width because the phone
 * is folded or because the shell gave the rail a third of the window, and it
 * should not have to.
 */
private val MAX_COLUMN = 560.dp

/** Air between two lanes of the same cluster, so overlapping blocks do not weld. */
private val LANE_GAP = 3.dp

/** One line of [Type.small] with its default leading. Counts lines, sizes nothing. */
private val LINE = 18.dp

/** Matches the week block's vertical padding, so the two agree on where text starts. */
private val BLOCK_PAD = 3.dp

/**
 * How far a stacked block is pushed down per event sharing its exact start
 * minute: one padded line, which is the row the block underneath has to keep
 * clear for its own header to be readable.
 */
private val CASCADE_HEADER = LINE + BLOCK_PAD

/**
 * The share of the column a whole cascade may spend on its insets.
 *
 * The step per event is this budget divided by the depth of the cluster, so a
 * two deep overlap indents boldly and a five deep one indents in small steps
 * instead of pushing the last block off the right edge. A third leaves two
 * thirds of the width on the deepest block, which still holds a title.
 */
private const val CASCADE_SHARE = 0.34f

/**
 * How long an anchored zoom will wait for the grid to be measured at its new
 * hour height before it gives up and scrolls anyway.
 *
 * A few frames. The wait normally ends on the first one; the bound is there so
 * a rounding mismatch cannot leave the correction pending forever.
 */
private const val MEASURE_WAIT_MS = 120L

@Composable
fun DayView(model: SkemaModel, palette: CoursePalette, modifier: Modifier = Modifier) {
    val c = skema

    // Both of these walk every event in the feed and are declared as computed
    // properties, so they are read once here rather than from inside a page
    // that recomposes on every scroll frame.
    val byDay = remember(model.events, model.settings.collapseDuplicates) { model.byDay }
    val conflicts = remember(model.events) { model.conflicts }

    // Page numbers are anchored to today rather than to whatever was selected
    // when the view opened, so a restored pager state and a freshly computed
    // origin still agree on which page is which day.
    val origin = today()
    val state = rememberPagerState(
        initialPage = DAY_SPAN + origin.daysUntil(model.selected),
        pageCount = { DAY_PAGES },
    )
    val pressure = remember { ZoomPressure() }

    // Swiping is what moves the selection, so the pager leads and the model
    // follows. settledPage rather than currentPage: a half finished swipe
    // should not change which day the rest of the app thinks you are on.
    LaunchedEffect(state, origin) {
        snapshotFlow { state.settledPage }.collect { page ->
            val date = origin.shift(page - DAY_SPAN)
            if (date != model.selected) model.select(date)
        }
    }

    // And the other direction, for a selection that arrived from the month
    // grid or the Today button. Guarded, or the two effects chase each other.
    LaunchedEffect(model.selected) {
        val target = DAY_SPAN + origin.daysUntil(model.selected)
        if (target in 0 until DAY_PAGES && target != state.currentPage) state.scrollToPage(target)
    }

    // The pinch is handled here rather than on the page, and that placement is
    // load bearing. This modifier sits outside both scrollers, and pinchZoom
    // reads the Initial pass, which travels parent to child: it therefore sees
    // a two finger gesture before the pager's horizontal drag and before the
    // page's vertical scroll, both of which read the Main pass on the way back
    // up. Consuming there is what stops the swipe and the scroll from eating
    // the pinch. One finger is never touched, so paging and scrolling still
    // work. Everything under it is a plain clickable, which also resolves on
    // Main, so a pinch that starts on top of an event block still zooms.
    HorizontalPager(
        state = state,
        key = { it },
        modifier = modifier
            .background(c.background)
            .pinchZoom { factor ->
                model.zoomBy(factor)
                // Read the zoom after applying it, the same way the week does:
                // once it has clamped, the pinch is pushing against a wall and
                // that is what builds the pressure that changes view. The two
                // flags come from the model so the tolerance on "clamped" is
                // decided in one place.
                val step = pressure.feed(
                    factor = factor,
                    atMin = model.atMinZoom,
                    atMax = model.atMaxZoom,
                )
                // Nothing is narrower than a day, so +1 has nowhere to go.
                if (step < 0) model.setView(ViewMode.Week)
            },
    ) { page ->
        val date = origin.shift(page - DAY_SPAN)
        DayPage(
            date = date,
            events = byDay[date].orEmpty(),
            palette = palette,
            conflicts = conflicts,
            hourHeight = model.hourHeight,
            onPick = { model.picked = it },
            onToday = { model.goToday() },
        )
    }
}

@Composable
private fun DayPage(
    date: LocalDate,
    events: List<CalendarEvent>,
    palette: CoursePalette,
    conflicts: Set<Long>,
    hourHeight: Float,
    onPick: (CalendarEvent) -> Unit,
    onToday: () -> Unit,
) {
    val c = skema
    val density = LocalDensity.current

    // Midnight to midnight, whatever is on. The window used to be derived from
    // the day's events, which put the same 13:00 at a different height on
    // every day of the week; finding the part you care about is now the
    // opening scroll's job and the zoom's job.
    val window = DayWindow.FULL
    val timed = remember(events) { events.filterNot { it.allDay } }
    val allDay = remember(events) { events.filter { it.allDay } }

    // Both arrangements, because which one is right depends on the column
    // width, and the width is only known further down inside the grid.
    val placed = remember(timed) {
        Lanes.layout(timed, { it.startMinute() }, { it.endMinute() })
    }
    val cascaded = remember(timed) {
        Lanes.cascade(timed, { it.startMinute() }, { it.endMinute() })
    }

    val scroll = rememberScrollState()

    // Open on the interesting part of the day. A full day grid parked at its
    // top is six hours of empty rules, so the scroll starts at the clock on
    // today and at the first event on any other day, each backed off by an
    // hour so the target is not welded to the top edge. Once per day: a sync
    // arriving later must not yank the scroll out from under a finger.
    var landed by remember(date) { mutableStateOf(false) }
    LaunchedEffect(date, events) {
        if (landed) return@LaunchedEffect
        // maxValue is Int.MAX_VALUE until the grid has been measured, and a
        // scroll issued before then is clamped back to the top.
        snapshotFlow { scroll.maxValue }.first { it != Int.MAX_VALUE }
        val y = window.offsetOf(openingMinute(date, events), hourHeight)
        scroll.scrollTo(with(density) { y.roundToPx() }.coerceAtLeast(0))
        landed = true
    }

    // The hour height the current scroll offset was measured against. It only
    // moves once a correction has actually been applied, so a pinch that fires
    // several times between frames accumulates its ratio instead of dropping
    // the steps whose effects were cancelled.
    var anchoredHour by remember { mutableStateOf(hourHeight) }
    LaunchedEffect(hourHeight) {
        val from = anchoredHour
        val viewport = scroll.viewportSize
        // A zoom that arrives before the day has landed, or before the grid
        // has been measured at all, has nothing to correct: the opening scroll
        // is about to place the day at the current hour height. The base
        // follows the zoom so the next correction is not computed against a
        // height the scroll offset never had.
        if (!landed || viewport <= 0 || from <= 0f) {
            anchoredHour = hourHeight
            return@LaunchedEffect
        }
        if (from == hourHeight) return@LaunchedEffect

        // Anchored zoom. Every y in the grid is linear in the hour height, so
        // the pixel a minute is drawn at scales by the ratio of the two
        // heights. Keeping the minute at the middle of the viewport where it
        // was is then one line, since the scroller keeps its pixel offset and
        // the content grew underneath it:
        //   focus  = scroll + viewport / 2      the pixel between the fingers
        //   scroll = focus * ratio - viewport / 2
        // The centre stands in for the pinch centroid, which the gesture does
        // not report; it is within half a viewport of the fingers, and it is
        // the difference between the day sliding under the pinch and the day
        // jumping by the whole change in height.
        val ratio = hourHeight / from
        val focus = scroll.value + viewport / 2f
        val target = (focus * ratio - viewport / 2f).roundToInt().coerceAtLeast(0)

        // scrollTo clamps against the height the grid was last measured at,
        // and this runs before the frame that measures the new one, so zooming
        // in near the end of the day would be cut short by the old maximum.
        // For a zoom in the target always lands inside the new maximum, so
        // waiting for the maximum to reach it ends on the next frame; a zoom
        // out never exceeds the old maximum and never waits at all.
        if (target > scroll.maxValue) {
            withTimeoutOrNull(MEASURE_WAIT_MS) {
                snapshotFlow { scroll.maxValue }.first { it >= target }
            }
        }
        scroll.scrollTo(target)
        anchoredHour = hourHeight
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = Grid.GUTTER + MAX_COLUMN).fillMaxSize()) {
            if (allDay.isNotEmpty()) {
                AllDayStrip(allDay, palette, onPick)
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
            }
            Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)) {
                Row(Modifier.fillMaxWidth()) {
                    // Labels thin out on their own as the hour shrinks, so 24
                    // hours at the minimum zoom is not a wall of numbers.
                    HourGutter(window, hourHeight)
                    DayGrid(
                        date = date,
                        window = window,
                        hourHeight = hourHeight,
                        placed = placed,
                        cascaded = cascaded,
                        palette = palette,
                        conflicts = conflicts,
                        onPick = onPick,
                        onToday = onToday,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * Where the day parks when it first appears.
 *
 * Today opens on the clock, because a day you are standing in is a question
 * about now. Any other day opens on its first event. Both back off by the hour
 * [DayWindow.openingMinute] already backs off by, so the two land the same way.
 */
private fun openingMinute(date: LocalDate, events: List<CalendarEvent>): Int =
    if (date == today()) (nowMinutes() - 60).coerceIn(0, 23 * 60)
    else DayWindow.openingMinute(events)

/**
 * The column itself: the rules, the blocks, the now line.
 *
 * Overlapping events are drawn side by side while the lanes still hold words,
 * which on a day column is nearly always. The fallback is the week's cascade
 * rather than a third arrangement of this view's own, so a folded phone reads
 * the same way in both views. The test is [sideBySideFits], on the widest
 * cluster of the day: mixing the two arrangements inside one column would be
 * worse than either, so the deepest overlap decides for all of them.
 */
@Composable
private fun DayGrid(
    date: LocalDate,
    window: DayWindow,
    hourHeight: Float,
    placed: List<Placed<CalendarEvent>>,
    cascaded: List<Stacked<CalendarEvent>>,
    palette: CoursePalette,
    conflicts: Set<Long>,
    onPick: (CalendarEvent) -> Unit,
    onToday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier
            .height(window.height(hourHeight))
            // Blocks are clickable children, so they consume the tap first and
            // this only ever fires on bare grid.
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onToday() }) },
    ) {
        val column = maxWidth
        HourRules(window, hourHeight)

        // The busiest moment of the day decides for the whole column.
        val lanes = placed.maxOfOrNull { it.lanes } ?: 1
        if (sideBySideFits(column, lanes)) {
            placed.forEach { p ->
                val event = p.item
                val span = window.spanOf(p.startMinute, p.endMinute, hourHeight)
                DayEventBlock(
                    event = event,
                    tint = palette.tint(event.courseCode),
                    height = span,
                    conflict = event.startEpochSeconds in conflicts,
                    modifier = Modifier
                        .offset(
                            x = column * p.offsetFraction,
                            y = window.offsetOf(p.startMinute, hourHeight),
                        )
                        .width((column * p.widthFraction - LANE_GAP).coerceAtLeast(LANE_GAP))
                        .height(span)
                        .clickable { onPick(event) },
                )
            }
        } else {
            // One step for the whole day, from the deepest stack in it, so
            // every cascade on the page indents by the same amount instead of
            // each one inventing its own. It is the stack depth and not the
            // lane count that sets it: a chain of events each overlapping the
            // next needs only two lanes and can still be six deep, and it is
            // the sixth block that would otherwise land off the right edge.
            val steps = cascaded.maxOfOrNull { it.depth } ?: 0
            val step = if (steps > 0) column * CASCADE_SHARE / steps else 0.dp
            cascaded.forEach { s ->
                val event = s.item
                val inset = step * s.depth
                // Events starting in the same minute would hide each other's
                // first line exactly, so each one after the first drops by a
                // header. The height gives the same amount back, or the block
                // would claim to run past its own end time.
                val shift = CASCADE_HEADER * s.headerSteps
                val span = window.spanOf(s.startMinute, s.endMinute, hourHeight)
                val height = (span - shift).coerceAtLeast(CASCADE_HEADER)
                DayEventBlock(
                    event = event,
                    tint = palette.tint(event.courseCode),
                    height = height,
                    conflict = event.startEpochSeconds in conflicts,
                    modifier = Modifier
                        .offset(
                            x = inset,
                            y = window.offsetOf(s.startMinute, hourHeight) + shift,
                        )
                        .width((column - inset - LANE_GAP).coerceAtLeast(LANE_GAP))
                        .height(height)
                        .clickable { onPick(event) },
                )
            }
        }

        // Last, so it crosses the blocks it is telling you about.
        if (date == today()) NowLine(window, hourHeight, nowMinutes())
    }
}

/**
 * A whole day entry. It has no start, no end and no place on a time axis, so
 * putting it in the grid means inventing a time for it. It goes in a strip
 * above instead, which is also where a reader looks for "true all day".
 */
@Composable
private fun AllDayStrip(
    events: List<CalendarEvent>,
    palette: CoursePalette,
    onPick: (CalendarEvent) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Space.small, vertical = Space.tight),
        horizontalArrangement = Arrangement.spacedBy(Space.tight),
    ) {
        events.forEach { event ->
            val tint = palette.tint(event.courseCode)
            val text = textFor(
                event.summary,
                event.location.courseName,
                event.courseCode,
                event.fromRoomBooking,
            )
            Text(
                text.title,
                modifier = Modifier
                    .clip(SquircleShape(Radius.chip))
                    .background(tint.fill)
                    .clickable { onPick(event) }
                    .padding(horizontal = Space.small, vertical = Space.unit),
                color = tint.ink,
                fontSize = Type.small,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

/**
 * A row of text inside a block, and how badly it wants to survive.
 *
 * [rank] 0 is dropped last. The room list outranks the course name because a
 * room list clipped down to its first entry is the thing that started this
 * app; the course name is also on the chip in the detail sheet and in the
 * colour of the block itself.
 */
private class BlockRow(val rank: Int, val text: String, val strong: Boolean)

/**
 * One event, in the day column.
 *
 * [EventBlock] is the week's version and it cannot say enough here: it has one
 * clock time and one room line, sized for a column too narrow for either. This
 * says the whole range, the title, the course and every room, and keeps the
 * week's tint, its 3dp rail and its chip radius so the two are visibly one
 * system.
 *
 * What actually fits is measured rather than guessed at: the height divided by
 * a line gives the number of lines available, and rows drop out in rank order
 * until the rest fit. Lines nothing claimed go to the last row shown, which is
 * the room list whenever there is one, so rooms wrap instead of ellipsing.
 */
@Composable
private fun DayEventBlock(
    event: CalendarEvent,
    tint: CourseTint,
    height: Dp,
    conflict: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = skema
    val text = textFor(
        event.summary,
        event.location.courseName,
        event.courseCode,
        event.fromRoomBooking,
    )

    // Display order, top to bottom.
    val rows = buildList {
        add(BlockRow(1, timeRange(event), strong = false))
        add(BlockRow(0, text.title, strong = true))
        text.subtitle?.let { add(BlockRow(3, it, strong = false)) }
        if (event.rooms.isNotEmpty()) {
            add(BlockRow(2, event.rooms.joinToString(SEPARATOR), strong = false))
        }
    }

    val lines = ((height - BLOCK_PAD * 2) / LINE).toInt().coerceAtLeast(1)
    val keep = rows.indices.sortedBy { rows[it].rank }.take(lines).toSet()
    val spare = (lines - keep.size).coerceAtLeast(0)
    val last = keep.maxOrNull()

    Box(
        modifier
            .clip(SquircleShape(Radius.chip))
            .background(tint.fill)
            .padding(start = 3.dp),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxSize()
                .offset(x = (-3).dp)
                .background(if (conflict) c.accentStrong else tint.rail),
        )
        Column(Modifier.fillMaxSize().padding(horizontal = Space.tight, vertical = BLOCK_PAD)) {
            rows.forEachIndexed { i, row ->
                if (i !in keep) return@forEachIndexed
                Text(
                    row.text,
                    color = if (row.strong) tint.ink else tint.ink.copy(alpha = 0.75f),
                    fontSize = Type.small,
                    fontWeight = if (row.strong) FontWeight.Medium else FontWeight.Normal,
                    maxLines = if (i == last) 1 + spare else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** "10:00 - 12:00". Plain hyphen, and the end is the clamped one the grid drew. */
private fun timeRange(event: CalendarEvent): String =
    clockOfMinute(event.startMinute()) + " - " + clockOfMinute(event.endMinute())
