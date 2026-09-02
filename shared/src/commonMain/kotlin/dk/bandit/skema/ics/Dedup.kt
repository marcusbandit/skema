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
            events.map { it.courseCode }.distinct().size == 1 &&
            spansAgree

    /**
     * Whether the entries in this slot describe something of the same length.
     *
     * Sharing a start time and a course is not enough to be the same class,
     * and assuming it was produced the worst bug in this app. Measured on one
     * real feed: 88 slots hold one course more than once, and 34 of them
     * disagree about duration. 12 of those are a zero length marker such as
     * "m02-solutions - Available" sitting at 11:00 on top of a 240 minute
     * lecture for the same course. Merging those took the marker's title AND
     * its empty end, which the grid then clamps to midnight, so a four hour
     * class became an eleven hour block called "m02-solutions - Available"
     * that swallowed the afternoon.
     *
     * The remaining 22 really are one class written down twice with slightly
     * different lengths, 180 against 240 minutes, and those should still
     * merge. So the test is overlap rather than equality: entries share a
     * start, so the shorter one is contained in the longer, and requiring the
     * shorter to cover at least half the longer keeps a 180 with a 240 and
     * separates a 0 from a 240.
     */
    private val spansAgree: Boolean
        get() {
            // Entries that do not state a length cannot disagree about one.
            val spans = events.mapNotNull { it.spanMinutes() }
            if (spans.size < 2) return true
            val longest = spans.max()
            if (longest <= 0) return true
            return spans.min() * 2 >= longest
        }

    /** Different courses at the same time: a real conflict worth surfacing. */
    val isConflict: Boolean
        get() = events.size > 1 && !isDuplicate
}

/**
 * Wall clock minutes from start to end, or null when the feed gave no DTEND.
 *
 * Null and zero are different answers and the difference decides a merge. No
 * DTEND means the entry does not say how long it runs, so it cannot disagree
 * with anything; a DTEND equal to DTSTART is the entry saying, explicitly,
 * that it is a moment rather than a session, which is what DTU's
 * "... - Available" markers do.
 *
 * Computed off the local fields rather than an instant, because both sides of
 * a duplicated pair are read from the same feed in the same zone.
 */
internal fun CalendarEvent.spanMinutes(): Int? {
    val e = end ?: return null
    val days = (e.date.toEpochDays() - start.date.toEpochDays()).toInt()
    return days * 24 * 60 + (e.hour * 60 + e.minute) - (start.hour * 60 + start.minute)
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
