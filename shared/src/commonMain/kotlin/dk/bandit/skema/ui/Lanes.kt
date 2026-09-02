package dk.bandit.skema.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Where overlapping events sit next to each other in a time column.
 *
 * A day column is one axis of time and one axis of "how many things are
 * happening at once". The second axis is not a property of an event, it is a
 * property of the set, so it has to be computed: an event that is alone at
 * 09:00 is full width, and the same event is half width the moment something
 * else lands on top of it.
 *
 * Nothing here knows about pixels. It hands back a lane index and the number
 * of lanes that lane is competing in, and the view turns that into a rectangle
 * with lane / lanes, so the same maths works for a 411dp folded column and an
 * 841dp unfolded one.
 */
/**
 * An event drawn stacked rather than side by side.
 *
 * Splitting a column between overlapping events is the textbook answer and it
 * fails on a folded phone: a 73dp day column split two ways leaves 36dp, which
 * holds no words at all, so two events become two anonymous stripes. Stacking
 * trades a little truth about width for the thing that actually matters, which
 * is that you can read what both of them are.
 *
 * Each event keeps nearly the full column and is inset from the left by its
 * depth, so every one below shows a margin and its coloured rail. They are
 * drawn in start order with the later on top, so the earlier event's header
 * sits in the clear above the later one's top edge. The only case that breaks
 * is two events starting in the same minute, where the upper one would cover
 * the lower one's header exactly; [headerSteps] counts those and the view
 * pushes the top down by that many header heights so both are legible.
 */
data class Stacked<T>(
    val item: T,
    /** How many events in this cluster start at or before it. Drives the inset. */
    val depth: Int,
    val cluster: Int,
    val startMinute: Int,
    val endMinute: Int,
    /** Header heights to push the drawn top down by, to clear an identical start. */
    val headerSteps: Int,
)

/**
 * Whether a column is wide enough for the honest side by side layout.
 *
 * Below this the lanes stop holding words, so the view switches to stacking.
 * Expressed as the width one lane needs, so the decision follows the zoom and
 * the fold rather than being a device check.
 */
fun sideBySideFits(columnWidth: Dp, lanes: Int): Boolean =
    lanes <= 1 || columnWidth / lanes >= 84.dp

data class Placed<T>(
    val item: T,
    /** Which column within the cluster, 0 based. */
    val lane: Int,
    /** How many columns the cluster needs. Width is 1f / lanes. */
    val lanes: Int,
    val startMinute: Int,
    val endMinute: Int,
) {
    /** Left edge as a fraction of the column, so the view never hardcodes a slot. */
    val offsetFraction: Float get() = lane.toFloat() / lanes
    val widthFraction: Float get() = 1f / lanes
}

object Lanes {

    /**
     * Anything shorter than this still gets a tappable block. DTU has a
     * handful of zero length markers ("Assignment due") and a 0 minute event
     * would otherwise be an invisible sliver.
     */
    const val MIN_MINUTES = 20

    /**
     * Greedy interval colouring, cluster by cluster.
     *
     * Events are placed in start order into the first lane whose previous
     * occupant has already finished. A cluster ends the moment nothing is
     * still running, and the lane count is recorded per cluster rather than
     * per day: one 4 way overlap at 13:00 must not squeeze the 08:00 lecture
     * that has the morning to itself.
     */
    fun <T> layout(
        items: List<T>,
        start: (T) -> Int,
        end: (T) -> Int,
    ): List<Placed<T>> {
        if (items.isEmpty()) return emptyList()

        val sorted = items
            .map { Triple(it, start(it), maxOf(end(it), start(it) + MIN_MINUTES)) }
            .sortedWith(compareBy({ it.second }, { -(it.third - it.second) }))

        val out = mutableListOf<Placed<T>>()
        var cluster = mutableListOf<Triple<T, Int, Int>>()
        var clusterLanes = mutableListOf<Int>()   // lane index -> minute it frees up
        var assigned = mutableListOf<Int>()       // lane per event in `cluster`
        var clusterEnd = Int.MIN_VALUE

        fun flush() {
            if (cluster.isEmpty()) return
            val n = clusterLanes.size
            cluster.forEachIndexed { i, (item, s, e) ->
                out += Placed(item, assigned[i], n, s, e)
            }
            cluster = mutableListOf()
            clusterLanes = mutableListOf()
            assigned = mutableListOf()
            clusterEnd = Int.MIN_VALUE
        }

        for (row in sorted) {
            val (_, s, e) = row
            // Nothing from the previous cluster is still running, so the lane
            // count resets and the next event goes back to full width.
            if (s >= clusterEnd) flush()

            var lane = clusterLanes.indexOfFirst { it <= s }
            if (lane == -1) {
                lane = clusterLanes.size
                clusterLanes.add(e)
            } else {
                clusterLanes[lane] = e
            }
            cluster.add(row)
            assigned.add(lane)
            clusterEnd = maxOf(clusterEnd, e)
        }
        flush()

        return out
    }

    /**
     * The stacking alternative to [layout], for columns too narrow to split.
     *
     * Clusters are found exactly as in [layout], so the two agree about what
     * overlaps what; only the arrangement inside a cluster differs.
     */
    fun <T> cascade(
        items: List<T>,
        start: (T) -> Int,
        end: (T) -> Int,
    ): List<Stacked<T>> {
        if (items.isEmpty()) return emptyList()

        val sorted = items
            .map { Triple(it, start(it), maxOf(end(it), start(it) + MIN_MINUTES)) }
            .sortedWith(compareBy({ it.second }, { -(it.third - it.second) }))

        val out = mutableListOf<Stacked<T>>()
        var cluster = mutableListOf<Triple<T, Int, Int>>()
        var clusterEnd = Int.MIN_VALUE

        fun flush() {
            if (cluster.isEmpty()) return
            val n = cluster.size
            cluster.forEachIndexed { i, (item, s, e) ->
                // Only events sharing this one's exact start can hide its
                // header, so that is what the shift counts, not the depth.
                val sameStart = cluster.take(i).count { it.second == s }
                out += Stacked(item, i, n, s, e, sameStart)
            }
            cluster = mutableListOf()
            clusterEnd = Int.MIN_VALUE
        }

        for (row in sorted) {
            if (row.second >= clusterEnd) flush()
            cluster.add(row)
            clusterEnd = maxOf(clusterEnd, row.third)
        }
        flush()
        return out
    }
}
