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
 * One colour per course, drawn at random and kept apart by a margin.
 *
 * There is no list of pretty hex values here on purpose. A fixed list of, say,
 * twelve colours is wrong the moment a term has thirteen courses, and it is
 * already wrong at three, because three colours picked out of a list of twelve
 * are nowhere near as far apart as three colours spread over the whole circle.
 * The hue is drawn from the circle instead, and every draw has to sit a
 * computed distance from the hues that semester already holds, so any n gets
 * as much separation as n allows and no two courses arrive on the same blue.
 *
 * Random, but the same every launch: the generator is seeded from the
 * semester's own course codes. See [assignHues].
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
 * Straight-line distance in OKLab.
 *
 * Two course fills are built at one lightness on purpose, so [contrastRatio]
 * between any two of them is close to 1:1 whatever their hues; a ratio only
 * sees luminance and is blind to the thing that tells two courses apart. This
 * is the measurement that is not blind to it, and it is what
 * CourseColorsTest asserts a floor on.
 */
fun oklabDistance(x: Color, y: Color): Float {
    val p = x.toOklch()
    val q = y.toOklch()
    val dl = (p.l - q.l).toDouble()
    val da = p.c * cos(p.h * DEG_TO_RAD) - q.c * cos(q.h * DEG_TO_RAD)
    val db = p.c * sin(p.h * DEG_TO_RAD) - q.c * sin(q.h * DEG_TO_RAD)
    return sqrt(dl * dl + da * da + db * db).toFloat()
}

/**
 * The colours for one feed, one theme.
 *
 * A palette colours whatever it is handed. The plain constructor treats its
 * list as a single semester; [CoursePalette.bySemester] takes one list per
 * semester and colours each in turn, which is the shape the app uses. Two
 * courses in different semesters may end up on the same colour, because they
 * never share a screen, and that permission is what buys the separation: a
 * term holds about five courses where the whole feed holds fifteen, and five
 * courses can be three times further apart.
 *
 * [overrides] pins a course to a hue in degrees, stored as a plain number so
 * settings can keep it as JSON. A pinned hue is honoured exactly and is never
 * moved, guard or no guard; the courses sharing its semester are placed around
 * it. An override only applies to a code the palette was handed, since the
 * palette describes this feed and not every feed.
 */
@Immutable
class CoursePalette private constructor(
    /** Course code to hue in degrees. */
    val hues: Map<String, Float>,
    val dark: Boolean,
) {

    /** One flat list of courses, coloured as though they were one semester. */
    constructor(
        codes: List<String>,
        overrides: Map<String, Float> = emptyMap(),
        dark: Boolean,
    ) : this(assignHues(listOf(codes), overrides), dark)

    private val ramp = if (dark) DarkRamp else LightRamp

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
         * The guard exists because separation alone does not avoid the red.
         * With the whole circle in play a six course term used to land on hue
         * 30, one degree off DTU red: its rail came out C83122 against the
         * accent's D10000, so a perfectly ordinary course looked like the
         * conflict marker that EventBlock draws in accentStrong.
         *
         * 14 was picked off the numbers. Hue 30's rail sat 0.033 away from
         * D10000 in OKLab, under the distance two neighbouring courses sit
         * at, so it read as the accent rather than as a course. The guard
         * puts the nearest reachable hue at 19.5 degrees and its rail at
         * 0.077. Widening it further only helps counts where the courses are
         * already crowded against each other, and every degree comes out of
         * the palette: 14 spends 28 of the 360, 20 would spend 40.
         */
        const val ACCENT_GUARD = 14f

        /** The circle less the guard at both ends: everything a draw can reach. */
        const val FREE_ARC = 360f - 2f * ACCENT_GUARD

        /**
         * How much of the even step the margin claims, leaving the rest as
         * slack for the random draw.
         *
         * Where the share is the binding term it leaves (1 - 0.7) * 332,
         * near enough 100 degrees, to hand out at random however many courses
         * there are, which is what makes a term look drawn rather than
         * stepped. Below n = 7 the ideal binds instead and there is more; past
         * n = 21 the floor binds and there is less.
         */
        const val MARGIN_SHARE = 0.7f

        /**
         * The margin stops growing here. Two fills 40 degrees apart measure
         * 0.046 in OKLab dark and 0.064 light, four times the 0.010 floor the
         * palette is held to, so a term of five spending more of the circle
         * on separation buys nothing and gives up all its variety.
         */
        const val MARGIN_IDEAL = 40f

        /**
         * The margin stops shrinking here, unless the circle is too full to
         * grant it.
         *
         * Measured on the dark ramp, which is the binding one: the cyans hit
         * the sRGB chroma ceiling at fill lightness 0.39, so a pair around
         * hue 188 measures 0.0105 in OKLab at 11 degrees apart and 0.0088 at
         * 10. The floor for two fills is 0.010, so 11 degrees is where two
         * dark blocks stop being different colours.
         */
        const val MARGIN_FLOOR = 11f

        /**
         * The separation n courses in one semester are guaranteed, in degrees.
         *
         * n courses cannot all be more than [FREE_ARC] / n apart, so the ask
         * is a share of that even step and not a fixed number of degrees,
         * which would break at large n. [MARGIN_IDEAL] and [MARGIN_FLOOR] cap
         * it at both ends, and the even step itself wins over the floor once
         * the term is large enough that the floor will not fit, which is n =
         * 31 and up. There the margin is the whole step, the slack is zero,
         * and the palette degrades to the even spread, the most separated
         * arrangement there is.
         *
         * The guarantee holds between automatically placed hues. A pin the
         * owner dragged somewhere is honoured exactly, so two pins can sit as
         * close as the owner put them; ColorEditor says so rather than moving
         * them.
         */
        fun marginFor(n: Int): Float {
            val step = FREE_ARC / max(1, n)
            return min(step, max(MARGIN_FLOOR, min(MARGIN_IDEAL, step * MARGIN_SHARE)))
        }

        /**
         * One palette over several semesters, each coloured within itself.
         *
         * Two subjects in different terms wearing the same colour does not
         * matter, because they never appear together. A course taught in two
         * terms still gets one colour, taken from whichever term is placed
         * first, so it does not change identity halfway through a degree.
         * Pass the semesters oldest first and a new term can never re-roll an
         * old one.
         */
        fun bySemester(
            semesters: List<List<String>>,
            overrides: Map<String, Float> = emptyMap(),
            dark: Boolean,
        ): CoursePalette = CoursePalette(assignHues(semesters, overrides), dark)
    }
}

// ---------------------------------------------------------------------------
// Hue assignment
// ---------------------------------------------------------------------------

/**
 * Hues for every course, one semester at a time.
 *
 * The hues are drawn from the circle rather than stepped round it, and every
 * draw keeps [CoursePalette.marginFor] degrees from each hue the same semester
 * already holds. The draw is what stops a term reading as a colour wheel with
 * the courses in alphabetical order; the margin is what stops two of them
 * arriving on the same blue.
 *
 * Random, and the same on every launch. The generator is seeded from the
 * semester's own sorted course codes, so the seed is a property of the data:
 * no clock, no platform RNG, no first-run file to write. The same roster
 * reproduces the same palette on every start and on every device, and a
 * palette that changed on every launch would be the actual bug here. Change
 * the roster and that term re-rolls, which is the one time colours should
 * move.
 *
 * Semesters are taken in the order given and a course placed by an earlier one
 * keeps its hue instead of getting a second. Callers pass the oldest first, so
 * adding this autumn's courses cannot disturb last spring's.
 */
private fun assignHues(
    semesters: List<List<String>>,
    overrides: Map<String, Float>,
): Map<String, Float> {
    val groups = semesters
        .map { term -> term.filter { it.isNotBlank() }.distinct().sorted() }
        .filter { it.isNotEmpty() }
    val present = groups.flatten().distinct().sorted()
    if (present.isEmpty()) return emptyMap()

    // An override is an explicit decision, so it is honoured exactly, even
    // inside the accent guard. The guard only governs automatic assignment.
    val placed = HashMap<String, Float>()
    for (code in present) overrides[code]?.let { placed[code] = wrapHue(it) }

    for (group in groups) placeSemester(group, placed)

    // Sorted, so the map reads the same whatever order the events arrived in.
    return present.associateWith { placed.getValue(it) }
}

/**
 * Places the courses of one semester that do not have a hue yet.
 *
 * Marks cut the circle into arcs. Every hue this semester already holds is a
 * mark, and so are the two edges of the accent guard, which is the whole
 * trick: the guard is a pin no course can occupy, so it needs no code of its
 * own. Because both edges are always marks, every arc that falls out is either
 * wholly inside the guard or wholly outside it, and the two courses either
 * side of the guard are pushed apart rather than crowded together.
 */
private fun placeSemester(members: List<String>, placed: MutableMap<String, Float>) {
    val pending = members.filterNot { it in placed }
    if (pending.isEmpty()) return

    // The margin comes from the size of the whole semester, not from how many
    // of it are still unplaced: the courses already sitting there take up the
    // same room whether they were pinned by hand or by an earlier term.
    val target = CoursePalette.marginFor(members.size)
    val random = HueRandom(seedOf(members))

    val pins = members.mapNotNull { placed[it] }.distinct()
    val pinned = pins.toSet()
    val marks = (
        pins +
            wrapHue(CoursePalette.ACCENT_HUE - CoursePalette.ACCENT_GUARD) +
            wrapHue(CoursePalette.ACCENT_HUE + CoursePalette.ACCENT_GUARD)
        ).distinct().sorted()

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

    // What each end of an arc asks for, in margins. A mark with a course
    // sitting on it wants a whole one. A guard edge has nothing sitting on it
    // and already carries 14 degrees of its own, so half is enough there.
    val heads = FloatArray(marks.size) { j -> if (marks[j] in pinned) 1f else 0.5f }
    val tails = FloatArray(marks.size) { j -> if (marks[(j + 1) % marks.size] in pinned) 1f else 0.5f }

    // Courses go one at a time to whichever arc would keep the most room
    // spare after taking one. That spreads them by width, and it is what
    // stops a pin's leftovers from being crowded: sharing them out in
    // proportion to width instead dropped a course into the 16.8 degree
    // sliver that a pin at hue 60 leaves against the guard, where the margin
    // collapsed to 11.2 degrees while 315 degrees sat empty next door.
    // Balancing the spare room is the allocation that maximises the tightest
    // arc, so when any split of the courses fits the margin, this one does.
    val usable = marks.indices.filterNot { blocked[it] }
    val counts = IntArray(marks.size)
    if (usable.isEmpty()) {
        // Out of reach with a 28 degree guard on a 360 degree circle, since
        // the arc opposite the red can never be blocked. Kept so that a
        // future guard cannot silently produce a term with no hues at all.
        counts[widths.indices.maxByOrNull { widths[it] } ?: 0] = pending.size
    } else {
        repeat(pending.size) {
            var best = usable[0]
            var bestRoom = -Float.MAX_VALUE
            for (j in usable) {
                val room = widths[j] - target * (counts[j] + heads[j] + tails[j])
                if (room > bestRoom) {
                    bestRoom = room
                    best = j
                }
            }
            counts[best]++
        }
    }

    // Which course takes which hue is drawn as well, so the alphabet decides
    // the colours no more than the circle does.
    val takers = shuffled(pending, random)
    var next = 0
    for (j in marks.indices) {
        val count = counts[j]
        if (count == 0) continue
        for (hue in arcHues(starts[j], widths[j], count, heads[j], tails[j], target, random)) {
            placed[takers[next]] = hue
            next++
        }
    }
}

/**
 * Where [count] courses land inside one arc.
 *
 * The arc is a run of gaps: from its start to the first course, then between
 * neighbours, then from the last course to its end. Every inner gap has to be
 * at least the margin and the two ends ask for [headShare] and [tailShare] of
 * one; what is left after those minimums is slack, and the slack is where the
 * randomness lives. Splitting it by sorting [count] uniform draws and taking
 * the differences lands a uniform point on the simplex, so the courses are
 * uniform over every arrangement the margin allows rather than wobbling around
 * fixed slots. Drawing positions independently instead would need rejection
 * and could fail; this cannot.
 *
 * The margin degrades in one step rather than by a search. An arc of width w
 * holding c courses cannot do better than w / (c - 1 + ends), so the ask is
 * capped there. When the circle is full that cap wins, the slack falls to
 * zero, and the arrangement collapses to the even spread. Nothing loops and
 * nothing throws.
 */
private fun arcHues(
    start: Float,
    width: Float,
    count: Int,
    headShare: Float,
    tailShare: Float,
    target: Float,
    random: HueRandom,
): List<Float> {
    val need = count - 1 + headShare + tailShare
    val margin = min(target, width / need)
    val slack = max(0f, width - margin * need)

    val draws = FloatArray(count) { random.nextFloat() }
    draws.sort()

    val out = ArrayList<Float>(count)
    var cursor = start + margin * headShare
    var previous = 0f
    for (i in 0 until count) {
        if (i > 0) cursor += margin
        cursor += (draws[i] - previous) * slack
        previous = draws[i]
        out.add(wrapHue(cursor))
    }
    return out
}

/** Fisher-Yates off the same stream, so the pairing replays like the hues do. */
private fun shuffled(codes: List<String>, random: HueRandom): List<String> {
    val out = codes.toMutableList()
    for (i in out.indices.reversed()) {
        val j = random.nextInt(i + 1)
        val held = out[i]
        out[i] = out[j]
        out[j] = held
    }
    return out
}

/**
 * A counter-based generator, seeded from data and never from a clock.
 *
 * SplitMix64's finaliser over seed + counter * golden ratio is enough mixing
 * for the handful of draws a semester needs, and it carries no state beyond
 * the counter, so the same seed replays the same sequence on every platform.
 * kotlin.random.Random is deliberately not used: its default is seeded from
 * the clock, and a palette that came out different on every launch is exactly
 * what this file exists to avoid.
 */
private class HueRandom(private val seed: Long) {
    private var counter = 0L

    fun nextFloat(): Float {
        counter++
        var z = seed + counter * GOLDEN
        z = (z xor (z ushr 30)) * MIX_A
        z = (z xor (z ushr 27)) * MIX_B
        z = z xor (z ushr 31)
        // The top 53 bits, which is every value a Double holds in [0, 1).
        return ((z ushr 11).toDouble() / MANTISSA).toFloat()
    }

    /** Uniform over 0 until [bound]. The coerce catches a draw that rounds to 1. */
    fun nextInt(bound: Int): Int = (nextFloat() * bound).toInt().coerceIn(0, bound - 1)
}

private const val GOLDEN = -0x61c8864680b583ebL
private const val MIX_A = -0x40a7b892e31b1a47L
private const val MIX_B = -0x6b2fb644ecceee15L
private const val MANTISSA = 9007199254740992.0

/**
 * FNV-1a over one semester's sorted codes, which is the seed.
 *
 * Hashing the codes rather than the whole feed keeps a re-roll local: adding a
 * course to this autumn changes this autumn's seed and no other. String
 * hashCode would have done the job on the JVM, but it is not specified to
 * match across Kotlin targets, and a course that came out green on Android and
 * blue on the desktop is the same bug as one that changes every launch.
 */
private fun seedOf(codes: List<String>): Long {
    var h = FNV_BASIS
    for (code in codes) {
        for (ch in code) h = (h xor ch.code.toLong()) * FNV_PRIME
        // A code boundary, so "021" + "01" cannot hash like "02" + "101".
        h = (h xor CODE_BREAK) * FNV_PRIME
    }
    return h
}

private const val FNV_BASIS = -0x340d631b7bdddcdbL
private const val FNV_PRIME = 0x100000001b3L
private const val CODE_BREAK = 0x1fL

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
 * One hue, four jobs. The lightness values are the design: `fill` carries a
 * block of text, `rail` carries most of the chroma because a 2dp line needs
 * saturation to register at all, and `soft` is a wash for dots and hover.
 *
 * The numbers come from sweeping every hue at n = 1, 2, 5, 12 and 30 and taking
 * the worst of three measurements rather than one: ink on fill, fill against
 * the page background, and the closest pair of fills in OKLab. Only the first
 * of those was ever checked, which is how the light ramp shipped in the state
 * described below.
 *
 * The light fill sat at L 0.91, C 0.080, and C 0.080 is not reachable at L 0.91.
 * The sRGB ceiling for a blue at that lightness is C 0.043, so [Oklch.toColor]
 * did its job and handed back half the asked chroma for the blues while the
 * yellows got all of it. Two blues in a thirty course term came out 0.0079
 * apart in OKLab, and against the F4F1EE page the worst block stood at 1.12:1,
 * so a screen of events read as one pale wash with the hues barely showing.
 *
 * Dropping fill to L 0.82 lifts that ceiling to C 0.0895, which is most of the
 * way to the 0.110 now asked for, so the blues come back nearly saturated: the
 * closest pair opens to 0.0169 and the worst block stands 1.48:1 off the page.
 * Ink follows to L 0.37 to pay for the darker fill and the worst ink on fill is
 * 5.90:1, still clear of the 4.5 floor. Rail follows to L 0.52 and soft to
 * L 0.93; soft had to move because at L 0.96 it measured 1.01:1 against the
 * L 0.9596 background, meaning a month view dot was invisible on the page and
 * only ever showed up on a white card.
 *
 * The dark ramp moves the same direction by less, because its background was
 * lifted at the same time: fill L 0.33 would have fallen to 1.20:1 against the
 * new 292423, and L 0.39 keeps it at 1.51:1. Dark soft moves furthest, L 0.24
 * to L 0.34, because 0.24 is below the new background's 0.265 and a hover wash
 * that darkens the surface it is on is backwards.
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
    fillL = 0.82f, fillC = 0.110f,
    inkL = 0.37f, inkC = 0.150f,
    railL = 0.52f, railC = 0.200f,
    softL = 0.93f, softC = 0.055f,
)

private val DarkRamp = Ramp(
    fillL = 0.39f, fillC = 0.095f,
    inkL = 0.88f, inkC = 0.135f,
    railL = 0.73f, railC = 0.180f,
    softL = 0.34f, softC = 0.060f,
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
