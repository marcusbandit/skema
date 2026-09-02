package com.marcusrosado.skemafordtu.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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

/**
 * Settings, and the subjects list it opens.
 *
 * Every row here used to carry a paragraph explaining how the setting was
 * built, which is a commit message rather than a label. A setting says what it
 * does, or says nothing when the title already did.
 *
 * Subjects were the other half of the problem: fifteen courses, each with a
 * swatch, a status line and a hue strip, filled the screen and buried
 * everything else. They live on their own screen now, reached by one row.
 */
@Composable
fun SettingsScreen(model: SkemaModel) {
    val reduced = reducedMotion()
    var subjects by remember { mutableStateOf(false) }

    // Back leaves one level at a time. Without this the gesture closed the
    // whole app from here, because nothing on the stack had claimed it. The
    // subjects screen claims it in turn while it is up, so this one stands
    // down and the two never both answer.
    BackHandler(enabled = !subjects) { model.screen = Screen.Calendar }

    AnimatedContent(
        targetState = subjects,
        modifier = Modifier.fillMaxSize(),
        // Opening subjects is a step inwards and leaving is a step back out,
        // the same grow-in shrink-out pair the calendar uses to change view.
        transitionSpec = {
            val transform = if (reduced) {
                fadeIn(tween(Motion.FADE)) togetherWith fadeOut(tween(Motion.FADE))
            } else {
                val deeper = targetState
                val from = if (deeper) 1f - Motion.VIEW_SCALE else 1f + Motion.VIEW_SCALE
                val to = if (deeper) 1f + Motion.VIEW_SCALE else 1f - Motion.VIEW_SCALE
                val enter = tween<Float>(Motion.VIEW_IN, easing = Motion.arrive)
                val exit = tween<Float>(Motion.VIEW_OUT, easing = Motion.leave)
                (fadeIn(enter) + scaleIn(enter, initialScale = from)) togetherWith
                    (fadeOut(exit) + scaleOut(exit, targetScale = to))
            }
            transform using SizeTransform(clip = false)
        },
        label = "settings",
    ) { open ->
        if (open) {
            SubjectsScreen(model, onBack = { subjects = false })
        } else {
            SettingsList(model, onSubjects = { subjects = true })
        }
    }
}

@Composable
private fun SettingsList(model: SkemaModel, onSubjects: () -> Unit) {
    val c = skema
    val scope = rememberCoroutineScope()

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
        SectionLabel("Feed", Ico.Link)

        Card(Modifier.fillMaxWidth()) {
            Column {
                OutlinedTextField(
                    value = model.settings.feedUrl,
                    onValueChange = { url -> model.update { it.copy(feedUrl = url.trim()) } },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text("Paste the link", color = c.inkMuted, fontSize = Type.small)
                    },
                    singleLine = false,
                    shape = SquircleShape(Radius.chip),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = Type.small,
                        color = c.ink,
                    ),
                )

                Spacer(Modifier.height(Space.small))
                // Where to find it, and the one thing about it worth a
                // warning: the token needs no login, so the link is a
                // password whether or not it looks like one.
                Text(
                    "DTU Learn, Calendar, Subscribe. The link needs no login, so keep it private.",
                    color = c.inkMuted,
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

        Spacer(Modifier.height(Space.small))
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
                    title = "Show weekends",
                    ico = Ico.Month,
                    checked = model.settings.showWeekend,
                    onChange = { v -> model.update { it.copy(showWeekend = v) } },
                )
                Divider()
                Toggle(
                    title = "Fold duplicate entries",
                    ico = Ico.Fold,
                    detail = "Merge the duplicate entries DTU sends",
                    checked = model.settings.collapseDuplicates,
                    onChange = { v -> model.update { it.copy(collapseDuplicates = v) } },
                )
                Divider()
                // The way in to the colours. A row that opens a list is a
                // list affordance, so it stays a row rather than growing a
                // button of its own.
                NavRow(
                    title = "Subjects",
                    value = if (model.courseCount == 0) "none" else model.courseCount.toString(),
                    ico = Ico.Palette,
                    trailing = Ico.Forward,
                    onClick = onSubjects,
                )
            }
        }

        Spacer(Modifier.height(Space.large))
        SectionLabel("Storage", Ico.Storage)

        Card(Modifier.fillMaxWidth()) {
            Column {
                Fact("Last sync", agoLabel(model.fetchedAt))
                Fact("Entries", if (model.events.isEmpty()) "none" else model.events.size.toString())
                Fact("Courses", if (model.events.isEmpty()) "none" else model.courseCount.toString())
                Fact("Feed size", if (model.feedBytes == 0) "none" else sizeLabel(model.feedBytes))
                Fact("Stored in", model.storageLocation)

                Spacer(Modifier.height(Space.medium))
                PillButton("Delete the cache", { model.forget() }, ico = Ico.Trash)
            }
        }

        Spacer(Modifier.height(Space.large))
    }
}

/**
 * A section heading. The glyph is what makes a wall of settings scannable:
 * you find the feed section by its shape long before you have read the word.
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

/**
 * A switch with a title, and a line under it only where the title leaves a
 * real question open. Most do not.
 */
@Composable
private fun Toggle(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    ico: Ico,
    detail: String? = null,
) {
    val c = skema
    Row(
        Modifier.fillMaxWidth().padding(Space.small),
        verticalAlignment = Alignment.Top,
    ) {
        // The glyphs line up in a column of their own, so the titles start on
        // one edge whatever shape sits beside them. Centred in a box one type
        // step tall, so the glyph lands on the title's line.
        Box(Modifier.height(Space.large), contentAlignment = Alignment.Center) {
            Glyph(ico, tint = c.inkMuted)
        }
        Spacer(Modifier.width(Space.small))
        Column(Modifier.weight(1f)) {
            Text(title, color = c.ink, fontSize = Type.normal, fontWeight = FontWeight.Medium)
            if (detail != null) {
                Spacer(Modifier.height(2.dp))
                Text(detail, color = c.inkMuted, fontSize = Type.small)
            }
        }
        Spacer(Modifier.width(Space.small))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * A row that goes somewhere: its current value on the right, and a glyph
 * saying whether the thing opens in place or on a screen of its own.
 */
@Composable
private fun NavRow(
    title: String,
    value: String,
    ico: Ico,
    trailing: Ico,
    onClick: () -> Unit,
) {
    val c = skema
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(Space.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.height(Space.large), contentAlignment = Alignment.Center) {
            Glyph(ico, tint = c.inkMuted)
        }
        Spacer(Modifier.width(Space.small))
        Text(
            title,
            color = c.ink,
            fontSize = Type.normal,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Text(value, color = c.inkMuted, fontSize = Type.small)
        Spacer(Modifier.width(Space.tight))
        Glyph(trailing, tint = c.inkMuted, size = IconSizeQuiet)
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

/**
 * The enrolment date: which end of the feed is actually yours.
 *
 * DTU's feed is the university's record rather than the student's, so it
 * reaches back years before you arrived and those entries take up month cells
 * and course colours. One row, and the month grid only once it is asked for.
 */
@Composable
private fun EnrolmentSection(model: SkemaModel) {
    val c = skema
    val from = model.enrolledFrom
    val suggested = model.suggestedEnrolment

    // Counted on the raw feed rather than as events minus visible. Merging
    // folds DTU's duplicate pairs into single entries, so that difference
    // would be two effects added together and honest about neither.
    val hidden = remember(model.events, from) { countBefore(model.events, from) }
    val years = remember(model.events, from, suggested) {
        yearsCovered(model.events, listOfNotNull(from, suggested))
    }

    var picking by remember { mutableStateOf(false) }

    // The picker is the shallower thing on screen, so back closes it before
    // it closes the settings under it.
    BackHandler(enabled = picking) { picking = false }

    Card(Modifier.fillMaxWidth(), PaddingValues(Space.small)) {
        Column(
            Modifier.animateContentSize(tween(Motion.VIEW_IN, easing = Motion.arrive))
        ) {
            NavRow(
                title = "Starting from",
                value = if (from == null) "everything" else monthLong(from.month) + " " + from.year,
                ico = Ico.Day,
                trailing = if (picking) Ico.Collapse else Ico.Expand,
                onClick = { picking = !picking },
            )

            if (picking) {
                Divider()
                Column(Modifier.padding(Space.small)) {
                    EnrolmentPicker(years, from) { date ->
                        model.update { it.copy(enrolledFrom = date.toString()) }
                        picking = false
                    }

                    Spacer(Modifier.height(Space.small))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.small)) {
                        // The feed states its own semesters, so the date is
                        // offered by the name of the term it opens rather
                        // than asking anyone to remember it.
                        if (suggested != null && suggested != from) {
                            PillButton(
                                Terms.of(suggested).label,
                                {
                                    model.update { it.copy(enrolledFrom = suggested.toString()) }
                                    picking = false
                                },
                                filled = true,
                                ico = Ico.Check,
                            )
                        }
                        if (from != null) {
                            PillButton(
                                "Everything",
                                {
                                    model.update { it.copy(enrolledFrom = null) }
                                    picking = false
                                },
                                ico = Ico.Reset,
                            )
                        }
                    }

                    if (from != null && hidden > 0) {
                        Spacer(Modifier.height(Space.small))
                        Text(
                            "Hides $hidden older entries",
                            color = c.inkMuted,
                            fontSize = Type.small,
                        )
                    }
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
