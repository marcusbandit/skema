package dk.bandit.skema.ui

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
