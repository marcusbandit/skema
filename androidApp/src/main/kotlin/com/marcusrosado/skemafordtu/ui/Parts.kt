package com.marcusrosado.skemafordtu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

/**
 * The app's small vocabulary of controls, each a thin reading of Material's.
 *
 * They exist so a screen says PillButton or Segmented and gets the same
 * thing every other screen got, with the app's three type sizes and the
 * theme's squircle corners already applied. Nothing here draws a control of
 * its own any more; where Material has the part, Material draws it, and
 * dynamic colour, pressed states and touch targets come with it.
 */

/**
 * A pill button, with an optional glyph before the label. Filled is
 * Material's Button, the rest OutlinedButton; both are pills through the
 * theme's shape tokens.
 *
 * The glyph carries no description on purpose: the label is right beside it,
 * and a screen reader that read both would say the same word twice.
 */
@Composable
fun PillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    enabled: Boolean = true,
    ico: Ico? = null,
) {
    val content: @Composable RowScope.() -> Unit = {
        if (ico != null) {
            Glyph(ico, tint = LocalContentColor.current, size = IconSizeQuiet)
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        }
        Text(label, fontSize = Type.small, fontWeight = FontWeight.Medium)
    }
    if (filled) {
        Button(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
    }
}

/**
 * The same control with the label taken away, for the square slots in a
 * toolbar where a word would not fit.
 *
 * The description is required rather than defaulted. A button whose entire
 * content is a picture tells a screen reader nothing without one, and a
 * parameter you can forget is a parameter that gets forgotten.
 */
@Composable
fun GlyphButton(
    ico: Ico,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    enabled: Boolean = true,
) {
    val content: @Composable () -> Unit = {
        Glyph(ico, tint = LocalContentColor.current, description = description)
    }
    if (filled) {
        FilledIconButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
    } else {
        OutlinedIconButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
    }
}

/**
 * A small tag, used for course codes and for the conflict marker.
 *
 * A tag sits on a filled colour field, so the glyph it takes is one of the
 * solid ones. See Icons.kt for why that split exists. Still drawn by hand:
 * Material's chips are controls, and this is a label.
 */
@Composable
fun Tag(
    label: String,
    modifier: Modifier = Modifier,
    background: Color = skema.accentSoft,
    foreground: Color = skema.accent,
    ico: Ico? = null,
) {
    Row(
        modifier
            .background(background, MaterialTheme.shapes.small)
            .padding(horizontal = Space.tight, vertical = Space.unit / 2),
        horizontalArrangement = Arrangement.spacedBy(Space.unit),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (ico != null) Glyph(ico, tint = foreground, size = IconSizeQuiet)
        Text(
            label,
            color = foreground,
            fontSize = Type.small,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * A row of mutually exclusive choices, Material's segmented row. Widths come
 * from the count, so adding a fourth option needs no layout change. With
 * [ico] each option is a glyph instead of a word, and the word becomes what
 * a screen reader says for it.
 *
 * The end segments are Material's own item shapes cut from a squircle pill,
 * so the row's outer corners are the house curve like everything else.
 */
@Composable
fun <T> Segmented(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    ico: ((T) -> Ico)? = null,
) {
    SingleChoiceSegmentedButtonRow(modifier) {
        options.forEachIndexed { index, option ->
            val active = option == selected
            SegmentedButton(
                selected = active,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size, baseShape = SEGMENT),
                icon = {
                    if (ico != null) {
                        Glyph(ico(option), tint = LocalContentColor.current, size = IconSizeQuiet, description = label(option))
                    } else {
                        SegmentedButtonDefaults.Icon(active)
                    }
                },
                label = {
                    if (ico == null) Text(label(option), fontSize = Type.small, fontWeight = FontWeight.Medium)
                },
            )
        }
    }
}

/**
 * A card, Material's, with the theme's medium corners. Surface on the page;
 * sunken when the card itself sits on a surface, such as inside a sheet.
 */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(Space.medium),
    background: Color = skema.surface,
    content: @Composable () -> Unit,
) {
    androidx.compose.material3.Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = background),
    ) {
        Box(Modifier.padding(padding)) { content() }
    }
}

/** The pill the segmented row's end pieces are cut from. */
private val SEGMENT = SquircleShape(Radius.sheet)
