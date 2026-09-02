package dk.bandit.skema.ics

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TermTest {

    @Test
    fun theFourTermsInTheRealFeedAllParse() {
        // Exactly the strings DTU writes, measured over one feed.
        assertEquals(Term(Season.Fall, 2025), Terms.parse("02322 Machine oriented programming, Fall 2025"))
        assertEquals(Term(Season.Fall, 2026), Terms.parse("02335 Operating systems, Fall 2026"))
        assertEquals(Term(Season.Spring, 2026), Terms.parse("02324 Advanced Programming, Spring 2026"))
        assertEquals(Term(Season.Summer, 2026), Terms.parse("02325 Programming project, Summer 2026"))
    }

    @Test
    fun danishTermNamesParseToo() {
        assertEquals(Term(Season.Fall, 2025), Terms.parse("02322 Maskinnaer programmering, Efteraar 2025"))
        assertEquals(Term(Season.Spring, 2026), Terms.parse("01922 Lineaer algebra, Foraar 2026"))
    }

    @Test
    fun aCourseNameWithNoTermGivesNothing() {
        assertNull(Terms.parse("02335 Operating systems"))
        assertNull(Terms.parse(null))
        assertNull(Terms.parse(""))
    }

    @Test
    fun theAcademicYearOpensInAutumn() {
        // The whole point of the ordering: Fall 2025 precedes Spring 2026.
        assertTrue(Term(Season.Fall, 2025) < Term(Season.Spring, 2026))
        assertTrue(Term(Season.Spring, 2026) < Term(Season.Summer, 2026))
        assertTrue(Term(Season.Summer, 2026) < Term(Season.Fall, 2026))
    }

    @Test
    fun theFeedsFourTermsSortIntoDegreeOrder() {
        val shuffled = listOf(
            Term(Season.Fall, 2026), Term(Season.Spring, 2026),
            Term(Season.Summer, 2026), Term(Season.Fall, 2025),
        )
        assertEquals(
            listOf("Fall 2025", "Spring 2026", "Summer 2026", "Fall 2026"),
            shuffled.sorted().map { it.label },
        )
    }

    @Test
    fun januaryBelongsToTheAutumnThatOpenedTheYear() {
        // DTU's three week January course is the tail of the Fall semester.
        // Filing it under Spring would put a January exam in the wrong half.
        assertEquals(Term(Season.Fall, 2025), Terms.of(LocalDate(2026, 1, 12)))
    }

    @Test
    fun theDateFallbackSplitsTheYearWhereTeachingDoes() {
        assertEquals(Term(Season.Spring, 2026), Terms.of(LocalDate(2026, 3, 4)))
        assertEquals(Term(Season.Spring, 2026), Terms.of(LocalDate(2026, 6, 10)))
        assertEquals(Term(Season.Summer, 2026), Terms.of(LocalDate(2026, 6, 25)))
        assertEquals(Term(Season.Summer, 2026), Terms.of(LocalDate(2026, 8, 3)))
        assertEquals(Term(Season.Fall, 2026), Terms.of(LocalDate(2026, 8, 31)))
        assertEquals(Term(Season.Fall, 2026), Terms.of(LocalDate(2026, 12, 4)))
    }

    @Test
    fun aTermStartsBeforeAnyOfItsTeaching() {
        // An enrolment date is compared against this, so it must not fall
        // after the first lecture of the term it names.
        assertTrue(Term(Season.Fall, 2025).startsOn <= LocalDate(2025, 9, 1))
        assertTrue(Term(Season.Spring, 2026).startsOn <= LocalDate(2026, 2, 2))
    }
}
