package com.marcusrosado.skemafordtu.ics

import kotlinx.datetime.TimeZone
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Runs the parser over a whole real feed rather than the six-event fixture.
 * Skips unless pointed at one, so it stays useful without committing personal
 * data to the repository:
 *
 *     ./gradlew :shared:jvmTest -Dskema.feed=/path/to/feed.ics --info
 *
 * The feed URL carries a bearer token for your entire calendar. Save the file
 * outside the repo, and never commit either the file or the URL.
 */
class FullFeedTest {

    @Test
    fun parsesARealFeedAndReportsItsShape() {
        val path = System.getProperty("skema.feed")
        if (path.isNullOrBlank()) {
            println("skema.feed not set, skipping the real-feed check")
            return
        }
        val file = File(path)
        if (!file.exists()) {
            println("skema.feed points at ${file.absolutePath}, which does not exist, skipping")
            return
        }

        val raw = file.readText()
        val entries = IcsLexer.vevents(raw)
        val events = IcsParser(TimeZone.of("Europe/Copenhagen")).parse(raw)
        val slots = Dedup.slots(events)
        val collapsed = Dedup.collapse(events)

        val shapes = events.groupingBy { it.location.shape }.eachCount()
        val forms = events.groupingBy { it.timeForm }.eachCount()

        println(
            buildString {
                appendLine("--- real feed ---")
                appendLine("entries (VEVENT)     ${entries.size}")
                appendLine("events after expand  ${events.size}")
                appendLine("from room bookings   ${events.count { it.fromRoomBooking }}")
                appendLine("hand made            ${events.count { !it.fromRoomBooking }}")
                appendLine("no course code       ${events.count { it.courseCode == null }}")
                appendLine("duplicated slots     ${slots.count { it.isDuplicate }}")
                appendLine("real conflicts       ${slots.count { it.isConflict }}")
                appendLine("after collapsing     ${collapsed.size}")
                appendLine("location shapes      $shapes")
                appendLine("time forms           $forms")
                appendLine("range                ${events.first().start.date} .. ${events.last().start.date}")
            }
        )

        // Invariants that must hold for any feed, not just this one.
        assertTrue(events.isNotEmpty(), "parsed nothing")
        assertTrue(events.none { it.uid.isBlank() }, "an event lost its UID")
        assertTrue(
            events.zipWithNext().all { (a, b) -> a.startEpochSeconds <= b.startEpochSeconds },
            "events came back out of order",
        )
        assertTrue(collapsed.size <= events.size, "collapsing invented events")
        assertTrue(
            events.none { it.location.raw.isNotBlank() && it.location.shape == LocationShape.EMPTY },
            "a non-empty LOCATION was classified as empty",
        )
        // Truncation canary: tab folding would leave unbalanced brackets behind.
        val truncated = events.filter {
            it.location.raw.count { c -> c == '(' } != it.location.raw.count { c -> c == ')' }
        }
        assertTrue(truncated.isEmpty(), "unbalanced brackets suggest a fold was mishandled: ${truncated.take(2).map { it.location.raw }}")
    }
}
