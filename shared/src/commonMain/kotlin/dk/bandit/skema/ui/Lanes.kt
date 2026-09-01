package dk.bandit.skema.ui

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
}
