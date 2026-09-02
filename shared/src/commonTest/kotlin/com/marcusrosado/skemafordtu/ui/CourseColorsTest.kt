package com.marcusrosado.skemafordtu.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The colours are computed, so they can be checked. Reading contrast off a
 * screen is the part that gets shipped wrong, so the AA floor is asserted for
 * every course in both themes rather than eyeballed.
 *
 * The hues are drawn at random now, which makes these floors matter more, not
 * less: an even spread could only reach 360/n hues and a draw can reach any of
 * them. So two of the tests below stop sampling and sweep instead. The
 * per-hue floors are checked against the whole circle, and the floors that
 * need a whole palette are checked against [seeds] different course sets at
 * every size, so a bad seed cannot ship.
 */
class CourseColorsTest {

    /** Plausible DTU codes: five digits, leading zero kept. */
    private fun codes(n: Int) = (0 until n).map { "02" + (300 + it * 7).toString().padStart(3, '0') }

    /**
     * n codes that no other seed produces. The generator is seeded from the
     * codes themselves, so a fresh set of codes is a fresh seed, and this is
     * the only handle a test has on the randomness.
     */
    private fun codes(n: Int, seed: Int) =
        (0 until n).map { "0" + (2 + seed).toString() + (300 + it * 7).toString().padStart(3, '0') }

    private val sizes = listOf(1, 2, 5, 12, 30)

    /** One code, for the tests that are about a hue rather than about a term. */
    private val one = "02300"

    /**
     * How many different course sets, and so how many different seeds, the
     * palette-wide floors are swept over at each size.
     */
    private val seeds = 80

    /**
     * Slack on a comparison against a margin. The hues are accumulated along
     * an arc, so the last gap in a long arc carries a few Float rounding
     * errors; measured worst 0.0009 degrees at n=60, and 0.01 is well inside
     * anything the eye or the OKLab floor cares about.
     */
    private val tolerance = 0.01f

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
    fun everyPairKeepsTheMarginTheTermGuarantees() {
        // What replaced the even spread. The hues are drawn, so where they
        // land is not predictable, but how close they may come is: no two
        // courses in one term are nearer than marginFor(n).
        for (n in 2..30) {
            val hues = CoursePalette(codes(n), dark = false).hues.values.sorted()
            val margin = CoursePalette.marginFor(n)
            for (i in hues.indices) {
                val next = if (i + 1 < hues.size) hues[i + 1] else hues[0] + 360f
                assertTrue(next - hues[i] >= margin - tolerance, "n=$n gap ${next - hues[i]} under $margin")
            }
        }
        println(
            "margin: the guarantee is " + round2(CoursePalette.marginFor(5)) + " degrees at n=5, " +
                round2(CoursePalette.marginFor(12)) + " at n=12, " +
                round2(CoursePalette.marginFor(30)) + " at n=30",
        )
    }

    @Test
    fun theMarginIsAShareOfWhatTheCountAllows() {
        // A term of five keeps the ideal 40, because more separation than that
        // buys nothing. Twelve gets 0.7 of its 27.67 step. Thirty is where the
        // 11 degree floor gives way to the step itself, so the draw has 2
        // degrees of slack and the palette is the even spread again.
        assertEquals(40f, round2f(CoursePalette.marginFor(5)))
        assertEquals(19.37f, round2f(CoursePalette.marginFor(12)))
        assertEquals(11f, round2f(CoursePalette.marginFor(30)))

        // And it never asks for more than n allows, at any n, which is the
        // property that keeps the placement from having to search or fail.
        for (n in 1..200) {
            val margin = CoursePalette.marginFor(n)
            assertTrue(margin > 0f, "n=$n asked for $margin")
            assertTrue(
                margin <= CoursePalette.FREE_ARC / n + tolerance,
                "n=$n asked for $margin, more than the ${CoursePalette.FREE_ARC / n} it can have",
            )
        }
    }

    @Test
    fun aTermFullEnoughToLeaveNoSlackFallsBackToTheEvenSpread() {
        // The circle really does run out. 60 courses in one term cannot be 11
        // degrees apart, so the margin drops to the step and the draw has
        // nothing left to draw: predictable degradation rather than a loop
        // that never ends or a throw on a screen full of events.
        val n = 60
        val step = CoursePalette.FREE_ARC / n
        assertEquals(round2f(step), round2f(CoursePalette.marginFor(n)))
        val hues = CoursePalette(codes(n), dark = false).hues.values.sorted()
        assertEquals(n, hues.size)
        for (i in 0 until hues.size - 1) {
            assertTrue(hues[i + 1] - hues[i] >= step - tolerance, "n=$n gap ${hues[i + 1] - hues[i]}")
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
            for (seed in 0 until seeds) {
                val p = CoursePalette(codes(n, seed), dark = false)
                for ((code, hue) in p.hues) {
                    val d = hueDistance(hue, CoursePalette.ACCENT_HUE)
                    if (d < closest) {
                        closest = d
                        closestAt = "n=$n seed $seed $code hue $hue"
                    }
                    assertTrue(d >= CoursePalette.ACCENT_GUARD, "n=$n $code at $hue is only $d from the accent")
                }
            }
        }
        println(
            "guard: over n=1..30 and $seeds course sets each, the closest drawn hue to the " +
                "accent is ${round2(closest)} degrees at $closestAt",
        )
    }

    @Test
    fun theAccentHueIsWhatDtuRedActuallyMeasures() {
        // The constant is a measurement, so it has to keep matching the palette.
        assertTrue(abs(Color(0xFFD10000).toOklch().h - CoursePalette.ACCENT_HUE) < 0.1f)
        assertTrue(abs(Color(0xFF7A0000).toOklch().h - CoursePalette.ACCENT_HUE) < 0.1f)
    }

    // -----------------------------------------------------------------------
    // Semesters
    // -----------------------------------------------------------------------

    private val autumn = listOf("02101", "02102", "02103", "02157", "02158")
    private val spring = listOf("02157", "02201", "02202", "02203", "02204")

    @Test
    fun eachSemesterIsColouredWithinItselfAndKeepsItsOwnMargin() {
        val p = CoursePalette.bySemester(listOf(autumn, spring), dark = false)
        // Nine courses, because 02157 is taught in both.
        assertEquals(9, p.hues.size)

        val margin = CoursePalette.marginFor(5)
        for (term in listOf(autumn, spring)) {
            for (i in term.indices) {
                for (j in i + 1 until term.size) {
                    val d = hueDistance(p.hue(term[i])!!, p.hue(term[j])!!)
                    assertTrue(d >= margin - tolerance, "${term[i]} and ${term[j]} are $d apart")
                }
            }
        }

        // The point of splitting them: nine courses coloured as one set would
        // guarantee 25.82 degrees, and as two terms of five they guarantee 40.
        assertTrue(CoursePalette.marginFor(5) > CoursePalette.marginFor(9))
        println(
            "semesters: five courses guarantee " + round2(CoursePalette.marginFor(5)) +
                " degrees, the nine they add up to would guarantee " +
                round2(CoursePalette.marginFor(9)),
        )
    }

    @Test
    fun aCourseTaughtInTwoTermsKeepsTheOneColour() {
        val p = CoursePalette.bySemester(listOf(autumn, spring), dark = true)
        val shared = p.hue("02157")!!
        // The spring is drawn around it rather than over it.
        val margin = CoursePalette.marginFor(5)
        for (code in spring - "02157") {
            assertTrue(hueDistance(p.hue(code)!!, shared) >= margin - tolerance, code)
        }
    }

    @Test
    fun aNewSemesterLeavesTheOldOnesWhereTheyWere() {
        // Semesters are placed in the order given and callers pass the oldest
        // first, so next February cannot repaint last autumn.
        val before = CoursePalette.bySemester(listOf(autumn), dark = false)
        val after = CoursePalette.bySemester(listOf(autumn, spring), dark = false)
        for (code in autumn) assertEquals(before.hue(code), after.hue(code), code)
    }

    @Test
    fun twoTermsAreAllowedToShareAColour() {
        // The owner's rule, stated as a test so nobody adds a global check
        // later: nothing here separates terms from each other, and the run
        // below finds a pair that proves it rather than asserting it cannot
        // happen by luck.
        val margin = CoursePalette.marginFor(5)
        var closest = 180f
        for (seed in 0 until seeds) {
            val one = codes(5, seed)
            val two = codes(5, seed + seeds)
            val p = CoursePalette.bySemester(listOf(one, two), dark = false)
            for (a in one) {
                for (b in two) closest = minOf(closest, hueDistance(p.hue(a)!!, p.hue(b)!!))
            }
        }
        assertTrue(closest < margin, "no cross-term pair ever came closer than $margin, so terms are not independent")
        println("semesters: closest pair across two terms over $seeds draws is ${round2(closest)} degrees")
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
    fun everyHueTheDrawCanReachClearsInkAndPageContrast() {
        // The even spread could only land on 360/n hues, so sweeping the hues
        // it assigned covered the whole palette. A draw lands anywhere outside
        // the guard, so the sweep has to be the circle itself, at a quarter of
        // a degree. That is stronger than trying seeds: it leaves no hue for a
        // future seed to find.
        for (dark in listOf(false, true)) {
            val background = skemaColors(dark).background
            var worstInk = Float.MAX_VALUE
            var worstInkAt = 0f
            var worstFill = Float.MAX_VALUE
            var worstFillAt = 0f
            var swept = 0
            var hue = 0f
            while (hue < 360f) {
                if (hueDistance(hue, CoursePalette.ACCENT_HUE) >= CoursePalette.ACCENT_GUARD) {
                    swept++
                    val t = CoursePalette(listOf(one), mapOf(one to hue), dark).tint(one)
                    val ink = contrastRatio(t.ink, t.fill)
                    val fill = contrastRatio(t.fill, background)
                    if (ink < worstInk) {
                        worstInk = ink
                        worstInkAt = hue
                    }
                    if (fill < worstFill) {
                        worstFill = fill
                        worstFillAt = hue
                    }
                    assertTrue(ink >= 4.5f, "dark=$dark hue $hue ink on fill is $ink")
                    assertTrue(fill >= fillOnBackgroundFloor, "dark=$dark hue $hue fill on page is $fill")
                }
                hue += 0.25f
            }
            println(
                "sweep: ${if (dark) "dark " else "light"} over $swept hues, worst ink-on-fill " +
                    "${round2(worstInk)}:1 at hue $worstInkAt, worst fill-on-page " +
                    "${round2(worstFill)}:1 at hue $worstFillAt",
            )
        }
    }

    @Test
    fun everyCourseSetSweptHoldsEveryFloor() {
        // The floors that need a whole palette rather than one hue: how close
        // two courses come, in degrees and in OKLab. Swept over a fresh
        // roster per seed at every size and in both themes, because the draw
        // is what is being trusted here and one roster says nothing about the
        // next.
        for (dark in listOf(false, true)) {
            val background = skemaColors(dark).background
            for (n in sizes) {
                val margin = CoursePalette.marginFor(n)
                var worstGap = 360f
                var worstPair = Float.MAX_VALUE
                var worstPairAt = ""
                var worstInk = Float.MAX_VALUE
                var worstFill = Float.MAX_VALUE
                for (seed in 0 until seeds) {
                    val all = codes(n, seed)
                    val p = CoursePalette(all, dark = dark)
                    assertEquals(n, p.hues.values.distinct().size, "n=$n seed $seed collided")
                    for (i in all.indices) {
                        val t = p.tint(all[i])
                        val ink = contrastRatio(t.ink, t.fill)
                        val fill = contrastRatio(t.fill, background)
                        if (ink < worstInk) worstInk = ink
                        if (fill < worstFill) worstFill = fill
                        assertTrue(ink >= 4.5f, "dark=$dark n=$n seed $seed ${all[i]} ink on fill is $ink")
                        assertTrue(
                            fill >= fillOnBackgroundFloor,
                            "dark=$dark n=$n seed $seed ${all[i]} fill on page is $fill",
                        )
                        for (j in i + 1 until all.size) {
                            val gap = hueDistance(p.hue(all[i])!!, p.hue(all[j])!!)
                            val d = oklabDistance(t.fill, p.tint(all[j]).fill)
                            if (gap < worstGap) worstGap = gap
                            if (d < worstPair) {
                                worstPair = d
                                worstPairAt = "seed $seed hues ${p.hue(all[i])}/${p.hue(all[j])}"
                            }
                            assertTrue(
                                gap >= margin - tolerance,
                                "dark=$dark n=$n seed $seed ${all[i]} and ${all[j]} are $gap apart, under $margin",
                            )
                            assertTrue(
                                d >= fillSeparationFloor,
                                "dark=$dark n=$n seed $seed ${all[i]} and ${all[j]} are $d apart in OKLab",
                            )
                        }
                    }
                }
                if (n > 1) {
                    println(
                        "seeds: ${if (dark) "dark " else "light"} n=$n over $seeds sets, " +
                            "closest pair ${round2(worstGap)} degrees against a ${round2(margin)} margin, " +
                            "$worstPair in OKLab at $worstPairAt, worst ink-on-fill ${round2(worstInk)}:1, " +
                            "worst fill-on-page ${round2(worstFill)}:1",
                    )
                }
            }
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
        // the four that are left are drawn into the 332 degrees outside it,
        // each keeping the term's margin from the next. Where exactly they
        // land is the draw's business, so the test asks about the gaps.
        val margin = CoursePalette.marginFor(5)
        val free = (all - pinnedCode).map { p.hue(it)!! }.sorted()
        for (i in 0 until free.size - 1) {
            assertTrue(free[i + 1] - free[i] >= margin - tolerance, "gap ${free[i + 1] - free[i]} under $margin")
        }
    }

    @Test
    fun twoOverridesSplitTheCircleAndTheRestFillTheArcs() {
        val all = codes(6)
        val p = CoursePalette(all, overrides = mapOf(all[0] to 0f, all[1] to 180f), dark = true)

        assertEquals(0f, p.hue(all[0]))
        assertEquals(180f, p.hue(all[1]))
        assertEquals(6, p.hues.values.distinct().size)
        // The guard splits the upper half, so the usable arcs are 0 to 15.2,
        // 43.2 to 180 and 180 to 360. Largest remainder gives them 0, 2 and 2
        // courses; the arc that starts on a guard edge asks half a margin
        // there and a whole one at the pin. With the pins 180 apart every
        // consecutive pair, drawn or pinned, still clears the margin.
        val margin = CoursePalette.marginFor(6)
        val hues = p.hues.values.sorted()
        for (i in hues.indices) {
            val next = if (i + 1 < hues.size) hues[i + 1] else hues[0] + 360f
            assertTrue(next - hues[i] >= margin - tolerance, "gap ${next - hues[i]} under $margin")
        }
        for (h in all.drop(2).map { p.hue(it)!! }) {
            assertTrue(hueDistance(h, CoursePalette.ACCENT_HUE) >= CoursePalette.ACCENT_GUARD, "$h is in the guard")
        }
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
        // A term of two, so the ideal 40 degree margin applies and the pair
        // is somewhere in the 292 degrees of slack left over.
        assertTrue(
            hueDistance(p.hue("02300")!!, p.hue("02307")!!) >= CoursePalette.marginFor(2) - tolerance,
        )
    }

    @Test
    fun theAssignmentIsDeterministicAcrossConstructions() {
        // The one thing randomness must not cost: an app that looks different
        // every time it opens. The generator is seeded from the codes, so the
        // same roster reproduces the same palette, and the codes are sorted
        // first, so the order the feed happened to list them in cannot matter.
        for (n in sizes) {
            val all = codes(n)
            val a = CoursePalette(all, dark = false)
            val b = CoursePalette(all, dark = false)
            val reversed = CoursePalette(all.reversed(), dark = false)
            for (code in all) {
                assertEquals(a.tint(code), b.tint(code), "n=$n $code moved between two constructions")
                assertEquals(a.tint(code), reversed.tint(code), "n=$n $code depends on input order")
            }
        }

        val all = codes(12)
        val o = mapOf(all[4] to 200f, all[9] to 12f)
        assertEquals(
            CoursePalette(all, o, dark = true).hues,
            CoursePalette(all.reversed(), o, dark = true).hues,
        )

        // Semesters too, including the shared course that crosses them.
        assertEquals(
            CoursePalette.bySemester(listOf(autumn, spring), dark = false).hues,
            CoursePalette.bySemester(listOf(autumn.reversed(), spring.reversed()), dark = false).hues,
        )
    }

    @Test
    fun addingACourseRedrawsThatTermAndOnlyThatTerm() {
        // The other half of determinism: colours are allowed to move when the
        // data moves, and the roster is the data. A course joining the spring
        // re-rolls the spring, because five hues have to become six, and the
        // autumn is untouched because its own seed did not change.
        val grown = spring + "02205"
        val before = CoursePalette.bySemester(listOf(autumn, spring), dark = false)
        val after = CoursePalette.bySemester(listOf(autumn, grown), dark = false)
        for (code in autumn) assertEquals(before.hue(code), after.hue(code), code)
        assertEquals(6, grown.count { after.hue(it) != null })
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
