package com.marcusrosado.skemafordtu.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.marcusrosado.skemafordtu.ics.CalendarEvent

/**
 * The courses, and the colour each one wears.
 *
 * This was the bottom half of the settings screen, where every course carried
 * a swatch, a status line and a full width hue strip at once. Fifteen courses
 * meant fifteen colour editors stacked up, when the question being asked is
 * usually only which colour 02322 is. So a course is one row until it is
 * tapped, and only the tapped one becomes an editor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectsScreen(model: SkemaModel, onBack: () -> Unit) {
    val c = skema

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
    // added.
    val palette = remember(semesters, model.settings.courseHues, c.isDark) {
        CoursePalette.bySemester(
            semesters.map { (_, subjects) -> subjects.map { it.code } },
            model.settings.courseHues,
            c.isDark,
        )
    }

    // Which course is open for editing. One at a time, because two hue strips
    // on screen is the density this screen exists to undo.
    var open by remember { mutableStateOf<String?>(null) }

    // An open editor is the shallower thing, so back closes it first and only
    // then leaves for the settings underneath.
    BackHandler { if (open != null) open = null else onBack() }

    // Material's bar, with back where every Android app has it. The drag to
    // the right that used to leave this screen is the system's own back
    // gesture now, predictive animation included, so it is not drawn twice.
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Subjects") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Glyph(Ico.Back, tint = LocalContentColor.current, size = IconSizeLarge, description = "Back to settings")
                    }
                },
            )
        },
    ) { insets ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.medium)
        ) {
            if (semesters.isEmpty()) {
                Text("No courses yet", color = c.inkMuted, fontSize = Type.small)
            }

            // Newest semester at the top: the term being taught is the one
            // worth reaching, and the ones under it are history. A course
            // taught twice appears under both, wearing the one colour it was
            // given.
            semesters.asReversed().forEach { (term, subjects) ->
                val peers = subjects.map { it.code }
                TermLabel(term.label)
                Card(Modifier.fillMaxWidth(), PaddingValues(Space.unit)) {
                    Column {
                        subjects.forEach { subject ->
                            val editing = open == subject.code
                            Column(
                                Modifier.animateContentSize(
                                    tween(Motion.VIEW_IN, easing = Motion.arrive)
                                )
                            ) {
                                if (editing) {
                                    // Tapping anywhere the editor is not
                                    // using closes it again. The strip and
                                    // the pill inside consume their own
                                    // touches, so this only catches the rest.
                                    Column(Modifier.clickable { open = null }) {
                                        CourseColorRow(
                                            code = subject.code,
                                            // The editor writes the label on
                                            // its own line, so a course the
                                            // feed never named falls back to
                                            // its code rather than to a gap.
                                            label = subject.name.ifBlank { subject.code },
                                            palette = palette,
                                            peers = peers,
                                            pinned = model.settings.courseHues[subject.code],
                                            onPick = { hue -> model.setHue(subject.code, hue) },
                                        )
                                    }
                                } else {
                                    CourseRow(
                                        code = subject.code,
                                        name = subject.name,
                                        tint = palette.tint(subject.code),
                                        pinned = subject.code in model.settings.courseHues,
                                        onClick = { open = subject.code },
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(Space.medium))
            }

            Spacer(Modifier.height(Space.large))
        }
    }
}

/**
 * One course, closed: Material's list item with the code in its own colour
 * leading and the name as the line. No hue strip, no status line, no chevron.
 * Fifteen of these read as a list of courses, which is what the screen is,
 * and the row grows into the editor when it is asked to.
 */
@Composable
private fun CourseRow(
    code: String,
    name: String,
    tint: CourseTint,
    pinned: Boolean,
    onClick: () -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    // A hue chosen by hand is worth saying, and the glyph says it without
    // spending a line of type on the number of degrees.
    val trailing: (@Composable () -> Unit)? = if (!pinned) null else {
        { Glyph(Ico.Palette, tint = muted, size = IconSizeQuiet, description = "Colour chosen by hand") }
    }
    ListItem(
        headlineContent = {
            // A course the feed never named shows its code, rather than a gap.
            Text(name.ifBlank { code }, fontSize = Type.normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = { Tag(code, background = tint.fill, foreground = tint.ink) },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** The semester a run of courses belongs to. */
@Composable
private fun TermLabel(text: String) {
    Text(
        text.uppercase(),
        color = skema.inkMuted,
        fontSize = Type.small,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = Space.unit, bottom = Space.tight),
    )
}

/** One course as this list shows it: the code, and the name if the feed gave one. */
private data class Subject(val code: String, val name: String)

private fun subjectsOf(events: List<CalendarEvent>): List<Subject> {
    val codes = events.mapNotNull { it.courseCode }.distinct().sorted()
    return codes.map { code -> Subject(code, nameOf(events, code).orEmpty()) }
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
