package dk.bandit.skema.ics

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val ZONE = TimeZone.of("Europe/Copenhagen")
private fun sample() = IcsParser(ZONE).parse(SAMPLE_FEED)

/** 3 September 2026, 13:00 Copenhagen. One slot, used by nearly every test. */
private const val SLOT = 1_756_904_400L
private val START = LocalDateTime(2026, Month.SEPTEMBER, 3, 13, 0)
private fun at(hour: Int) = LocalDateTime(2026, Month.SEPTEMBER, 3, hour, 0)

/**
 * A stand-in for one VEVENT. Everything a merge test cares about is a named
 * argument, so each test states only the field it is about.
 */
private fun event(
    uid: String,
    summary: String,
    location: String?,
    generated: Boolean,
    startEpochSeconds: Long = SLOT,
    end: LocalDateTime? = at(15),
    occurrence: Int = 0,
    description: String? = null,
    descriptionWasHtml: Boolean = false,
    sequence: Int = 0,
    lastModified: String? = null,
    rrule: String? = null,
) = CalendarEvent(
    uid = uid,
    occurrence = occurrence,
    summary = summary,
    start = START,
    end = end,
    startEpochSeconds = startEpochSeconds,
    allDay = false,
    timeForm = TimeForm.UTC,
    location = DtuLocation.parse(location),
    description = description,
    descriptionWasHtml = descriptionWasHtml,
    fromRoomBooking = generated,
    sequence = sequence,
    lastModified = lastModified,
    rrule = rrule,
)

/** The pair the feed actually ships: a person's entry and the room booking. */
private fun handMade(
    summary: String = "W3: Scheduling",
    location: String? = "02335 Operating systems, Fall 2026",
    uid: String = "6606-100@learn.inside.dtu.dk",
    end: LocalDateTime? = at(17),
    description: String? = null,
    descriptionWasHtml: Boolean = false,
    sequence: Int = 0,
    lastModified: String? = null,
    startEpochSeconds: Long = SLOT,
) = event(
    uid = uid,
    summary = summary,
    location = location,
    generated = false,
    end = end,
    description = description,
    descriptionWasHtml = descriptionWasHtml,
    sequence = sequence,
    lastModified = lastModified,
    startEpochSeconds = startEpochSeconds,
)

private fun generated(
    summary: String = "Lecture",
    location: String? = "LYN.308.AUD13.R:104 (02335 Operating systems, Fall 2026)",
    uid: String = "6606-900@learn.inside.dtu.dk",
    end: LocalDateTime? = at(15),
    description: String? = "This event is automatically created and maintained based on room bookings in TimeEdit.",
    descriptionWasHtml: Boolean = false,
    sequence: Int = 0,
    lastModified: String? = null,
    startEpochSeconds: Long = SLOT,
) = event(
    uid = uid,
    summary = summary,
    location = location,
    generated = true,
    end = end,
    description = description,
    descriptionWasHtml = descriptionWasHtml,
    sequence = sequence,
    lastModified = lastModified,
    startEpochSeconds = startEpochSeconds,
)

class MergeTest {

    @Test
    fun anIdenticalPairMergesToOne() {
        val a = handMade()
        val b = a.copy(uid = "6606-901@learn.inside.dtu.dk", fromRoomBooking = true)
        val merged = Merge.collapse(listOf(a, b))

        assertEquals(1, merged.size)
        val only = merged.single()
        assertEquals(a.summary, only.summary)
        assertEquals(a.end, only.end)
        assertEquals(a.rooms, only.rooms)
        assertEquals(a.location.courseName, only.location.courseName)
    }

    @Test
    fun aDifferingTitleTakesTheHandMadeOne() {
        val merged = Merge.collapse(listOf(generated(), handMade(summary = "W3: Scheduling"))).single()
        assertEquals("W3: Scheduling", merged.summary)
    }

    @Test
    fun theGeneratedTitleStandsInWhenTheHumanWroteNothing() {
        // IcsParser writes "(no title)" for an empty SUMMARY, which is a
        // placeholder rather than a title, so the generated word wins.
        val merged = Merge.collapse(listOf(handMade(summary = "(no title)"), generated())).single()
        assertEquals("Lecture", merged.summary)
    }

    @Test
    fun roomsSurviveFromTheGeneratedSideWhenTheHandMadeEntryHasNone() {
        val human = handMade(location = "02335 Operating systems, Fall 2026")
        assertTrue(human.rooms.isEmpty(), "the fixture stopped testing what it says it does")

        val merged = Merge.collapse(listOf(human, generated())).single()
        assertEquals(listOf("LYN.308.AUD13.R:104"), merged.rooms)
        assertEquals("W3: Scheduling", merged.summary)
    }

    @Test
    fun roomsFromBothSidesAreUnionedHandMadeFirst() {
        val human = handMade(location = "421.072.AUD (128) (02335 Operating systems, Fall 2026)")
        val booking = generated(
            location = "LYN.308.AUD13.R:104 | LYN.306.AUD34.R:134 (02335 Operating systems, Fall 2026)",
        )
        val merged = Merge.collapse(listOf(booking, human)).single()

        assertEquals(
            listOf("421.072.AUD (128)", "LYN.308.AUD13.R:104", "LYN.306.AUD34.R:134"),
            merged.rooms,
        )
    }

    @Test
    fun aRoomListedOnBothSidesIsNotRepeated() {
        val human = handMade(location = "lyn.308.aud13.r:104 (02335 Operating systems, Fall 2026)")
        val merged = Merge.collapse(listOf(human, generated())).single()
        assertEquals(listOf("lyn.308.aud13.r:104"), merged.rooms)
    }

    @Test
    fun twoDifferentCoursesInOneSlotStayTwoEvents() {
        // A real timetable clash. Merging this would hide a decision the
        // student has to make.
        val a = handMade(location = "02335 Operating systems, Fall 2026")
        val b = handMade(
            uid = "6606-200@learn.inside.dtu.dk",
            summary = "Lab",
            location = "02339 Cybersecurity fundamentals, Fall 2026",
        )
        val merged = Merge.collapse(listOf(a, b))

        assertEquals(2, merged.size)
        assertEquals(setOf("02335", "02339"), merged.mapNotNull { it.courseCode }.toSet())
    }

    @Test
    fun anEventWithoutACourseCodeIsNeverMerged() {
        val a = handMade(location = null)
        val b = generated(location = null)
        assertEquals(2, Merge.collapse(listOf(a, b)).size)
    }

    @Test
    fun threeEntriesOfOneCourseMergeToOne() {
        val human = handMade(location = "02335 Operating systems, Fall 2026")
        val first = generated(uid = "6606-900@learn.inside.dtu.dk")
        val second = generated(
            uid = "6606-901@learn.inside.dtu.dk",
            location = "LYN.306.AUD35.R:135 (02335 Operating systems, Fall 2026)",
        )
        val merged = Merge.collapse(listOf(first, human, second))

        assertEquals(1, merged.size)
        assertEquals("W3: Scheduling", merged.single().summary)
        assertEquals(listOf("LYN.308.AUD13.R:104", "LYN.306.AUD35.R:135"), merged.single().rooms)
        assertFalse(merged.single().fromRoomBooking)
    }

    @Test
    fun anAllGeneratedSlotStillCollapses() {
        val first = generated(uid = "6606-900@learn.inside.dtu.dk")
        val second = generated(
            uid = "6606-901@learn.inside.dtu.dk",
            location = "LYN.306.AUD35.R:135 (02335 Operating systems, Fall 2026)",
        )
        val merged = Merge.collapse(listOf(first, second)).single()

        assertEquals("Lecture", merged.summary)
        assertEquals(listOf("LYN.308.AUD13.R:104", "LYN.306.AUD35.R:135"), merged.rooms)
        // No person wrote any of this, so the card should still lead with the
        // course name rather than the word "Lecture".
        assertTrue(merged.fromRoomBooking)
    }

    @Test
    fun aMergeWithAHandMadeSideIsNoLongerFromARoomBooking() {
        val merged = Merge.collapse(listOf(generated(), handMade())).single()
        assertFalse(merged.fromRoomBooking, "the human title would be demoted to a subtitle")
    }

    @Test
    fun theHandMadeUidSurvivesWhateverOrderTheFeedArrivesIn() {
        val human = handMade()
        val booking = generated()
        val one = Merge.collapse(listOf(human, booking)).single()
        val other = Merge.collapse(listOf(booking, human)).single()

        assertEquals(human.uid, one.uid)
        assertEquals(human.uid, other.uid)
        assertEquals(human.occurrence, one.occurrence)
        // SkemaRepository diffs on this key, so an unstable choice would
        // report the class as removed and re-added on every sync.
        assertEquals(Merge.keyOf(one), Merge.keyOf(other))
    }

    @Test
    fun theWinnerIsTheSameWhenOnlyGeneratedEntriesCompete() {
        val first = generated(uid = "6606-900@learn.inside.dtu.dk")
        val second = generated(uid = "6606-901@learn.inside.dtu.dk")
        assertEquals(
            Merge.collapse(listOf(first, second)).single().uid,
            Merge.collapse(listOf(second, first)).single().uid,
        )
    }

    @Test
    fun theHandMadeEndWinsButAMissingOneDoesNot() {
        val longer = Merge.collapse(listOf(handMade(end = at(17)), generated(end = at(15)))).single()
        assertEquals(at(17), longer.end)

        val borrowed = Merge.collapse(listOf(handMade(end = null), generated(end = at(15)))).single()
        assertEquals(at(15), borrowed.end)
    }

    @Test
    fun theDescriptionKeepsItsOwnHtmlFlag() {
        val human = handMade(description = "Bring the assignment", descriptionWasHtml = false)
        val booking = generated(description = "Room booking boilerplate", descriptionWasHtml = true)
        val merged = Merge.collapse(listOf(booking, human)).single()

        assertEquals("Bring the assignment", merged.description)
        assertFalse(merged.descriptionWasHtml)
    }

    @Test
    fun theGeneratedDescriptionFillsInAnEmptyOne() {
        val human = handMade(description = "   ")
        val booking = generated(description = "Room booking boilerplate", descriptionWasHtml = true)
        val merged = Merge.collapse(listOf(human, booking)).single()

        assertEquals("Room booking boilerplate", merged.description)
        assertTrue(merged.descriptionWasHtml)
    }

    @Test
    fun aSlotWithNoDescriptionAnywhereKeepsNone() {
        val merged = Merge.collapse(listOf(handMade(description = null), generated(description = null))).single()
        assertNull(merged.description)
        assertFalse(merged.descriptionWasHtml)
    }

    @Test
    fun sequenceAndLastModifiedComeFromTheNewestSide() {
        val human = handMade(sequence = 2, lastModified = "20260814T141426Z")
        val booking = generated(sequence = 7, lastModified = "20260827T212400Z")
        val merged = Merge.collapse(listOf(human, booking)).single()

        assertEquals(7, merged.sequence)
        assertEquals("20260827T212400Z", merged.lastModified)
    }

    @Test
    fun theLongerCourseNameWins() {
        // The hand-made side routinely drops the term suffix the generated
        // one carries, and the suffix is a fact worth keeping.
        val human = handMade(location = "02335 Operating systems")
        val booking = generated(location = "LYN.308.AUD13.R:104 (02335 Operating systems, Fall 2026)")
        val merged = Merge.collapse(listOf(human, booking)).single()
        assertEquals("02335 Operating systems, Fall 2026", merged.location.courseName)
    }

    @Test
    fun theMergedLocationReadsBackLikeAFeedValue() {
        val human = handMade(location = "02335 Operating systems, Fall 2026")
        val merged = Merge.collapse(listOf(human, generated())).single()

        assertEquals(LocationShape.ROOMS_AND_COURSE, merged.location.shape)
        assertEquals("LYN.308.AUD13.R:104 (02335 Operating systems, Fall 2026)", merged.location.raw)
        // Re-parsing the rebuilt string has to give the same parts back.
        val again = DtuLocation.parse(merged.location.raw)
        assertEquals(merged.rooms, again.rooms)
        assertEquals(merged.location.courseName, again.courseName)
        assertEquals(merged.courseCode, again.courseCode)
    }

    @Test
    fun aRoomlessMergeStaysCourseOnly() {
        val human = handMade(location = "02335 Operating systems, Fall 2026")
        val booking = generated(location = "02335 Operating systems, Fall 2026")
        val merged = Merge.collapse(listOf(human, booking)).single()

        assertEquals(LocationShape.COURSE_ONLY, merged.location.shape)
        assertEquals("02335 Operating systems, Fall 2026", merged.location.raw)
        assertTrue(merged.rooms.isEmpty())
    }

    @Test
    fun collapsingTwiceEqualsCollapsingOnce() {
        val events = listOf(
            generated(),
            handMade(),
            // A separate slot two hours later, one course, one entry.
            handMade(
                uid = "6606-300@learn.inside.dtu.dk",
                summary = "Study group",
                startEpochSeconds = SLOT + 7200,
            ),
            // And a genuine clash, which must survive both passes untouched.
            handMade(
                uid = "6606-400@learn.inside.dtu.dk",
                summary = "Lab",
                location = "02339 Cybersecurity fundamentals, Fall 2026",
                startEpochSeconds = SLOT + 14400,
            ),
            generated(
                uid = "6606-401@learn.inside.dtu.dk",
                location = "LYN.306.AUD34.R:134 (02324 Advanced Programming, Spring 2026)",
                startEpochSeconds = SLOT + 14400,
            ),
        )
        val once = Merge.collapse(events)
        assertEquals(once, Merge.collapse(once))
        assertEquals(4, once.size)
    }

    @Test
    fun eventsComeBackInTimeOrder() {
        val events = listOf(
            handMade(uid = "6606-300@learn.inside.dtu.dk", startEpochSeconds = SLOT + 7200),
            generated(),
            handMade(),
        )
        val merged = Merge.collapse(events)
        assertTrue(merged.zipWithNext().all { (a, b) -> a.startEpochSeconds <= b.startEpochSeconds })
    }

    @Test
    fun theSourcesOfAMergedEventAreReported() {
        val human = handMade()
        val booking = generated()
        val lone = handMade(uid = "6606-300@learn.inside.dtu.dk", startEpochSeconds = SLOT + 7200)
        val merged = Merge.merge(listOf(booking, human, lone))

        val byKey = Merge.sourcesByKey(merged)
        assertEquals(setOf(human.uid, booking.uid), byKey.getValue(Merge.keyOf(human)).map { it.uid }.toSet())
        assertEquals(listOf(lone.uid), byKey.getValue(Merge.keyOf(lone)).map { it.uid })
        // Hand made first, so the provenance list reads in priority order.
        assertEquals(human.uid, byKey.getValue(Merge.keyOf(human)).first().uid)
    }

    @Test
    fun theSampleFeedKeepsTheHumanTitleAndTheBookedRoom() {
        val before = sample()
        val after = Merge.collapse(before)
        assertTrue(after.size < before.size, "nothing was collapsed")

        val start = Dedup.slots(before).first { it.isDuplicate }.startEpochSeconds
        val kept = after.single { it.startEpochSeconds == start }

        assertEquals("02369 Teaching", kept.summary)
        assertFalse(kept.fromRoomBooking)
        assertTrue(kept.rooms.contains("LYN.421.AUD72.R:035"), "lost the booked room: ${kept.rooms}")
        assertTrue(kept.rooms.contains("421.072.AUD (128)"), "lost the hand-made room: ${kept.rooms}")
        // Both sides end at 17:00 Copenhagen, one written as 15:00Z.
        assertEquals(17, kept.end?.hour)
        assertEquals("6606-436193@learn.inside.dtu.dk", kept.uid)
    }

    @Test
    fun theSampleFeedCollapsesIdempotently() {
        val once = Merge.collapse(sample())
        assertEquals(once, Merge.collapse(once))
    }

    @Test
    fun dedupStillDelegatesToTheMerge() {
        val events = sample()
        assertEquals(Merge.collapse(events), Dedup.collapse(events))
    }
}
