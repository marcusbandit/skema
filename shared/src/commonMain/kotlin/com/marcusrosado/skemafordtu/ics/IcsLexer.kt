package com.marcusrosado.skemafordtu.ics

/**
 * Turns raw iCalendar text into properties, dealing with the two things
 * that quietly corrupt naive parsers of the DTU Learn feed.
 *
 * Line folding: RFC 5545 says a long value continues on the next line when
 * that line starts with a space OR a horizontal tab. The DTU feed uses TAB.
 * Unfold on spaces only, which is the obvious shortcut, and 373 of 712
 * LOCATION values lose their tail mid-string. Nothing throws; the data is
 * just wrong, and so is every count derived from it.
 *
 * Line endings: the spec says CRLF. This feed ships bare LF. Splitting on
 * "\r\n" alone yields one enormous line.
 */

/** One `NAME;PARAM=VALUE:value` line, already unfolded and split. */
data class IcsProperty(
    val name: String,
    val params: Map<String, String>,
    val value: String,
) {
    fun param(key: String): String? = params[key.uppercase()]
}

object IcsLexer {

    /** Join continuation lines back onto their parent. Space and tab both. */
    fun unfold(raw: String): String =
        raw.replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace("\n ", "")
            .replace("\n\t", "")

    /**
     * Undo RFC 5545 text escaping. Note `\,` and `\;` are real: DTU course
     * names contain commas, so "02335 Operating systems\, Fall 2026" is one
     * value, not two.
     */
    fun unescape(value: String): String {
        if ('\\' !in value) return value
        val out = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                when (val next = value[i + 1]) {
                    'n', 'N' -> out.append('\n')
                    '\\', ',', ';' -> out.append(next)
                    else -> out.append(next)
                }
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }

    /**
     * Split one unfolded line. The colon that ends the name-and-params part
     * is the first one NOT inside a quoted parameter value, because a param
     * may legally be quoted and contain a colon.
     */
    fun parseLine(line: String): IcsProperty? {
        if (line.isBlank()) return null

        var colon = -1
        var quoted = false
        for (i in line.indices) {
            val c = line[i]
            if (c == '"') quoted = !quoted
            else if (c == ':' && !quoted) { colon = i; break }
        }
        if (colon < 0) return null

        val head = line.substring(0, colon)
        val value = line.substring(colon + 1)

        val parts = splitUnquoted(head, ';')
        val name = parts.firstOrNull()?.uppercase() ?: return null

        val params = LinkedHashMap<String, String>()
        for (p in parts.drop(1)) {
            val eq = p.indexOf('=')
            if (eq <= 0) continue
            val k = p.substring(0, eq).uppercase()
            val v = p.substring(eq + 1).removeSurrounding("\"")
            params[k] = v
        }
        return IcsProperty(name, params, value)
    }

    private fun splitUnquoted(s: String, delim: Char): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var quoted = false
        for (c in s) {
            when {
                c == '"' -> { quoted = !quoted; cur.append(c) }
                c == delim && !quoted -> { out.add(cur.toString()); cur.clear() }
                else -> cur.append(c)
            }
        }
        out.add(cur.toString())
        return out
    }

    /** Every VEVENT block, as lists of properties. */
    fun vevents(raw: String): List<List<IcsProperty>> {
        val out = mutableListOf<List<IcsProperty>>()
        var current: MutableList<IcsProperty>? = null
        for (line in unfold(raw).split('\n')) {
            val trimmed = line.trimEnd('\r')
            when {
                trimmed == "BEGIN:VEVENT" -> current = mutableListOf()
                trimmed == "END:VEVENT" -> { current?.let(out::add); current = null }
                current != null -> parseLine(trimmed)?.let(current::add)
            }
        }
        return out
    }
}
