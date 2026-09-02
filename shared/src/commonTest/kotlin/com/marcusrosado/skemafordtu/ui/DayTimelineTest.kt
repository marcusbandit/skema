package com.marcusrosado.skemafordtu.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The graph is drawn row by row from these facts, so a wrong fact is a line
 * that forks from nowhere or a branch left hanging. Pinned here on minutes.
 */
class DayTimelineTest {

    private data class Ev(val s: Int, val e: Int, val course: String)

    private fun graph(vararg ev: Ev) = dayGraph(ev.toList(), { it.s }, { it.e }, { it.course })

    @Test
    fun aQuietDayIsOneTrunk() {
        // 08:00 to 10:00 then 10:00 to 12:00: nothing overlaps, so every row
        // sits on the trunk and nothing forks, merges or stays open.
        val g = graph(Ev(480, 600, "A"), Ev(600, 720, "B"))
        assertEquals(1, g.lanes)
        assertTrue(g.rows.all { it.forkFrom == null && it.through.isEmpty() && it.merges.isEmpty() })
        assertTrue(g.openBranches.isEmpty())
    }

    @Test
    fun aClashIsASecondLaneAndNotABranch() {
        // Two courses at once: the later one takes lane 1 in its own right.
        val g = graph(Ev(540, 660, "A"), Ev(600, 720, "B"))
        assertEquals(2, g.lanes)
        assertEquals(1, g.placed[1].lane)
        assertNull(g.rows[1].forkFrom)
        assertEquals(listOf(0), g.rows[1].through)
        assertTrue(g.openBranches.isEmpty())
    }

    @Test
    fun aLabInsideItsLectureForksAndStaysOpen() {
        // 13:00 to 17:00 and, inside it, 15:00 to 17:00 of the same course.
        // The second forks off the first and is still running at the last
        // row, so the tail has to merge it.
        val g = graph(Ev(780, 1020, "A"), Ev(900, 1020, "A"))
        assertEquals(0, g.rows[1].forkFrom)
        assertEquals(listOf(1), g.openBranches)
    }

    @Test
    fun aBranchThatEndsMergesOnTheNextRow() {
        // The lab ends at 10:00, so the 11:00 row is where it merges, into
        // the trunk because that is where its parent runs.
        val g = graph(Ev(480, 720, "A"), Ev(540, 600, "A"), Ev(660, 720, "B"))
        assertEquals(listOf(1 to 0), g.rows[2].merges)
        assertTrue(g.openBranches.isEmpty())
    }

    @Test
    fun aBranchMergesIntoItsParentsLaneWhileTheParentRuns() {
        // A on the trunk all morning, B beside it in lane 1, C a part of B in
        // lane 2, then D taking lane 2 once C has gone. C merges into B's
        // lane, not the trunk, because B is still running when D starts.
        val g = graph(
            Ev(480, 720, "A"),
            Ev(510, 720, "B"),
            Ev(540, 600, "B"),
            Ev(660, 720, "D"),
        )
        assertEquals(1, g.placed[1].lane)
        assertEquals(2, g.placed[2].lane)
        assertEquals(1, g.rows[2].forkFrom)
        assertEquals(listOf(2 to 1), g.rows[3].merges)
        assertEquals(3, g.lanes)
    }

    @Test
    fun continuesSaysWhetherTheLineGoesOnBelowTheDot() {
        // The first runs past the second's start; the second does not run
        // past the third's.
        val g = graph(Ev(480, 660, "A"), Ev(540, 600, "B"), Ev(700, 720, "C"))
        assertTrue(g.rows[0].continues)
        assertEquals(false, g.rows[1].continues)
    }
}
