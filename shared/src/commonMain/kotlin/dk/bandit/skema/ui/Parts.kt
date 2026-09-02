package dk.bandit.skema.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * A pill button, with an optional glyph before the label.
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
    val c = skema
    val shape = pillShape()
    val fg = when {
        !enabled -> c.inkMuted
        filled -> c.onAccent
        else -> c.accent
    }
    Box(
        modifier
            .background(if (filled && enabled) c.accent else Color.Transparent, shape)
            .then(if (filled) Modifier else Modifier.border(1.dp, c.hairline, shape))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Space.medium, vertical = Space.tight),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.tight),
        ) {
            if (ico != null) Glyph(ico, tint = fg, size = IconSizeQuiet)
            Text(
                label,
                color = fg,
                fontSize = Type.small,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
        }
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
    val c = skema
    val shape = SquircleShape(Radius.chip)
    val fg = when {
        !enabled -> c.inkMuted
        filled -> c.onAccent
        else -> c.accent
    }
    Box(
        modifier
            .background(if (filled && enabled) c.accent else Color.Transparent, shape)
            .then(if (filled) Modifier else Modifier.border(1.dp, c.hairline, shape))
            .clickable(enabled = enabled, onClick = onClick)
            // Equal on all four sides, which is what makes the control square.
            .padding(Space.tight),
        contentAlignment = Alignment.Center,
    ) {
        Glyph(ico, tint = fg, description = description)
    }
}

/**
 * A small tag, used for course codes and for the conflict marker.
 *
 * A tag sits on a filled colour field, so the glyph it takes is one of the
 * solid ones. See Icons.kt for why that split exists.
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
            .background(background, SquircleShape(Radius.chip))
            .padding(horizontal = Space.tight, vertical = 2.dp),
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
 * A row of mutually exclusive choices. Widths come from the count, so adding
 * a fourth option needs no layout change.
 */
@Composable
fun <T> Segmented(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = skema
    Row(
        modifier
            .background(c.surfaceSunken, pillShape())
            .padding(2.dp),
    ) {
        options.forEach { option ->
            val active = option == selected
            Box(
                Modifier
                    .weight(1f)
                    .background(if (active) c.accent else Color.Transparent, pillShape())
                    .clickable { onSelect(option) }
                    .padding(vertical = Space.tight),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    color = if (active) c.onAccent else c.inkMuted,
                    fontSize = Type.small,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/** A surface card. The only place the card radius is named. */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(Space.medium),
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .background(skema.surface, SquircleShape(Radius.card))
            .padding(padding)
    ) { content() }
}
