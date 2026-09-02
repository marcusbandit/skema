package com.marcusrosado.skemafordtu.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Lane assignment is the one piece of geometry that cannot be checked by
 * looking at a screenshot: two blocks side by side look correct whether the
 * split was computed or guessed. These pin the cases that actually go wrong.
 */
class LanesTest {

    private data class Ev(val s: Int, val e: Int)

    private fun lay(vararg ev: Ev) = Lanes.layout(ev.toList(), { it.s }, { it.e })

    @Test
    fun oneEventTakesTheWholeColumn() {
        val out = lay(Ev(540, 600))
        assertEquals(1, out.size)
        assertEquals(1, out[0].lanes)
        assertEquals(0, out[0].lane)
        assertEquals(1f, out[0].widthFraction)
    }

    @Test
    fun eventsThatDoNotTouchBothStayFullWidth() {
        // 09:00 to 10:00, then 11:00 to 12:00. Nothing is shared, so a
        // clash later in the day must not shrink either of them.
        val out = lay(Ev(540, 600), Ev(660, 720))
        assertTrue(out.all { it.lanes == 1 }, "separate events should not share a cluster")
    }

    @Test
    fun twoOverlappingEventsSplitTheColumn() {
        val out = lay(Ev(540, 660), Ev(600, 720))
        assertEquals(2, out.size)
        assertTrue(out.all { it.lanes == 2 })
        assertEquals(setOf(0, 1), out.map { it.lane }.toSet())
    }

    @Test
    fun aThreeWayOverlapUsesThreeLanes() {
        val out = lay(Ev(540, 660), Ev(560, 680), Ev(580, 700))
        assertTrue(out.all { it.lanes == 3 })
        assertEquals(setOf(0, 1, 2), out.map { it.lane }.toSet())
    }

    @Test
    fun aLaneIsReusedOnceItsOccupantHasFinished() {
        // A runs 09:00 to 12:00. B 09:00 to 10:00 and C 10:00 to 11:00 both
        // fit in the second lane, so this is two lanes wide, not three.
        val out = lay(Ev(540, 720), Ev(540, 600), Ev(600, 660))
        assertTrue(out.all { it.lanes == 2 }, "C should reuse B's lane")
    }

    @Test
    fun aBusyAfternoonDoesNotSqueezeAQuietMorning() {
        val morning = Ev(480, 540)
        val out = lay(morning, Ev(780, 840), Ev(790, 850), Ev(800, 860))
        val m = out.first { it.item == morning }
        assertEquals(1, m.lanes, "the morning has the column to itself")
        assertTrue(out.filter { it.item != morning }.all { it.lanes == 3 })
    }

    @Test
    fun aZeroLengthEventStillGetsATappableBlock() {
        val out = lay(Ev(600, 600))
        assertEquals(Lanes.MIN_MINUTES, out[0].endMinute - out[0].startMinute)
    }

    @Test
    fun backToBackEventsDoNotCountAsOverlapping() {
        // 09:00 to 10:00 then 10:00 to 11:00. Sharing an edge is not a clash.
        val out = lay(Ev(540, 600), Ev(600, 660))
        assertTrue(out.all { it.lanes == 1 })
    }

    @Test
    fun lanesAndOffsetsAlwaysStayInsideTheColumn() {
        val out = lay(Ev(540, 660), Ev(560, 700), Ev(560, 580), Ev(900, 960))
        out.forEach {
            assertTrue(it.lane in 0 until it.lanes, "lane ${it.lane} of ${it.lanes}")
            assertTrue(it.offsetFraction >= 0f && it.offsetFraction + it.widthFraction <= 1.0001f)
        }
    }

    @Test
    fun emptyInputGivesEmptyOutput() {
        assertEquals(emptyList(), Lanes.layout(emptyList<Ev>(), { it.s }, { it.e }))
    }
}

/**
 * The stacking layout used when a column is too narrow to split.
 *
 * The property that matters is that every event keeps a readable header. Side
 * by side fails that on a folded phone: a 73dp column split two ways is 36dp
 * a lane, which holds no words. These pin the arrangement that replaces it.
 */
class CascadeTest {

    private data class Ev(val name: String, val s: Int, val e: Int)

    private fun cascade(vararg ev: Ev) = Lanes.cascade(ev.toList(), { it.s }, { it.e })

    private fun of(out: List<Stacked<Ev>>, name: String) = out.first { it.item.name == name }

    @Test
    fun aLoneEventIsNotStackedOnAnything() {
        val out = cascade(Ev("a", 540, 600))
        assertEquals(1, out.size)
        assertEquals(0, out[0].depth)
        assertEquals(1, out[0].cluster)
        assertEquals(0, out[0].headerSteps)
    }

    @Test
    fun theRealCaseFromTheFeedKeepsBothHeadersInTheClear() {
        // Thursday 3 Sep: a lecture 13:00 to 17:00 with group work 15:00 to
        // 17:00 on top of it. The lecture's header sits in the two hours
        // before the group work starts, so neither needs shifting down.
        val out = cascade(Ev("lecture", 780, 1020), Ev("group", 900, 1020))
        assertEquals(0, of(out, "lecture").depth)
        assertEquals(1, of(out, "group").depth)
        assertEquals(0, of(out, "lecture").headerSteps)
        assertEquals(0, of(out, "group").headerSteps, "a later start already clears the header")
    }

    @Test
    fun anIdenticalStartPushesTheUpperBlockDownSoBothHeadersShow() {
        val out = cascade(Ev("a", 540, 660), Ev("b", 540, 660))
        assertEquals(setOf(0, 1), out.map { it.depth }.toSet())
        assertEquals(listOf(0, 1), out.sortedBy { it.depth }.map { it.headerSteps })
    }

    @Test
    fun threeAtTheSameMinuteStepDownOneAfterAnother() {
        val out = cascade(Ev("a", 600, 720), Ev("b", 600, 720), Ev("c", 600, 720))
        assertEquals(listOf(0, 1, 2), out.sortedBy { it.depth }.map { it.headerSteps })
    }

    @Test
    fun onlyAnIdenticalStartCountsTowardsTheShift() {
        // Three overlap, but the third starts later, so it is stacked without
        // being pushed down: its header is already below the others' tops.
        val out = cascade(Ev("a", 540, 720), Ev("b", 540, 720), Ev("c", 600, 720))
        assertEquals(0, of(out, "c").headerSteps)
        assertEquals(2, of(out, "c").depth)
    }

    @Test
    fun separateEventsAreSeparateClusters() {
        val out = cascade(Ev("morning", 540, 600), Ev("afternoon", 780, 840))
        assertTrue(out.all { it.depth == 0 && it.cluster == 1 })
    }

    @Test
    fun backToBackEventsDoNotStack() {
        val out = cascade(Ev("a", 540, 600), Ev("b", 600, 660))
        assertTrue(out.all { it.cluster == 1 }, "sharing an edge is not an overlap")
    }

    @Test
    fun bothArrangementsGroupTheSameEventsTogether() {
        // The two must disagree about arrangement only, never about what
        // overlaps what, or switching between them at a zoom threshold would
        // silently regroup the day.
        //
        // Note the two numbers are not the same thing: `cluster` counts the
        // events sharing a run, `lanes` counts how many columns that run
        // needs. Here a, b and c form one run of three that fits in two
        // lanes, because c reuses the lane b vacated.
        val evs = listOf(Ev("a", 540, 720), Ev("b", 560, 600), Ev("c", 600, 660), Ev("d", 900, 960))
        val lanes = Lanes.layout(evs, { it.s }, { it.e })
        val stack = Lanes.cascade(evs, { it.s }, { it.e })

        assertEquals(evs.size, stack.size)
        listOf("a", "b", "c").forEach {
            assertEquals(3, of(stack, it).cluster, "$it belongs to the run of three")
        }
        assertEquals(1, of(stack, "d").cluster, "d is alone")

        val laneOf = { n: String -> lanes.first { p -> p.item.name == n } }
        listOf("a", "b", "c").forEach { assertEquals(2, laneOf(it).lanes, "$it shares two lanes") }
        assertEquals(1, laneOf("d").lanes)
    }

    @Test
    fun aZeroLengthEventStillGetsATappableBlock() {
        val out = cascade(Ev("a", 600, 600))
        assertEquals(Lanes.MIN_MINUTES, out[0].endMinute - out[0].startMinute)
    }

    @Test
    fun emptyInputGivesEmptyOutput() {
        assertEquals(emptyList(), Lanes.cascade(emptyList<Ev>(), { it.s }, { it.e }))
    }

    @Test
    fun sideBySideIsRefusedOnceLanesStopHoldingWords() {
        // A folded day column is about 73dp. One event is fine, two is not.
        assertTrue(sideBySideFits(73.dp, 1))
        assertTrue(!sideBySideFits(73.dp, 2), "36dp a lane holds no words")
        // The unfolded pane gives wider columns, where splitting is honest.
        assertTrue(sideBySideFits(180.dp, 2))
    }
}
