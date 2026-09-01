package dk.bandit.skema.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dk.bandit.skema.ics.CalendarEvent

/**
 * Everything the feed knows about one entry.
 *
 * The grid has to compress; this does not. It is the same content in both
 * postures, because the difference between folded and unfolded is where this
 * is shown, not what it says: a rail beside the week when there is width for
 * one, a sheet over it when there is not.
 */
@Composable
fun EventDetail(
    event: CalendarEvent,
    tint: CourseTint,
    conflict: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = skema
    val text = textFor(event.summary, event.location.courseName, event.courseCode, event.fromRoomBooking)

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(Space.medium),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(Space.small)
                    .background(tint.rail, pillShape()),
            )
            Spacer(Modifier.size(Space.tight))
            event.courseCode?.let {
                Text(it, color = c.inkMuted, fontSize = Type.small, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.weight(1f))
            PillButton("Close", onClose)
        }

        Spacer(Modifier.height(Space.small))
        Text(
            text.title,
            color = c.ink,
            fontSize = Type.large,
            fontWeight = FontWeight.SemiBold,
        )

        text.subtitle?.let {
            Spacer(Modifier.height(Space.unit))
            Text(it, color = c.inkMuted, fontSize = Type.normal)
        }

        Spacer(Modifier.height(Space.medium))
        Fact("When", whenLabel(event))
        if (event.rooms.isNotEmpty()) Fact("Where", event.rooms.joinToString(SEPARATOR))

        if (conflict) {
            Spacer(Modifier.height(Space.small))
            Text(
                "Another course is booked at this time.",
                color = c.accentStrong,
                fontSize = Type.small,
            )
        }

        // A room booking DTU generated and a teacher's own entry read very
        // differently, and which one you are looking at explains why the
        // title is the word "Lecture". Worth saying once, quietly.
        Spacer(Modifier.height(Space.small))
        Text(
            if (event.fromRoomBooking) "From the room booking" else "Entered by hand",
            color = c.inkMuted,
            fontSize = Type.small,
        )

        event.description?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(Space.medium))
            Text(it, color = c.ink, fontSize = Type.small)
        }
    }
}

private fun whenLabel(event: CalendarEvent): String {
    val day = dateLabel(event.start.date)
    val relative = relativeDay(event.start.date)
    val head = if (relative != null) "$relative, $day" else day
    if (event.allDay) return "$head, all day"
    val end = event.end?.let { " - " + clock(it) }.orEmpty()
    return head + ", " + clock(event.start) + end
}

@Composable
private fun Fact(label: String, value: String) {
    val c = skema
    Row(Modifier.fillMaxWidth().padding(vertical = Space.unit), verticalAlignment = Alignment.Top) {
        Text(label, color = c.inkMuted, fontSize = Type.small, modifier = Modifier.size(width = 56.dp, height = 20.dp))
        Text(value, color = c.ink, fontSize = Type.small, modifier = Modifier.weight(1f))
    }
}

/**
 * What the rail shows when nothing is picked: the selected day as a list.
 * An empty pane beside a full week would be wasted width, and this is the
 * thing you most often want next to a week grid anyway.
 */
@Composable
fun DaySummary(
    model: SkemaModel,
    palette: CoursePalette,
    modifier: Modifier = Modifier,
) {
    val c = skema
    val events = model.eventsOn(model.selected)
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Space.medium)) {
        Text(
            relativeDay(model.selected) ?: dateLabel(model.selected),
            color = c.ink,
            fontSize = Type.large,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(Space.small))

        if (events.isEmpty()) {
            Text("Nothing booked.", color = c.inkMuted, fontSize = Type.normal)
            return@Column
        }

        events.forEach { event ->
            val tint = palette.tint(event.courseCode)
            val text = textFor(event.summary, event.location.courseName, event.courseCode, event.fromRoomBooking)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = Space.tight),
                verticalAlignment = Alignment.Top,
            ) {
                Box(Modifier.size(width = 3.dp, height = 34.dp).background(tint.rail, pillShape()))
                Spacer(Modifier.size(Space.small))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (event.allDay) "All day" else clock(event.start),
                        color = c.inkMuted,
                        fontSize = Type.small,
                    )
                    Text(text.title, color = c.ink, fontSize = Type.normal, fontWeight = FontWeight.Medium)
                    if (event.rooms.isNotEmpty()) {
                        // Capped here, in full in EventDetail: this is the
                        // glanceable list, that one is the deliberate look.
                        Text(roomsLabel(event.rooms, cap = 3), color = c.inkMuted, fontSize = Type.small)
                    }
                }
            }
        }
    }
}
