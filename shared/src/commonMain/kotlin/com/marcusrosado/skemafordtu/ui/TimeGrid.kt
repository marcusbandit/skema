package com.marcusrosado.skemafordtu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import com.marcusrosado.skemafordtu.ics.CalendarEvent
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * The chrome every time based view shares: the clock down the left, the hour
 * rules behind the columns, the line at the current time, and the block an
 * event is drawn as.
 *
 * Week and Day are the same grid with a different number of columns, so they
 * are the same code with a different number of columns. Keeping the primitives
 * here is what stops the two drifting into slightly different type sizes and
 * slightly different corner radii.
 */

/**
 * The hours the grid plots.
 *
 * 07:00 to 18:00 by default: the teaching day, with an hour's air either
 * side of the 08:00 to 17:00 DTU actually timetables. A day or a week with
 * something outside that grows, in whole hours, until it holds it. A 19:00
 * exercise class closes the grid at 20:00 that week and nowhere else.
 *
 * The window went midnight to midnight for a while, on the argument that a
 * grid cut to its events put the same 13:00 at a different height on every
 * page. That is still true across the pages that grow, and it is paid on
 * purpose: a fixed day is 24 rows with 13 of them empty on every page, and
 * the owner would rather the eleven that matter filled the screen. What the
 * old argument keeps is that the default is fixed rather than fitted, so two
 * ordinary weeks still line up exactly.
 */
data class DayWindow(val startHour: Int, val endHour: Int) {

    val hours: Int get() = endHour - startHour
    val startMinute: Int get() = startHour * 60

    fun height(hourHeight: Float): Dp = (hours * hourHeight).dp

    /** Where a minute of the day sits, measured from the top of the grid. */
    fun offsetOf(minute: Int, hourHeight: Float): Dp =
        ((minute - startMinute) / 60f * hourHeight).dp

    fun spanOf(fromMinute: Int, toMinute: Int, hourHeight: Float): Dp =
        (((toMinute - fromMinute).coerceAtLeast(Lanes.MIN_MINUTES)) / 60f * hourHeight).dp

    /** Whether a minute of the day has an hour on this grid to sit in. */
    fun holds(minute: Int): Boolean = minute >= startMinute && minute < endHour * 60

    companion object {
        /** Midnight to midnight, the most a window can be. */
        val FULL = DayWindow(0, 24)

        /** The teaching day, and what a grid shows until something falls outside it. */
        val DEFAULT = DayWindow(7, 18)

        /**
         * The default, grown to take in every event that occupies time. A
         * marker does not: this feed's only entries outside the teaching
         * day are "Due" stamps at 23:59 and "Available" stamps at 00:01,
         * and letting those grow the grid would open most spring weeks at
         * midnight for the sake of a hairline. They go in the strip instead.
         */
        fun around(events: List<CalendarEvent>): DayWindow =
            holding(
                events
                    .filterNot { it.allDay || it.isMarker() }
                    .flatMap { listOf(it.startMinute(), it.endMinute()) },
            )

        /**
         * The default, grown to hold every minute given. In whole hours,
         * because the gutter is labelled in them: a 06:30 start opens the
         * grid at 06:00 and an 18:30 end closes it at 19:00. A minute on the
         * boundary needs no growing; 18:00 is the end of the default and
         * an event ending then is already inside it.
         */
        fun holding(minutes: List<Int>): DayWindow {
            if (minutes.isEmpty()) return DEFAULT
            val start = min(DEFAULT.startHour, minutes.min() / 60).coerceAtLeast(FULL.startHour)
            val end = max(DEFAULT.endHour, ceil(minutes.max() / 60f).toInt()).coerceAtMost(FULL.endHour)
            return DayWindow(start, end)
        }

        /**
         * Where to park the scroll when a day first appears: the earliest
         * event, backed off by a margin so it is not welded to the top edge.
         * Falls back to the working morning on an empty day rather than to
         * midnight, because midnight is never what you wanted to look at.
         */
        fun openingMinute(events: List<CalendarEvent>): Int {
            val timed = events.filterNot { it.allDay || it.isMarker() }
            val first = timed.minOfOrNull { it.startMinute() } ?: (8 * 60)
            return (first - 60).coerceIn(0, 23 * 60)
        }
    }
}

/**
 * Whether an event belongs in the strip above the grid rather than on it: a
 * whole day entry always, and a marker whose moment the grid does not reach.
 * A deadline at 23:59 is still that day's deadline when the day on screen
 * ends at 18:00; it just has no hour to sit on, so it sits with the things
 * that never had one.
 */
fun CalendarEvent.inStrip(window: DayWindow): Boolean =
    allDay || (isMarker() && !window.holds(startMinute()))

/**
 * How long a scroll correction will wait for the grid to be measured at its
 * new height before it gives up and scrolls anyway. Wanted by an anchored
 * zoom, and by a window that has just grown at the top.
 *
 * A few frames. The wait normally ends on the first one; the bound is there so
 * a rounding mismatch cannot leave the correction pending forever.
 */
internal const val MEASURE_WAIT_MS = 120L

/**
 * How often to print an hour label.
 *
 * At 64dp an hour every label fits; pinched down to 28dp they collide, and the
 * usual fix of shrinking the type is not available here because the app has
 * exactly three sizes. So labels thin out instead, by the smallest whole step
 * that clears MIN_GAP. Derived from the zoom, so there is no table of zoom
 * levels to keep in step with anything.
 */
fun labelEvery(hourHeight: Float): Int {
    val minGap = 30f
    return max(1, ceil(minGap / hourHeight).toInt())
}

@Composable
fun HourGutter(window: DayWindow, hourHeight: Float, modifier: Modifier = Modifier) {
    val c = skema
    val every = labelEvery(hourHeight)
    Box(modifier.width(Grid.GUTTER).height(window.height(hourHeight))) {
        for (h in window.startHour until window.endHour) {
            if ((h - window.startHour) % every != 0) continue
            Text(
                clockOfMinute(h * 60),
                color = c.inkMuted,
                fontSize = Type.small,
                maxLines = 1,
                modifier = Modifier
                    // Nudged up by half a line so the label straddles its rule
                    // instead of sitting under it.
                    .offset(y = window.offsetOf(h * 60, hourHeight) - 8.dp)
                    .padding(end = Space.tight)
                    .fillMaxWidth(),
            )
        }
    }
}

/** The rules behind the columns. One per hour, quieter than the hairline between days. */
@Composable
fun HourRules(window: DayWindow, hourHeight: Float, modifier: Modifier = Modifier) {
    val c = skema
    Box(modifier.fillMaxWidth().height(window.height(hourHeight))) {
        for (h in window.startHour..window.endHour) {
            Box(
                Modifier
                    .offset(y = window.offsetOf(h * 60, hourHeight))
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(c.hairline),
            )
        }
    }
}

/**
 * The current time, drawn only on the column that is actually today. A now
 * line on every column would be four lies and one truth.
 */
@Composable
fun NowLine(window: DayWindow, hourHeight: Float, minute: Int, modifier: Modifier = Modifier) {
    if (minute < window.startMinute || minute > window.endHour * 60) return
    val c = skema
    Box(
        modifier
            .offset(y = window.offsetOf(minute, hourHeight) - 1.dp)
            .fillMaxWidth()
            .height(2.dp)
            .background(c.accentStrong),
    )
}

/**
 * One event.
 *
 * What it can say depends on the box it got, and that box is two numbers, not
 * one. Height comes from the event's length and the zoom; width comes from how
 * many other events are running at the same time, because overlapping events
 * share the column. A folded phone gives a day column about 73dp, so the
 * moment two things overlap each lane is 36dp, and a title rendered there
 * reads "Te a..." which is worse than no title: it looks like a bug and it
 * says nothing. Below MIN_TEXT the block is deliberately just its colour, and
 * the tap that opens the detail is the way to read it.
 *
 * Both axes are thresholds on the space a line actually needs, so nothing here
 * branches on a device or a view.
 */
@Composable
fun EventBlock(
    event: CalendarEvent,
    tint: CourseTint,
    height: Dp,
    width: Dp,
    conflict: Boolean,
    dimmed: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = skema
    val text = textFor(event.summary, event.location.courseName, event.courseCode, event.fromRoomBooking)
    val shape = SquircleShape(Radius.chip)

    // Thresholds in characters, not pixels.
    //
    // What fits is a question about the type, so the numbers come from the
    // type scale rather than from a screen that was measured once. A system
    // sans runs about 0.55 of its point size per character, so five characters
    // plus the padding is the least that can hold a word, six holds "08:00",
    // and eight holds enough of a room code to recognise it. Deriving it this
    // way also means a larger system font scale widens the thresholds with it
    // instead of clipping.
    val glyph = with(LocalDensity.current) { Type.small.toDp() } * 0.55f
    val air = Space.tight * 2
    val textFits = width >= glyph * 5 + air
    val timeFits = height >= 40.dp && width >= glyph * 6 + air
    val roomFits = height >= 62.dp && width >= glyph * 8 + air

    Box(
        modifier
            .clip(shape)
            .background(if (dimmed) tint.soft else tint.fill)
            .padding(start = 3.dp),
    ) {
        // The rail is the saturated edge of the course colour. It survives
        // being 3dp wide, which the fill deliberately does not.
        Box(
            Modifier
                .width(3.dp)
                .fillMaxSize()
                .offset(x = (-3).dp)
                .background(if (conflict) c.accentStrong else tint.rail),
        )
        if (!textFits) return@Box
        Column(Modifier.fillMaxSize().padding(horizontal = Space.tight, vertical = 3.dp)) {
            if (timeFits) {
                Text(
                    clockOfMinute(event.startMinute()),
                    color = tint.ink.copy(alpha = 0.75f),
                    fontSize = Type.small,
                    maxLines = 1,
                )
            }
            Text(
                text.title,
                color = tint.ink,
                fontSize = Type.small,
                fontWeight = FontWeight.Medium,
                maxLines = if (roomFits) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (roomFits && event.rooms.isNotEmpty()) {
                Text(
                    event.rooms.joinToString(SEPARATOR),
                    color = tint.ink.copy(alpha = 0.75f),
                    fontSize = Type.small,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Middle dot, deliberately not a dash. */
const val SEPARATOR = "  ·  "

/**
 * Rooms, joined, with a cap.
 *
 * One real entry in this feed books nine rooms at once. Printed in full it
 * takes six lines in a summary list and pushes everything else off the pane,
 * so a compact list asks for a cap and the detail view asks for all of them.
 * The count in the tail is the honest part: it says how much is not shown
 * rather than trailing off into an ellipsis.
 */
fun roomsLabel(rooms: List<String>, cap: Int = Int.MAX_VALUE): String {
    if (rooms.size <= cap) return rooms.joinToString(SEPARATOR)
    val shown = rooms.take(cap).joinToString(SEPARATOR)
    return shown + SEPARATOR + "+" + (rooms.size - cap) + " more"
}

/** The weekday heading over a column. Today gets the accent, nothing else does. */
/**
 * The mark behind today's number: a line of Type.normal with Space.tight
 * above and below it, which is the smallest disc that holds two digits with
 * air around them.
 */
private val DAY_DISC = Space.large + Space.tight

@Composable
fun DayHeading(
    label: String,
    number: String,
    isToday: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = skema
    Column(
        modifier.padding(vertical = Space.tight),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = c.inkMuted, fontSize = Type.small, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        // A disc, not a pill: the box is held square so a one digit day and a
        // two digit day wear the same round mark, and the number sits in the
        // middle of it rather than on its left edge. A true circle rather than
        // the squircle every other pill is, because a circle has no corner to
        // smooth and a squared-off disc reads as a badge, not a day.
        Box(
            Modifier
                .defaultMinSize(minWidth = DAY_DISC, minHeight = DAY_DISC)
                .background(if (isToday) c.accent else Color.Transparent, CircleShape)
                .padding(horizontal = Space.tight),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                number,
                color = if (isToday) c.onAccent else c.ink,
                fontSize = Type.normal,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}
