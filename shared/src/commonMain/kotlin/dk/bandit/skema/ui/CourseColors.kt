package dk.bandit.skema.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One colour per course, computed from how many courses there are.
 *
 * There is no list of pretty hex values here on purpose. A fixed list of, say,
 * twelve colours is wrong the moment a term has thirteen courses, and it is
 * already wrong at three, because three colours picked out of a list of twelve
 * are nowhere near as far apart as three colours spread over the whole circle.
 * The hue is a function of the course's position in the sorted set of codes and
 * of the size of that set, so any n gets the widest separation n allows.
 *
 * One arc of the circle is not available: the app's own red. See
 * [CoursePalette.ACCENT_GUARD].
 *
 * The wheel is OKLCH, not HSV. Equal steps of HSV hue are not equal steps to
 * the eye: at one nominal value HSV yellow is glaring and HSV blue is muddy,
 * so an evenly spaced HSV ramp produces event blocks of visibly different
 * weight. OKLab was fitted to perceptual data, so holding L fixed and walking
 * the hue gives blocks that read as the same lightness, which is also what
 * makes a single contrast guarantee hold for every course.
 */
@Immutable
data class CourseTint(
    /** The event block's background. */
    val fill: Color,
    /** Text that sits on [fill]. At least 4.5:1 against it, in both themes. */
    val ink: Color,
    /** The saturated 2dp edge or accent line. */
    val rail: Color,
    /** A quiet wash, for month-view dots and hover. */
    val soft: Color,
)

/** Lightness 0..1, chroma 0..roughly 0.4, hue in degrees. */
@Immutable
data class Oklch(val l: Float, val c: Float, val h: Float) {

    /**
     * OKLCH to sRGB, the whole chain: polar to OKLab, cube the cone responses,
     * the LMS to linear sRGB matrix, then the sRGB transfer function.
     *
     * Colours outside sRGB are brought back by reducing chroma, not by
     * clamping the channels. Clamping a channel shifts the colour's lightness
     * by an amount that depends on the hue, and lightness is exactly what the
     * contrast guarantee in [CoursePalette] rests on; bisecting the chroma
     * holds L and h exact and only gives up saturation. The clamp at the end
     * catches floating point slop, nothing more.
     */
    fun toColor(): Color {
        val lightness = l.toDouble()
        val rad = h.toDouble() * DEG_TO_RAD
        val ca = cos(rad)
        val cb = sin(rad)

        val wanted = c.toDouble()
        var fit = linearSrgb(lightness, wanted * ca, wanted * cb)
        if (!inGamut(fit)) {
            // C = 0 is always representable, so the search always has a floor.
            var lo = 0.0
            var hi = wanted
            fit = linearSrgb(lightness, 0.0, 0.0)
            repeat(GAMUT_STEPS) {
                val mid = (lo + hi) / 2.0
                val probe = linearSrgb(lightness, mid * ca, mid * cb)
                if (inGamut(probe)) {
                    lo = mid
                    fit = probe
                } else {
                    hi = mid
                }
            }
        }
        return Color(
            red = encode(fit[0]),
            green = encode(fit[1]),
            blue = encode(fit[2]),
        )
    }
}

/** sRGB back to OKLCH. Used to check the forward path and to read a hue off an existing colour. */
fun Color.toOklch(): Oklch {
    val r = decode(red.toDouble())
    val g = decode(green.toDouble())
    val b = decode(blue.toDouble())

    val lms0 = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b
    val lms1 = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b
    val lms2 = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b

    val l0 = cubeRoot(lms0)
    val l1 = cubeRoot(lms1)
    val l2 = cubeRoot(lms2)

    val lightness = 0.2104542553 * l0 + 0.7936177850 * l1 - 0.0040720468 * l2
    val a = 1.9779984951 * l0 - 2.4285922050 * l1 + 0.4505937099 * l2
    val bb = 0.0259040371 * l0 + 0.7827717662 * l1 - 0.8086757660 * l2

    val hue = atan2(bb, a) / DEG_TO_RAD
    return Oklch(
        l = lightness.toFloat(),
        c = sqrt(a * a + bb * bb).toFloat(),
        h = wrapHue(hue.toFloat()),
    )
}

/**
 * WCAG 2 contrast, (lighter + 0.05) / (darker + 0.05) on relative luminance.
 * Body text needs 4.5; the palette below is tested against that for every
 * course in both themes.
 */
fun contrastRatio(a: Color, b: Color): Float {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    return (((max(la, lb) + 0.05) / (min(la, lb) + 0.05))).toFloat()
}

fun relativeLuminance(color: Color): Double =
    0.2126 * decode(color.red.toDouble()) +
        0.7152 * decode(color.green.toDouble()) +
        0.0722 * decode(color.blue.toDouble())

/**
 * The colours for one term, one theme.
 *
 * [codes] is every course code in the feed; blanks are dropped and the rest
 * sorted, so the same term always produces the same colours whatever order the
 * events arrived in. [overrides] pins a course to a hue in degrees, stored as
 * a plain number so settings can keep it as JSON. A pinned hue is taken out of
 * circulation: the courses that are left get re-spread over the arcs between
 * the pins, again by formula. An override only applies to a code that is
 * actually in [codes]; the palette describes this term, not every term.
 */
@Immutable
class CoursePalette(
    codes: List<String>,
    overrides: Map<String, Float> = emptyMap(),
    val dark: Boolean,
) {
    private val ramp = if (dark) DarkRamp else LightRamp

    /** Course code to hue in degrees. */
    val hues: Map<String, Float> = assignHues(codes, overrides)

    private val tints: Map<String, CourseTint> = hues.mapValues { (_, hue) -> ramp.tint(hue) }

    /** Chroma zero off the same ramp, so an unknown course still sits at the right lightness. */
    val neutral: CourseTint = ramp.tint(0f, saturated = false)

    /** Total, by design: 18 entries in the real feed carry no course code at all. */
    fun tint(code: String?): CourseTint = tints[code] ?: neutral

    fun hue(code: String?): Float? = hues[code]

    companion object {
        /**
         * DTU red, measured rather than chosen: 7A0000 and D10000 both come
         * out at OKLCH hue 29.2, and the dark theme's FF6161 at 23.5. The
         * three reds the university uses interchangeably really are the same
         * hue at three lightnesses.
         */
        const val ACCENT_HUE = 29.2f

        /**
         * Half the arc kept clear of [ACCENT_HUE]. Automatic assignment never
         * enters it, so no course can be mistaken for the app's own accent.
         *
         * The guard exists because spacing alone does not avoid the red. With
         * the whole circle in play and a step of 360/n, a six course term used
         * to land on hue 30, one degree off DTU red: its rail came out C83122
         * against the accent's D10000, so a perfectly ordinary course looked
         * like the conflict marker that EventBlock draws in accentStrong. n=4
         * and n=12 hit the same wall.
         *
         * 14 was picked off the numbers. Hue 30's rail sat 0.033 away from
         * D10000 in OKLab, under the distance two neighbouring courses sit
         * at, so it read as the accent rather than as a course. The guard
         * puts the nearest reachable hue at 19.5 degrees and its rail at
         * 0.077, and a term of six keeps its nearest at 41.7 degrees and
         * 0.155. Widening it further only helps counts where the courses
         * are already crowded against each other, and every degree comes
         * out of the palette: 14 spends 28 of the 360, 20 would spend 40.
         */
        const val ACCENT_GUARD = 14f

        /**
         * The phase of the automatic spread. The slots come out of the arc
         * geometry, so course one takes whichever slot sits nearest this hue
         * and the rest follow it round the circle. 150 is a green, and picking
         * a green means the course a term is usually named after keeps a
         * recognisable colour as courses are added and removed.
         *
         * With one course there is a single slot and nothing to choose, so the
         * anchor has no effect; that slot is the middle of the free arc, which
         * is as far from the red as it is possible to be.
         */
        const val START_HUE = 150f
    }
}

// ---------------------------------------------------------------------------
// Hue assignment
// ---------------------------------------------------------------------------

private fun assignHues(codes: List<String>, overrides: Map<String, Float>): Map<String, Float> {
    val present = codes.filter { it.isNotBlank() }.distinct().sorted()
    if (present.isEmpty()) return emptyMap()

    // An override is an explicit decision, so it is honoured exactly, even
    // inside the accent guard. The guard only governs automatic assignment.
    val pinned = present.mapNotNull { code -> overrides[code]?.let { code to wrapHue(it) } }.toMap()
    val free = present.filterNot { it in pinned }
    if (free.isEmpty()) return pinned

    // Marks cut the circle into arcs. Every pinned hue is a mark, and so are
    // the two edges of the accent guard, which is the whole trick: the guard
    // is a pin that no course can occupy, so it needs no code of its own.
    // Because both edges are always marks, every arc that falls out is either
    // wholly inside the guard or wholly outside it, and the two courses either
    // side of the guard are pushed apart rather than crowded together.
    val pins = pinned.values.distinct().toSet()
    val marks = (
        pins +
            wrapHue(CoursePalette.ACCENT_HUE - CoursePalette.ACCENT_GUARD) +
            wrapHue(CoursePalette.ACCENT_HUE + CoursePalette.ACCENT_GUARD)
        ).sorted()

    val starts = FloatArray(marks.size) { marks[it] }
    val widths = FloatArray(marks.size) { j ->
        val next = if (j + 1 < marks.size) marks[j + 1] else marks[0] + 360f
        next - marks[j]
    }
    // An arc lies inside the guard exactly when its midpoint does, since no
    // arc can straddle an edge.
    val blocked = BooleanArray(marks.size) { j ->
        hueDistance(starts[j] + widths[j] / 2f, CoursePalette.ACCENT_HUE) < CoursePalette.ACCENT_GUARD
    }

    // Each usable arc takes a share of the courses proportional to its width.
    // Largest remainder hands out the seats the division leaves over, ties
    // going to the lower arc so the result does not depend on sort order.
    val usable = marks.indices.filterNot { blocked[it] }
    val usableWidth = usable.map { widths[it] }.sum()
    val m = free.size
    val exact = FloatArray(marks.size)
    val counts = IntArray(marks.size)
    for (j in usable) {
        exact[j] = m * widths[j] / usableWidth
        counts[j] = exact[j].toInt()
    }
    val order = usable.sortedWith(compareByDescending<Int> { exact[it] - counts[it] }.thenBy { it })
    var leftover = m - counts.sum()
    var pick = 0
    while (leftover > 0) {
        counts[order[pick % order.size]]++
        leftover--
        pick++
    }

    // Placement inside an arc of width w holding c courses:
    //
    //     hue(i) = start + w * (i + m0) / (c - 1 + m0 + m1)
    //
    // where m0 and m1 are the margins the two ends ask for. A pinned course
    // sits on its end and wants a whole step of clearance, so its margin is 1
    // and the familiar (i + 1) / (c + 1) comes back out. A guard edge has
    // nothing sitting on it and already carries 14 degrees of its own, so half
    // a step is enough; a six course term with no overrides then spreads at
    // 332/6 rather than 332/7, which is the separation that was there before
    // the guard took its share.
    val slots = buildList {
        for (j in marks.indices) {
            val c = counts[j]
            if (c == 0) continue
            val m0 = if (marks[j] in pins) 1f else 0.5f
            val m1 = if (marks[(j + 1) % marks.size] in pins) 1f else 0.5f
            val denominator = c - 1 + m0 + m1
            for (i in 0 until c) add(wrapHue(starts[j] + widths[j] * (i + m0) / denominator))
        }
    }

    // The geometry fixes where the slots are; START_HUE fixes which one comes
    // first. The first course by code takes the slot nearest it and the rest
    // follow round the circle, so the set of hues is untouched and only the
    // pairing rotates. Ties go to the lower slot, which keeps this total.
    var anchor = 0
    for (i in slots.indices) {
        if (hueDistance(slots[i], CoursePalette.START_HUE) < hueDistance(slots[anchor], CoursePalette.START_HUE)) {
            anchor = i
        }
    }
    val rotated = List(slots.size) { slots[(anchor + it) % slots.size] }

    return pinned + free.zip(rotated).toMap()
}

/** Degrees between two hues the short way round, so never more than 180. */
fun hueDistance(a: Float, b: Float): Float {
    val d = abs(wrapHue(a) - wrapHue(b))
    return min(d, 360f - d)
}

/**
 * An override is stored as a plain number and read back, so a hue already in
 * range comes through untouched: 29.2 through the modulo twice comes back as
 * 29.200012, and a persisted setting that will not compare equal to itself is
 * a bug waiting to happen.
 */
private fun wrapHue(h: Float): Float =
    if (h >= 0f && h < 360f) h else ((h % 360f) + 360f) % 360f

// ---------------------------------------------------------------------------
// The two ramps
// ---------------------------------------------------------------------------

/**
 * One hue, four jobs. The lightness values are the design: `fill` is a wash a
 * long way from the ink so a block of text sits on it comfortably, `rail`
 * carries most of the chroma because a 2dp line needs saturation to register
 * at all, and `soft` is barely tinted for dots and hover.
 *
 * The numbers were picked by sweeping every hue and taking the worst contrast,
 * which is why they are not round: at fill 0.91 and ink 0.40 the poorest hue in
 * the light theme still clears 6.8:1, and the dark theme's poorest clears 7.9:1.
 */
private class Ramp(
    val fillL: Float, val fillC: Float,
    val inkL: Float, val inkC: Float,
    val railL: Float, val railC: Float,
    val softL: Float, val softC: Float,
) {
    fun tint(hue: Float, saturated: Boolean = true): CourseTint {
        val k = if (saturated) 1f else 0f
        return CourseTint(
            fill = Oklch(fillL, fillC * k, hue).toColor(),
            ink = Oklch(inkL, inkC * k, hue).toColor(),
            rail = Oklch(railL, railC * k, hue).toColor(),
            soft = Oklch(softL, softC * k, hue).toColor(),
        )
    }
}

private val LightRamp = Ramp(
    fillL = 0.91f, fillC = 0.080f,
    inkL = 0.40f, inkC = 0.140f,
    railL = 0.55f, railC = 0.190f,
    softL = 0.96f, softC = 0.035f,
)

private val DarkRamp = Ramp(
    fillL = 0.33f, fillC = 0.075f,
    inkL = 0.86f, inkC = 0.120f,
    railL = 0.72f, railC = 0.170f,
    softL = 0.24f, softC = 0.045f,
)

// ---------------------------------------------------------------------------
// OKLab plumbing
// ---------------------------------------------------------------------------

private const val DEG_TO_RAD = PI / 180.0

/** 24 halvings puts the chroma within 1e-7 of the gamut boundary. */
private const val GAMUT_STEPS = 24

/** Loose enough to let a colour that is exactly on the boundary through. */
private const val GAMUT_EPS = 1e-6

private fun linearSrgb(l: Double, a: Double, b: Double): DoubleArray {
    val lp = l + 0.3963377774 * a + 0.2158037573 * b
    val mp = l - 0.1055613458 * a - 0.0638541728 * b
    val sp = l - 0.0894841775 * a - 1.2914855480 * b

    val lc = lp * lp * lp
    val mc = mp * mp * mp
    val sc = sp * sp * sp

    return doubleArrayOf(
        4.0767416621 * lc - 3.3077115913 * mc + 0.2309699292 * sc,
        -1.2684380046 * lc + 2.6097574011 * mc - 0.3413193965 * sc,
        -0.0041960863 * lc - 0.7034186147 * mc + 1.7076147010 * sc,
    )
}

private fun inGamut(v: DoubleArray): Boolean =
    v.all { it >= -GAMUT_EPS && it <= 1.0 + GAMUT_EPS }

/** Linear light to the sRGB transfer function's output, clamped to the display range. */
private fun encode(v: Double): Float {
    val c = min(1.0, max(0.0, v))
    val e = if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1.0 / 2.4) - 0.055
    return e.toFloat()
}

private fun decode(v: Double): Double =
    if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)

/** kotlin.math.cbrt is not in the common stdlib, and the inputs can be negative. */
private fun cubeRoot(v: Double): Double =
    if (v < 0.0) -((-v).pow(1.0 / 3.0)) else v.pow(1.0 / 3.0)
