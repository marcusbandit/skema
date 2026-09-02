package com.marcusrosado.skemafordtu.ics

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant

/**
 * Deliberately not a full RFC 5545 recurrence engine, because the DTU feed
 * does not need one. Measured over a real feed: 2 of 712 entries carry an
 * RRULE, both `FREQ=WEEKLY;UNTIL=...;INTERVAL=1;BYDAY=TH`, and there is not
 * a single EXDATE, RDATE, EXRULE or RECURRENCE-ID anywhere in it. DTU emits
 * occurrences explicitly.
 *
 * So this handles weekly rules and returns the event unexpanded for anything
 * else, which is the honest failure mode: you get one event instead of a
 * wrong set of them. If a future feed grows monthly rules, this is where to
 * look, and [UNSUPPORTED_KINDS] is what to check for.
 */
object Recurrence {

    private const val MAX_OCCURRENCES = 500

    val UNSUPPORTED_KINDS = listOf("EXDATE", "RDATE", "EXRULE", "RECURRENCE-ID")

    private val DAYS = mapOf(
        "MO" to DayOfWeek.MONDAY, "TU" to DayOfWeek.TUESDAY, "WE" to DayOfWeek.WEDNESDAY,
        "TH" to DayOfWeek.THURSDAY, "FR" to DayOfWeek.FRIDAY, "SA" to DayOfWeek.SATURDAY,
        "SU" to DayOfWeek.SUNDAY,
    )

    fun expand(base: CalendarEvent, rrule: String, zone: TimeZone): List<CalendarEvent> {
        val parts = rrule.split(';').mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i).uppercase() to it.substring(i + 1)
        }.toMap()

        if (parts["FREQ"]?.uppercase() != "WEEKLY") return listOf(base)

        val interval = parts["INTERVAL"]?.toIntOrNull() ?: 1
        val count = parts["COUNT"]?.toIntOrNull()
        val untilEpoch = parts["UNTIL"]?.let { parseUntil(it, zone) }
        val byDay = parts["BYDAY"]?.split(',')?.mapNotNull { DAYS[it.trim().uppercase()] }.orEmpty()

        val durationSeconds = base.end
            ?.let { it.toInstant(zone).epochSeconds - base.start.toInstant(zone).epochSeconds }
            ?: 3600L

        val targetDays = byDay.ifEmpty { listOf(base.start.date.dayOfWeek) }
        val out = mutableListOf<CalendarEvent>()
        var week = 0

        while (out.size < MAX_OCCURRENCES) {
            val weekStart = base.start.date.plus(week * interval, DateTimeUnit.WEEK)
            var advanced = false

            for (day in targetDays.sortedBy { it.isoDayNumber() }) {
                val delta = day.isoDayNumber() - weekStart.dayOfWeek.isoDayNumber()
                val date = weekStart.plus(delta, DateTimeUnit.DAY)
                val local = kotlinx.datetime.LocalDateTime(
                    date.year, date.month, date.day,
                    base.start.hour, base.start.minute, base.start.second,
                )
                val epoch = local.toInstant(zone).epochSeconds
                if (epoch < base.startEpochSeconds) continue
                if (untilEpoch != null && epoch > untilEpoch) return out.ifEmpty { listOf(base) }

                out.add(
                    base.copy(
                        occurrence = out.size,
                        start = local,
                        end = localAt(epoch + durationSeconds, zone),
                        startEpochSeconds = epoch,
                    )
                )
                advanced = true
                if (count != null && out.size >= count) return out
            }

            week++
            if (!advanced && week > 2) break
            if (week > 520) break
        }
        return out.ifEmpty { listOf(base) }
    }

    private fun parseUntil(v: String, zone: TimeZone): Long? {
        val t = v.trim()
        return runCatching {
            if (t.endsWith("Z")) {
                val b = t.dropLast(1)
                isoLocal(b).toInstant(TimeZone.UTC).epochSeconds
            } else if (t.contains('T')) {
                isoLocal(t).toInstant(zone).epochSeconds
            } else {
                kotlinx.datetime.LocalDate.parse(
                    "${t.substring(0, 4)}-${t.substring(4, 6)}-${t.substring(6, 8)}"
                ).atStartOfDayIn(zone).epochSeconds
            }
        }.getOrNull()
    }

    private fun isoLocal(v: String): kotlinx.datetime.LocalDateTime {
        val d = v.substringBefore('T')
        val t = v.substringAfter('T')
        return kotlinx.datetime.LocalDateTime.parse(
            "${d.substring(0, 4)}-${d.substring(4, 6)}-${d.substring(6, 8)}T" +
                "${t.substring(0, 2)}:${t.substring(2, 4)}:${t.substring(4, 6)}"
        )
    }

    private fun DayOfWeek.isoDayNumber(): Int = this.ordinal + 1
}
