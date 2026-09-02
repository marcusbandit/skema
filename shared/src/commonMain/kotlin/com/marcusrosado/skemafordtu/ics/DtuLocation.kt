package com.marcusrosado.skemafordtu.ics

/**
 * The DTU feed puts three different things in LOCATION, and the course code
 * is only ever available here: every UID in the feed begins "6606-", the
 * university's org unit, so the UID tells you nothing about which course an
 * event belongs to.
 *
 * Measured over one real feed of 712 entries:
 *   305  other text, then course   "B341-A022 (02335 Operating systems, Fall 2026)"
 *   278  rooms, then course        "LYN.308.AUD13.R:104 (02324 Advanced Programming, Spring 2026)"
 *   153  course only, no room      "02335 Operating systems, Fall 2026"
 *
 * Room separators differ by origin: events generated from room bookings join
 * with "|", hand-made ones with commas.
 */
enum class LocationShape { ROOMS_AND_COURSE, TEXT_AND_COURSE, COURSE_ONLY, EMPTY }

data class ParsedLocation(
    val rooms: List<String>,
    val courseName: String?,
    val courseCode: String?,
    val shape: LocationShape,
    val raw: String,
)

object DtuLocation {

    /** A DTU course code is five digits, e.g. 02335, and keeps its leading zero. */
    private val CODE = Regex("""\b(\d{5})\b""")

    /** Building prefixes seen in generated room strings. */
    private val ROOMISH = Regex("""\b(?:LYN|BAL)\d*\.""")

    /** Trailing "(...)" holding the course, when present. */
    private val TRAILING_PARENS = Regex("""\(([^()]*)\)\s*$""")

    fun parse(rawIn: String?): ParsedLocation {
        val raw = rawIn?.trim().orEmpty()
        if (raw.isEmpty()) {
            return ParsedLocation(emptyList(), null, null, LocationShape.EMPTY, raw)
        }

        val match = TRAILING_PARENS.find(raw)
        val head = if (match != null) raw.substring(0, match.range.first).trim() else raw
        val course = match?.groupValues?.get(1)?.trim()
        val hasRooms = ROOMISH.containsMatchIn(head)

        // No trailing parens and nothing room-shaped: the whole value is the
        // course name, and this event simply has no room attached.
        if (match == null && !hasRooms) {
            return ParsedLocation(
                rooms = emptyList(),
                courseName = raw,
                courseCode = CODE.find(raw)?.groupValues?.get(1),
                shape = LocationShape.COURSE_ONLY,
                raw = raw,
            )
        }

        val shape = when {
            match != null && hasRooms -> LocationShape.ROOMS_AND_COURSE
            match != null -> LocationShape.TEXT_AND_COURSE
            else -> LocationShape.ROOMS_AND_COURSE
        }

        return ParsedLocation(
            rooms = splitRooms(head),
            courseName = course,
            courseCode = CODE.find(course ?: raw)?.groupValues?.get(1),
            shape = shape,
            raw = raw,
        )
    }

    /**
     * Split on "|" first, then on commas, because the two sources disagree
     * about the separator. Commas inside a trailing capacity marker such as
     * "421.MIDT.SA (32)" must survive, so only split on a comma when it is
     * not inside brackets.
     */
    private fun splitRooms(head: String): List<String> {
        if (head.isBlank()) return emptyList()
        return head.split('|')
            .flatMap { splitOutsideParens(it) }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun splitOutsideParens(s: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var depth = 0
        for (c in s) {
            when {
                c == '(' -> { depth++; cur.append(c) }
                c == ')' -> { if (depth > 0) depth--; cur.append(c) }
                c == ',' && depth == 0 -> { out.add(cur.toString()); cur.clear() }
                else -> cur.append(c)
            }
        }
        out.add(cur.toString())
        return out
    }
}
