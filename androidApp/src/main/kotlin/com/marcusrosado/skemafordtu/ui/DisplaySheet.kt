package com.marcusrosado.skemafordtu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.marcusrosado.skemafordtu.data.ThemeChoice
import com.marcusrosado.skemafordtu.data.ViewMode

/**
 * What the tune control opens: the calendar's own settings, as a sheet over
 * the calendar rather than a screen instead of it, so a change shows behind
 * the panel as it is made. The app's settings, the feed, the enrolment date,
 * storage and the subjects, stay on their screen behind the sidebar.
 *
 * The layout row is drawn rather than written: four small screens, one per
 * view, each sketching what that view lays out, in the accent for the one
 * that is on. A word says which view; a picture says what it looks like,
 * which is the question someone opening this is asking.
 */
@Composable
fun DisplaySheet(model: SkemaModel, onDismiss: () -> Unit) {
    val c = skema
    BottomSheet(onDismiss) {
        Text(
            "Settings",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Spacer(Modifier.height(Space.medium))

        SectionLabel("Layout")
        // Sunken rather than surface, because the sheet is already surface
        // and a card the colour of its page is not a card.
        Card(Modifier.fillMaxWidth(), PaddingValues(Space.small), background = c.surfaceSunken) {
            Row(Modifier.fillMaxWidth()) {
                ViewMode.entries.forEach { mode ->
                    LayoutTile(
                        mode = mode,
                        selected = model.view == mode,
                        onPick = { model.setView(mode) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Spacer(Modifier.height(Space.medium))
        SectionLabel("Display")
        Card(Modifier.fillMaxWidth(), PaddingValues(vertical = Space.unit), background = c.surfaceSunken) {
            Column {
                Picker("Theme", Ico.Theme) {
                    Segmented(
                        options = ThemeChoice.entries,
                        selected = model.settings.theme,
                        label = { it.name },
                        onSelect = { choice -> model.update { it.copy(theme = choice) } },
                        modifier = Modifier.fillMaxWidth(),
                        ico = { themeGlyph(it) },
                    )
                }
                Divider()
                Toggle(
                    title = "Show weekends",
                    ico = Ico.Month,
                    checked = model.settings.showWeekend,
                    onChange = { v -> model.update { it.copy(showWeekend = v) } },
                )
            }
        }
    }
}

/**
 * The theme choices as pictures: a monitor for whatever the phone says, a
 * sun and a moon for the two it can be told. Three words in a row read as
 * a sentence; three glyphs read as a switch.
 */
internal fun themeGlyph(choice: ThemeChoice): Ico = when (choice) {
    ThemeChoice.System -> Ico.Monitor
    ThemeChoice.Light -> Ico.Sun
    ThemeChoice.Dark -> Ico.Moon
}

/**
 * The little screen each view is sketched on. Phone proportions, and small
 * enough that four sit in a row on a folded phone with air between them.
 */
private val SCREEN_WIDTH = Space.large + Space.medium
private val SCREEN_HEIGHT = Space.large * 3

/** How many of each mark a sketch draws. The marks are placed from these, never by hand. */
private const val MONTH_COLUMNS = 3
private const val MONTH_ROWS = 4
private const val WEEK_COLUMNS = 3
private const val DAY_BLOCKS = 3
private const val AGENDA_LINES = 6

/** The cell of the month sketch that stands for today: the middle one. */
private const val MONTH_TODAY = MONTH_ROWS / 2 * MONTH_COLUMNS + MONTH_COLUMNS / 2

/** How faint an ordinary day is next to today in the month sketch. */
private const val MONTH_FAINT = 0.35f

/** An agenda entry is a title over a room, so every second line is shorter. */
private const val AGENDA_SHORT = 0.6f

/**
 * One view: its sketch above its name, and the accent on both when it is the
 * view that is on. The name sits in a soft pill then, which is the same mark
 * the sidebar puts behind the current view.
 */
@Composable
private fun LayoutTile(
    mode: ViewMode,
    selected: Boolean,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = skema
    val tint = if (selected) c.accent else c.inkMuted
    Column(
        modifier
            .clip(SquircleShape(Radius.card))
            .clickable(onClick = onPick)
            .padding(vertical = Space.small),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.small),
    ) {
        Box(
            Modifier
                .size(width = SCREEN_WIDTH, height = SCREEN_HEIGHT)
                .border(Space.unit / 2, tint, SquircleShape(Radius.chip))
                .padding(Space.tight),
        ) {
            Sketch(mode, tint)
        }
        Text(
            mode.name,
            color = tint,
            fontSize = Type.normal,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            modifier = Modifier
                .background(if (selected) c.accentSoft else Color.Transparent, pillShape())
                .padding(horizontal = Space.small, vertical = Space.unit),
        )
    }
}

/**
 * What each view lays out, in marks. A month is a grid, so its sketch is one,
 * with today darker than the rest. A week is columns, each holding a block
 * that steps down with the column's index, so the row reads as a timetable
 * rather than three bars. A day is one column of blocks the lengths of a
 * morning's lectures. An agenda is lines, a long one over a short one, the
 * way an entry is a title over a room.
 */
@Composable
private fun Sketch(mode: ViewMode, tint: Color) {
    val mark = SquircleShape(Space.unit / 2)
    val gap = Space.unit
    when (mode) {
        ViewMode.Month -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
            repeat(MONTH_ROWS) { row ->
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    repeat(MONTH_COLUMNS) { column ->
                        val today = row * MONTH_COLUMNS + column == MONTH_TODAY
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(if (today) tint else tint.copy(alpha = MONTH_FAINT), mark),
                        )
                    }
                }
            }
        }

        ViewMode.Week -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            repeat(WEEK_COLUMNS) { column ->
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    // The air above and below add up to the same in every
                    // column, so the blocks are the same height and only
                    // their position moves with the index.
                    Spacer(Modifier.weight(1f + column))
                    Box(Modifier.fillMaxWidth().weight(3f).background(tint, mark))
                    Spacer(Modifier.weight(1f + (WEEK_COLUMNS - 1 - column)))
                }
            }
        }

        ViewMode.Day -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
            repeat(DAY_BLOCKS) { i ->
                Box(Modifier.fillMaxWidth().weight(2f + i % 2).background(tint, mark))
            }
            Spacer(Modifier.weight(1f))
        }

        ViewMode.Agenda -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
            repeat(AGENDA_LINES) { i ->
                Box(
                    Modifier
                        .fillMaxWidth(if (i % 2 == 0) 1f else AGENDA_SHORT)
                        .height(gap)
                        .background(tint, mark),
                )
            }
        }
    }
}
