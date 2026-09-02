package com.marcusrosado.skemafordtu.ui

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private val WEEKDAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val MONTHS = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)
private val MONTH_NAMES = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

@OptIn(ExperimentalTime::class)
fun today(): LocalDate =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

@OptIn(ExperimentalTime::class)
fun nowSeconds(): Long = Clock.System.now().epochSeconds

fun weekdayShort(d: DayOfWeek): String = WEEKDAYS[d.ordinal]

fun monthShort(m: Month): String = MONTHS[m.ordinal]

/**
 * The month, spelled out.
 *
 * Abbreviating is a habit from column headers, and it leaked into places with
 * room to spare: a title bar saying "Jun" when "June" fits reads as a machine
 * saving three characters nobody asked it to save. Short names stay only
 * where the space is genuinely fixed, which in this app is nothing: even a
 * folded phone's title bar holds "September".
 */
fun monthLong(m: Month): String = MONTH_NAMES[m.ordinal]

private fun pad(n: Int): String = if (n < 10) "0$n" else n.toString()

fun clock(t: LocalDateTime): String = pad(t.hour) + ":" + pad(t.minute)

/** Minutes since midnight, right now. What the "you are here" line is drawn at. */
@OptIn(ExperimentalTime::class)
fun nowMinutes(): Int {
    val t = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    return t.hour * 60 + t.minute
}

/** "09:00" from a plain minute count, for the grid's clock gutter. */
fun clockOfMinute(minute: Int): String = pad((minute / 60) % 24) + ":" + pad(minute % 60)

/** "Wed 3 Sep", with the year only when it is not this one. */
fun dateLabel(d: LocalDate, reference: LocalDate = today()): String {
    val base = weekdayShort(d.dayOfWeek) + " " + d.day + " " + monthShort(d.month)
    return if (d.year == reference.year) base else "$base ${d.year}"
}

fun relativeDay(d: LocalDate, reference: LocalDate = today()): String? = when (d) {
    reference -> "Today"
    reference.plusDays(1) -> "Tomorrow"
    reference.plusDays(-1) -> "Yesterday"
    else -> null
}

private fun LocalDate.plusDays(n: Int): LocalDate = this.plus(n, DateTimeUnit.DAY)

/** How long ago the feed last landed, in the coarsest unit that still says something. */
fun agoLabel(epochSeconds: Long?): String {
    if (epochSeconds == null || epochSeconds <= 0) return "never"
    val delta = nowSeconds() - epochSeconds
    return when {
        delta < 60 -> "just now"
        delta < 3600 -> "${delta / 60} min ago"
        delta < 86_400 -> "${delta / 3600} h ago"
        delta < 172_800 -> "yesterday"
        else -> "${delta / 86_400} days ago"
    }
}

fun sizeLabel(bytes: Int): String =
    if (bytes < 1024) "$bytes B" else "${bytes / 1024} kB"

private val TERM_TAIL = Regex(
    """,\s*(Fall|Spring|Autumn|Summer|Winter|Efter\S*r|For\S*r)\s*\d{4}\s*$""",
    RegexOption.IGNORE_CASE,
)
private val LEADING_CODE = Regex("""^\d{5}\s+""")

data class EventText(val title: String, val subtitle: String?)

/**
 * What to actually put on the card.
 *
 * Entries DTU generated from a room booking are all titled "Lecture", and the
 * course they belong to is only ever named in LOCATION. So for those the
 * course leads and the generic word goes underneath. Entries a teacher wrote
 * keep their own title, because that is where the real information is: an
 * assignment deadline says what is due, and the course name would bury it.
 */
fun textFor(
    summary: String,
    courseName: String?,
    courseCode: String?,
    fromRoomBooking: Boolean,
): EventText {
    val course = courseName?.let(::tidy)?.takeIf { it.isNotBlank() }
    val own = tidy(summary).takeIf { it.isNotBlank() } ?: summary

    return if (fromRoomBooking && course != null) {
        EventText(course, own.takeIf { !it.equals(course, ignoreCase = true) })
    } else {
        val names = courseCode != null && courseCode in summary
        EventText(own, if (names) null else course?.takeIf { !it.equals(own, ignoreCase = true) })
    }
}

/** The chip carries the code and the term repeats on every single entry. */
private fun tidy(s: String): String =
    s.replace(TERM_TAIL, "").replace(LEADING_CODE, "").trim()
