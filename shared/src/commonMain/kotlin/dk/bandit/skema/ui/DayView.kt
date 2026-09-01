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
import kotlinx.coroutines.flow.first
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

    HorizontalPager(
        state = state,
        key = { it },
        modifier = modifier
            .background(c.background)
            .pinchZoom { factor ->
                model.zoomBy(factor)
                // Read the zoom after applying it, the same way the week does:
                // once it has clamped, the pinch is pushing against a wall and
                // that is what builds the pressure that changes view.
                val step = pressure.feed(
                    factor = factor,
                    atMin = model.hourHeight <= Grid.MIN_HOUR,
                    atMax = model.hourHeight >= Grid.MAX_HOUR,
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

    val window = remember(events) { DayWindow.of(events) }
    val timed = remember(events) { events.filterNot { it.allDay } }
    val allDay = remember(events) { events.filter { it.allDay } }
    val placed = remember(timed) {
        Lanes.layout(timed, { it.startMinute() }, { it.endMinute() })
    }

    val scroll = rememberScrollState()

    // Open on the first thing that happens. Landing at the top of the window
    // means landing on an hour of empty grid, and the window already has an
    // hour of air built into it. Once per day: a sync arriving later must not
    // yank the scroll out from under a finger.
    var landed by remember(date) { mutableStateOf(false) }
    LaunchedEffect(date, placed) {
        val first = placed.minOfOrNull { it.startMinute }
        if (landed || first == null) return@LaunchedEffect
        // maxValue is Int.MAX_VALUE until the grid has been measured, and a
        // scroll issued before then is clamped back to the top.
        snapshotFlow { scroll.maxValue }.first { it != Int.MAX_VALUE }
        val y = window.offsetOf(first, hourHeight) - Space.medium
        scroll.scrollTo(with(density) { y.roundToPx() }.coerceAtLeast(0))
        landed = true
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = Grid.GUTTER + MAX_COLUMN).fillMaxSize()) {
            if (allDay.isNotEmpty()) {
                AllDayStrip(allDay, palette, onPick)
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
            }
            Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)) {
                Row(Modifier.fillMaxWidth()) {
                    HourGutter(window, hourHeight)
                    DayGrid(
                        date = date,
                        window = window,
                        hourHeight = hourHeight,
                        placed = placed,
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

@Composable
private fun DayGrid(
    date: LocalDate,
    window: DayWindow,
    hourHeight: Float,
    placed: List<Placed<CalendarEvent>>,
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

/** One line of [Type.small] with its default leading. Counts lines, sizes nothing. */
private val LINE = 18.dp

/** Matches the week block's vertical padding, so the two agree on where text starts. */
private val BLOCK_PAD = 3.dp

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
