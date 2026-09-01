package dk.bandit.skema.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Picking a course's colour by hand.
 *
 * A colour here is one number, a hue in degrees, because the rest of the ramp
 * is fixed by [CoursePalette] to hold contrast: lightness and chroma are what
 * make the text on an event block readable, so they are not the user's to
 * move. That leaves a single continuous quantity, and a continuous quantity
 * gets a strip you drag rather than a tray of swatches to pick from. Swatches
 * would also have to be a fixed list, and a fixed list is the thing the
 * palette exists to avoid.
 *
 * Clearing an override hands the course back to the palette, which re-spreads
 * every unpinned course over the arcs left between the pinned ones.
 */

/** Tall enough to hit with a thumb, short enough not to dominate the row. */
private val StripHeight = 32.dp

/** One sample per this many dp of measured width. */
private const val SAMPLE_STEP_DP = 2f

/**
 * Roughly where two OKLCH hues at one lightness stop being reliably told
 * apart side by side. Used only to warn, never to move a hue the user chose.
 */
private const val HUE_JND = 24f

@Composable
fun CourseColorRow(
    code: String,
    label: String,
    palette: CoursePalette,
    pinned: Float?,
    onPick: (Float?) -> Unit,
) {
    val c = skema
    val tint = palette.tint(code)
    // The palette is the authority on where the course currently sits, so the
    // marker follows it rather than the stored override. An unpinned course
    // still has a hue, and the strip should say where.
    val hue = palette.hue(code)
    val clashes = remember(code, palette) { clashing(code, palette) }

    Column(Modifier.fillMaxWidth().padding(Space.small)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Swatch(code, tint)
            Spacer(Modifier.width(Space.small))
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    color = c.ink,
                    fontSize = Type.normal,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (pinned == null) "Automatic" else "Pinned at " + pinned.roundToInt() + "°",
                    color = c.inkMuted,
                    fontSize = Type.small,
                )
            }
            if (pinned != null) {
                Spacer(Modifier.width(Space.small))
                PillButton("Automatic", { onPick(null) })
            }
        }

        Spacer(Modifier.height(Space.tight))
        HueStrip(hue = hue, pinned = pinned != null, onPick = { onPick(it) })

        if (clashes.isNotEmpty()) {
            Spacer(Modifier.height(Space.tight))
            Text(
                "Hard to tell apart from " + clashes.joinToString(SEPARATOR),
                color = c.accentStrong,
                fontSize = Type.small,
            )
        }
    }
}

/** The course's colour as the calendar draws it: fill, rail and ink together. */
@Composable
private fun Swatch(code: String, tint: CourseTint) {
    Row(
        Modifier
            .clip(SquircleShape(Radius.chip))
            .background(tint.fill)
            .height(StripHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(tint.rail))
        Text(
            code,
            modifier = Modifier.padding(horizontal = Space.tight),
            color = tint.ink,
            fontSize = Type.small,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * The hue circle, laid out left to right, with a marker where the course sits.
 *
 * The band is sampled rather than gradient filled so that every pixel of it is
 * a colour the app would really use: each slice is a hue run through the same
 * [CoursePalette] the calendar draws with, in the theme that is on. A linear
 * gradient between a handful of stops would interpolate in sRGB and show
 * colours the palette can never produce.
 */
@Composable
private fun HueStrip(hue: Float?, pinned: Boolean, onPick: (Float) -> Unit) {
    val c = skema
    val dark = c.isDark
    val density = LocalDensity.current.density

    var widthPx by remember { mutableStateOf(0) }

    // Sample count follows the measured width, so a wide window gets a finer
    // band instead of the same handful of colours stretched over more space.
    val samples = remember(widthPx, density) {
        max(2, (widthPx / density / SAMPLE_STEP_DP).roundToInt())
    }
    val band = remember(samples, dark) { bandColors(samples, dark) }

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(StripHeight)
            .clip(SquircleShape(Radius.chip))
            .onSizeChanged { widthPx = it.width }
            .pointerInput(widthPx) {
                // Touching the strip picks straight away and the finger keeps
                // picking as it moves, so a tap is just a drag of no length
                // and there is no slop to cross before the colour answers.
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (widthPx > 0) onPick(hueAt(down.position.x, widthPx))
                    down.consume()
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { change ->
                            if (change.pressed) {
                                if (widthPx > 0) onPick(hueAt(change.position.x, widthPx))
                                change.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        val slice = size.width / band.size
        band.forEachIndexed { i, colour ->
            // A hair of overlap, or antialiasing leaves seams between slices.
            drawRect(colour, Offset(i * slice, 0f), Size(slice + 1f, size.height))
        }

        if (hue != null) {
            // A halo in the surface colour so the marker is legible against
            // any hue, with an ink core only when the hue was chosen by hand.
            // Automatic reads as a notch, pinned as a solid caret.
            val halo = Space.tight.toPx()
            val core = 2.dp.toPx()
            val x = (hue / 360f * size.width).coerceIn(halo / 2f, size.width - halo / 2f)
            drawRect(c.surface, Offset(x - halo / 2f, 0f), Size(halo, size.height))
            if (pinned) drawRect(c.ink, Offset(x - core / 2f, 0f), Size(core, size.height))
        }
    }
}

private fun hueAt(x: Float, widthPx: Int): Float =
    (x / widthPx).coerceIn(0f, 1f) * 360f

/**
 * The strip's colours, taken from a throwaway palette whose courses are each
 * pinned to one sample hue.
 *
 * Going through [CoursePalette] rather than converting here means the strip
 * shows the exact ramp an event block will use, in the current theme, and
 * that there is still only one place in the app that turns a hue into a
 * colour. The rail is the band's colour because it carries most of the
 * chroma; the fill is a wash by design and a strip of washes is a strip you
 * cannot pick a hue out of.
 */
private fun bandColors(samples: Int, dark: Boolean): List<Color> {
    val codes = List(samples) { it.toString() }
    val pins = codes.withIndex().associate { (i, code) -> code to i * 360f / samples }
    val palette = CoursePalette(codes, pins, dark)
    return codes.map { palette.tint(it).rail }
}

/**
 * Course codes whose colour is close enough to this one to be mistaken for it.
 *
 * The limit is half the spacing the palette would hand out for this many
 * courses, capped at the point where two hues stop being separable at a
 * glance. Without the cap a two course term would call 60 degrees a
 * collision; without the halving a crowded term would call everything one.
 * This only warns. A hue the owner pinned is never moved for them.
 */
private fun clashing(code: String, palette: CoursePalette): List<String> {
    val mine = palette.hue(code) ?: return emptyList()
    val n = palette.hues.size
    if (n < 2) return emptyList()
    val limit = min(HUE_JND, 360f / n / 2f)
    return palette.hues
        .filter { (other, h) -> other != code && hueGap(mine, h) < limit }
        .keys
        .sorted()
}

/** Degrees apart on the circle, the short way round. */
private fun hueGap(a: Float, b: Float): Float {
    val d = abs(a - b) % 360f
    return min(d, 360f - d)
}
