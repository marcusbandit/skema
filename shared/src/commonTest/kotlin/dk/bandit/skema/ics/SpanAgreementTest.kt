package dk.bandit.skema.ics

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Sharing a start time and a course does not make two entries the same class.
 *
 * These pin the case that produced an eleven hour block titled
 * "m02-solutions - Available": a zero length marker landing on the start of a
 * four hour lecture for the same course, merged, taking the marker's title and
 * its empty end, which the grid then clamps to midnight. Twelve slots in one
 * real feed do exactly this.
 */
class SpanAgreementTest {

    private val day = 2
    private fun at(hour: Int, minute: Int = 0) = LocalDateTime(2026, Month.SEPTEMBER, day, hour, minute)

    /** One entry starting at 11:00, running for [minutes]. */
    private fun ev(uid: String, minutes: Int, summary: String, generated: Boolean = false): CalendarEvent {
        val start = at(11)
        return CalendarEvent(
            uid = uid,
            occurrence = 0,
            summary = summary,
            start = start,
            end = at(11 + minutes / 60, minutes % 60),
            startEpochSeconds = 1_788_000_000L,
            allDay = false,
            timeForm = TimeForm.UTC,
            location = DtuLocation.parse("LYN.308.AUD13.R:104 (02335 Operating systems, Fall 2026)"),
            description = if (generated) "This event is automatically created and maintained based on room bookings in TimeEdit." else null,
            descriptionWasHtml = false,
            fromRoomBooking = generated,
            sequence = 0,
            lastModified = null,
            rrule = null,
        )
    }

    @Test
    fun aZeroLengthMarkerIsNotADuplicateOfTheLectureItSitsOn() {
        val slots = Dedup.slots(listOf(ev("a", 240, "Teaching"), ev("b", 0, "m02-solutions - Available")))
        assertEquals(1, slots.size, "they share a start, so they share a slot")
        assertFalse(slots[0].isDuplicate, "a marker and a lecture are two different things")
    }

    @Test
    fun bothSurviveTheMerge() {
        val out = Merge.collapse(listOf(ev("a", 240, "Teaching"), ev("b", 0, "m02-solutions - Available")))
        assertEquals(2, out.size)
        assertTrue(out.any { it.summary == "Teaching" })
        assertTrue(out.any { it.summary == "m02-solutions - Available" })
    }

    @Test
    fun theLectureKeepsItsOwnLength() {
        // The bug was the lecture inheriting the marker's empty end.
        val out = Merge.collapse(listOf(ev("a", 240, "Teaching"), ev("b", 0, "Available")))
        assertEquals(240, out.first { it.summary == "Teaching" }.spanMinutes())
    }

    @Test
    fun oneClassWrittenTwiceWithSlightlyDifferentLengthsStillMerges() {
        // 180 against 240 is one class recorded twice. 22 slots in the real
        // feed look like this, and merging them is correct.
        val events = listOf(ev("a", 240, "Teaching"), ev("b", 180, "Teaching"))
        assertTrue(Dedup.slots(events)[0].isDuplicate)
        assertEquals(1, Merge.collapse(events).size)
    }

    @Test
    fun exactlyHalfStillCounts() {
        val events = listOf(ev("a", 240, "Teaching"), ev("b", 120, "Teaching"))
        assertTrue(Dedup.slots(events)[0].isDuplicate, "half the longer span is the boundary, inclusive")
    }

    @Test
    fun justUnderHalfDoesNot() {
        val events = listOf(ev("a", 240, "Teaching"), ev("b", 119, "Teaching"))
        assertFalse(Dedup.slots(events)[0].isDuplicate)
    }

    @Test
    fun theOrdinaryTeacherAndBookingPairIsUntouched() {
        // The case the merge exists for: identical spans, one generated.
        val events = listOf(ev("a", 120, "Modelleringsopgave 2"), ev("b", 120, "Lecture", generated = true))
        assertTrue(Dedup.slots(events)[0].isDuplicate)
        assertEquals(1, Merge.collapse(events).size)
    }

    @Test
    fun anEntryThatStatesNoLengthStillMerges() {
        // No DTEND is "unknown", not "a moment". It cannot disagree, and the
        // merge is what gives it the other side's end.
        val noEnd = ev("a", 240, "Teaching").copy(end = null)
        val events = listOf(noEnd, ev("b", 240, "Lecture", generated = true))
        assertTrue(Dedup.slots(events)[0].isDuplicate)
        assertEquals(1, Merge.collapse(events).size)
    }

    @Test
    fun twoMarkersAtOneMomentAreStillTheSameThing() {
        val events = listOf(ev("a", 0, "Assignment due"), ev("b", 0, "Assignment due"))
        assertTrue(Dedup.slots(events)[0].isDuplicate, "all zero length, nothing to disagree about")
    }
}
