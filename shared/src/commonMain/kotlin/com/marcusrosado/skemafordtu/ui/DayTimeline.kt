package com.marcusrosado.skemafordtu.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.marcusrosado.skemafordtu.ics.CalendarEvent
import kotlinx.datetime.LocalDate

/**
 * One day as a graph, the way a git log draws history.
 *
 * A vertical trunk runs down the day with a dot per event in the course's
 * colour, the time above the name. Two things happening at once cannot share
 * a line, so the later one takes the lane beside the trunk for as long as the
 * two overlap, in its own colour: a clash reads as two lines running side by
 * side. When the two are the same course the second is not a clash but a
 * part of the first, a lab inside a lecture, and it is drawn the way git
 * draws one: forked off the parent's line, run alongside, and merged back
 * when it ends.
 *
 * Rows are events, not minutes, so the graph is drawn row by row like git's
 * own: each row paints the lines passing through it, its dot, and any fork or
 * merge that lands on it. Nothing measures where a row ended up; a row only
 * needs its own height. The arithmetic behind it is [dayGraph], which knows
 * nothing about pixels and is tested on its own.
 */
@Composable
fun DayTimeline(
    date: LocalDate,
    events: List<CalendarEvent>,
    palette: CoursePalette,
    onPick: (CalendarEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = skema
    val timed = remember(events) { events.filterNot { it.allDay } }
    val allDay = remember(events) { events.filter { it.allDay } }
    val graph = remember(timed) {
        dayGraph(timed, { it.startMinute() }, { it.endMinute() }, { it.courseCode })
    }
    val width = TRUNK_X * 2 + LANE_GAP * (graph.lanes - 1).coerceAtLeast(0)

    Column(modifier) {
        TimelineHeader(date)

        if (events.isEmpty()) {
            Spacer(Modifier.height(Space.small))
            Text(
                "Nothing booked.",
                color = c.inkMuted,
                fontSize = Type.normal,
                modifier = Modifier.padding(start = TRUNK_X * 2 + Space.small),
            )
            return@Column
        }

        // The trunk between the header's disc and the first row, so the line
        // visibly starts at the day rather than at the first thing on it.
        Canvas(Modifier.width(width).height(Space.small)) {
            drawLine(c.hairline, Offset(TRUNK_X.toPx(), 0f), Offset(TRUNK_X.toPx(), size.height), LINE.toPx())
        }

        // Whole day entries first, on the trunk, with a ring rather than a
        // dot: they are on the day without being at any point of it.
        allDay.forEach { event ->
            val tint = palette.tint(event.courseCode).rail
            TimelineRow(event, "All day", width, onPick) { h ->
                drawLine(c.hairline, Offset(TRUNK_X.toPx(), 0f), Offset(TRUNK_X.toPx(), h), LINE.toPx())
                drawCircle(tint, DOT.toPx() / 2 - LINE.toPx() / 2, Offset(TRUNK_X.toPx(), h / 2), style = Stroke(LINE.toPx()))
            }
        }

        graph.rows.forEach { row ->
            val placed = graph.placed[row.index]
            val event = placed.item
            // A stamp is a moment, and a moment has one time.
            val from = clockOfMinute(event.startMinute())
            val span = if (event.isMarker()) from else from + " → " + clockOfMinute(event.endMinute())
            // The trunk stops at the last dot, unless a branch still has to
            // come home below it.
            val last = row.index == graph.rows.lastIndex && graph.openBranches.isEmpty()
            TimelineRow(event, span, width, onPick) { h ->
                paintRow(graph, row, h, last, trunk = c.hairline) { palette.tint(graph.placed[it].item.courseCode).rail }
            }
        }

        // Branches still running when the list runs out come home here, so a
        // lab that outlasts the day's last row still ends on the trunk rather
        // than in mid air.
        if (graph.openBranches.isNotEmpty()) {
            Canvas(Modifier.width(width).height(Space.large)) {
                val x0 = TRUNK_X.toPx()
                drawLine(c.hairline, Offset(x0, 0f), Offset(x0, size.height), LINE.toPx())
                graph.openBranches.forEach { j ->
                    val tint = palette.tint(graph.placed[j].item.courseCode).rail
                    curve(tint, Offset(laneX(graph.placed[j].lane), 0f), Offset(x0, size.height))
                }
            }
        }
    }
}

/**
 * The day named, the way the reference names it: its number in a disc that
 * sits on the trunk, a word beside it, and the date on the far side.
 */
@Composable
private fun TimelineHeader(date: LocalDate) {
    val c = skema
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.background(c.surfaceSunken, pillShape()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(TRUNK_X * 2).background(c.accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    date.day.toString(),
                    color = c.onAccent,
                    fontSize = Type.normal,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
            Text(
                relativeDay(date) ?: longWeekday(date.dayOfWeek),
                color = c.ink,
                fontSize = Type.normal,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.padding(start = Space.small, end = Space.medium),
            )
        }
        Spacer(Modifier.weight(1f))
        Glyph(Ico.Month, tint = c.inkMuted, size = IconSizeQuiet)
        Spacer(Modifier.width(Space.tight))
        Text(dateLabel(date), color = c.inkMuted, fontSize = Type.small, maxLines = 1)
    }
}

/**
 * One row: the graph cell on the left, painted by the caller, and the words.
 * The row is as tall as its words, and the cell fills that, which is what
 * lets the paint draw from the row's top edge to its bottom and meet the
 * rows either side without a seam.
 */
@Composable
private fun TimelineRow(
    event: CalendarEvent,
    span: String,
    width: Dp,
    onPick: (CalendarEvent) -> Unit,
    paint: DrawScope.(height: Float) -> Unit,
) {
    val c = skema
    val text = textFor(event.summary, event.location.courseName, event.courseCode, event.fromRoomBooking)
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(SquircleShape(Radius.chip))
            .clickable { onPick(event) },
    ) {
        Canvas(Modifier.width(width).fillMaxHeight()) { paint(size.height) }
        Column(Modifier.weight(1f).padding(start = Space.small, top = Space.small, end = Space.small, bottom = Space.small)) {
            Text(span, color = c.inkMuted, fontSize = Type.small, maxLines = 1)
            Text(text.title, color = c.ink, fontSize = Type.normal, fontWeight = FontWeight.Medium)
            if (event.rooms.isNotEmpty()) {
                Text(
                    roomsLabel(event.rooms, cap = 3),
                    color = c.inkMuted,
                    fontSize = Type.small,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The x of a lane, in pixels. Lane 0 is the trunk. */
private fun DrawScope.laneX(lane: Int): Float = (TRUNK_X + LANE_GAP * lane).toPx()

/**
 * Paints one row of the graph: the trunk, every lane passing through from
 * above, any branch merging back on this row, this row's own fork if it is
 * one, its line onward if it is still running, and last its dot, on top.
 */
private fun DrawScope.paintRow(
    graph: DayGraph<CalendarEvent>,
    row: GraphRow,
    h: Float,
    last: Boolean,
    trunk: Color,
    colour: (Int) -> Color,
) {
    val stroke = LINE.toPx()
    val x0 = laneX(0)
    drawLine(trunk, Offset(x0, 0f), Offset(x0, if (last) h / 2 else h), stroke)

    row.through.forEach { j ->
        val lane = graph.placed[j].lane
        if (lane > 0) drawLine(colour(j), Offset(laneX(lane), 0f), Offset(laneX(lane), h), stroke)
    }
    row.merges.forEach { (j, into) ->
        curve(colour(j), Offset(laneX(graph.placed[j].lane), 0f), Offset(laneX(into), h / 2))
    }

    val own = graph.placed[row.index]
    val x = laneX(own.lane)
    val tint = colour(row.index)
    if (row.forkFrom != null) {
        curve(tint, Offset(laneX(graph.placed[row.forkFrom].lane), 0f), Offset(x, h / 2))
    }
    // A branch always carries on below its dot, because a merge or the tail
    // is waiting to take it; a lane of its own only does while it is still
    // running when the next row starts.
    if (own.lane > 0 && (row.continues || row.forkFrom != null)) {
        drawLine(tint, Offset(x, h / 2), Offset(x, h), stroke)
    }
    drawCircle(tint, DOT.toPx() / 2, Offset(x, h / 2))
}

/**
 * The S curve a fork or a merge is drawn with: vertical as it leaves, vertical
 * as it arrives, so it reads as a line changing lanes rather than a diagonal.
 */
private fun DrawScope.curve(colour: Color, from: Offset, to: Offset) {
    val mid = (from.y + to.y) / 2
    val path = Path().apply {
        moveTo(from.x, from.y)
        cubicTo(from.x, mid, to.x, mid, to.x, to.y)
    }
    drawPath(path, colour, style = Stroke(LINE.toPx(), cap = StrokeCap.Round))
}

/** Where the trunk runs: half the header's disc in, so the disc sits on it. */
private val TRUNK_X = Space.medium

/** Between one lane and the next. Wider than a dot, so two never touch. */
private val LANE_GAP = Space.large

/** A dot, and the line it sits on. */
private val DOT = Space.medium
private val LINE = Space.unit / 2

/** One row of the graph, in terms of the rows around it. */
internal data class GraphRow(
    val index: Int,
    /** The row this one forks off: an earlier, still running event of its own course, in a lower lane. */
    val forkFrom: Int?,
    /** Earlier rows still running when this one starts, whose lines pass through. */
    val through: List<Int>,
    /** Branches that ended before this row, each with the lane it merges into. */
    val merges: List<Pair<Int, Int>>,
    /** Whether this row is still running when the next one starts. */
    val continues: Boolean,
)

internal data class DayGraph<T>(
    val placed: List<Placed<T>>,
    val rows: List<GraphRow>,
    /** Branches still running when the rows run out, to be merged in the tail. */
    val openBranches: List<Int>,
    /** How many lanes the day needs at its widest. */
    val lanes: Int,
)

/**
 * The graph of a day.
 *
 * Lanes come from [Lanes.layout], the same greedy colouring the week grid
 * uses, so an event sits in the same lane here as it does there. On top of
 * that, three facts per row: which earlier events are still running when it
 * starts, whether it is a branch of one of them, and which branches have
 * ended since the row before and so merge here. A branch is a row in a
 * side lane that overlaps an earlier event of its own course in a lower
 * lane; it forks from that one and merges back into it, or into the trunk
 * if the parent has ended by the time the branch does.
 */
internal fun <T> dayGraph(
    items: List<T>,
    start: (T) -> Int,
    end: (T) -> Int,
    course: (T) -> String?,
): DayGraph<T> {
    val placed = Lanes.layout(items, start, end)
    val n = placed.size

    fun runningAt(row: Int): List<Int> =
        (0 until row).filter { placed[it].endMinute > placed[row].startMinute }

    val forkFrom = List(n) { i ->
        if (placed[i].lane == 0) return@List null
        runningAt(i)
            .filter { course(placed[it].item) == course(placed[i].item) && placed[it].lane < placed[i].lane }
            .minByOrNull { placed[it].lane }
    }

    /** Whether row j is on screen as a line at row i: it is row i, or still running then. */
    fun activeAt(j: Int, i: Int): Boolean = j == i || (j < i && placed[j].endMinute > placed[i].startMinute)

    val rows = List(n) { i ->
        val merges = if (i == 0) {
            emptyList()
        } else {
            (0 until i)
                .filter { forkFrom[it] != null && activeAt(it, i - 1) && !activeAt(it, i) }
                .map { j ->
                    val parent = forkFrom[j]!!
                    j to (if (activeAt(parent, i)) placed[parent].lane else 0)
                }
        }
        GraphRow(
            index = i,
            forkFrom = forkFrom[i],
            through = runningAt(i),
            merges = merges,
            continues = i < n - 1 && placed[i].endMinute > placed[i + 1].startMinute,
        )
    }

    val open = if (n == 0) emptyList() else (0 until n).filter { forkFrom[it] != null && activeAt(it, n - 1) }

    return DayGraph(
        placed = placed,
        rows = rows,
        openBranches = open,
        lanes = (placed.maxOfOrNull { it.lane } ?: 0) + 1,
    )
}
