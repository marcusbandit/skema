package com.marcusrosado.skemafordtu.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The window rule is arithmetic on minutes, and a grid is only as right as
 * that arithmetic, so it is pinned here rather than read off a screenshot.
 */
class DayWindowTest {

    @Test
    fun nothingGivesTheTeachingDay() {
        assertEquals(DayWindow(7, 18), DayWindow.holding(emptyList()))
    }

    @Test
    fun anOrdinaryDayStaysOnTheDefault() {
        // 08:00 to 17:00 sits inside the window on both sides.
        assertEquals(DayWindow(7, 18), DayWindow.holding(listOf(8 * 60, 17 * 60)))
    }

    @Test
    fun theEdgesNeedNoRoom() {
        // Starting at 07:00 and ending at 18:00 exactly is already inside.
        assertEquals(DayWindow(7, 18), DayWindow.holding(listOf(7 * 60, 18 * 60)))
    }

    @Test
    fun anEarlyStartOpensOnTheHourBefore() {
        // 06:30 opens at 06:00, not 06:30: the gutter is labelled in hours.
        assertEquals(DayWindow(6, 18), DayWindow.holding(listOf(6 * 60 + 30, 8 * 60)))
    }

    @Test
    fun aLateEndClosesOnTheHourAfter() {
        assertEquals(DayWindow(7, 20), DayWindow.holding(listOf(18 * 60, 19 * 60 + 15)))
    }

    @Test
    fun aMinutePastTheEdgeGrowsAWholeHour() {
        assertEquals(DayWindow(7, 19), DayWindow.holding(listOf(17 * 60, 18 * 60 + 1)))
    }

    @Test
    fun holdsIsClosedAtTheStartAndOpenAtTheEnd() {
        // 07:00 has a row; 18:00 is the bottom rule and nothing sits below it.
        val w = DayWindow(7, 18)
        assertEquals(true, w.holds(7 * 60))
        assertEquals(false, w.holds(7 * 60 - 1))
        assertEquals(true, w.holds(18 * 60 - 1))
        assertEquals(false, w.holds(18 * 60))
    }

    @Test
    fun theWindowNeverLeavesTheDay() {
        assertEquals(DayWindow(0, 24), DayWindow.holding(listOf(0, 24 * 60)))
    }
}
