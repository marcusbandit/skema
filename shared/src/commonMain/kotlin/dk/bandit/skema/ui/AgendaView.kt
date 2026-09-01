package dk.bandit.skema.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dk.bandit.skema.ics.CalendarEvent
import kotlinx.datetime.LocalDate

/**
 * The calendar as a list.
 *
 * Everything the grids do with position, this does with reading order, which
 * is why it stays: a grid answers "when is my Thursday busy" and a list
 * answers "what is next", and the second question is the one asked while
 * walking. It owns no chrome. The shell above it holds the title, the sync
 * button and the view switch, so this draws rows and nothing else.
 */

private sealed interface AgendaRow {
    data class Day(val date: LocalDate, val isToday: Boolean, val isPast: Boolean) : AgendaRow
    data class Entry(val event: CalendarEvent, val conflict: Boolean, val isPast: Boolean) : AgendaRow
}

@Composable
fun AgendaView(model: SkemaModel, palette: CoursePalette, modifier: Modifier = Modifier) {
    val c = skema
    val listState = rememberLazyListState()

    // Keyed on the raw feed and the merge setting rather than on the merged
    // list, because reading model.visible runs the merge, and a key that has
    // to be computed before it can be compared saves nothing.
    val rows = remember(model.events, model.settings.collapseDuplicates) {
        buildRows(model.visible, model.conflicts)
    }

    // Land on today the first time entries arrive, then leave the scroll
    // alone. A list that re-centres itself on every sync loses your place.
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(rows.size) {
        if (!placed && rows.isNotEmpty()) {
            listState.scrollToItem(todayIndex(rows))
            placed = true
        }
    }

    if (rows.isEmpty()) {
        Box(modifier.fillMaxSize().padding(Space.large), contentAlignment = Alignment.Center) {
            Text(
                if (model.hasUrl) "Nothing cached yet." else "No feed link yet.",
                color = c.inkMuted,
                fontSize = Type.normal,
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Space.medium,
            end = Space.medium,
            top = Space.small,
            bottom = Space.large,
        ),
        verticalArrangement = Arrangement.spacedBy(Space.unit),
    ) {
        items(rows.size, key = { keyFor(rows[it]) }) { index ->
            when (val row = rows[index]) {
                is AgendaRow.Day -> DayHeader(row)
                is AgendaRow.Entry -> EventRow(
                    row = row,
                    tint = palette.tint(row.event.courseCode),
                    onPick = { model.picked = row.event },
                )
            }
        }
    }
}

@Composable
private fun DayHeader(row: AgendaRow.Day) {
    val c = skema
    val relative = relativeDay(row.date)
    Row(
        Modifier.fillMaxWidth().padding(top = Space.medium, bottom = Space.tight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (row.isToday) {
            Box(Modifier.size(Space.tight).background(c.accentStrong, pillShape()))
            Spacer(Modifier.width(Space.tight))
        }
        Text(
            (relative ?: dateLabel(row.date)).uppercase(),
            color = when {
                row.isToday -> c.accentStrong
                row.isPast -> c.inkMuted
                else -> c.ink
            },
            fontSize = Type.small,
            fontWeight = FontWeight.SemiBold,
        )
        if (relative != null) {
            Spacer(Modifier.width(Space.tight))
            Text(dateLabel(row.date), color = c.inkMuted, fontSize = Type.small)
        }
    }
}

@Composable
private fun EventRow(row: AgendaRow.Entry, tint: CourseTint, onPick: () -> Unit) {
    val c = skema
    val e = row.event
    val dim = row.isPast

    Card(
        Modifier
            .fillMaxWidth()
            .clip(SquircleShape(Radius.card))
            .clickable(onClick = onPick),
        PaddingValues(Space.small),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            // Times, monospace so the columns line up down the whole list.
            Column(Modifier.width(52.dp)) {
                Text(
                    if (e.allDay) "all" else clock(e.start),
                    color = if (dim) c.inkMuted else c.ink,
                    fontSize = Type.small,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                )
                e.end?.let {
                    Text(
                        if (e.allDay) "day" else clock(it),
                        color = c.inkMuted,
                        fontSize = Type.small,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            // The course's own colour, so the list scans by subject the way
            // the grid does. Red is kept out of it: red means a clash here,
            // and a course that happened to land on red would say so falsely.
            Box(
                Modifier
                    .width(2.dp)
                    .height(34.dp)
                    .background(if (dim) tint.fill else tint.rail, pillShape())
            )
            Spacer(Modifier.width(Space.small))

            Column(Modifier.weight(1f)) {
                val text = textFor(e.summary, e.location.courseName, e.courseCode, e.fromRoomBooking)
                Text(
                    text.title,
                    color = if (dim) c.inkMuted else c.ink,
                    fontSize = Type.normal,
                    fontWeight = FontWeight.Medium,
                )
                text.subtitle?.let {
                    Text(it, color = c.inkMuted, fontSize = Type.small)
                }
                if (e.rooms.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    // Every room, wrapped. The DTU page puts them on one line
                    // and clips the tail, which is the thing that started all
                    // of this.
                    Text(
                        e.rooms.joinToString(SEPARATOR),
                        color = c.inkMuted,
                        fontSize = Type.small,
                    )
                }
                if (e.courseCode != null || row.conflict) {
                    Spacer(Modifier.height(Space.tight))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.tight)) {
                        e.courseCode?.let {
                            Tag(
                                it,
                                background = if (dim) tint.soft else tint.fill,
                                foreground = if (dim) c.inkMuted else tint.ink,
                            )
                        }
                        if (row.conflict) {
                            Tag("clash", background = c.accentStrong, foreground = c.onAccent)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Day headers interleaved with entries, flattened into one list so the whole
 * thing is a single LazyColumn and a header costs nothing to scroll past.
 */
private fun buildRows(events: List<CalendarEvent>, clashes: Set<Long>): List<AgendaRow> {
    if (events.isEmpty()) return emptyList()
    val reference = today()

    val out = mutableListOf<AgendaRow>()
    var day: LocalDate? = null
    for (e in events.sortedBy { it.startEpochSeconds }) {
        val d = e.start.date
        if (d != day) {
            out.add(AgendaRow.Day(d, d == reference, d < reference))
            day = d
        }
        out.add(AgendaRow.Entry(e, e.startEpochSeconds in clashes, d < reference))
    }
    return out
}

/** First row on or after today, so the list opens where you actually are. */
private fun todayIndex(rows: List<AgendaRow>): Int {
    val reference = today()
    val i = rows.indexOfFirst { it is AgendaRow.Day && it.date >= reference }
    return if (i >= 0) i else (rows.size - 1).coerceAtLeast(0)
}

private fun keyFor(row: AgendaRow): String = when (row) {
    is AgendaRow.Day -> "d" + row.date.toString()
    is AgendaRow.Entry -> "e" + row.event.uid + "#" + row.event.occurrence
}
