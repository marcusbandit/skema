package dk.bandit.skema.ics

/**
 * The DTU feed lists many classes twice: once as a teacher's hand-made entry
 * and once generated from the room booking, with different titles and
 * different room notations. Measured over one real feed, 99 slots contain
 * the same course twice.
 *
 * Two events sharing a start time is not by itself evidence of anything: a
 * genuine timetable clash looks identical at that level. A missing course
 * code is not evidence of sameness either. So a slot is only treated as
 * duplicated when every event in it names the same course.
 */
data class Slot(
    val startEpochSeconds: Long,
    val events: List<CalendarEvent>,
) {
    /** Same course listed more than once: the feed's own redundancy. */
    val isDuplicate: Boolean
        get() = events.size > 1 &&
            events.all { it.courseCode != null } &&
            events.map { it.courseCode }.distinct().size == 1

    /** Different courses at the same time: a real conflict worth surfacing. */
    val isConflict: Boolean
        get() = events.size > 1 && !isDuplicate
}

object Dedup {

    fun slots(events: List<CalendarEvent>): List<Slot> =
        events.groupBy { it.startEpochSeconds }
            .map { (start, group) -> Slot(start, group) }
            .sortedBy { it.startEpochSeconds }

    /**
     * Collapse duplicated slots to one event each. The work is in
     * [Merge.collapse], which merges the entries field by field instead of
     * keeping one and discarding the other; this stays as the name the UI
     * already calls. Use [Merge.merge] when you also want to know which feed
     * entries an event came from.
     */
    fun collapse(events: List<CalendarEvent>): List<CalendarEvent> = Merge.collapse(events)
}
