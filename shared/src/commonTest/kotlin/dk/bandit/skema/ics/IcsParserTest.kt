package dk.bandit.skema.ics

import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val CPH = TimeZone.of("Europe/Copenhagen")
private fun parse() = IcsParser(CPH).parse(SAMPLE_FEED)

class IcsLexerTest {

    @Test
    fun unfoldsTabContinuations() {
        // The trap. DTU folds with a tab, so a space-only unfold truncates.
        val folded = "LOCATION:LYN.306.AUD34.R:134 | LYN.306.AUD35.R:135 (02339 Cyber\n\tsecurity)"
        val prop = IcsLexer.parseLine(IcsLexer.unfold(folded))
        assertNotNull(prop)
        assertTrue(prop.value.endsWith("(02339 Cybersecurity)"), "tail was lost: ${prop.value}")
    }

    @Test
    fun unfoldsSpaceContinuationsToo() {
        // The fold marker is the line break AND the whitespace after it, so
        // both are removed. A fold can land mid-word, and usually does.
        val prop = IcsLexer.parseLine(IcsLexer.unfold("SUMMARY:Advanced Program\n ming"))
        assertEquals("Advanced Programming", prop?.value)
    }

    @Test
    fun handlesBareLineFeeds() {
        // The feed ships LF, not the CRLF the spec asks for.
        assertEquals(6, IcsLexer.vevents(SAMPLE_FEED).size)
    }

    @Test
    fun keepsEscapedCommasInOneValue() {
        // "02335 Operating systems\, Fall 2026" is one value, not two.
        assertEquals("02335 Operating systems, Fall 2026", IcsLexer.unescape("""02335 Operating systems\, Fall 2026"""))
    }

    @Test
    fun readsParameters() {
        val prop = IcsLexer.parseLine("DTSTART;TZID=Europe/Copenhagen:20260903T130000")
        assertEquals("DTSTART", prop?.name)
        assertEquals("Europe/Copenhagen", prop?.param("TZID"))
        assertEquals("20260903T130000", prop?.value)
    }
}

class TimeFormTest {

    @Test
    fun utcIsConvertedToLocalWallTime() {
        // 11:00Z on 2 September is 13:00 in Copenhagen, summer time.
        val e = parse().first { it.uid.startsWith("6606-446789") }
        assertEquals(TimeForm.UTC, e.timeForm)
        assertEquals(13, e.start.hour)
    }

    @Test
    fun zonedIsNotTreatedAsUtc() {
        // This is how the duplicate hides. TZID 13:00 local is 13:00 local,
        // not 15:00, and only then does it collide with the UTC event above.
        val e = parse().first { it.uid.startsWith("6606-436193") }
        assertEquals(TimeForm.ZONED, e.timeForm)
        assertEquals(13, e.start.hour)
    }

    @Test
    fun allDayEventsAreMarked() {
        val e = parse().first { it.uid.startsWith("6606-435148") }
        assertTrue(e.allDay)
        assertEquals(TimeForm.DATE, e.timeForm)
    }
}

class DtuLocationTest {

    @Test
    fun splitsGeneratedRoomsOnPipe() {
        val p = DtuLocation.parse("LYN.306.AUD34.R:134 | LYN.306.AUD35.R:135 (02339 Cybersecurity fundamentals, Fall 2026)")
        assertEquals(listOf("LYN.306.AUD34.R:134", "LYN.306.AUD35.R:135"), p.rooms)
        assertEquals("02339", p.courseCode)
        assertEquals(LocationShape.ROOMS_AND_COURSE, p.shape)
    }

    @Test
    fun splitsHandMadeRoomsOnComma() {
        val p = DtuLocation.parse("421.MIDT.SA (32),421.EAST.SA (48) (02369 Software processes and patterns, Fall 2026)")
        assertEquals(listOf("421.MIDT.SA (32)", "421.EAST.SA (48)"), p.rooms)
        assertEquals("02369", p.courseCode)
    }

    @Test
    fun courseOnlyValueYieldsNoRooms() {
        val p = DtuLocation.parse("02335 Operating systems, Fall 2026")
        assertEquals(emptyList(), p.rooms)
        assertEquals("02335", p.courseCode)
        assertEquals(LocationShape.COURSE_ONLY, p.shape)
    }

    @Test
    fun emptyLocationIsNotAnError() {
        val p = DtuLocation.parse(null)
        assertEquals(LocationShape.EMPTY, p.shape)
        assertEquals(null, p.courseCode)
    }

    @Test
    fun courseCodeKeepsItsLeadingZero() {
        assertEquals("02335", DtuLocation.parse("02335 Operating systems, Fall 2026").courseCode)
    }
}

class EventTest {

    @Test
    fun generatedEventsAreDistinguishedFromHandMade() {
        val events = parse()
        assertTrue(events.first { it.uid.startsWith("6606-449151") }.fromRoomBooking)
        assertTrue(!events.first { it.uid.startsWith("6606-436193") }.fromRoomBooking)
    }

    @Test
    fun theGeneratedBoilerplateBecomesAReservationRatherThanADescription() {
        // This entry's whole DESCRIPTION is DTU's sentence about the booking
        // being maintained from TimeEdit. 278 of one feed's 688 descriptions
        // are that same sentence, differing only in the number and carrying
        // nothing else, so it is kept as the number and dropped as prose.
        val e = parse().first { it.uid.startsWith("6606-440425") }
        assertNull(e.description, "the boilerplate is not a description")
        assertEquals("74249", e.reservation)
        assertTrue(e.fromRoomBooking, "removing the text must not erase the evidence")
    }

    @Test
    fun markupIsStrippedBeforeTheBoilerplateIsRecognised() {
        // The 02339 entry wraps the same sentence in a span. If the markup
        // were not flattened first the sentence would not match and the
        // paragraph would reach the screen.
        val e = parse().first { it.uid.startsWith("6606-446789") }
        assertTrue(e.descriptionWasHtml, "this one really is the HTML variant")
        assertNull(e.description)
        assertEquals("72864", e.reservation)
    }

    @Test
    fun aHumanDescriptionIsLeftAlone() {
        val e = parse().first { it.uid.startsWith("6606-436193") }
        assertEquals("Teaching", e.description)
        assertNull(e.reservation, "a teacher's entry has no booking behind it")
    }

    @Test
    fun roomsSurviveTheFold() {
        // The pipe-joined room list is exactly what tab folding truncates.
        val e = parse().first { it.uid.startsWith("6606-446789") }
        assertEquals(2, e.rooms.size)
        assertContains(e.rooms, "LYN.306.AUD35.R:135")
    }
}

class RecurrenceTest {

    @Test
    fun weeklyRuleExpandsUntilItsEndDate() {
        val occurrences = parse().filter { it.uid.startsWith("6606-436193") }
        assertTrue(occurrences.size > 1, "weekly rule did not expand")
        assertTrue(occurrences.all { it.start.hour == 13 }, "an occurrence drifted off 13:00")
        // UNTIL=20261126, so nothing may land in December.
        assertTrue(occurrences.none { it.start.year == 2026 && it.start.month == Month.DECEMBER })
    }

    @Test
    fun occurrencesAreNumberedAndOrdered() {
        val occurrences = parse().filter { it.uid.startsWith("6606-436193") }
        assertEquals(occurrences.indices.toList(), occurrences.map { it.occurrence })
    }
}

class DedupTest {

    @Test
    fun sameCourseSameSlotIsADuplicate() {
        // 02369 at 13:00 on 3 September: one hand made, one from the booking.
        val slot = Dedup.slots(parse()).first { it.events.size > 1 }
        assertTrue(slot.isDuplicate, "expected a duplicate, got ${slot.events.map { it.summary }}")
        assertTrue(!slot.isConflict)
    }

    @Test
    fun collapsingKeepsTheHumanTitleAndTheGeneratedRoom() {
        val before = parse()
        val after = Dedup.collapse(before)
        assertTrue(after.size < before.size, "nothing was collapsed")

        val slotStart = Dedup.slots(before).first { it.isDuplicate }.startEpochSeconds
        val kept = after.filter { it.startEpochSeconds == slotStart }
        assertEquals(1, kept.size)
        assertEquals("02369 Teaching", kept.single().summary)
        assertTrue(kept.single().rooms.isNotEmpty(), "kept the entry without a room")
    }

    @Test
    fun eventsWithoutCourseCodesAreNeverCalledDuplicates() {
        val a = parse().first { it.uid.startsWith("6606-446789") }
        val nameless = a.copy(location = DtuLocation.parse(null))
        val other = a.copy(uid = "other", location = DtuLocation.parse(null))
        val slot = Dedup.slots(listOf(nameless, other)).single()
        assertTrue(!slot.isDuplicate)
        assertTrue(slot.isConflict)
    }
}
