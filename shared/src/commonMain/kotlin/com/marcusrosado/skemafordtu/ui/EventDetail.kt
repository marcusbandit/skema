package com.marcusrosado.skemafordtu.ui

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
import androidx.compose.ui.unit.em
import com.marcusrosado.skemafordtu.ics.CalendarEvent

/**
 * Everything the feed knows about one entry.
 *
 * The grid has to compress; this does not. It is the same content in both
 * postures, because the difference between folded and unfolded is where this
 * is shown, not what it says: a rail beside the week when there is width for
 * one, a sheet over it when there is not.
 *
 * Content only: no close, no fullscreen, no chrome of its own. Both of those
 * buttons used to sit in the row below, and both were a picture of a gesture
 * that already existed. The sheet is dragged up to fill the screen and down
 * to dismiss, and on the wide layout the rail is closed by back, so a control
 * for either would be a second way to do a thing the finger already does.
 */
@Composable
fun EventDetail(
    event: CalendarEvent,
    tint: CourseTint,
    conflict: Boolean,
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

        // Only a real description reaches here now. The paragraph DTU repeats
        // on every generated entry is stripped in the parser and comes back as
        // the reservation stamp below, so this block is empty when there is
        // genuinely nothing to say rather than empty of meaning.
        event.description?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(Space.medium))
            Text(it, color = c.ink, fontSize = Type.small)
        }

        event.reservation?.let { ReservationStamp(it) }
    }
}

/**
 * The booking's provenance, reduced to what it actually says.
 *
 * DTU spends a paragraph on this and repeats it on 278 of one feed's 688
 * descriptions, identical every time except the number. As prose it is noise
 * that buries any real description; as a stamp it is a fact you can check
 * against TimeEdit. Set quiet and in caps so it reads as a filing mark rather
 * than as something to do.
 */
@Composable
private fun ReservationStamp(number: String) {
    val c = skema
    Spacer(Modifier.height(Space.medium))
    Row(
        Modifier
            .background(c.surfaceSunken, SquircleShape(Radius.chip))
            .padding(horizontal = Space.small, vertical = Space.tight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(Ico.Room, tint = c.inkMuted, size = IconSizeQuiet)
        Spacer(Modifier.size(Space.tight))
        Text(
            "TIMEEDIT RESERVATION " + number,
            color = c.inkMuted,
            fontSize = Type.small,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.08.em,
        )
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
 * What the rail shows when nothing is picked: the selected day as the same
 * timeline the month view draws under its grid, so the two never disagree
 * about what a day looks like. Tapping a row picks it, and the rail turns
 * into that event's detail.
 */
@Composable
fun DaySummary(
    model: SkemaModel,
    palette: CoursePalette,
    modifier: Modifier = Modifier,
) {
    DayTimeline(
        date = model.selected,
        events = model.eventsOn(model.selected),
        palette = palette,
        onPick = { model.picked = it },
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Space.medium),
        ground = skema.surface,
    )
}
