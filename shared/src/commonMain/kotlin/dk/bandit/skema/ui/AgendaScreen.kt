package dk.bandit.skema.ui

import androidx.compose.foundation.background
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dk.bandit.skema.ics.CalendarEvent
import dk.bandit.skema.ics.Dedup
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** Middle dot, deliberately not a dash. */
private const val SEPARATOR = "  ·  "

private sealed interface AgendaRow {
    data class Day(val date: LocalDate, val isToday: Boolean, val isPast: Boolean) : AgendaRow
    data class Entry(val event: CalendarEvent, val conflict: Boolean, val isPast: Boolean) : AgendaRow
}

@Composable
fun AgendaScreen(model: SkemaModel) {
    val c = skema
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val rows = remember(model.events, model.settings.collapseDuplicates) {
        buildRows(model.events, model.settings.collapseDuplicates)
    }

    // Land on today the first time events arrive, then leave the scroll alone.
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(rows.size) {
        if (!placed && rows.isNotEmpty()) {
            listState.scrollToItem(todayIndex(rows))
            placed = true
        }
    }

    Column(Modifier.fillMaxSize().background(c.background)) {
        TopBar(
            model = model,
            onToday = { scope.launch { listState.animateScrollToItem(todayIndex(rows)) } },
            onRefresh = { scope.launch { model.refresh() } },
        )

        if (rows.isEmpty()) {
            EmptyState(model, onRefresh = { scope.launch { model.refresh() } })
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
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
                        is AgendaRow.Entry -> EventCard(row)
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(model: SkemaModel, onToday: () -> Unit, onRefresh: () -> Unit) {
    val c = skema
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.background)
            .padding(horizontal = Space.medium, vertical = Space.small)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Skema",
                color = c.accent,
                fontSize = Type.large,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(Space.small))
            Text(
                dateLabel(today()),
                color = c.inkMuted,
                fontSize = Type.small,
                modifier = Modifier.weight(1f),
            )
            PillButton("Today", onToday)
            Spacer(Modifier.width(Space.tight))
            PillButton(
                label = if (model.busy) "Syncing" else "Sync",
                onClick = onRefresh,
                enabled = !model.busy && model.hasUrl,
                filled = true,
            )
            Spacer(Modifier.width(Space.tight))
            PillButton("Settings", { model.screen = Screen.Settings })
        }

        if (model.status.isNotBlank()) {
            Spacer(Modifier.height(Space.tight))
            Text(
                model.status + ", " + agoLabel(model.fetchedAt),
                color = if (model.failed) c.accentStrong else c.inkMuted,
                fontSize = Type.small,
            )
        }

        Spacer(Modifier.height(Space.small))
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
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
private fun EventCard(row: AgendaRow.Entry) {
    val c = skema
    val e = row.event
    val dim = row.isPast

    Card(Modifier.fillMaxWidth(), PaddingValues(Space.small)) {
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

            Box(
                Modifier
                    .width(2.dp)
                    .height(34.dp)
                    .background(if (dim) c.hairline else c.accentStrong, pillShape())
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
                        e.courseCode?.let { Tag(it) }
                        if (row.conflict) {
                            Tag("clash", background = c.accentStrong, foreground = c.onAccent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(model: SkemaModel, onRefresh: () -> Unit) {
    val c = skema
    Box(Modifier.fillMaxSize().padding(Space.large), contentAlignment = Alignment.Center) {
        Card {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (model.hasUrl) "Nothing cached yet" else "No feed link yet",
                    color = c.ink,
                    fontSize = Type.large,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(Space.small))
                Text(
                    if (model.hasUrl) {
                        "Sync to pull your calendar from DTU Learn."
                    } else {
                        "Paste your DTU Learn calendar link in Settings and this fills up."
                    },
                    color = c.inkMuted,
                    fontSize = Type.normal,
                )
                Spacer(Modifier.height(Space.medium))
                if (model.hasUrl) {
                    PillButton("Sync now", onRefresh, filled = true, enabled = !model.busy)
                } else {
                    PillButton("Open settings", { model.screen = Screen.Settings }, filled = true)
                }
            }
        }
    }
}

private fun buildRows(events: List<CalendarEvent>, collapse: Boolean): List<AgendaRow> {
    if (events.isEmpty()) return emptyList()
    val list = if (collapse) Dedup.collapse(events) else events
    val clashes = Dedup.slots(list).filter { it.isConflict }.map { it.startEpochSeconds }.toSet()
    val reference = today()

    val out = mutableListOf<AgendaRow>()
    var day: LocalDate? = null
    for (e in list) {
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
