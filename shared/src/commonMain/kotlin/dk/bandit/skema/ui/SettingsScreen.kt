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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dk.bandit.skema.data.ThemeChoice
import dk.bandit.skema.data.ViewMode
import dk.bandit.skema.ics.CalendarEvent
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(model: SkemaModel) {
    val c = skema
    val scope = rememberCoroutineScope()

    // Keyed on the raw feed and the merge setting, because reading
    // model.visible runs the merge and a key you have to compute first is not
    // a key. The palette is rebuilt whenever a hue is pinned or released, so
    // every row below it shows the re-spread that pinning caused.
    val subjects = remember(model.events, model.settings.collapseDuplicates) {
        subjectsOf(model.visible)
    }
    val palette = remember(subjects, model.settings.courseHues, c.isDark) {
        CoursePalette(subjects.map { it.code }, model.settings.courseHues, c.isDark)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .verticalScroll(rememberScrollState())
            .padding(Space.medium)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Settings",
                color = c.accent,
                fontSize = Type.large,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            PillButton("Done", { model.screen = Screen.Calendar }, filled = true)
        }

        Spacer(Modifier.height(Space.large))
        SectionLabel("Calendar link")

        Card(Modifier.fillMaxWidth()) {
            Column {
                OutlinedTextField(
                    value = model.settings.feedUrl,
                    onValueChange = { url -> model.update { it.copy(feedUrl = url.trim()) } },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            "https://learn.inside.dtu.dk/d2l/le/calendar/feed/user/feed.ics?token=",
                            color = c.inkMuted,
                            fontSize = Type.small,
                        )
                    },
                    singleLine = false,
                    shape = SquircleShape(Radius.chip),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = Type.small,
                        color = c.ink,
                    ),
                )

                Spacer(Modifier.height(Space.small))
                Text(
                    "In DTU Learn open the Calendar, press Subscribe, and copy the address it gives you.",
                    color = c.inkMuted,
                    fontSize = Type.small,
                )

                Spacer(Modifier.height(Space.small))
                Text(
                    "Treat this link like a password. The token in it reads your whole calendar with no login, so do not paste it into a chat or a repo.",
                    color = c.accentStrong,
                    fontSize = Type.small,
                )

                Spacer(Modifier.height(Space.medium))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.small),
                ) {
                    PillButton(
                        label = if (model.busy) "Syncing" else "Sync now",
                        onClick = { scope.launch { model.refresh() } },
                        filled = true,
                        enabled = !model.busy && model.hasUrl,
                    )
                    if (model.status.isNotBlank()) {
                        Text(
                            model.status,
                            color = if (model.failed) c.accentStrong else c.inkMuted,
                            fontSize = Type.small,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(Space.large))
        SectionLabel("Behaviour")

        Card(Modifier.fillMaxWidth(), PaddingValues(Space.small)) {
            Column {
                Column(Modifier.padding(Space.small)) {
                    Text(
                        "Theme",
                        color = c.ink,
                        fontSize = Type.normal,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(Space.tight))
                    Segmented(
                        options = ThemeChoice.entries,
                        selected = model.settings.theme,
                        label = { it.name },
                        onSelect = { choice -> model.update { it.copy(theme = choice) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Divider()
                Column(Modifier.padding(Space.small)) {
                    Text(
                        "Calendar view",
                        color = c.ink,
                        fontSize = Type.normal,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(Space.tight))
                    Segmented(
                        options = ViewMode.entries,
                        selected = model.settings.view,
                        label = { it.name },
                        onSelect = { mode -> model.update { it.copy(view = mode) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Divider()
                Toggle(
                    title = "Show Saturday and Sunday",
                    detail = "A teaching week runs Monday to Friday, so the weekend is left out and its width goes to the five days that hold something.",
                    checked = model.settings.showWeekend,
                    onChange = { v -> model.update { it.copy(showWeekend = v) } },
                )
                Divider()
                Toggle(
                    title = "Fold duplicate entries",
                    detail = "DTU lists most classes twice, once written by a teacher and once generated from the room booking. Merge the pair field by field: where the two differ the teacher's entry wins, and where it says nothing the booking fills in, which is how the rooms survive.",
                    checked = model.settings.collapseDuplicates,
                    onChange = { v -> model.update { it.copy(collapseDuplicates = v) } },
                )
                Divider()
                Toggle(
                    title = "Sync when the app opens",
                    detail = "The feed sends no last-modified header, so every sync pulls the whole calendar and works out what moved.",
                    checked = model.settings.refreshOnOpen,
                    onChange = { v -> model.update { it.copy(refreshOnOpen = v) } },
                )
            }
        }

        Spacer(Modifier.height(Space.large))
        SectionLabel("Subjects")

        Card(Modifier.fillMaxWidth(), PaddingValues(Space.small)) {
            Column {
                Text(
                    if (subjects.isEmpty()) {
                        "Courses turn up here after a sync."
                    } else {
                        "Every course gets a colour as far from the others as the number of courses allows. Drag a strip to choose one yourself; the rest move out of its way."
                    },
                    color = c.inkMuted,
                    fontSize = Type.small,
                    modifier = Modifier.padding(Space.small),
                )
                subjects.forEach { subject ->
                    Divider()
                    CourseColorRow(
                        code = subject.code,
                        label = subject.label,
                        palette = palette,
                        pinned = model.settings.courseHues[subject.code],
                        onPick = { hue -> model.setHue(subject.code, hue) },
                    )
                }
            }
        }

        Spacer(Modifier.height(Space.large))
        SectionLabel("What is cached")

        Card(Modifier.fillMaxWidth()) {
            Column {
                Fact("Last sync", agoLabel(model.fetchedAt))
                Fact("Entries", if (model.events.isEmpty()) "none" else model.events.size.toString())
                Fact("Courses", if (model.events.isEmpty()) "none" else model.courseCount.toString())
                Fact("Feed size", if (model.feedBytes == 0) "none" else sizeLabel(model.feedBytes))
                Fact("Stored in", model.storageLocation)

                Spacer(Modifier.height(Space.medium))
                PillButton("Forget the cached calendar", { model.forget() })
            }
        }

        Spacer(Modifier.height(Space.large))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = skema.inkMuted,
        fontSize = Type.small,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = Space.tight, start = Space.unit),
    )
}

@Composable
private fun Toggle(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = skema
    Row(
        Modifier.fillMaxWidth().padding(Space.small),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.ink, fontSize = Type.normal, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(detail, color = c.inkMuted, fontSize = Type.small)
        }
        Spacer(Modifier.width(Space.small))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Fact(label: String, value: String) {
    val c = skema
    Row(Modifier.fillMaxWidth().padding(vertical = Space.unit), verticalAlignment = Alignment.Top) {
        Text(label, color = c.inkMuted, fontSize = Type.small, modifier = Modifier.width(90.dp))
        Text(value, color = c.ink, fontSize = Type.small, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.small)
            .height(1.dp)
            .background(skema.hairline)
    )
}

/** One course as the settings list shows it: the code, and everything else. */
private data class Subject(val code: String, val label: String)

private fun subjectsOf(events: List<CalendarEvent>): List<Subject> {
    val counts = events.mapNotNull { it.courseCode }.groupingBy { it }.eachCount()
    return counts.keys.sorted().map { code ->
        val entries = counts.getValue(code)
        val tail = if (entries == 1) "1 entry" else entries.toString() + " entries"
        Subject(code, listOfNotNull(nameOf(events, code), tail).joinToString(SEPARATOR))
    }
}

/**
 * The course's name, tidied.
 *
 * textFor is asked for the room booking shape on purpose: that is the branch
 * that returns the course name as the title, with the term suffix that
 * repeats on every single entry and the leading code already stripped off.
 * Asking it saves duplicating that trimming here, where it would drift.
 */
private fun nameOf(events: List<CalendarEvent>, code: String): String? {
    val named = events.firstOrNull {
        it.courseCode == code && !it.location.courseName.isNullOrBlank()
    } ?: return null
    return textFor(named.summary, named.location.courseName, code, fromRoomBooking = true)
        .title
        .takeIf { it.isNotBlank() }
}
