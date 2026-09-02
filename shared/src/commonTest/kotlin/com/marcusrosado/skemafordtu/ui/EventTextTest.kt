package com.marcusrosado.skemafordtu.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What the card says. These are display decisions about DTU's data, not
 * formatting trivia: the feed titles every generated entry "Lecture" and
 * names the course only in LOCATION, so getting this wrong gives you an
 * agenda that says "Lecture" nine times.
 */
class EventTextTest {

    @Test
    fun generatedEntriesLeadWithTheCourseNotTheWordLecture() {
        val t = textFor(
            summary = "Lecture",
            courseName = "02339 Cybersecurity fundamentals, Fall 2026",
            courseCode = "02339",
            fromRoomBooking = true,
        )
        assertEquals("Cybersecurity fundamentals", t.title)
        assertEquals("Lecture", t.subtitle)
    }

    @Test
    fun handMadeEntriesKeepTheirOwnTitle() {
        // An assignment deadline says what is due. The course name would bury it.
        val t = textFor(
            summary = "Modelleringsopgave 2 - Availability Ends",
            courseName = "02335 Operating systems, Fall 2026",
            courseCode = "02335",
            fromRoomBooking = false,
        )
        assertEquals("Modelleringsopgave 2 - Availability Ends", t.title)
        assertEquals("Operating systems", t.subtitle)
    }

    @Test
    fun theCourseCodeIsNotPrintedTwice() {
        // The chip already carries it, so it comes off the front of the title.
        val t = textFor(
            summary = "02369 Teaching",
            courseName = null,
            courseCode = "02369",
            fromRoomBooking = false,
        )
        assertEquals("Teaching", t.title)
        assertNull(t.subtitle, "the title already named the course")
    }

    @Test
    fun theTermIsStrippedBecauseEveryEntryCarriesIt() {
        val t = textFor("Lecture", "02324 Advanced Programming, Spring 2026", "02324", true)
        assertEquals("Advanced Programming", t.title)
    }

    @Test
    fun anEntryWithNoLocationStillGetsATitle() {
        val t = textFor("Study group", null, null, false)
        assertEquals("Study group", t.title)
        assertNull(t.subtitle)
    }

    @Test
    fun aGeneratedEntryWithNoCourseNameFallsBackToItsSummary() {
        val t = textFor("Lecture", null, null, true)
        assertEquals("Lecture", t.title)
        assertNull(t.subtitle)
    }

    @Test
    fun theSubtitleIsDroppedWhenItWouldRepeatTheTitle() {
        val t = textFor("Cybersecurity fundamentals", "02339 Cybersecurity fundamentals, Fall 2026", "02339", true)
        assertEquals("Cybersecurity fundamentals", t.title)
        assertNull(t.subtitle)
    }
}
