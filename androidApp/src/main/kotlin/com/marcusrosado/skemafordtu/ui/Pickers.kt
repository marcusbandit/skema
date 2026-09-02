package com.marcusrosado.skemafordtu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.plus
import kotlin.math.ceil

/**
 * The jump picker: a month and a year, both written out.
 *
 * Nothing in here is abbreviated. "Jun" and "Jul" differ by one letter in the
 * middle of a word, which is the worst possible place to put the difference,
 * and this panel exists precisely so a month can be chosen by reading it. The
 * grid is sized from the longest name there is rather than from a column count
 * picked in advance, so the full word always has room.
 *
 * The panel is a [BottomSheet], Material's, so its way in and out, the drag,
 * the scrim and the back gesture are every other sheet's. A pick lands first
 * and the panel leaves afterwards, so the month behind is already the month
 * asked for while the sheet is still sliding off it.
 */
@Composable
fun MonthYearSheet(
    initial: LocalDate,
    minYear: Int,
    maxYear: Int,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = skema
    val years = remember(minYear, maxYear) { (minYear..maxYear.coerceAtLeast(minYear)).toList() }
    var year by remember { mutableStateOf(initial.year.coerceIn(years.first(), years.last())) }

    BottomSheet(onDismiss) { close ->
        Column {
            Text(
                "Jump to",
                color = c.accent,
                fontSize = Type.large,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                "Now on " + monthLong(initial.month) + " " + initial.year,
                color = c.inkMuted,
                fontSize = Type.small,
                maxLines = 1,
            )
        }

        Spacer(Modifier.height(Space.medium))
        YearRow(years, year, onPick = { year = it })

        Spacer(Modifier.height(Space.small))
        MonthGrid(
            year = year,
            // The month is only the one you are on while the year on screen
            // is still the year you are on. Marking September in every year
            // would say something untrue.
            selected = if (year == initial.year) initial.month else null,
            onPick = { monthNumber ->
                onPick(jumpTo(year, monthNumber, initial.day))
                close()
            },
        )
    }
}

/**
 * The years, as one scrolling row of Material's filter chips.
 *
 * The range comes from the caller, which knows the enrolment date and the end
 * of the feed, so this never offers a decade nobody is enrolled for. It opens
 * one pill short of the current year, which is what puts the year you are on
 * next to the year before it rather than hard against the left edge.
 */
@Composable
private fun YearRow(years: List<Int>, selected: Int, onPick: (Int) -> Unit) {
    val start = (years.indexOf(selected) - 1).coerceAtLeast(0)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = start)
    LazyRow(
        state = state,
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.tight),
    ) {
        items(years.size, key = { years[it] }) { i ->
            FilterChip(
                selected = years[i] == selected,
                onClick = { onPick(years[i]) },
                label = { Text(years[i].toString(), fontSize = Type.small, fontWeight = FontWeight.Medium) },
            )
        }
    }
}

/** Twelve, and the grid is built from that number rather than from a shape. */
private const val MONTHS_IN_YEAR = 12

/** The narrowest and widest a month grid is allowed to get. */
private const val MIN_COLUMNS = 2
private const val MAX_COLUMNS = 4

/**
 * Rough advance of one character in the system sans, as a share of the type
 * size. The same estimate the event block uses to decide what it can say.
 */
private const val MONTH_GLYPH = 0.55f

/**
 * The months of one year.
 *
 * How many columns is a question about the longest name and the width there
 * is, so both are measured: at [Type.normal] a system sans runs about 0.55 of
 * its point size per character, which is the same estimate the event block
 * uses, and the widest month decides the cell. A folded phone gets three
 * columns, a narrow desktop window two, a wide one four, and no arrangement
 * ever has to shorten a word to fit.
 */
@Composable
private fun MonthGrid(year: Int, selected: Month?, onPick: (Int) -> Unit) {
    val glyph = with(LocalDensity.current) { Type.normal.toDp() } * MONTH_GLYPH
    val months = remember(year) { List(MONTHS_IN_YEAR) { LocalDate(year, it + 1, 1).month } }
    val names = remember(months) { months.map { monthLong(it) } }
    val cell = glyph * names.maxOf { it.length } + Space.small * 2

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = ((maxWidth + Space.tight) / (cell + Space.tight))
            .toInt()
            .coerceIn(MIN_COLUMNS, MAX_COLUMNS)
        val rows = ceil(MONTHS_IN_YEAR / columns.toFloat()).toInt()

        Column(verticalArrangement = Arrangement.spacedBy(Space.tight)) {
            repeat(rows) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Space.tight)) {
                    repeat(columns) { column ->
                        val index = row * columns + column
                        if (index < MONTHS_IN_YEAR) {
                            MonthCell(
                                label = names[index],
                                selected = months[index] == selected,
                                onClick = { onPick(index + 1) },
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            // The last row keeps its cells the width of every
                            // other row's rather than stretching to fill.
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthCell(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = skema
    // The theme's small squircle and the scheme's colours: the chosen month
    // is primary on onPrimary, the rest sit one tone up from the sheet.
    val shape = MaterialTheme.shapes.small
    Box(
        modifier
            .clip(shape)
            .background(if (selected) c.accent else c.surfaceSunken)
            .clickable(onClick = onClick)
            .padding(vertical = Space.small, horizontal = Space.unit),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) c.onAccent else c.ink,
            fontSize = Type.normal,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The date a pick lands on.
 *
 * The day of the month is carried across rather than reset to the first,
 * because in the week and day views the day is the thing being kept: jumping
 * from the 20th of one month to another should land on its 20th. Clamped to
 * the length of the month it arrives in, which is measured by stepping a month
 * on and a day back rather than by keeping a table of month lengths.
 */
private fun jumpTo(year: Int, monthNumber: Int, day: Int): LocalDate {
    val first = LocalDate(year, monthNumber, 1)
    val length = first.plus(1, DateTimeUnit.MONTH).shift(-1).day
    return LocalDate(year, monthNumber, day.coerceAtMost(length))
}
