package dk.bandit.skema.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The colours are computed, so they can be checked. Reading contrast off a
 * screen is the part that gets shipped wrong, so the AA floor is asserted for
 * every course in both themes rather than eyeballed.
 */
class CourseColorsTest {

    /** Plausible DTU codes: five digits, leading zero kept. */
    private fun codes(n: Int) = (0 until n).map { "02" + (300 + it * 7).toString().padStart(3, '0') }

    private val sizes = listOf(1, 2, 5, 12, 30)

    // -----------------------------------------------------------------------
    // Hue assignment
    // -----------------------------------------------------------------------

    @Test
    fun nCoursesGetNDistinctHues() {
        for (n in sizes) {
            val p = CoursePalette(codes(n), dark = false)
            assertEquals(n, p.hues.size, "n=$n")
            assertEquals(n, p.hues.values.distinct().size, "n=$n hues collided")
        }
    }

    @Test
    fun theHuesAreEvenlySpreadOverEverythingOutsideTheGuard() {
        // Neighbours sit one step apart, the step being the free arc over the
        // count. The single exception is the pair either side of the guard,
        // which is a step plus the guard's whole width.
        val free = 360f - 2f * CoursePalette.ACCENT_GUARD
        for (n in 1..30) {
            val hues = CoursePalette(codes(n), dark = false).hues.values.sorted()
            val step = free / n
            val across = step + 2f * CoursePalette.ACCENT_GUARD
            var crossings = 0
            for (i in hues.indices) {
                val next = if (i + 1 < hues.size) hues[i + 1] else hues[0] + 360f
                val gap = next - hues[i]
                if (abs(gap - across) < 0.01f) {
                    crossings++
                } else {
                    assertTrue(abs(gap - step) < 0.01f, "n=$n gap $gap is neither $step nor $across")
                }
            }
            assertEquals(1, crossings, "n=$n should step over the guard exactly once")
        }
    }

    @Test
    fun noAutomaticallyAssignedHueLandsInsideTheAccentGuard() {
        // The bug this guards: with the whole circle in play a six course term
        // put one course on hue 30, a degree off DTU red, so an ordinary
        // course wore the colour EventBlock draws conflicts in.
        var closest = 180f
        var closestAt = ""
        for (n in 1..30) {
            val p = CoursePalette(codes(n), dark = false)
            for ((code, hue) in p.hues) {
                val d = hueDistance(hue, CoursePalette.ACCENT_HUE)
                if (d < closest) {
                    closest = d
                    closestAt = "n=$n $code hue $hue"
                }
                assertTrue(d >= CoursePalette.ACCENT_GUARD, "n=$n $code at $hue is only $d from the accent")
            }
        }
        println("guard: closest automatic hue to the accent over n=1..30 is $closest degrees at $closestAt")
    }

    @Test
    fun theAccentHueIsWhatDtuRedActuallyMeasures() {
        // The constant is a measurement, so it has to keep matching the palette.
        assertTrue(abs(Color(0xFFD10000).toOklch().h - CoursePalette.ACCENT_HUE) < 0.1f)
        assertTrue(abs(Color(0xFF7A0000).toOklch().h - CoursePalette.ACCENT_HUE) < 0.1f)
    }

    @Test
    fun theFirstCourseTakesTheSlotNearestStartHue() {
        for (n in listOf(2, 5, 6, 12, 30)) {
            val p = CoursePalette(codes(n), dark = false)
            val nearest = p.hues.values.minByOrNull { hueDistance(it, CoursePalette.START_HUE) }
            assertEquals(nearest, p.hue(codes(n).first()), "n=$n")
        }
        // The owner's current term, six courses, lands course one on a green.
        assertEquals(126.2f, round2f(CoursePalette(codes(6), dark = false).hue("02300")!!))
    }

    // -----------------------------------------------------------------------
    // Contrast
    // -----------------------------------------------------------------------

    @Test
    fun inkClearsAaOnFillForEveryCourseInBothThemes() {
        for (dark in listOf(false, true)) {
            var worst = Float.MAX_VALUE
            var best = 0f
            var worstAt = ""
            for (n in sizes) {
                val p = CoursePalette(codes(n), dark = dark)
                for (code in codes(n)) {
                    val t = p.tint(code)
                    val ratio = contrastRatio(t.ink, t.fill)
                    if (ratio < worst) {
                        worst = ratio
                        worstAt = "n=$n $code hue ${p.hue(code)}"
                    }
                    if (ratio > best) best = ratio
                    assertTrue(
                        ratio >= 4.5f,
                        "dark=$dark n=$n $code hue ${p.hue(code)} ink on fill is $ratio",
                    )
                }
            }
            println(
                "contrast: ${if (dark) "dark " else "light"} ink-on-fill " +
                    "worst ${round2(worst)}:1 at $worstAt, best ${round2(best)}:1",
            )
        }
    }

    @Test
    fun theNeutralTintClearsAaToo() {
        for (dark in listOf(false, true)) {
            val t = CoursePalette(emptyList(), dark = dark).neutral
            val ratio = contrastRatio(t.ink, t.fill)
            println("contrast: ${if (dark) "dark " else "light"} neutral ink-on-fill ${round2(ratio)}:1")
            assertTrue(ratio >= 4.5f, "neutral dark=$dark is $ratio")
        }
    }

    // -----------------------------------------------------------------------
    // Separation: the block against the page, and the blocks against each other
    // -----------------------------------------------------------------------

    /**
     * Ink on fill was the only thing measured before, and a palette can pass it
     * while every block on the screen looks the same. These two floors cover
     * the rest: can you see a block at all, and can you see that two of them
     * differ. Both are set from what the palette actually holds, with the
     * numbers below.
     *
     * A filled area has no WCAG floor, so this one is chosen: the light fill
     * measures 1.48:1 worst case and the dark 1.51:1, while the ramp that
     * shipped before measured 1.12:1 and the owner reported it as a wash. 1.25
     * clears both of the new numbers and fails the old one, which is the point
     * of having it.
     */
    private val fillOnBackgroundFloor = 1.25f

    /**
     * Two fills share a lightness by construction, so a contrast ratio between
     * them stays near 1:1 whatever the hues and proves nothing; the separation
     * is a distance in OKLab instead. Roughly 0.02 there is one just noticeable
     * difference for an isolated patch, and rather less is needed for two
     * blocks meeting along an edge, which is how events actually appear.
     *
     * 0.010 is the floor because the dark theme cannot do much better at n=30:
     * a cyan at fill lightness 0.39 hits the sRGB ceiling at C 0.066, so the
     * pair at hue 192.6 and 203.7 measures 0.0120 with its chroma already
     * maxed out, and light manages 0.0169. n=30 is the pathological end of the
     * range; at n=12 the closest pair is 0.0341 dark and 0.0440 light, and at
     * n=5 both are past 0.09. The old light ramp measured 0.0079 here.
     */
    private val fillSeparationFloor = 0.010f

    @Test
    fun anEventBlockIsDistinguishableFromThePageItSitsOn() {
        for (dark in listOf(false, true)) {
            val background = skemaColors(dark).background
            var worst = Float.MAX_VALUE
            var worstAt = ""
            for (n in sizes) {
                val p = CoursePalette(codes(n), dark = dark)
                for (code in codes(n)) {
                    val ratio = contrastRatio(p.tint(code).fill, background)
                    if (ratio < worst) {
                        worst = ratio
                        worstAt = "n=$n $code hue ${p.hue(code)}"
                    }
                    assertTrue(
                        ratio >= fillOnBackgroundFloor,
                        "dark=$dark n=$n $code hue ${p.hue(code)} fill on background is $ratio",
                    )
                }
            }
            val neutral = contrastRatio(CoursePalette(emptyList(), dark = dark).neutral.fill, background)
            assertTrue(neutral >= fillOnBackgroundFloor, "neutral fill on background dark=$dark is $neutral")
            println(
                "separation: ${if (dark) "dark " else "light"} fill-on-background " +
                    "worst ${round2(worst)}:1 at $worstAt, neutral ${round2(neutral)}:1",
            )
        }
    }

    @Test
    fun noTwoCoursesInATermShareALook() {
        // Every pair, not only the hue neighbours. For this ramp the two agree,
        // because the fills sit at one lightness and the closest pair is always
        // the closest in hue. That equivalence rests on the ramp, though, and a
        // future ramp that varied lightness per hue would break it, so the test
        // does not assume it: 435 comparisons at n=30 costs nothing.
        for (dark in listOf(false, true)) {
            var worst = Float.MAX_VALUE
            var worstAt = ""
            for (n in sizes) {
                if (n < 2) continue
                val p = CoursePalette(codes(n), dark = dark)
                val all = codes(n)
                for (i in all.indices) {
                    for (j in i + 1 until all.size) {
                        val d = oklabDistance(p.tint(all[i]).fill, p.tint(all[j]).fill)
                        if (d < worst) {
                            worst = d
                            worstAt = "n=$n hues ${p.hue(all[i])}/${p.hue(all[j])}"
                        }
                        assertTrue(
                            d >= fillSeparationFloor,
                            "dark=$dark n=$n ${all[i]} and ${all[j]} are $d apart in OKLab",
                        )
                    }
                }
            }
            println(
                "separation: ${if (dark) "dark " else "light"} closest pair of fills " +
                    "$worst in OKLab at $worstAt",
            )
        }
    }

    @Test
    fun aCourseKeepsItsColourWhenTheThemeFlips() {
        // Only L and C differ between the ramps. If a hue ever moved, a course
        // would change identity on a theme switch, which is worse than either
        // theme being slightly off.
        //
        // The tolerance is not zero because a Color holds 8 bits per channel:
        // reading the hue back off two colours that differ in lightness picks
        // up the rounding, worst measured 1.19 degrees on light soft at C 0.055,
        // where the smallest chroma makes the rounding count for most.
        val tolerance = 2.5f
        val all = codes(12)
        val light = CoursePalette(all, dark = false)
        val dark = CoursePalette(all, dark = true)
        for (code in all) {
            for (pair in listOf(
                light.tint(code).fill to dark.tint(code).fill,
                light.tint(code).rail to dark.tint(code).rail,
                light.tint(code).ink to dark.tint(code).ink,
                light.tint(code).soft to dark.tint(code).soft,
            )) {
                val (l, d) = pair
                assertTrue(
                    hueDistance(l.toOklch().h, d.toOklch().h) < tolerance,
                    "$code moved hue across themes: ${l.toOklch().h} to ${d.toOklch().h}",
                )
            }
        }
    }

    private fun round2(v: Float) = (kotlin.math.round(v * 100f) / 100f).toString()

    // -----------------------------------------------------------------------
    // Overrides
    // -----------------------------------------------------------------------

    @Test
    fun anOverridePinsThatCourseAndNoOtherCourseTakesTheHue() {
        val all = codes(5)
        val pinnedCode = all[2]
        val p = CoursePalette(all, overrides = mapOf(pinnedCode to 29.2f), dark = false)

        assertEquals(29.2f, p.hue(pinnedCode))
        for (code in all - pinnedCode) {
            val h = p.hue(code)!!
            assertTrue(hueDistance(h, 29.2f) > 1f, "$code landed on the pinned hue at $h")
        }
        // The pin is inside the guard, so it cuts an arc nothing was using and
        // the four that are left share the 332 degrees outside it: the free arc
        // runs 43.2 to 375.2 and they sit at (i + 0.5) / 4 along it.
        val free = (all - pinnedCode).map { p.hue(it)!! }.sorted()
        assertEquals(listOf(84.7f, 167.7f, 250.7f, 333.7f), free.map { round2f(it) })
    }

    @Test
    fun twoOverridesSplitTheCircleAndTheRestFillTheArcs() {
        val all = codes(6)
        val p = CoursePalette(all, overrides = mapOf(all[0] to 0f, all[1] to 180f), dark = true)

        assertEquals(0f, p.hue(all[0]))
        assertEquals(180f, p.hue(all[1]))
        // The guard splits the upper half, so the usable arcs are 0 to 15.2,
        // 43.2 to 180 and 180 to 360. Largest remainder gives them 0, 2 and 2
        // courses; the arc that starts on a guard edge takes a half margin
        // there and a whole one at the pin, the arc between two pins takes a
        // whole margin at each end and comes out on the thirds.
        val free = all.drop(2).map { p.hue(it)!! }.sorted().map { round2f(it) }
        assertEquals(listOf(70.56f, 125.28f, 240f, 300f), free)
        assertEquals(6, p.hues.values.distinct().size)
    }

    @Test
    fun anOverrideOutsideZeroToThreeSixtyIsWrappedNotRejected() {
        val all = codes(3)
        val p = CoursePalette(all, overrides = mapOf(all[0] to 420f), dark = false)
        assertEquals(60f, p.hue(all[0]))
    }

    @Test
    fun anOverrideInsideTheGuardIsHonouredExactly() {
        // The guard governs automatic assignment only. Asking for DTU red by
        // hand is a decision, and the palette does not second guess it.
        val all = codes(5)
        val p = CoursePalette(all, overrides = mapOf(all[2] to CoursePalette.ACCENT_HUE), dark = true)
        assertEquals(CoursePalette.ACCENT_HUE, p.hue(all[2]))
        assertTrue(hueDistance(p.hue(all[2])!!, CoursePalette.ACCENT_HUE) < CoursePalette.ACCENT_GUARD)
        for (code in all - all[2]) {
            assertTrue(
                hueDistance(p.hue(code)!!, CoursePalette.ACCENT_HUE) >= CoursePalette.ACCENT_GUARD,
                "$code strayed into the guard",
            )
        }
    }

    private fun round2f(v: Float) = kotlin.math.round(v * 100f) / 100f

    // -----------------------------------------------------------------------
    // Totality and determinism
    // -----------------------------------------------------------------------

    @Test
    fun aNullOrUnknownCodeGetsTheNeutralTint() {
        val p = CoursePalette(codes(5), dark = false)
        assertEquals(p.neutral, p.tint(null))
        assertEquals(p.neutral, p.tint("99999"))
        assertEquals(p.neutral, p.tint(""))
        // An empty term is legal: the feed can be all room bookings with no code.
        val empty = CoursePalette(emptyList(), dark = true)
        assertEquals(empty.neutral, empty.tint(null))
    }

    @Test
    fun blanksAndDuplicatesDoNotConsumeAHue() {
        val p = CoursePalette(listOf("02300", "02300", "", "  ", "02307"), dark = false)
        assertEquals(2, p.hues.size)
        // Two courses over the 332 degrees outside the guard, so 166 apart.
        assertEquals(166f, round2f(hueDistance(p.hue("02300")!!, p.hue("02307")!!)))
    }

    @Test
    fun theAssignmentIsDeterministicAcrossConstructions() {
        val all = codes(12)
        val a = CoursePalette(all, dark = false)
        val b = CoursePalette(all, dark = false)
        val shuffled = CoursePalette(all.reversed(), dark = false)
        for (code in all) {
            assertEquals(a.tint(code), b.tint(code), code)
            assertEquals(a.tint(code), shuffled.tint(code), "$code depends on input order")
        }

        val o = mapOf(all[4] to 200f, all[9] to 12f)
        assertEquals(
            CoursePalette(all, o, dark = true).hues,
            CoursePalette(all.reversed(), o, dark = true).hues,
        )
    }

    @Test
    fun aCoursesHueIsTheSameInBothThemes() {
        val all = codes(12)
        val light = CoursePalette(all, dark = false)
        val dark = CoursePalette(all, dark = true)
        for (code in all) assertEquals(light.hue(code), dark.hue(code), code)
    }

    // -----------------------------------------------------------------------
    // The conversion itself
    // -----------------------------------------------------------------------

    @Test
    fun oklchMatchesReferenceValues() {
        // Ottosson's published figures for the sRGB primaries and for white.
        assertOklch(Color(0xFFFFFFFF), l = 1.0000f, c = 0.0000f, h = null)
        assertOklch(Color(0xFF808080), l = 0.5999f, c = 0.0000f, h = null)
        assertOklch(Color(0xFF0000FF), l = 0.4520f, c = 0.3132f, h = 264.05f)
        assertOklch(Color(0xFFFF0000), l = 0.6280f, c = 0.2577f, h = 29.23f)
        assertOklch(Color(0xFF00FF00), l = 0.8664f, c = 0.2948f, h = 142.50f)
    }

    @Test
    fun oklchRoundTripsBackToTheSameColour() {
        val samples = listOf(
            Color(0xFFFFFFFF),
            Color(0xFF000000),
            Color(0xFF808080),
            Color(0xFF0000FF),
            Color(0xFFFF0000),
            Color(0xFF00FF00),
            Color(0xFF7A0000), // the app's own accent
            Color(0xFF3B7DD8),
        )
        for (s in samples) {
            val back = s.toOklch().toColor()
            assertChannel(s.red, back.red, s)
            assertChannel(s.green, back.green, s)
            assertChannel(s.blue, back.blue, s)
        }
    }

    @Test
    fun outOfGamutChromaLosesSaturationAndKeepsLightness() {
        // Nothing in sRGB is this saturated at this lightness, so the chroma
        // has to give. The point is that L survives, because contrast rests on it.
        val asked = Oklch(l = 0.91f, c = 0.40f, h = 150f)
        val got = asked.toColor().toOklch()
        assertTrue(got.c < asked.c, "chroma ${got.c} should have been reduced from ${asked.c}")
        assertTrue(abs(got.l - asked.l) < 0.005f, "lightness moved to ${got.l}")
        assertTrue(abs(got.h - asked.h) < 0.5f, "hue moved to ${got.h}")
    }

    @Test
    fun contrastRatioMatchesTheKnownExtremes() {
        assertTrue(abs(contrastRatio(Color(0xFF000000), Color(0xFFFFFFFF)) - 21f) < 0.01f)
        assertEquals(1f, contrastRatio(Color(0xFF3B7DD8), Color(0xFF3B7DD8)))
        // Order must not matter.
        assertEquals(
            contrastRatio(Color(0xFF000000), Color(0xFFFFFFFF)),
            contrastRatio(Color(0xFFFFFFFF), Color(0xFF000000)),
        )
    }

    private fun assertOklch(color: Color, l: Float, c: Float, h: Float?) {
        val got = color.toOklch()
        assertTrue(abs(got.l - l) < 0.001f, "$color L was ${got.l}, expected $l")
        assertTrue(abs(got.c - c) < 0.001f, "$color C was ${got.c}, expected $c")
        if (h != null) assertTrue(abs(got.h - h) < 0.05f, "$color H was ${got.h}, expected $h")
    }

    private fun assertChannel(want: Float, got: Float, of: Color) {
        assertTrue(abs(want - got) < 1f / 255f, "$of channel $want came back as $got")
    }
}
