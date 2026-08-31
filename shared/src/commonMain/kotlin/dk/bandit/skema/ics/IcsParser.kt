package dk.bandit.skema.ics

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant

/** How a timestamp arrived. Getting this wrong is what hides the duplicates. */
enum class TimeForm {
    /** "20260902T110000Z", an absolute instant. Almost everything. */
    UTC,

    /** "TZID=Europe/Copenhagen:20260903T130000", wall time in a named zone. */
    ZONED,

    /** "20260903T130000" with no marker: wall time, zone unstated. */
    FLOATING,

    /** "VALUE=DATE:20260903", a whole day. */
    DATE,
}

data class CalendarEvent(
    val uid: String,
    /** Which occurrence of a recurring entry this is. 0 for one-off events. */
    val occurrence: Int,
    val summary: String,
    /** Local wall time in the display zone. What a person reads. */
    val start: LocalDateTime,
    val end: LocalDateTime?,
    /** Absolute time, for ordering and for spotting two events in one slot. */
    val startEpochSeconds: Long,
    val allDay: Boolean,
    val timeForm: TimeForm,
    val location: ParsedLocation,
    val description: String?,
    val descriptionWasHtml: Boolean,
    /** True when DTU generated this from a room booking rather than a human. */
    val fromRoomBooking: Boolean,
    val sequence: Int,
    val lastModified: String?,
    val rrule: String?,
) {
    val courseCode: String? get() = location.courseCode
    val rooms: List<String> get() = location.rooms
}

class IcsParser(private val zone: TimeZone) {

    fun parse(raw: String): List<CalendarEvent> =
        IcsLexer.vevents(raw).flatMap { toEvents(it) }.sortedBy { it.startEpochSeconds }

    private fun toEvents(props: List<IcsProperty>): List<CalendarEvent> {
        val by = props.associateBy { it.name }
        val dtStart = by["DTSTART"] ?: return emptyList()

        val startStamp = parseStamp(dtStart) ?: return emptyList()
        val endStamp = by["DTEND"]?.let { parseStamp(it) }

        val rawLocation = by["LOCATION"]?.value?.let(IcsLexer::unescape)
        val location = DtuLocation.parse(rawLocation)

        val rawDescription = by["DESCRIPTION"]?.value?.let(IcsLexer::unescape)
        val hadHtml = rawDescription?.let { HTML_TAG.containsMatchIn(it) } ?: false
        val description = rawDescription?.let(::stripHtml)?.ifBlank { null }

        val base = CalendarEvent(
            uid = by["UID"]?.value.orEmpty(),
            occurrence = 0,
            summary = by["SUMMARY"]?.value?.let(IcsLexer::unescape).orEmpty().ifBlank { "(no title)" },
            start = startStamp.local,
            end = endStamp?.local,
            startEpochSeconds = startStamp.epochSeconds,
            allDay = startStamp.form == TimeForm.DATE,
            timeForm = startStamp.form,
            location = location,
            description = description,
            descriptionWasHtml = hadHtml,
            fromRoomBooking = description?.contains(GENERATED_MARKER) == true,
            sequence = by["SEQUENCE"]?.value?.toIntOrNull() ?: 0,
            lastModified = by["LAST-MODIFIED"]?.value,
            rrule = by["RRULE"]?.value,
        )

        val rrule = base.rrule ?: return listOf(base)
        return Recurrence.expand(base, rrule, zone)
    }

    private data class Stamp(val local: LocalDateTime, val epochSeconds: Long, val form: TimeForm)

    private fun parseStamp(prop: IcsProperty): Stamp? {
        val v = prop.value.trim()

        if (prop.param("VALUE") == "DATE" || (v.length == 8 && !v.contains('T'))) {
            val d = runCatching { LocalDate.parse(isoDate(v)) }.getOrNull() ?: return null
            val instant = d.atStartOfDayIn(zone)
            return Stamp(LocalDateTime(d.year, d.month, d.day, 0, 0), instant.epochSeconds, TimeForm.DATE)
        }

        val isUtc = v.endsWith("Z")
        val body = if (isUtc) v.dropLast(1) else v
        val ldt = runCatching { LocalDateTime.parse(isoDateTime(body)) }.getOrNull() ?: return null

        return when {
            isUtc -> {
                val instant = ldt.toInstant(TimeZone.UTC)
                Stamp(localAt(instant.epochSeconds, zone), instant.epochSeconds, TimeForm.UTC)
            }
            prop.param("TZID") != null -> {
                val tz = runCatching { TimeZone.of(prop.param("TZID")!!) }.getOrDefault(zone)
                val instant = ldt.toInstant(tz)
                Stamp(localAt(instant.epochSeconds, zone), instant.epochSeconds, TimeForm.ZONED)
            }
            else -> {
                val instant = ldt.toInstant(zone)
                Stamp(ldt, instant.epochSeconds, TimeForm.FLOATING)
            }
        }
    }

    private companion object {
        const val GENERATED_MARKER = "automatically created and maintained"
        val HTML_TAG = Regex("""<[a-zA-Z/][^>]*>""")

        /** "20260902" -> "2026-09-02" */
        fun isoDate(v: String) = "${v.substring(0, 4)}-${v.substring(4, 6)}-${v.substring(6, 8)}"

        /** "20260902T110000" -> "2026-09-02T11:00:00" */
        fun isoDateTime(v: String): String {
            val d = isoDate(v.substringBefore('T'))
            val t = v.substringAfter('T')
            return "${d}T${t.substring(0, 2)}:${t.substring(2, 4)}:${t.substring(4, 6)}"
        }

        fun stripHtml(s: String): String =
            s.replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
                .replace(Regex("""<[^>]+>"""), "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&#39;", "'")
                .replace("&quot;", "\"")
                .replace(Regex("""[ \t]+"""), " ")
                .trim()
    }
}
