package com.marcusrosado.skemafordtu.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.marcusrosado.skemafordtu.data.ViewMode
import com.marcusrosado.skemafordtu.ics.CalendarEvent
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.monthsUntil
import kotlinx.datetime.plus

/**
 * A month, and under it the day you have your finger on.
 *
 * A university term is the same five days repeating, so a month of it carries
 * almost no information that the week does not carry better. Its job is to
 * let you see where you are in the term and to pick a day, and the grid is
 * built to be cheap and quiet: no titles, no times, course colour and a count
 * as the whole vocabulary. What the grid does not say, the day under it does.
 * Tap a day and its classes appear below as a timeline; tap the day you are
 * already on and the week opens around it.
 */

/** Months either side of the one the view opens on. A century each way. */
private const val MONTH_SPAN = 1_200
private const val MONTH_PAGES = MONTH_SPAN * 2 + 1

/** Not a layout choice. Weeks have seven days. */
private const val WEEK = 7

/** A dot, and the gap after it. Together they are one slot in a cell's dot row. */
private val DOT = Space.tight
private val DOT_GAP = Space.unit / 2

/**
 * The clash mark, twice the dot.
 *
 * A warning triangle drawn at 6dp is a smudge, and this is the one thing in a
 * month cell worth interrupting the pattern for. It is also the tallest thing
 * the dot row can hold, so it is what the row reserves, which is what keeps
 * every date number in a week sitting on the same line.
 */
private val MARK = DOT * 2

/**
 * One week of the grid, as tall as its number's disc and its dot row and the
 * air around them. Fixed rather than a share of the height, because the day
 * under the grid is what the rest of the height is for.
 */
private val ROW = DAY_DISC + Space.unit + MARK + Space.unit * 2

/**
 * Space kept back for the "+n" when the dots overflow.
 *
 * Compose cannot tell you how wide a string will be before it lays it out, and
 * subcomposing every cell to find out would be an absurd price for a label
 * that is at most three characters. This is that label's width at
 * [Type.small], reserved rather than positioned, and generous enough that a
 * two digit overflow still fits.
 */
private val OVERFLOW_WIDTH = 24.dp

@Composable
fun MonthView(model: SkemaModel, palette: CoursePalette, modifier: Modifier = Modifier) {
    val c = skema

    // One walk of the feed for the whole grid, rather than one per cell.
    val byDay = remember(model.events, model.settings.collapseDuplicates) { model.byDay }
    val conflicts = remember(model.events) { model.conflicts }

    // Anchored to this month, so a restored pager state and a freshly computed
    // origin still agree on which page is which month.
    val origin = firstOfMonth(today())
    val state = rememberPagerState(
        initialPage = MONTH_SPAN + origin.monthsUntil(firstOfMonth(model.anchor)),
        pageCount = { MONTH_PAGES },
    )
    val pressure = remember { ZoomPressure() }

    LaunchedEffect(state, origin) {
        snapshotFlow { state.settledPage }.collect { page ->
            val month = origin.plus(page - MONTH_SPAN, DateTimeUnit.MONTH)
            // The anchor is what the shell's headline reads, and a month view
            // is anchored to the month, not to a day inside it.
            if (firstOfMonth(model.anchor) != month) model.anchor = month
        }
    }

    LaunchedEffect(model.anchor) {
        val target = MONTH_SPAN + origin.monthsUntil(firstOfMonth(model.anchor))
        if (target in 0 until MONTH_PAGES && target != state.currentPage) state.scrollToPage(target)
    }

    HorizontalPager(
        state = state,
        key = { it },
        modifier = modifier
            .background(c.background)
            .pinchZoom { factor ->
                // The month has no hour height, so it is sitting at both ends
                // of a zoom it does not have; the pressure alone decides, and
                // model.hourHeight is deliberately neither read nor written
                // here. Only the outward pinch has anywhere to go: a positive
                // step is one view narrower, which is the week. A pinch the
                // other way returns -1 and is dropped, because nothing in the
                // app is wider than a month.
                if (pressure.feed(factor, atMin = true, atMax = true) > 0) {
                    model.setView(ViewMode.Week)
                }
            },
    ) { page ->
        MonthPage(
            month = origin.plus(page - MONTH_SPAN, DateTimeUnit.MONTH),
            byDay = byDay,
            conflicts = conflicts,
            palette = palette,
            selected = model.selected,
            // While a class is picked the shell's detail pane has the lower
            // half, so the day list steps aside rather than being squeezed.
            showDay = model.picked == null,
            onPick = { date ->
                // The first tap on a day is a question, answered under the
                // grid. A second tap on the same day is the way into it: the
                // week opens around it, which is where a day is read.
                if (date == model.selected) model.setView(ViewMode.Week) else model.select(date)
            },
            onEvent = { model.picked = it },
        )
    }
}

@Composable
private fun MonthPage(
    month: LocalDate,
    byDay: Map<LocalDate, List<CalendarEvent>>,
    conflicts: Set<Long>,
    palette: CoursePalette,
    selected: LocalDate,
    showDay: Boolean,
    onPick: (LocalDate) -> Unit,
    onEvent: (CalendarEvent) -> Unit,
) {
    val c = skema

    // The grid starts on the Monday of the week the 1st falls in and runs in
    // whole weeks to the end of the month, so the leading and trailing days
    // fall out of the arithmetic instead of being a special case.
    val gridStart = weekStart(month)
    val next = month.plus(1, DateTimeUnit.MONTH)
    val span = gridStart.daysUntil(next)
    val weeks = (span + WEEK - 1) / WEEK
    val reference = today()

    // With the day in a rail beside the calendar the grid has the height to
    // itself, and its weeks share it the way they used to.
    val aside = LocalDayAside.current

    Column(Modifier.fillMaxSize().padding(horizontal = Space.small)) {
        Row(Modifier.fillMaxWidth().padding(bottom = Space.unit)) {
            // Labelled off the grid's own first week, so the header cannot
            // drift out of step with the columns under it.
            for (i in 0 until WEEK) {
                Text(
                    weekdayShort(gridStart.shift(i).dayOfWeek),
                    modifier = Modifier.weight(1f),
                    color = c.inkMuted,
                    fontSize = Type.small,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))

        BoxWithConstraints(if (aside) Modifier.weight(1f).fillMaxWidth() else Modifier.fillMaxWidth()) {
            val cell = maxWidth / WEEK
            Column(if (aside) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {
                for (w in 0 until weeks) {
                    Row((if (aside) Modifier.weight(1f) else Modifier.height(ROW)).fillMaxWidth()) {
                        for (i in 0 until WEEK) {
                            val date = gridStart.shift(w * WEEK + i)
                            val events = byDay[date].orEmpty()
                            MonthCell(
                                date = date,
                                inMonth = date >= month && date < next,
                                isToday = date == reference,
                                isSelected = date == selected,
                                events = events,
                                clash = events.any { it.startEpochSeconds in conflicts },
                                palette = palette,
                                width = cell,
                                onOpen = { onPick(date) },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                }
            }
        }

        if (aside) return@Column

        // The day itself, in whatever height the grid left. It scrolls on its
        // own, so a day with nine things on it never pushes the grid up. It
        // leaves the way the detail pane arrives, folding into the grid's
        // bottom edge, so the swap reads as one pane giving way to another.
        AnimatedVisibility(
            visible = showDay,
            modifier = Modifier.weight(1f, fill = false),
            enter = expandVertically(tween(Motion.SHEET_IN, easing = Motion.arrive), expandFrom = Alignment.Top),
            exit = shrinkVertically(tween(Motion.SHEET_OUT, easing = Motion.leave), shrinkTowards = Alignment.Top),
            label = "day",
        ) {
            DayTimeline(
                date = selected,
                events = byDay[selected].orEmpty(),
                palette = palette,
                onPick = onEvent,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = Space.medium, bottom = Space.medium),
            )
        }
    }
}

/**
 * One day of the grid: its number, and what is on.
 *
 * The number wears the accent disc when it is the selected day, the one whose
 * classes are listed underneath. Today, when it is not selected, is the
 * accent as ink only: the disc is one mark and it means "this one", and two
 * discs in one grid would make the eye pick.
 *
 * The dot row keeps its height on a day with nothing on it, so the numbers
 * across a week still sit on one line rather than bobbing up wherever a day is
 * free. That reserved height is the clash mark's, the tallest thing the row
 * can hold, so a day with a clash does not shift its number either.
 */
@Composable
private fun MonthCell(
    date: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    events: List<CalendarEvent>,
    clash: Boolean,
    palette: CoursePalette,
    width: Dp,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = skema
    Column(
        modifier.clickable(onClick = onOpen).padding(vertical = Space.unit),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // The same disc DayHeading puts round today's number, so the marker
        // means one thing wherever it turns up.
        Box(
            Modifier
                .defaultMinSize(minWidth = DAY_DISC, minHeight = DAY_DISC)
                .background(if (isSelected) c.accent else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                date.day.toString(),
                color = when {
                    isSelected -> c.onAccent
                    isToday -> c.accent
                    inMonth -> c.ink
                    else -> c.inkMuted
                },
                fontSize = Type.normal,
                fontWeight = when {
                    isSelected || isToday -> FontWeight.SemiBold
                    inMonth -> FontWeight.Medium
                    else -> FontWeight.Normal
                },
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(Space.unit))
        Box(Modifier.height(MARK), contentAlignment = Alignment.Center) {
            if (events.isNotEmpty()) {
                DotRow(events, palette, width, dim = !inMonth, clash = clash)
            }
        }
    }
}

/**
 * What is on, at a glance.
 *
 * One dot per event in the course's own colour, in the order the day runs, so
 * a week of the same four courses looks like the same four colours in the same
 * order and anything unusual stands out as a break in the pattern. Titles are
 * not attempted: a 56dp cell holds about six characters, which is worse than
 * showing nothing.
 *
 * The dot is drawn in the rail colour rather than the soft one. A 6dp dot has
 * the same problem a 3dp rail has, which is that it needs saturation to
 * register at all; the soft wash sits at the same lightness as the background
 * and disappears.
 *
 * A day where two courses genuinely collide leads with the clash mark. It is
 * the one thing here worth more than a dot, and an icon says it where a colour
 * cannot: every other colour in the row already means a course.
 */
@Composable
private fun DotRow(
    events: List<CalendarEvent>,
    palette: CoursePalette,
    width: Dp,
    dim: Boolean,
    clash: Boolean,
) {
    val c = skema
    // The mark takes its slot out of the row before the dots are counted, so
    // it displaces a dot rather than pushing one off the edge.
    val room = width - Space.unit * 2 - (if (clash) MARK + DOT_GAP else 0.dp)
    val slot = DOT + DOT_GAP

    // How many dots the cell can actually hold, and how many it can hold once
    // the "+n" has taken its share. Both from the measured width, so a wider
    // frame simply shows more dots before it starts counting.
    val fits = (room / slot).toInt()
    val overflowing = events.size > fits
    val shown = if (overflowing) ((room - OVERFLOW_WIDTH) / slot).toInt() else events.size
    val count = shown.coerceIn(0, events.size)

    Row(
        horizontalArrangement = Arrangement.spacedBy(DOT_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (clash) {
            Glyph(
                Ico.Clash,
                tint = if (dim) c.accentStrong.copy(alpha = 0.4f) else c.accentStrong,
                size = MARK,
                description = "Clashing events",
            )
        }
        events.take(count).forEach { event ->
            val rail = palette.tint(event.courseCode).rail
            Box(
                Modifier
                    .size(DOT)
                    .background(if (dim) rail.copy(alpha = 0.4f) else rail, pillShape()),
            )
        }
        if (count < events.size) {
            Text(
                "+" + (events.size - count),
                color = c.inkMuted,
                fontSize = Type.small,
                // Set on the row's own height, so the label sits inside the
                // strip the dots reserve instead of spilling into the week
                // below, which drew over its bottom half.
                lineHeight = with(LocalDensity.current) { MARK.toSp() },
                maxLines = 1,
            )
        }
    }
}

/** The 1st of whatever month this date is in. The page's identity. */
private fun firstOfMonth(date: LocalDate): LocalDate = date.shift(-(date.day - 1))
