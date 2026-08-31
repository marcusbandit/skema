package dk.bandit.skema.shared

import kotlinx.datetime.LocalDateTime

data class VEvent(val uid: String, val summary: String, val start: LocalDateTime?)

fun unfold(raw: String): List<String> =
    raw.replace("\r\n", "\n").split("\n").fold(mutableListOf<String>()) { acc, line ->
        if ((line.startsWith(" ") || line.startsWith("\t")) && acc.isNotEmpty())
            acc[acc.lastIndex] = acc.last() + line.drop(1)
        else acc.add(line)
        acc
    }
