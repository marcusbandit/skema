package com.marcusrosado.skemafordtu.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.marcusrosado.skemafordtu.data.ThemeChoice
import com.marcusrosado.skemafordtu.data.ViewMode
import com.marcusrosado.skemafordtu.ics.CalendarEvent
import com.marcusrosado.skemafordtu.ics.Terms
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

@Composable
fun SettingsScreen(model: SkemaModel) {
    val c = skema
    val scope = rememberCoroutineScope()

    // Keyed on the raw feed and on the two settings that decide what is
    // visible, because reading model.visible runs the merge and a key you
    // have to compute first is not a key.
    val semesters = remember(
        model.events,
        model.settings.collapseDuplicates,
        model.settings.enrolledFrom,
    ) {
        model.terms.map { term -> term to subjectsOf(model.eventsIn(term)) }
    }

    // Oldest semester first, which is the order the palette wants: a term is
    // coloured around whatever the terms before it already took, so passing
    // this autumn first would re-roll every older term the day a course is
    // added. The palette is rebuilt whenever a hue is pinned or released, so
    // every row below shows the redraw that pinning caused.
    val palette = remember(semesters, model.settings.courseHues, c.isDark) {
        CoursePalette.bySemester(
            semesters.map { (_, subjects) -> subjects.map { it.code } },
            model.settings.courseHues,
            c.isDark,
        )
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
            PillButton(
                "Done",
                { model.screen = Screen.Calendar },
                filled = true,
                ico = Ico.Check,
            )
        }

        Spacer(Modifier.height(Space.large))
        SectionLabel("Calendar link", Ico.Link)

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
                        ico = Ico.Sync,
                    )
                    if (model.status.isNotBlank()) {
                        // A failed sync gets the outline warning, not the
                        // solid clash triangle: this lands on paper beside
                        // muted text, and clash means two classes at once.
                        if (model.failed) {
                            Glyph(Ico.Warn, tint = c.accentStrong, size = IconSizeQuiet)
                        }
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
        SectionLabel("When you started", Ico.Day)

        EnrolmentSection(model)

        Spacer(Modifier.height(Space.large))
        SectionLabel("Behaviour", Ico.Tune)

        Card(Modifier.fillMaxWidth(), PaddingValues(Space.small)) {
            Column {
                Picker("Theme", Ico.Theme) {
                    Segmented(
                        options = ThemeChoice.entries,
                        selected = model.settings.theme,
                        label = { it.name },
                        onSelect = { choice -> model.update { it.copy(theme = choice) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Divider()
                Picker("Calendar view", Ico.Layout) {
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
                    ico = Ico.Month,
                    detail = "A teaching week runs Monday to Friday, so the weekend is left out and its width goes to the five days that hold something.",
                    checked = model.settings.showWeekend,
                    onChange = { v -> model.update { it.copy(showWeekend = v) } },
                )
                Divider()
                Toggle(
                    title = "Fold duplicate entries",
                    ico = Ico.Fold,
                    detail = "DTU lists most classes twice, once written by a teacher and once generated from the room booking. Merge the pair field by field: where the two differ the teacher's entry wins, and where it says nothing the booking fills in, which is how the rooms survive.",
                    checked = model.settings.collapseDuplicates,
                    onChange = { v -> model.update { it.copy(collapseDuplicates = v) } },
                )
            }
        }

        Spacer(Modifier.height(Space.large))
        SectionLabel("Subjects", Ico.Palette)

        Card(Modifier.fillMaxWidth(), PaddingValues(Space.small)) {
            Column {
                Text(
                    if (semesters.isEmpty()) {
                        "Courses turn up here after a sync."
                    } else {
                        "Colours are drawn at random within a semester, each one kept a set distance from the others that term. Two semesters may land on the same colour, since they never share a screen. Drag a strip to choose one yourself."
                    },
                    color = c.inkMuted,
                    fontSize = Type.small,
                    modifier = Modifier.padding(Space.small),
                )
                // Newest semester at the top: the term being taught is the
                // one worth reaching, and the ones under it are history. A
                // course taught twice appears under both, wearing the one
                // colour it was given.
                semesters.asReversed().forEach { (term, subjects) ->
                    val peers = subjects.map { it.code }
                    Divider()
                    SectionLabel(
                        term.label,
                        modifier = Modifier.padding(top = Space.small, start = Space.tight),
                    )
                    subjects.forEachIndexed { index, subject ->
                        if (index > 0) Divider()
                        CourseColorRow(
                            code = subject.code,
                            label = subject.label,
                            palette = palette,
                            peers = peers,
                            pinned = model.settings.courseHues[subject.code],
                            onPick = { hue -> model.setHue(subject.code, hue) },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(Space.large))
        SectionLabel("What is cached", Ico.Storage)

        Card(Modifier.fillMaxWidth()) {
            Column {
                Fact("Last sync", agoLabel(model.fetchedAt))
                Fact("Entries", if (model.events.isEmpty()) "none" else model.events.size.toString())
                Fact("Courses", if (model.events.isEmpty()) "none" else model.courseCount.toString())
                Fact("Feed size", if (model.feedBytes == 0) "none" else sizeLabel(model.feedBytes))
                Fact("Stored in", model.storageLocation)

                Spacer(Modifier.height(Space.medium))
                PillButton("Forget the cached calendar", { model.forget() }, ico = Ico.Trash)
            }
        }

        Spacer(Modifier.height(Space.large))
    }
}

/**
 * A section heading. The glyph is what makes a wall of settings scannable:
 * you find the link section by its shape long before you have read the word.
 *
 * The semester headings inside the subjects card pass no glyph. They sit
 * under one that is already there, and a second row of icons two steps in
 * would be decoration rather than a way to find anything.
 */
@Composable
private fun SectionLabel(text: String, ico: Ico? = null, modifier: Modifier = Modifier) {
    Row(
        modifier.padding(bottom = Space.tight, start = Space.unit),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.tight),
    ) {
        if (ico != null) Glyph(ico, tint = skema.inkMuted, size = IconSizeQuiet)
        Text(
            text.uppercase(),
            color = skema.inkMuted,
            fontSize = Type.small,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** A titled row holding a chooser, shaped like [Toggle] so the card lines up. */
@Composable
private fun Picker(title: String, ico: Ico, content: @Composable () -> Unit) {
    val c = skema
    Row(Modifier.fillMaxWidth().padding(Space.small), verticalAlignment = Alignment.Top) {
        // Centred in a box one type step tall, so the glyph lands on the
        // title's line rather than riding above it.
        Box(Modifier.height(Space.large), contentAlignment = Alignment.Center) {
            Glyph(ico, tint = c.inkMuted)
        }
        Spacer(Modifier.width(Space.small))
        Column(Modifier.weight(1f)) {
            Text(title, color = c.ink, fontSize = Type.normal, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(Space.tight))
            content()
        }
    }
}

@Composable
private fun Toggle(
    title: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    ico: Ico,
) {
    val c = skema
    Row(
        Modifier.fillMaxWidth().padding(Space.small),
        verticalAlignment = Alignment.Top,
    ) {
        // The glyphs line up in a column of their own, so the titles start on
        // one edge whatever shape sits beside them.
        // Centred in a box one type step tall, so the glyph lands on the
        // title's line rather than riding above it.
        Box(Modifier.height(Space.large), contentAlignment = Alignment.Center) {
            Glyph(ico, tint = c.inkMuted)
        }
        Spacer(Modifier.width(Space.small))
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

/**
 * The enrolment date: which end of the feed is actually yours.
 *
 * DTU's feed is the university's record rather than the student's. This one
 * reaches back to 2019, and the entries down there belong to whoever else
 * shares the org unit; they take up month cells and they take hues off the
 * course palette. Naming the day you arrived is the whole fix.
 */
@Composable
private fun EnrolmentSection(model: SkemaModel) {
    val c = skema
    val from = model.enrolledFrom
    val suggested = model.suggestedEnrolment
    val total = model.events.size

    // Counted on the raw feed rather than as events minus visible. Merging
    // folds DTU's duplicate pairs into single entries, so that difference
    // would be two effects added together and honest about neither.
    val hidden = remember(model.events, from) { countBefore(model.events, from) }
    val ifSuggested = remember(model.events, suggested) { countBefore(model.events, suggested) }

    val years = remember(model.events, from, suggested) {
        yearsCovered(model.events, listOfNotNull(from, suggested))
    }

    var picking by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column {
            Text(
                "The feed reaches further back than you do. Anything dated before this day is left out of the calendar and out of the course colours.",
                color = c.inkMuted,
                fontSize = Type.small,
            )

            Spacer(Modifier.height(Space.small))
            Text(
                if (from == null) "Showing everything the feed holds" else "Starting from " + dateLabel(from),
                color = c.ink,
                fontSize = Type.normal,
                fontWeight = FontWeight.Medium,
            )

            Spacer(Modifier.height(Space.unit))
            Text(
                effectOf(total, from, hidden, suggested, ifSuggested),
                color = c.inkMuted,
                fontSize = Type.small,
            )

            // The feed states its own semesters, so the date is offered by the
            // name of the term it opens. Asking someone to remember a date the
            // feed already knows is the rude version of this control.
            if (suggested != null && suggested != from) {
                Spacer(Modifier.height(Space.medium))
                PillButton(
                    "Start at " + Terms.of(suggested).label,
                    { model.update { it.copy(enrolledFrom = suggested.toString()) } },
                    filled = true,
                    ico = Ico.Check,
                )
            }

            Spacer(Modifier.height(Space.small))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.small)) {
                PillButton(
                    if (picking) "Close" else "Pick a month",
                    { picking = !picking },
                    ico = if (picking) Ico.Collapse else Ico.Expand,
                )
                if (from != null) {
                    PillButton(
                        "Show everything",
                        { model.update { it.copy(enrolledFrom = null) } },
                        ico = Ico.Reset,
                    )
                }
            }

            if (picking) {
                Spacer(Modifier.height(Space.medium))
                EnrolmentPicker(years, from) { date ->
                    model.update { it.copy(enrolledFrom = date.toString()) }
                    picking = false
                }
            }
        }
    }
}

/**
 * A month and a year, and no day grid.
 *
 * An enrolment date is a semester boundary rather than an appointment: the
 * autumn opens in the same week every year and nobody remembers which day.
 * The month is chosen and the first of it is stored, so landing a fortnight
 * early costs nothing, while a day grid would be thirty times the targets for
 * precision that changes no answer.
 */
@Composable
private fun EnrolmentPicker(years: IntRange, selected: LocalDate?, onPick: (LocalDate) -> Unit) {
    val c = skema

    // Opens on the year already set, else on this one.
    var year by remember(years, selected) {
        mutableStateOf((selected?.year ?: today().year).coerceIn(years.first, years.last))
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphButton(
                Ico.Back,
                "Show an earlier year",
                { year -= 1 },
                enabled = year > years.first,
            )
            Text(
                year.toString(),
                color = c.ink,
                fontSize = Type.normal,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            GlyphButton(
                Ico.Forward,
                "Show a later year",
                { year += 1 },
                enabled = year < years.last,
            )
        }

        Spacer(Modifier.height(Space.small))

        // Every month is spelled out: this is a settings card with room, and
        // "Sep" is a habit borrowed from column headers that have none.
        val months = (1..12).map { n -> n to monthLong(LocalDate(year, n, 1).month) }

        // Rows come from the column count and the padding from the remainder,
        // so the months stay in columns whatever either number becomes.
        Column(verticalArrangement = Arrangement.spacedBy(Space.unit)) {
            months.chunked(MONTH_COLUMNS).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Space.unit)) {
                    row.forEach { (number, name) ->
                        EnrolmentMonth(
                            label = name,
                            selected = selected != null &&
                                selected.year == year &&
                                selected.monthNumber == number,
                            onClick = { onPick(LocalDate(year, number, 1)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(MONTH_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun EnrolmentMonth(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = skema
    val shape = SquircleShape(Radius.chip)
    Box(
        modifier
            .background(if (selected) c.accent else c.surfaceSunken, shape)
            .clickable(onClick = onClick)
            .padding(vertical = Space.tight, horizontal = Space.unit),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) c.onAccent else c.ink,
            fontSize = Type.small,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/** Three across, because that is the width the longest month name needs. */
private const val MONTH_COLUMNS = 3

private fun countBefore(events: List<CalendarEvent>, date: LocalDate?): Int =
    if (date == null) 0 else events.count { it.start.date < date }

/**
 * The years the picker can reach: the ones the feed covers, widened to hold
 * this year and anything already set, so a date typed into the settings file
 * by hand is still reachable rather than stranded outside the range.
 */
private fun yearsCovered(events: List<CalendarEvent>, marks: List<LocalDate>): IntRange {
    val years = events.map { it.start.date.year } + marks.map { it.year } + today().year
    return years.min()..years.max()
}

/**
 * What the setting does to the feed, in numbers that are true.
 *
 * The count is of raw entries older than the date, which is exactly what the
 * date decides. Quoting the drop from events to visible instead would fold in
 * the duplicate merge, and report a number that belongs to neither setting.
 */
private fun effectOf(
    total: Int,
    from: LocalDate?,
    hidden: Int,
    suggested: LocalDate?,
    ifSuggested: Int,
): String = when {
    total == 0 -> "Nothing is cached yet, so there is nothing to leave out."
    from != null && hidden == 0 -> "Nothing in the feed is older than that."
    from != null -> "Leaving out $hidden of the ${countLabel(total)} the feed holds."
    suggested != null && ifSuggested > 0 ->
        "Starting at ${Terms.of(suggested).label} would leave out $ifSuggested of the " +
            "${countLabel(total)} the feed holds."
    else -> "Every entry the feed holds is on screen."
}

private fun countLabel(n: Int): String = if (n == 1) "1 entry" else "$n entries"
