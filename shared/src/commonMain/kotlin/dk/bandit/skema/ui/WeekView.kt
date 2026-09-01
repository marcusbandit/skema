package dk.bandit.skema.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dk.bandit.skema.data.ViewMode
import dk.bandit.skema.ics.CalendarEvent
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/**
 * The week, which is the view this app exists for.
 *
 * One clock down the left, one column per day, the hour rules behind them and
 * the events sitting where their real start and end times put them. Everything
 * that scales with the number of days is derived from that number: the column
 * width, the separators between columns, the lane a clashing event lands in.
 * Nothing here knows that a week is five days or seven.
 *
 * The layout splits into a fixed left rail and a paged week, so the clock is
 * chrome that stays where it is and the week is content that slides. The rail
 * lines up with the grid by measuring the heading band rather than by guessing
 * its height, which means the headings can change size without this file
 * knowing about it.
 */

/** Weeks reachable either side of the week the view opened on. Fifty years each way. */
private const val PAGE_REACH = 2_600
private const val PAGE_ORIGIN = PAGE_REACH
private const val PAGE_COUNT = PAGE_REACH * 2 + 1

/** How many weeks either side share the anchor week's hour window. See [windowAround]. */
private const val NEIGHBOUR_WEEKS = 1

/** Where the grid opens when the week has nothing in it. */
private const val WORKING_START = 8 * 60

/**
 * Air either side of a block, as a share of the column it sits in.
 *
 * A fixed inset is either invisible on the unfolded phone's 159dp columns or
 * eats a folded one's 73dp, so it scales with the column and is clamped to a
 * hairline at the bottom and to one spacing step at the top, past which the
 * air would start costing the title its words.
 */
private const val COLUMN_AIR = 0.02f

private fun columnAir(columnWidth: Dp): Dp = (columnWidth * COLUMN_AIR).coerceIn(1.dp, Space.tight)

/** Two events side by side need a seam, or they read as one wide block. */
private val LANE_GAP = 1.dp

/**
 * Rough advance of one character at [Type.small] in the system sans. Used only
 * to decide whether the long weekday name still fits a column; no layout is
 * measured from it.
 */
private val LABEL_CHAR = 7.dp

@Composable
fun WeekView(
    model: SkemaModel,
    palette: CoursePalette,
    modifier: Modifier = Modifier,
) {
    val c = skema
    val frame = LocalFrame.current
    val density = LocalDensity.current

    val showWeekend = model.settings.showWeekend
    val dayCount = Grid.dayCount(showWeekend)
    val onScreen = Grid.visibleDays(frame, showWeekend)
    val columnWidth = Grid.columnWidth(frame, onScreen)

    // Only make the columns draggable sideways when some of them are genuinely
    // off screen. A scroller that cannot move still competes with the pager for
    // the same drag, and losing a week swipe to it would be the worse bug.
    val sideways = onScreen < dayCount

    // Dedup walks the whole feed, and a pinch recomposes on every pointer
    // event, so this is done per feed change rather than per frame.
    val byDay = remember(model.events, model.settings.collapseDuplicates) { model.byDay }
    val conflicts = remember(model.events) { model.conflicts }

    val week = weekStart(model.anchor)
    val hourHeight = model.hourHeight

    val window = remember(byDay, week) { windowAround(byDay, week) }
    val allDay = remember(byDay, week, dayCount) { anyAllDayAround(byDay, week, dayCount) }

    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()

    // Page zero is fifty years back, so the origin week has to be pinned once
    // and never recomputed; the anchor moving is what the pager reacts to.
    val origin = remember { weekStart(model.anchor) }
    val pager = rememberPagerState(initialPage = PAGE_ORIGIN, pageCount = { PAGE_COUNT })

    // The settled page is the week now. Shifting the anchor rather than
    // replacing it keeps the weekday you were looking at.
    LaunchedEffect(pager, origin) {
        snapshotFlow { pager.settledPage }.collect { page ->
            val target = origin.shift((page - PAGE_ORIGIN) * 7)
            val weeks = weekStart(model.anchor).daysUntil(target) / 7
            if (weeks != 0) model.anchor = model.anchor.shift(weeks * 7)
        }
    }

    // And the other direction, for when something outside the week moved the
    // anchor: the Today button, a date picked in the month view.
    LaunchedEffect(pager, origin) {
        snapshotFlow { weekStart(model.anchor) }.collect { w ->
            val page = (PAGE_ORIGIN + origin.daysUntil(w) / 7).coerceIn(0, PAGE_COUNT - 1)
            if (page != pager.settledPage && !pager.isScrollInProgress) pager.animateScrollToPage(page)
        }
    }

    // The rail has to start level with the grid, and the grid starts under a
    // band whose height belongs to DayHeading, not to this file. So it is
    // measured once and read back rather than assumed.
    var bandPx by remember { mutableIntStateOf(0) }
    val bandHeight = with(density) { bandPx.toDp() }

    // Land on the working day the first time a feed arrives, then leave the
    // scroll alone: re-running this would fight the user on every recomposition.
    var landed by remember { mutableStateOf(false) }
    LaunchedEffect(model.events) {
        if (landed || model.events.isEmpty()) return@LaunchedEffect
        // maxValue starts at Int.MAX_VALUE and only means anything once the
        // grid has been laid out, so the scroll waits for the first measure.
        snapshotFlow { vScroll.maxValue }.first { it != Int.MAX_VALUE }
        val y = window.offsetOf(openingMinute(byDay, week, dayCount), model.hourHeight) - Space.small
        vScroll.scrollTo(with(density) { y.roundToPx() }.coerceAtLeast(0))
        landed = true
    }

    val pressure = remember { ZoomPressure() }

    Row(
        modifier
            .fillMaxSize()
            .background(c.background)
            .pinchZoom { factor ->
                model.zoomBy(factor)
                // Read the zoom after applying it: once it has clamped, the
                // pinch is pushing against a wall and that is what builds the
                // pressure that changes view.
                val step = pressure.feed(
                    factor = factor,
                    atMin = model.hourHeight <= Grid.MIN_HOUR,
                    atMax = model.hourHeight >= Grid.MAX_HOUR,
                )
                when (step) {
                    -1 -> model.setView(ViewMode.Month)
                    1 -> model.setView(ViewMode.Day)
                }
            },
    ) {
        Column(Modifier.width(Grid.GUTTER).fillMaxHeight()) {
            Box(
                Modifier.fillMaxWidth().height(bandHeight),
                contentAlignment = Alignment.BottomEnd,
            ) {
                if (allDay) {
                    // Names the strip beside it. Short, because the gutter is
                    // sized for a clock and not for a sentence.
                    Text(
                        "all",
                        color = c.inkMuted,
                        fontSize = Type.small,
                        maxLines = 1,
                        modifier = Modifier.padding(end = Space.tight, bottom = Space.unit),
                    )
                }
            }
            Hairline()
            Box(Modifier.weight(1f).clipToBounds()) {
                // The clock does not page, so it tracks the shared scroll by
                // hand instead of living inside a scroller of its own.
                HourGutter(
                    window = window,
                    hourHeight = hourHeight,
                    modifier = Modifier.offset { IntOffset(0, -vScroll.value) },
                )
            }
        }

        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            beyondViewportPageCount = 1,
            key = { it },
        ) { page ->
            WeekPage(
                week = origin.shift((page - PAGE_ORIGIN) * 7),
                dayCount = dayCount,
                columnWidth = columnWidth,
                window = window,
                hourHeight = hourHeight,
                byDay = byDay,
                conflicts = conflicts,
                palette = palette,
                showAllDay = allDay,
                vScroll = vScroll,
                hScroll = hScroll,
                sideways = sideways,
                onBand = { if (it != bandPx) bandPx = it },
                onPick = { model.picked = it },
                onOpenDay = {
                    model.select(it)
                    model.setView(ViewMode.Day)
                },
                onToday = { model.goToday() },
            )
        }
    }
}

/**
 * One week.
 *
 * The heading band sits above the scroller so it stays put while the grid
 * moves, and shares the sideways scroll state with the grid so the two never
 * come apart. Every page draws on the window it was handed rather than one of
 * its own, which is what keeps the page sliding in level with the page sliding
 * out, and what stops two pages arguing over the shared scroll's extent.
 */
@Composable
private fun WeekPage(
    week: LocalDate,
    dayCount: Int,
    columnWidth: Dp,
    window: DayWindow,
    hourHeight: Float,
    byDay: Map<LocalDate, List<CalendarEvent>>,
    conflicts: Set<Long>,
    palette: CoursePalette,
    showAllDay: Boolean,
    vScroll: ScrollState,
    hScroll: ScrollState,
    sideways: Boolean,
    onBand: (Int) -> Unit,
    onPick: (CalendarEvent) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onToday: () -> Unit,
) {
    val days = remember(week, dayCount) { List(dayCount) { week.shift(it) } }
    val reference = today()
    val minute = nowMinutes()
    val slide = if (sideways) Modifier.horizontalScroll(hScroll) else Modifier

    // The long name only earns its place when the column can hold it with room
    // to spare, so the threshold comes from the longest name actually on
    // screen. Every page shows the same weekdays, so every page agrees.
    val longest = days.maxOf { longWeekday(it.dayOfWeek).length }
    val fullNames = columnWidth >= LABEL_CHAR * longest + Space.large

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().onSizeChanged { onBand(it.height) }) {
            Row(slide) {
                days.forEach { d ->
                    DayHeading(
                        label = if (fullNames) longWeekday(d.dayOfWeek) else weekdayShort(d.dayOfWeek),
                        number = d.day.toString(),
                        isToday = d == reference,
                        // Tapping a day is the way into it, so the day view
                        // needs no button of its own.
                        modifier = Modifier.width(columnWidth).clickable { onOpenDay(d) },
                    )
                }
            }
            if (showAllDay) {
                Row(slide) {
                    days.forEach { d ->
                        AllDayCell(
                            entries = byDay[d].orEmpty().filter { it.allDay },
                            palette = palette,
                            width = columnWidth,
                            onPick = onPick,
                        )
                    }
                }
            }
        }
        Hairline()
        Box(
            Modifier
                .weight(1f)
                .clipToBounds()
                .verticalScroll(vScroll)
                .then(slide),
        ) {
            WeekGrid(
                days = days,
                columnWidth = columnWidth,
                window = window,
                hourHeight = hourHeight,
                byDay = byDay,
                conflicts = conflicts,
                palette = palette,
                reference = reference,
                minute = minute,
                onPick = onPick,
                onToday = onToday,
            )
        }
    }
}

/**
 * The grid itself, drawn back to front: today's wash, the hour rules, the
 * separators, the events, then the now line. Everything that repeats per day
 * is placed at index times the column width, so adding the weekend changes a
 * count and nothing else.
 */
@Composable
private fun WeekGrid(
    days: List<LocalDate>,
    columnWidth: Dp,
    window: DayWindow,
    hourHeight: Float,
    byDay: Map<LocalDate, List<CalendarEvent>>,
    conflicts: Set<Long>,
    palette: CoursePalette,
    reference: LocalDate,
    minute: Int,
    onPick: (CalendarEvent) -> Unit,
    onToday: () -> Unit,
) {
    val c = skema
    val height = window.height(hourHeight)
    val marked = days.indexOf(reference)

    Box(Modifier.width(columnWidth * days.size).height(height)) {
        // Under the rules rather than over them, so today reads as a lit
        // column and not as a column with its hours rubbed out.
        if (marked >= 0) {
            Box(
                Modifier
                    .offset(x = columnWidth * marked)
                    .width(columnWidth)
                    .fillMaxHeight()
                    // A wash, not a highlight. accentSoft at full strength
                    // competes with the course fills, which are the thing on
                    // this screen actually carrying meaning, and a solid red
                    // column reads as an alert rather than as "today".
                    .background(c.accentSoft.copy(alpha = 0.45f)),
            )
        }

        HourRules(window, hourHeight)

        for (i in 1 until days.size) {
            Box(
                Modifier
                    .offset(x = columnWidth * i)
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(c.hairline),
            )
        }

        Row {
            days.forEach { d ->
                DayColumn(
                    date = d,
                    events = byDay[d].orEmpty(),
                    window = window,
                    hourHeight = hourHeight,
                    columnWidth = columnWidth,
                    palette = palette,
                    conflicts = conflicts,
                    reference = reference,
                    minute = minute,
                    onPick = onPick,
                    onToday = onToday,
                )
            }
        }

        if (marked >= 0) {
            Box(
                Modifier
                    .offset(x = columnWidth * marked)
                    .width(columnWidth)
                    .height(height),
            ) {
                NowLine(window, hourHeight, minute)
            }
        }
    }
}

@Composable
private fun DayColumn(
    date: LocalDate,
    events: List<CalendarEvent>,
    window: DayWindow,
    hourHeight: Float,
    columnWidth: Dp,
    palette: CoursePalette,
    conflicts: Set<Long>,
    reference: LocalDate,
    minute: Int,
    onPick: (CalendarEvent) -> Unit,
    onToday: () -> Unit,
) {
    val timed = remember(events) { events.filterNot { it.allDay } }
    val placed = remember(timed) {
        Lanes.layout(timed, { it.startMinute() }, { it.endMinute() })
    }

    // Lanes divide the column's inside, not its edge to edge width, so a block
    // never sits on the separator it shares with the next day.
    val air = columnAir(columnWidth)
    val inner = columnWidth - air * 2

    Box(Modifier.width(columnWidth).height(window.height(hourHeight))) {
        // Empty grid takes the double tap. The blocks are hit first because
        // they are drawn on top of this, so their own taps still land.
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { onToday() })
            },
        )

        placed.forEach { p ->
            val e = p.item
            val span = window.spanOf(p.startMinute, p.endMinute, hourHeight)
            val laneWidth = (inner * p.widthFraction - LANE_GAP).coerceAtLeast(1.dp)
            EventBlock(
                event = e,
                tint = palette.tint(e.courseCode),
                height = span,
                width = laneWidth,
                conflict = e.startEpochSeconds in conflicts,
                dimmed = isPast(date, e, reference, minute),
                modifier = Modifier
                    .offset(
                        x = air + inner * p.offsetFraction,
                        y = window.offsetOf(p.startMinute, hourHeight),
                    )
                    .width(laneWidth)
                    .height(span)
                    // Clipped before the click so the press stays inside the
                    // squircle instead of flashing its bounding box.
                    .clip(SquircleShape(Radius.chip))
                    .clickable { onPick(e) },
            )
        }
    }
}

/**
 * A whole day entry has no honest position on a time grid, so it gets a chip
 * over the column instead of a block inside it. One chip per day: a second
 * entry on the same day is counted rather than stacked, because a strip whose
 * height depends on the week would move the grid under the user's finger every
 * time they paged.
 *
 * The cell always draws a line of text, blank when there is nothing, so every
 * day in the strip is the same height whether it has an entry or not.
 */
@Composable
private fun AllDayCell(
    entries: List<CalendarEvent>,
    palette: CoursePalette,
    width: Dp,
    onPick: (CalendarEvent) -> Unit,
) {
    val c = skema
    val first = entries.firstOrNull()
    val tint = palette.tint(first?.courseCode)
    val extra = entries.size - 1

    // A space, not an empty string, so an empty day still measures a line and
    // the strip keeps one height across the week.
    val label = if (first == null) {
        " "
    } else {
        val text = textFor(first.summary, first.location.courseName, first.courseCode, first.fromRoomBooking)
        if (extra > 0) text.title + " +" + extra else text.title
    }

    Box(Modifier.width(width).padding(horizontal = columnAir(width), vertical = 2.dp)) {
        Text(
            label,
            color = if (first == null) c.inkMuted else tint.ink,
            fontSize = Type.small,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (first == null) {
                        Modifier
                    } else {
                        Modifier
                            .clip(SquircleShape(Radius.chip))
                            .background(tint.fill)
                            .clickable { onPick(first) }
                    },
                )
                .padding(horizontal = Space.tight, vertical = 1.dp),
        )
    }
}

@Composable
private fun Hairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(skema.hairline))
}

/**
 * The hour window every visible page is drawn on.
 *
 * It covers the week either side as well as the anchor week, so the pages you
 * can see mid swipe sit on the same rules as the one you are on, and so the
 * shared vertical scroll has one extent rather than one per page. Consecutive
 * teaching weeks are near identical, so in practice this is the anchor week's
 * own window.
 */
private fun windowAround(byDay: Map<LocalDate, List<CalendarEvent>>, week: LocalDate): DayWindow {
    val span = (-NEIGHBOUR_WEEKS * 7) until ((NEIGHBOUR_WEEKS + 1) * 7)
    return DayWindow.of(span.flatMap { byDay[week.shift(it)].orEmpty() })
}

/**
 * Whether the all day strip is worth its height. Asked over the same three
 * weeks as the window, and for the same reason: the strip appearing halfway
 * through a swipe would shorten one page's grid and not the other's.
 */
private fun anyAllDayAround(
    byDay: Map<LocalDate, List<CalendarEvent>>,
    week: LocalDate,
    dayCount: Int,
): Boolean = (-NEIGHBOUR_WEEKS..NEIGHBOUR_WEEKS).any { w ->
    // Only the days actually drawn: a Saturday marker must not buy the strip
    // for a five day week that never shows Saturday.
    (0 until dayCount).any { d -> byDay[week.shift(w * 7 + d)].orEmpty().any { it.allDay } }
}

/** Where the grid should open: the week's first class, or the working day if it has none. */
private fun openingMinute(
    byDay: Map<LocalDate, List<CalendarEvent>>,
    week: LocalDate,
    dayCount: Int,
): Int = (0 until dayCount)
    .flatMap { byDay[week.shift(it)].orEmpty() }
    .filterNot { it.allDay }
    .minOfOrNull { it.startMinute() }
    ?: WORKING_START

private fun isPast(date: LocalDate, event: CalendarEvent, reference: LocalDate, minute: Int): Boolean =
    date < reference || (date == reference && event.endMinute() <= minute)

/** "Monday" from the enum, so no second table of weekday names has to be kept in step. */
private fun longWeekday(d: DayOfWeek): String =
    d.name.lowercase().replaceFirstChar { it.uppercase() }
