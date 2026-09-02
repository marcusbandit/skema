package com.marcusrosado.skemafordtu.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.plus
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * The jump picker: a month and a year, both written out.
 *
 * Nothing in here is abbreviated. "Jun" and "Jul" differ by one letter in the
 * middle of a word, which is the worst possible place to put the difference,
 * and this panel exists precisely so a month can be chosen by reading it. The
 * grid is sized from the longest name there is rather than from a column count
 * picked in advance, so the full word always has room.
 *
 * The panel owns its own way in and out. The caller composes it when it is
 * wanted and drops it when it is done, so an exit animation only exists if the
 * panel asks for the time itself: it goes visible on the first frame, and a
 * dismissal plays the slide before it tells the caller that it is over. The
 * timings are the detail sheet's, read off [Motion], so the two panels arrive
 * and leave as the same object.
 *
 * Three ways out, none of them a button: push the panel down, tap the page
 * behind it, or press back. The close cross that used to sit in the corner
 * said what the gesture already says and took a thumb across the panel to
 * reach, so it has gone the way the detail sheet's did.
 */
@Composable
fun MonthYearSheet(
    initial: LocalDate,
    minYear: Int,
    maxYear: Int,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = skema
    val scope = rememberCoroutineScope()
    val feel = LocalFeel.current
    val reduced = reducedMotion()
    val flick = with(LocalDensity.current) { FLICK.toPx() }

    val years = remember(minYear, maxYear) { (minYear..maxYear.coerceAtLeast(minYear)).toList() }
    var year by remember { mutableStateOf(initial.year.coerceIn(years.first(), years.last())) }

    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    // True from the instant a dismissal starts rather than when it finishes.
    // The slide out has to play before the caller drops the panel, and during
    // those milliseconds nothing here may start a second exit over the top of
    // the first.
    var leaving by remember { mutableStateOf(false) }

    // How far the panel has been pushed below its resting place, and how tall
    // it turned out to be, which is what the push is measured against.
    val pushed = remember { Animatable(0f) }
    var panelPx by remember { mutableIntStateOf(0) }

    fun close() {
        if (leaving) return
        leaving = true
        shown = false
        scope.launch {
            delay(if (reduced) 0L else Motion.SHEET_OUT.toLong())
            onDismiss()
        }
    }

    // Back closes the picker and stops there: the month behind it does not
    // change and the calendar under it does not move. It goes through close(),
    // so the panel leaves down through the bottom of the screen the way a
    // dragged one does rather than blinking out. Disabled the moment it starts
    // leaving, so a second press falls through to the screen behind instead of
    // being swallowed by a panel already on its way out.
    BackHandler(enabled = !leaving) { close() }

    // The jump lands straight away and the panel leaves afterwards, so the
    // month behind is already the month asked for while the sheet is still
    // sliding off it.
    fun choose(monthNumber: Int) {
        onPick(jumpTo(year, monthNumber, initial.day))
        close()
    }

    // Downwards only. There is nothing above the panel to pull into view, and
    // a drag that can go the wrong way offers a move that does nothing.
    val drag = rememberDraggableState { dy ->
        scope.launch { pushed.snapTo((pushed.value + dy).coerceAtLeast(0f)) }
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = shown,
            enter = fadeIn(tween(Motion.SHEET_FADE)),
            exit = fadeOut(tween(Motion.SHEET_FADE)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(c.ink.copy(alpha = SCRIM_MAX))
                    .clickable(onClick = { close() }),
            )
        }

        AnimatedVisibility(
            visible = shown,
            modifier = Modifier.align(Alignment.BottomCenter),
            // The panel is exactly as tall as its content, so its own height
            // is the distance from the bottom edge of the frame: it enters
            // from where it looks like it came from.
            enter = slideInVertically(tween(Motion.SHEET_IN, easing = Motion.arrive)) {
                if (reduced) 0 else it
            } + fadeIn(tween(Motion.SHEET_FADE)),
            // Leaves from wherever the drag left it, so a dismissal that
            // began as a gesture finishes as one continuous movement instead
            // of restarting from the panel's full height.
            exit = slideOutVertically(tween(Motion.SHEET_OUT, easing = Motion.leave)) {
                if (reduced) 0 else (it - pushed.value).roundToInt().coerceAtLeast(0)
            } + fadeOut(tween(Motion.SHEET_FADE)),
            label = "jump",
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, pushed.value.roundToInt()) }
                    .padding(horizontal = Space.small)
                    .padding(bottom = Space.small)
                    .clip(SquircleShape(Radius.sheet))
                    .background(c.surface)
                    .onSizeChanged { panelPx = it.height }
                    // The whole panel is the grab surface, not only the strip
                    // drawn at the top of it. A month cell takes a tap and
                    // leaves a drag alone, and the year row runs the other
                    // way, so nothing inside is competing for this.
                    .draggable(
                        state = drag,
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity ->
                            // Velocity beats position past the flick, so a
                            // throw goes where it was aimed however far the
                            // panel actually travelled.
                            val far = panelPx > 0 && pushed.value > panelPx * DISMISS_SHARE
                            if (velocity > flick || far) {
                                feel.thud()
                                close()
                            } else {
                                pushed.animateTo(0f, Motion.settle)
                            }
                        },
                    )
                    .padding(Space.medium),
            ) {
                // The grab strip, the detail sheet's own. It is a mark saying
                // which way the panel moves rather than a control: the drag
                // works anywhere on the panel, and this is what tells you so.
                Box(
                    Modifier.fillMaxWidth().padding(bottom = Space.small),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .width(Space.large + Space.medium)
                            .height(Space.unit)
                            .background(c.hairline, pillShape()),
                    )
                }

                Column {
                    Text(
                        "Jump to",
                        color = c.accent,
                        fontSize = Type.large,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Text(
                        "Now on " + monthLong(initial.month) + " " + initial.year,
                        color = c.inkMuted,
                        fontSize = Type.small,
                        maxLines = 1,
                    )
                }

                Spacer(Modifier.height(Space.medium))
                YearRow(years, year, onPick = { year = it })

                Spacer(Modifier.height(Space.small))
                MonthGrid(
                    year = year,
                    // The month is only the one you are on while the year on
                    // screen is still the year you are on. Marking September
                    // in every year would say something untrue.
                    selected = if (year == initial.year) initial.month else null,
                    onPick = { choose(it) },
                )
            }
        }
    }
}

/**
 * How much of its own height the panel has to be pushed down before letting go
 * dismisses it. A share rather than a distance, so the same gesture works
 * whatever the year row and the month grid add up to on a given screen.
 */
private const val DISMISS_SHARE = 0.3f

/**
 * The years, as one scrolling row of pills.
 *
 * The range comes from the caller, which knows the enrolment date and the end
 * of the feed, so this never offers a decade nobody is enrolled for. It opens
 * one pill short of the current year, which is what puts the year you are on
 * next to the year before it rather than hard against the left edge.
 */
@Composable
private fun YearRow(years: List<Int>, selected: Int, onPick: (Int) -> Unit) {
    val start = (years.indexOf(selected) - 1).coerceAtLeast(0)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = start)
    LazyRow(
        state = state,
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.tight),
    ) {
        items(years.size, key = { years[it] }) { i ->
            PillButton(
                label = years[i].toString(),
                onClick = { onPick(years[i]) },
                filled = years[i] == selected,
            )
        }
    }
}

/** Twelve, and the grid is built from that number rather than from a shape. */
private const val MONTHS_IN_YEAR = 12

/** The narrowest and widest a month grid is allowed to get. */
private const val MIN_COLUMNS = 2
private const val MAX_COLUMNS = 4

/**
 * Rough advance of one character in the system sans, as a share of the type
 * size. The same estimate the event block uses to decide what it can say.
 */
private const val MONTH_GLYPH = 0.55f

/**
 * The months of one year.
 *
 * How many columns is a question about the longest name and the width there
 * is, so both are measured: at [Type.normal] a system sans runs about 0.55 of
 * its point size per character, which is the same estimate the event block
 * uses, and the widest month decides the cell. A folded phone gets three
 * columns, a narrow desktop window two, a wide one four, and no arrangement
 * ever has to shorten a word to fit.
 */
@Composable
private fun MonthGrid(year: Int, selected: Month?, onPick: (Int) -> Unit) {
    val glyph = with(LocalDensity.current) { Type.normal.toDp() } * MONTH_GLYPH
    val months = remember(year) { List(MONTHS_IN_YEAR) { LocalDate(year, it + 1, 1).month } }
    val names = remember(months) { months.map { monthLong(it) } }
    val cell = glyph * names.maxOf { it.length } + Space.small * 2

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = ((maxWidth + Space.tight) / (cell + Space.tight))
            .toInt()
            .coerceIn(MIN_COLUMNS, MAX_COLUMNS)
        val rows = ceil(MONTHS_IN_YEAR / columns.toFloat()).toInt()

        Column(verticalArrangement = Arrangement.spacedBy(Space.tight)) {
            repeat(rows) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Space.tight)) {
                    repeat(columns) { column ->
                        val index = row * columns + column
                        if (index < MONTHS_IN_YEAR) {
                            MonthCell(
                                label = names[index],
                                selected = months[index] == selected,
                                onClick = { onPick(index + 1) },
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            // The last row keeps its cells the width of every
                            // other row's rather than stretching to fill.
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthCell(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = skema
    val shape = SquircleShape(Radius.chip)
    Box(
        modifier
            .clip(shape)
            .background(if (selected) c.accent else c.surfaceSunken)
            .clickable(onClick = onClick)
            .padding(vertical = Space.small, horizontal = Space.unit),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) c.onAccent else c.ink,
            fontSize = Type.normal,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The date a pick lands on.
 *
 * The day of the month is carried across rather than reset to the first,
 * because in the week and day views the day is the thing being kept: jumping
 * from the 20th of one month to another should land on its 20th. Clamped to
 * the length of the month it arrives in, which is measured by stepping a month
 * on and a day back rather than by keeping a table of month lengths.
 */
private fun jumpTo(year: Int, monthNumber: Int, day: Int): LocalDate {
    val first = LocalDate(year, monthNumber, 1)
    val length = first.plus(1, DateTimeUnit.MONTH).shift(-1).day
    return LocalDate(year, monthNumber, day.coerceAtMost(length))
}
