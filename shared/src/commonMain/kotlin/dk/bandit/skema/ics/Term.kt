package dk.bandit.skema.ics

import kotlinx.datetime.LocalDate

/**
 * Which semester an entry belongs to.
 *
 * This is not inferred: DTU writes it into LOCATION on almost every entry, as
 * a suffix on the course name. Measured over one real feed of 712 entries,
 * 694 carry one, and they are exactly four: Fall 2025 (300), Fall 2026 (211),
 * Spring 2026 (167) and Summer 2026 (16). The display code was already
 * throwing this away as noise, because the same term repeats on every card;
 * across a whole feed it is the strongest signal there is about which part of
 * a degree an entry belongs to.
 *
 * The 18 entries with no term are the ones with no course either, so they get
 * the term their date falls in instead.
 */
enum class Season { Spring, Summer, Fall }

data class Term(val season: Season, val year: Int) : Comparable<Term> {

    /**
     * Ordering is academic, not alphabetical: Fall 2025 comes BEFORE Spring
     * 2026, because a Danish academic year opens in the autumn.
     */
    private val rank: Int get() = year * 10 + season.ordinal

    override fun compareTo(other: Term): Int = rank.compareTo(other.rank)

    val label: String get() = season.name + " " + year

    /** Roughly when teaching starts, which is what an enrolment date is compared against. */
    val startsOn: LocalDate
        get() = when (season) {
            Season.Spring -> LocalDate(year, 2, 1)
            Season.Summer -> LocalDate(year, 6, 21)
            Season.Fall -> LocalDate(year, 8, 15)
        }
}

object Terms {

    /** ", Fall 2026" or ", Forår 2026" on the end of a course name. */
    private val SUFFIX = Regex(
        """,\s*(Fall|Autumn|Efter\S*r|Spring|For\S*r|Summer|Sommer)\s*(\d{4})\s*$""",
        RegexOption.IGNORE_CASE,
    )

    private fun season(word: String): Season = when {
        word.startsWith("spr", true) || word.startsWith("for", true) -> Season.Spring
        word.startsWith("sum", true) || word.startsWith("som", true) -> Season.Summer
        else -> Season.Fall
    }

    /** The term DTU stated, if it stated one. */
    fun parse(courseName: String?): Term? {
        val m = SUFFIX.find(courseName?.trim().orEmpty()) ?: return null
        val year = m.groupValues[2].toIntOrNull() ?: return null
        return Term(season(m.groupValues[1]), year)
    }

    /**
     * The term a date falls in, for the entries DTU left untagged.
     *
     * January belongs to the autumn that opened the academic year, not to the
     * calendar year it happens to sit in: DTU's three week January course is
     * the tail of the Fall semester, and putting it in Spring would file a
     * student's January exam under the wrong half of their degree.
     */
    fun of(date: LocalDate): Term = when (date.monthNumber) {
        1 -> Term(Season.Fall, date.year - 1)
        in 2..5 -> Term(Season.Spring, date.year)
        6 -> if (date.dayOfMonth < 21) Term(Season.Spring, date.year) else Term(Season.Summer, date.year)
        7 -> Term(Season.Summer, date.year)
        8 -> if (date.dayOfMonth < 15) Term(Season.Summer, date.year) else Term(Season.Fall, date.year)
        else -> Term(Season.Fall, date.year)
    }

    /** Stated term first, date second. Total, so every event lands somewhere. */
    fun of(event: CalendarEvent): Term =
        parse(event.location.courseName) ?: of(event.start.date)
}
