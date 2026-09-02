package com.marcusrosado.skemafordtu.ics

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * DTU teaches in thirteen week blocks and everyone counts in them. The owner's
 * reference point: the autumn 2026 block opens on Monday 31 August, which is
 * the Monday of the week holding 1 September.
 */
class SemesterWeekTest {

    @Test
    fun autumnTwentyTwentySixOpensOnTheThirtyFirstOfAugust() {
        assertEquals(LocalDate(2026, 8, 31), Term(Season.Fall, 2026).teachingStartsOn)
    }

    @Test
    fun theBlockAlwaysOpensOnAMonday() {
        for (y in 2024..2032) {
            for (season in Season.entries) {
                val start = Term(season, y).teachingStartsOn
                assertEquals(0, start.dayOfWeek.ordinal, "$season $y opened on ${start.dayOfWeek}")
            }
        }
    }

    @Test
    fun theOpeningWeekIsWeekOne() {
        assertEquals(1, Terms.semesterWeek(LocalDate(2026, 8, 31)))
        assertEquals(1, Terms.semesterWeek(LocalDate(2026, 9, 4)), "still the opening week on Friday")
    }

    @Test
    fun theWeekAfterIsWeekTwo() {
        assertEquals(2, Terms.semesterWeek(LocalDate(2026, 9, 7)))
    }

    @Test
    fun aThirteenWeekBlockRunsToWeekThirteen() {
        // 13 weeks from Monday 31 August is Monday 23 November.
        assertEquals(13, Terms.semesterWeek(LocalDate(2026, 11, 23)))
        assertEquals(Terms.TEACHING_WEEKS, 13)
    }

    @Test
    fun theExamPeriodCountsPastThirteenRatherThanFailing() {
        assertTrue(Terms.semesterWeek(LocalDate(2026, 12, 7)) > Terms.TEACHING_WEEKS)
    }

    @Test
    fun springOpensOnTheMondayOfTheWeekHoldingTheFirstOfFebruary() {
        // 1 February 2026 is a Sunday, so its week opened on 26 January.
        assertEquals(LocalDate(2026, 1, 26), Term(Season.Spring, 2026).teachingStartsOn)
    }

    @Test
    fun lateJanuaryIsStillFiledUnderTheAutumnBlock() {
        // Deliberately recorded rather than asserted as correct: Terms.of puts
        // every January date in the preceding autumn, so 26 January counts as
        // a late autumn week rather than spring week 1, even though spring's
        // own block opens that Monday. DTU runs a separate three week January
        // period and which side it belongs to is a real question this code
        // does not yet answer. Flagged for the owner.
        val lateJanuary = LocalDate(2026, 1, 26)
        assertEquals(Term(Season.Fall, 2025), Terms.of(lateJanuary))
        assertTrue(Terms.semesterWeek(lateJanuary) > Terms.TEACHING_WEEKS)
    }

    @Test
    fun januaryStillBelongsToTheAutumnBlockItOpened() {
        // Terms.of puts January in the preceding Fall, so its semester week
        // keeps counting from that autumn rather than restarting.
        val january = LocalDate(2027, 1, 11)
        assertEquals(Term(Season.Fall, 2026), Terms.of(january))
        assertTrue(Terms.semesterWeek(january) > Terms.TEACHING_WEEKS)
    }

    @Test
    fun consecutiveWeeksAlwaysDifferByOne() {
        var d = LocalDate(2026, 8, 31)
        var previous = Terms.semesterWeek(d)
        repeat(12) {
            d = LocalDate(2026, 8, 31).let { s -> LocalDate.fromEpochDays(s.toEpochDays() + 7L * (it + 1)) }
            val now = Terms.semesterWeek(d)
            assertEquals(previous + 1, now, "week jumped at $d")
            previous = now
        }
    }
}
