package dk.bandit.skema.ics

/**
 * One event as shown, plus the feed entries behind it. A merged card comes
 * from two or three entries, an untouched one from itself.
 *
 * This is a separate structure rather than a field on [CalendarEvent] on
 * purpose. A CalendarEvent models one VEVENT, and SkemaRepository fingerprints
 * and diffs it; a "these are my sources" list would be a self-referential
 * field that changes equality and drags a display concern into the parser
 * model. Callers that need the provenance ask [Merge.merge] for it.
 */
data class Merged(val event: CalendarEvent, val sources: List<CalendarEvent>)

/**
 * The DTU feed lists most classes twice: once as a teacher's hand-made entry
 * and once generated from the room booking. Measured over one real feed of
 * 712 entries, 736 events once recurrences are expanded, 99 slots hold the
 * same course more than once and collapse to 603 events.
 *
 * The two sides disagree in useful ways. The generated entry carries the
 * booked room ("LYN.308.AUD13.R:104") and is titled the literal word
 * "Lecture". The hand-made entry carries the title a person wrote and often a
 * longer course name. Keeping one whole entry throws away everything the
 * loser held, so a duplicated slot is merged field by field instead:
 *
 *   both sides agree            take the value
 *   they differ                 take the hand-made one
 *   hand made is null or blank  take the generated one
 *
 * The second rule is why a title is a person's again; the third is why the
 * rooms survive. On that same feed 82 of the 99 merged events end up with
 * more rooms than the entry that won the merge held on its own.
 *
 * Neither side can be assumed present: 49 of those slots are two hand-made
 * entries with no room booking behind them, 13 are generated only, and 30
 * hold three entries or more.
 */
object Merge {

    /** The parser's stand-in when SUMMARY is empty. It is not a title. */
    private const val NO_TITLE = "(no title)"

    fun collapse(events: List<CalendarEvent>): List<CalendarEvent> =
        merge(events).map { it.event }

    /** [collapse], but keeping what each event was merged from. */
    fun merge(events: List<CalendarEvent>): List<Merged> =
        Dedup.slots(events)
            .flatMap { slot ->
                if (!slot.isDuplicate) {
                    slot.events.map { Merged(it, listOf(it)) }
                } else {
                    val ordered = byPriority(slot.events)
                    listOf(Merged(mergeAll(ordered), ordered))
                }
            }
            .sortedBy { it.event.startEpochSeconds }

    /** Keyed the way SkemaRepository keys its diff, so the two line up. */
    fun sourcesByKey(merged: List<Merged>): Map<String, List<CalendarEvent>> =
        merged.associate { keyOf(it.event) to it.sources }

    fun keyOf(event: CalendarEvent): String = event.uid + "#" + event.occurrence

    /**
     * Hand-made first, then generated, each ordered by UID and occurrence
     * rather than by the order the VEVENTs happened to arrive in. The first
     * entry's UID becomes the merged event's identity and SkemaRepository
     * keys its diff on uid + "#" + occurrence, so ordering by the feed's own
     * layout would report every merged class as removed and re-added the day
     * DTU reshuffles its entries. UID plus occurrence is unique, so this is a
     * total order and two syncs of the same data pick the same winner.
     */
    private fun byPriority(events: List<CalendarEvent>): List<CalendarEvent> =
        events.sortedWith(compareBy({ it.fromRoomBooking }, { it.uid }, { it.occurrence }))

    /** [ordered] runs from most to least trusted; earlier entries win ties. */
    private fun mergeAll(ordered: List<CalendarEvent>): CalendarEvent {
        val primary = ordered.first()

        // Whichever entry supplies the text also supplies the "was it HTML"
        // flag, so the two never describe different strings.
        val described = ordered.firstOrNull { !it.description.isNullOrBlank() }

        // Copying from primary is what keeps the identity, uid and occurrence,
        // and the timing form of the winning entry: see byPriority.
        return primary.copy(
            summary = ordered.firstNotNullOfOrNull(::titleOf) ?: primary.summary,
            // A disagreement over DTEND goes to the human, a missing one
            // does not. The two sides agree on the end in every duplicated
            // slot of the feed measured, so this rule is for the day they
            // stop agreeing rather than for something seen today.
            end = ordered.firstNotNullOfOrNull { it.end },
            location = mergeLocations(ordered.map { it.location }),
            description = described?.description,
            descriptionWasHtml = described?.descriptionWasHtml ?: false,
            // The booking number comes from whichever side was generated;
            // a hand-made entry never has one, so first non-null is right.
            reservation = ordered.firstNotNullOfOrNull { it.reservation },
            // The flag means "no person wrote this". After a merge with a
            // hand-made side, one did, and the title on the card is theirs.
            // ui.textFor reads the flag to decide whether the course name or
            // the event's own title leads, so this has to be false or the
            // human title we just merged in would be demoted to a subtitle.
            fromRoomBooking = ordered.all { it.fromRoomBooking },
            // SEQUENCE is a revision counter and LAST-MODIFIED a timestamp;
            // the merged event holds data from every side, so it is as new as
            // the newest of them. Reporting the lower number would make a
            // fresh merge look like a stale copy of one half.
            sequence = ordered.maxOf { it.sequence },
            lastModified = newestStamp(ordered.mapNotNull { it.lastModified }),
            rrule = ordered.firstNotNullOfOrNull { it.rrule },
        )
    }

    private fun titleOf(event: CalendarEvent): String? =
        event.summary.trim().takeIf { it.isNotBlank() && it != NO_TITLE }

    /**
     * ICS stamps are fixed-width UTC ("20260827T212400Z"), so lexicographic
     * order is chronological order and no date parsing is needed here.
     */
    private fun newestStamp(stamps: List<String>): String? = stamps.maxOrNull()

    /**
     * Merged part by part rather than by picking one whole ParsedLocation,
     * because the room lives on one side and the name on the other, and a
     * winner-takes-all choice always loses one of them.
     */
    private fun mergeLocations(parts: List<ParsedLocation>): ParsedLocation {
        val rooms = unionRooms(parts.flatMap { it.rooms })

        // Prefer the longer name when the two differ: the sides disagree by
        // one carrying the term suffix ("02335 Operating systems, Fall 2026"
        // against "02335 Operating systems"), and the longer string is the
        // one holding the extra fact. Equal lengths fall to the hand-made
        // side, which is first in the list.
        val courseName = parts.mapNotNull { it.courseName?.trim()?.ifBlank { null } }
            .reduceOrNull { best, next -> if (next.length > best.length) next else best }

        // Equal by construction: Slot.isDuplicate only fires when every event
        // in the slot names the same course.
        val courseCode = parts.firstNotNullOfOrNull { it.courseCode }

        // A raw string in the feed's own notation, so anything re-reading it
        // sees what a single entry carrying all of this would have looked
        // like. Generated entries join rooms with "|", so this does too.
        val raw = when {
            rooms.isEmpty() -> courseName.orEmpty()
            courseName == null -> rooms.joinToString(" | ")
            else -> rooms.joinToString(" | ") + " (" + courseName + ")"
        }

        val shape = when {
            rooms.isEmpty() && courseName == null -> LocationShape.EMPTY
            rooms.isEmpty() -> LocationShape.COURSE_ONLY
            // Rooms are unioned, so a side that had real building codes still
            // has them here and the merged value is genuinely room-shaped.
            parts.any { it.shape == LocationShape.ROOMS_AND_COURSE } -> LocationShape.ROOMS_AND_COURSE
            else -> LocationShape.TEXT_AND_COURSE
        }

        return ParsedLocation(rooms, courseName, courseCode, shape, raw)
    }

    /**
     * Union in order, hand-made rooms first, dropping exact repeats on the
     * trimmed value ignoring case. That is as far as this goes: anything
     * cleverer would have to decide whether "421.072.AUD (128)" and
     * "LYN.421.AUD72.R:035" name the same auditorium, and nothing in the feed
     * says. Listing both is the honest answer, and no room is ever dropped.
     */
    private fun unionRooms(rooms: List<String>): List<String> {
        val seen = mutableSetOf<String>()
        val out = mutableListOf<String>()
        for (room in rooms) {
            val trimmed = room.trim()
            if (trimmed.isEmpty()) continue
            if (seen.add(trimmed.lowercase())) out.add(trimmed)
        }
        return out
    }
}
