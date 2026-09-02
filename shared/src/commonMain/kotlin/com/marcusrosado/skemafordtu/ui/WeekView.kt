package com.marcusrosado.skemafordtu.ui

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
import com.marcusrosado.skemafordtu.data.ViewMode
import com.marcusrosado.skemafordtu.ics.CalendarEvent
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlin.math.roundToInt

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
 *
 * The grid plots the whole day, midnight to midnight, on every week. Which
 * part of it you are looking at is the scroll's job, and how much of it fits is
 * the zoom's job. The pinch drives both axes: one hour taller is one column
 * wider, so a week too narrow to say anything can be widened until it does.
 */

/** Weeks reachable either side of the week the view opened on. Fifty years each way. */
private const val PAGE_REACH = 2_600
private const val PAGE_ORIGIN = PAGE_REACH
private const val PAGE_COUNT = PAGE_REACH * 2 + 1

/** How many weeks either side are asked about the all day strip. See [anyAllDayAround]. */
private const val NEIGHBOUR_WEEKS = 1

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
 * How much of a column the cascade spends showing what is underneath the top
 * block, as a share of the column's inside.
 *
 * Split by how many blocks are stacked, so the block on top is the same width
 * whether two things clash or six, and floored at [RAIL_REVEAL] so a deep pile
 * still shows every rail it covers.
 */
private const val CASCADE_REVEAL = 0.26f

/** The least a covered block shows of itself: its 3dp rail and enough fill to read as a colour. */
private val RAIL_REVEAL = 8.dp

/** A cascaded block cuts the one under it with the page colour rather than with a shadow. */
private val CASCADE_SEAM = 2.dp

/**
 * A line of [Type.small] as a multiple of the type size.
 *
 * Compose measures text properly at draw time; the cascade needs the number in
 * advance, to know how far to push a block whose start another block is
 * sitting on. Derived from the type rather than fixed in dp, so a larger system
 * font scale moves it too.
 */
private const val LINE_HEIGHT = 1.35f

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
    val hourHeight = model.hourHeight

    // The zoom widens the week as well as heightening it.
    //
    // The complaint this answers is that five columns on a folded phone are
    // 73dp each, which holds a colour and not a word, and no amount of vertical
    // zoom fixes that. So the pinch drives both axes off one rule: an hour tall
    // is a day wide, a square cell. At the 64dp default that asks for a 64dp
    // column and five still fit on the narrowest screen this runs on, so the
    // five day week survives untouched; past that the columns take the width
    // they need and the week scrolls sideways for the days that no longer fit.
    // Pinching back out puts them back. Nothing here is a table of zoom levels:
    // it is one division, and the day count falls out of it.
    val minColumn = maxOf(Grid.MIN_COLUMN, hourHeight.dp)
    val onScreen = ((frame.width - Grid.GUTTER) / minColumn).toInt().coerceIn(1, dayCount)
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

    // Midnight to midnight, every week, so the same 13:00 is at the same height
    // whatever else is in the feed. Every page shares it, which is also what
    // keeps the page sliding in level with the page sliding out and stops two
    // pages arguing over the extent of the shared scroll.
    val window = DayWindow.FULL
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
    // anchor: the Today button, a date picked in the month view. This is the
    // one animation the week runs, and it earns it: a week that jumped would
    // not say which way it went.
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

    // Land on the part of the day worth looking at.
    //
    // A 24 hour grid opens at midnight unless it is told otherwise, and
    // midnight is never what the app was opened for. This runs again while the
    // feed is empty, because the first landing then has nothing to aim at, and
    // stops for good once it has landed on real events: a sync arriving later
    // must not yank the scroll out from under a finger.
    var landed by remember { mutableStateOf(false) }
    LaunchedEffect(model.events) {
        if (landed) return@LaunchedEffect
        // maxValue starts at Int.MAX_VALUE and only means anything once the
        // grid has been laid out, so the scroll waits for the first measure.
        snapshotFlow { vScroll.maxValue }.first { it != Int.MAX_VALUE }
        val y = window.offsetOf(openingMinute(byDay, week, dayCount, today(), nowMinutes()), hourHeight)
        vScroll.scrollTo(with(density) { y.roundToPx() }.coerceAtLeast(0))
        landed = model.events.isNotEmpty()
    }

    val pressure = remember { ZoomPressure() }

    // Where the vertical scroll wants to be, kept apart from where it is.
    //
    // A scroller clamps to the height it was last measured at, and during a
    // zoom in that height is a frame behind and shorter than the grid is about
    // to be, so asking it to scroll past the old bottom silently loses the
    // difference. Every lost pixel is the content sliding under the fingers,
    // which is the "stuck" feeling. So the target is carried here, applied
    // straight away for the frame the gesture is on, and applied again below
    // once the layout has caught up and the clamp is honest.
    var wanted by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(hourHeight) {
        val to = wanted ?: return@LaunchedEffect
        vScroll.scrollTo(to.roundToInt().coerceAtLeast(0))
        wanted = null
    }

    // Changing how many days fit changes how wide each one is, by exactly the
    // inverse. Multiplying the sideways scroll by the same inverse leaves
    // whichever day was against the left edge against the left edge, so
    // zooming does not also walk the week sideways.
    var lastOnScreen by remember { mutableIntStateOf(onScreen) }
    LaunchedEffect(onScreen) {
        val was = lastOnScreen
        lastOnScreen = onScreen
        if (was != onScreen && hScroll.value > 0) {
            hScroll.scrollTo((hScroll.value.toFloat() * was / onScreen).roundToInt())
        }
    }

    Row(
        modifier
            .fillMaxSize()
            .background(c.background)
            .pinchZoom { factor ->
                val before = model.hourHeight
                model.zoomBy(factor)
                val after = model.hourHeight

                // Anchor the zoom on the middle of what is on screen.
                //
                // The content point at the centre of the viewport is
                // value + viewport / 2, and scaling the hour scales that point
                // by the same amount, so the offset that leaves it where it was
                // is (value + viewport / 2) * factor - viewport / 2. The
                // centre is the honest guess at where the fingers are: the
                // gesture reports how much it pinched, not where.
                if (after != before) {
                    val middle = vScroll.viewportSize / 2f
                    val from = wanted ?: vScroll.value.toFloat()
                    val to = ((from + middle) * (after / before) - middle).coerceAtLeast(0f)
                    wanted = to
                    vScroll.dispatchRawDelta(to - vScroll.value)
                }

                // Read the zoom after applying it: once it has clamped, the
                // pinch is pushing against a wall and that is what builds the
                // pressure that changes view.
                val step = pressure.feed(
                    factor = factor,
                    atMin = model.atMinZoom,
                    atMax = model.atMaxZoom,
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
 * come apart. Every page draws on the window and the column width it was
 * handed rather than ones of its own, which is what keeps the page sliding in
 * level with the page sliding out.
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

/**
 * One day's blocks, arranged one of two ways.
 *
 * Splitting the column into equal lanes is the honest arrangement: two events
 * at 10:00 get half the width each and neither lies about anything. It is also
 * the arrangement that fails hardest on the screen this app is for. A folded
 * Pixel Fold gives a day column 73dp; split two ways that is 36dp a lane,
 * which holds no word at all, so a clash renders as two anonymous coloured
 * stripes and the only way to find out what either of them is, is to tap them
 * both. Two events you cannot read are worse than one you can.
 *
 * So when [sideBySideFits] says the lanes have stopped holding words, the
 * column cascades instead. Each block keeps nearly the whole width and is
 * inset from the left by its depth in the clash, later starts drawn over
 * earlier ones. What that buys is the earlier event's header, its time and its
 * name, sitting in the clear above the later event's top edge, and a slice of
 * its left edge, including its coloured rail, running the whole way down. Both
 * events say what they are.
 *
 * What it costs is width, which was never true anyway once the events were
 * sharing a column, and, for two events starting in the very same minute, a
 * little of the start time: the upper block would cover the lower one's header
 * exactly, so it is pushed down by one header and shortened by the same amount
 * so that its end stays where it belongs. That push is capped at what the
 * block can spare, so a short event never gets pushed past its own bottom. The
 * clock printed inside the block is still the real start, so the only thing
 * bent is the position, and only for the case where the honest position would
 * have hidden the event entirely.
 *
 * The choice is a formula, not a device check, so it moves with the zoom: pinch
 * a clashing day wide enough and the columns go back to honest lanes on their
 * own, which is the same gesture that made the week readable in the first
 * place.
 */
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
    val c = skema
    val density = LocalDensity.current
    val timed = remember(events) { events.filterNot { it.allDay } }

    // Both arrangements, cached on the day's events. Which one is drawn is a
    // question about width and width moves with the zoom, so the decision sits
    // outside the remember and the layouts stay off the pinch's path.
    val placed = remember(timed) { Lanes.layout(timed, { it.startMinute() }, { it.endMinute() }) }
    val stacked = remember(timed) { Lanes.cascade(timed, { it.startMinute() }, { it.endMinute() }) }

    // Lanes divide the column's inside, not its edge to edge width, so a block
    // never sits on the separator it shares with the next day.
    val air = columnAir(columnWidth)
    val inner = columnWidth - air * 2

    // One arrangement per column, decided by the worst clash in it. Deciding
    // per clash instead would put honest lanes at 10:00 and a cascade at 14:00
    // in the same day, and the two read as different kinds of event rather
    // than as the same events arranged differently.
    val widest = placed.maxOfOrNull { it.lanes } ?: 1
    val cascading = !sideBySideFits(inner, widest)

    // A header is what an event leads with, its time and its name, plus the
    // padding above them. Taken from the type scale for the same reason
    // EventBlock takes its own thresholds from it: the number has to move when
    // the system font scale does.
    val header = with(density) { Type.small.toDp() } * (LINE_HEIGHT * 2) + Space.tight

    Box(Modifier.width(columnWidth).height(window.height(hourHeight))) {
        // Empty grid takes the double tap. The blocks are hit first because
        // they are drawn on top of this, so their own taps still land.
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { onToday() })
            },
        )

        if (cascading) {
            // In list order, which is start order, so the later event is drawn
            // over the earlier one and the earlier one's header stays clear.
            stacked.forEach { s ->
                val e = s.item
                val span = window.spanOf(s.startMinute, s.endMinute, hourHeight)

                // Every block stacked on this one takes a slice of its left
                // edge, so the slice is the budget divided by how many are
                // stacked. The top block ends up the same width whatever the
                // count, and the ones under it are revealed evenly.
                val steps = (s.cluster - 1).coerceAtLeast(1)
                val slice = (inner * CASCADE_REVEAL / steps).coerceAtLeast(RAIL_REVEAL)
                val left = air + slice * s.depth
                val width = (columnWidth - air - left).coerceAtLeast(RAIL_REVEAL)

                // Only an identical start hides a header, and only then does
                // the top move. The bottom never does, so the block gives back
                // in height exactly what it took in position.
                val push = (header * s.headerSteps).coerceAtMost((span - header).coerceAtLeast(0.dp))
                val top = window.offsetOf(s.startMinute, hourHeight) + push
                val height = span - push

                if (s.depth > 0) {
                    // A seam in the page colour along the two edges that lie
                    // over the block underneath. Two events of the same course
                    // colour would otherwise merge into one shape.
                    Box(
                        Modifier
                            .offset(x = left - CASCADE_SEAM, y = top - CASCADE_SEAM)
                            .width(width + CASCADE_SEAM)
                            .height(height + CASCADE_SEAM)
                            .clip(SquircleShape(Radius.chip))
                            .background(c.background),
                    )
                }

                EventBlock(
                    event = e,
                    tint = palette.tint(e.courseCode),
                    height = height,
                    width = width,
                    conflict = e.startEpochSeconds in conflicts,
                    dimmed = isPast(date, e, reference, minute),
                    modifier = Modifier
                        .offset(x = left, y = top)
                        .width(width)
                        .height(height)
                        // Clipped before the click so the press stays inside
                        // the squircle instead of flashing its bounding box.
                        .clip(SquircleShape(Radius.chip))
                        .clickable { onPick(e) },
                )
            }
        } else {
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
                        .clip(SquircleShape(Radius.chip))
                        .clickable { onPick(e) },
                )
            }
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
 * Whether the all day strip is worth its height. Asked over the week either
 * side as well as the anchor week, because the strip appearing halfway through
 * a swipe would shorten one page's grid and not the other's.
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

/**
 * Where the grid opens.
 *
 * The week the app opens on almost always contains today, and then the thing
 * you opened it to see is what is left of today, so the target is the next
 * event that has not finished yet. Every other week has no "now" in it, so it
 * opens on its first class instead. [DayWindow.openingMinute] owns both the
 * fallback and the margin above the target, which is why the times go through
 * it rather than being worked out again here.
 */
private fun openingMinute(
    byDay: Map<LocalDate, List<CalendarEvent>>,
    week: LocalDate,
    dayCount: Int,
    reference: LocalDate,
    minute: Int,
): Int {
    val days = List(dayCount) { week.shift(it) }
    val whole = days.flatMap { byDay[it].orEmpty() }
    if (reference !in days) return DayWindow.openingMinute(whole)
    // An evening with nothing left in it falls back to the week, so the grid
    // still opens on teaching hours rather than on the small hours.
    val ahead = byDay[reference].orEmpty().filter { !it.allDay && it.endMinute() > minute }
    return DayWindow.openingMinute(ahead.ifEmpty { whole })
}

private fun isPast(date: LocalDate, event: CalendarEvent, reference: LocalDate, minute: Int): Boolean =
    date < reference || (date == reference && event.endMinute() <= minute)

/** "Monday" from the enum, so no second table of weekday names has to be kept in step. */
private fun longWeekday(d: DayOfWeek): String =
    d.name.lowercase().replaceFirstChar { it.uppercase() }
