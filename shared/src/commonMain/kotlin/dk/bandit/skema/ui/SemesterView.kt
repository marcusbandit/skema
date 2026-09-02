package dk.bandit.skema.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dk.bandit.skema.ics.CalendarEvent
import dk.bandit.skema.ics.Term
import dk.bandit.skema.ics.Terms
import kotlin.math.ceil

/**
 * The degree, one semester at a time.
 *
 * The calendar answers "what is on now"; this answers "what is this semester",
 * which is a different question and was not askable before. Two levels live on
 * the one screen: the list of every semester the feed touches, and, for one of
 * them, the shape of its ordinary teaching week.
 *
 * Everything is read off the same model and the same palette as the calendar,
 * so a course is the colour here that it is there, and nothing is stored.
 */
@Composable
fun SemesterView(model: SkemaModel, palette: CoursePalette, modifier: Modifier = Modifier) {
    val c = skema
    val reduced = reducedMotion()

    // model.terms is academic order, oldest first, which is the right order to
    // reason about a degree in and the wrong one to open a list on: the
    // semester a student wants is nearly always the one they are in.
    val terms = remember(model.terms) { model.terms.asReversed() }
    var open by remember { mutableStateOf<Term?>(null) }

    // A semester can stop existing under the screen: the enrolment date moves,
    // a sync drops the last entry of a term. Falling back to the list beats
    // drawing a week built out of nothing.
    LaunchedEffect(terms) { if (open != null && open !in terms) open = null }

    Column(modifier.fillMaxSize().background(c.background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.medium, vertical = Space.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (open != null) {
                GlyphButton(Ico.Back, "Back to the semester list", { open = null })
                Spacer(Modifier.width(Space.small))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    open?.label ?: "Semesters",
                    color = c.accent,
                    fontSize = Type.large,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    remember(model.visible, open, terms.size) { subhead(model, open, terms.size) },
                    color = c.inkMuted,
                    fontSize = Type.small,
                    maxLines = 1,
                )
            }
            PillButton("Done", { model.screen = Screen.Calendar }, filled = true, ico = Ico.Check)
        }

        AnimatedContent(
            targetState = open,
            modifier = Modifier.fillMaxSize(),
            // Opening a semester is a step inwards, closing one a step back
            // out, and the motion says which: the same grow-in, shrink-out
            // pair the calendar uses when it changes view.
            transitionSpec = {
                val transform = if (reduced) {
                    fadeIn(tween(Motion.FADE)) togetherWith fadeOut(tween(Motion.FADE))
                } else {
                    val deeper = targetState != null
                    val from = if (deeper) 1f - Motion.VIEW_SCALE else 1f + Motion.VIEW_SCALE
                    val to = if (deeper) 1f + Motion.VIEW_SCALE else 1f - Motion.VIEW_SCALE
                    val enter = tween<Float>(Motion.VIEW_IN, easing = Motion.arrive)
                    val exit = tween<Float>(Motion.VIEW_OUT, easing = Motion.leave)
                    (fadeIn(enter) + scaleIn(enter, initialScale = from)) togetherWith
                        (fadeOut(exit) + scaleOut(exit, targetScale = to))
                }
                transform using SizeTransform(clip = false)
            },
            label = "semester",
        ) { term ->
            if (term == null) {
                TermList(model, palette, terms, onOpen = { open = it })
            } else {
                TermWeek(model, palette, term)
            }
        }
    }
}

private fun subhead(model: SkemaModel, open: Term?, count: Int): String {
    if (open != null) {
        val events = model.eventsIn(open)
        val subjects = events.mapNotNull { it.courseCode }.distinct().size
        return countLabel(events.size, "entry", "entries") + SEPARATOR +
            countLabel(subjects, "subject", "subjects")
    }
    if (count == 0) return if (model.hasUrl) "Nothing in the feed yet" else "No calendar link yet"
    return countLabel(count, "semester", "semesters") + SEPARATOR + "newest first"
}

/** A count with the right noun on it. "1 entries" reads as a bug, because it is one. */
private fun countLabel(n: Int, one: String, many: String): String = "$n " + if (n == 1) one else many

@Composable
private fun TermList(
    model: SkemaModel,
    palette: CoursePalette,
    terms: List<Term>,
    onOpen: (Term) -> Unit,
) {
    if (terms.isEmpty()) {
        Empty(
            "No semesters to show",
            "A semester appears here as soon as the feed has an entry in it.",
        )
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Space.medium),
        verticalArrangement = Arrangement.spacedBy(Space.small),
    ) {
        items(terms.size, key = { terms[it].label }) { i ->
            TermCard(model, palette, terms[i], onOpen)
        }
    }
}

@Composable
private fun TermCard(
    model: SkemaModel,
    palette: CoursePalette,
    term: Term,
    onOpen: (Term) -> Unit,
) {
    val c = skema
    val events = remember(model.visible, term) { model.eventsIn(term) }
    val subjects = remember(events) { subjectsIn(events) }

    Card(
        Modifier
            .fillMaxWidth()
            // Clipped before the click so the press stays inside the squircle
            // rather than flashing the card's bounding box.
            .clip(SquircleShape(Radius.card))
            .clickable { onOpen(term) },
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    term.label,
                    color = c.ink,
                    fontSize = Type.normal,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                Text(
                    countLabel(events.size, "entry", "entries"),
                    color = c.inkMuted,
                    fontSize = Type.small,
                    maxLines = 1,
                )
                Spacer(Modifier.width(Space.tight))
                Glyph(Ico.Forward, tint = c.inkMuted, size = IconSizeQuiet)
            }
            if (subjects.isNotEmpty()) {
                Spacer(Modifier.height(Space.small))
                SubjectChips(subjects, palette)
            }
        }
    }
}

/**
 * The semester's courses, as chips in their own colours.
 *
 * They wrap rather than scroll, because the number of subjects in a term is
 * the thing being read: a row that runs off the edge hides exactly the
 * information the card exists to give.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubjectChips(subjects: List<TermSubject>, palette: CoursePalette, modifier: Modifier = Modifier) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(Space.tight),
        verticalArrangement = Arrangement.spacedBy(Space.tight),
    ) {
        subjects.forEach { subject ->
            val tint = palette.tint(subject.code)
            Tag(
                label = subject.code + SEPARATOR + subject.name,
                background = tint.fill,
                foreground = tint.ink,
            )
        }
    }
}

/** One semester: its subjects, then the week it usually runs. */
@Composable
private fun TermWeek(model: SkemaModel, palette: CoursePalette, term: Term) {
    val events = remember(model.visible, term) { model.eventsIn(term) }
    val subjects = remember(events) { subjectsIn(events) }

    // The week is built from the feed's own entries rather than from the
    // merged cards the calendar draws, and the merge is why.
    //
    // A slot is keyed on the start time alone, and a slot holding one course
    // is treated as that course listed twice, so a hand-made entry that opens
    // on the hour a class starts is merged INTO the class and the merged card
    // takes the hand-made end: a 13:00 lecture and a "Week 2 - Available"
    // window that opens at 13:00 and closes at 23:59 come out as one eleven
    // hour block. Merged, that block is indistinguishable from a class, and
    // dropping it would take the lecture with it. Unmerged, they are two
    // entries again, one with a room and a four hour booking and one without,
    // and [eligible] can tell them apart. Nothing is shown twice, because the
    // grouping below folds a course's entries at one weekday and time into one
    // slot whatever they came from.
    //
    // The enrolment filter is applied the way the model applies it, so the
    // week never contains a semester's entries from before the student.
    val from = model.enrolledFrom
    val entries = remember(model.events, term, from) {
        model.events.filter { Terms.of(it) == term && (from == null || it.start.date >= from) }
    }
    val slots = remember(entries) { typicalWeek(entries) }

    Column(Modifier.fillMaxSize()) {
        if (subjects.isNotEmpty()) {
            SubjectChips(
                subjects,
                palette,
                Modifier.padding(horizontal = Space.medium, vertical = Space.small),
            )
        }
        if (slots.isEmpty()) {
            Empty(
                "No ordinary week here",
                "Nothing in " + term.label + " repeats often enough to call it typical. " +
                    "The calendar still has every entry.",
            )
        } else {
            TypicalGrid(slots, palette, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Empty(title: String, detail: String) {
    val c = skema
    Column(
        Modifier.fillMaxSize().padding(Space.large),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, color = c.ink, fontSize = Type.normal, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(Space.tight))
        Text(detail, color = c.inkMuted, fontSize = Type.small)
    }
}

// ---------------------------------------------------------------------------
// The typical week
// ---------------------------------------------------------------------------

/** Monday to Friday. The weekend is added only when a semester puts something there. */
private const val TEACHING_DAYS = 5

private const val HOURS_IN_DAY = 24

/** Air either side of a block, as a share of its column. The week view's rule. */
private const val BLOCK_AIR = 0.02f

/** Two blocks side by side need a seam, or they read as one wide block. */
private val LANE_SEAM = 1.dp

/** How much of a column a stack spends showing what is underneath the top block. */
private const val STACK_REVEAL = 0.26f

/** The least a covered block shows of itself: its rail and enough fill to read as a colour. */
private val STACK_FLOOR = 8.dp

/** A stacked block cuts the one under it with the page colour rather than with a shadow. */
private val STACK_SEAM = 2.dp

/** A line of [Type.small] as a multiple of the type size, for the stack's header push. */
private const val TEXT_LINE = 1.35f

/**
 * Rough advance of one character in the system sans, as a share of the type
 * size. The same estimate the event block uses to decide what it can say.
 */
private const val GLYPH_SHARE = 0.55f

/**
 * The week this semester usually runs.
 *
 * The grid is the calendar's own: the same clock down the left, the same hour
 * rules, the same blocks, the same lane and stack arithmetic when two courses
 * land on each other. What is different is that a column is a weekday rather
 * than a date, so nothing here is dimmed for being in the past and no day is
 * marked as today: every Monday of the semester is the one Monday column.
 */
@Composable
private fun TypicalGrid(slots: List<Slot>, palette: CoursePalette, modifier: Modifier = Modifier) {
    val c = skema
    val density = LocalDensity.current

    val dayCount = maxOf(TEACHING_DAYS, slots.maxOf { it.dayIndex } + 1)
    val byDay = remember(slots) { slots.groupBy { it.dayIndex } }
    // Any Monday will do: the dates are never shown, they are only how the
    // weekday names are got without keeping a second table of them.
    val monday = remember { weekStart(today()) }

    // The teaching day this semester has, rather than the whole twenty four
    // hours. The week view plots midnight to midnight because its pages have
    // to agree with each other as they slide past; there is one grid here and
    // nothing for it to agree with, and this screen exists to show the shape
    // of a week at a glance, so it crops to the hours that hold something.
    val window = remember(slots) {
        DayWindow(
            startHour = slots.minOf { it.startMinute } / 60,
            endHour = ceil(slots.maxOf { it.endMinute } / 60f).toInt().coerceAtMost(HOURS_IN_DAY),
        )
    }

    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()
    var bandPx by remember { mutableIntStateOf(0) }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val fit = ((maxWidth - Grid.GUTTER) / Grid.MIN_COLUMN).toInt().coerceIn(1, dayCount)
        val columnWidth = (maxWidth - Grid.GUTTER) / fit
        val slide = if (fit < dayCount) Modifier.horizontalScroll(hScroll) else Modifier

        // An hour is as tall as the height left over divided by the hours
        // there are, so an ordinary teaching day lands on screen whole.
        // Clamped to the calendar's own zoom range, so a semester with a
        // fourteen hour day scrolls instead of collapsing into stripes.
        val band = with(density) { bandPx.toDp() }
        val hourHeight = Grid.clampHour(((maxHeight - band).coerceAtLeast(0.dp) / window.hours).value)

        // The long weekday only earns its place when the column can hold it
        // with room to spare, so the threshold comes from the longest name on
        // screen rather than from a width measured once on one device.
        val glyph = with(density) { Type.small.toDp() } * GLYPH_SHARE
        val names = remember(dayCount, monday) {
            List(dayCount) { weekdayName(monday.shift(it).dayOfWeek.name) }
        }
        val full = columnWidth >= glyph * names.maxOf { it.length } + Space.medium

        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { if (it.height != bandPx) bandPx = it.height },
            ) {
                Spacer(Modifier.width(Grid.GUTTER))
                Row(slide) {
                    names.forEachIndexed { i, name ->
                        WeekdayHeading(
                            label = if (full) name else weekdayShort(monday.shift(i).dayOfWeek),
                            width = columnWidth,
                        )
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
            Box(Modifier.weight(1f).clipToBounds().verticalScroll(vScroll)) {
                Row {
                    HourGutter(window, hourHeight)
                    Box(slide) {
                        Box(
                            Modifier
                                .width(columnWidth * dayCount)
                                .height(window.height(hourHeight)),
                        ) {
                            HourRules(window, hourHeight)
                            for (i in 1 until dayCount) {
                                Box(
                                    Modifier
                                        .offset(x = columnWidth * i)
                                        .width(1.dp)
                                        .fillMaxHeight()
                                        .background(c.hairline),
                                )
                            }
                            Row {
                                repeat(dayCount) { i ->
                                    TypicalColumn(
                                        slots = byDay[i].orEmpty(),
                                        columnWidth = columnWidth,
                                        window = window,
                                        hourHeight = hourHeight,
                                        palette = palette,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The heading over a column: [DayHeading]'s top line with the date left off.
 *
 * A typical week has no dates. Printing one Monday's number over a column that
 * stands for every Monday of the semester would name a week that is not the
 * one being drawn.
 */
@Composable
private fun WeekdayHeading(label: String, width: Dp) {
    Box(
        Modifier.width(width).padding(vertical = Space.tight),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = skema.inkMuted, fontSize = Type.small, maxLines = 1)
    }
}

/**
 * The most blocks one clash is drawn as before the rest become a count.
 *
 * The week view needs no cap: a real week rarely stacks past two, and the
 * cascade was built for exactly that. An aggregated week stacks by
 * construction, because every clash the semester ever had lands on the one
 * column, and past three the covered blocks are down to a letter each. A
 * letter is the failure the cascade exists to avoid, reached from the other
 * end, so the third block is the last one drawn and the rest become a tag
 * saying how many there are. The tag opens the stack for the reader who wants
 * it, which keeps the cap a default rather than a wall.
 */
private const val MAX_STACK = 3

/** A clash with more in it than [MAX_STACK] lets it draw. */
private data class Overflow(
    val startMinute: Int,
    val endMinute: Int,
    val hidden: Int,
    val open: Boolean,
)

/** What a column draws, once the deep stacks have been capped. */
private data class Capped(val slots: List<Slot>, val marks: List<Overflow>)

/**
 * The clashes in a column, as lists.
 *
 * Clustering is [Lanes]' own: cascade emits a cluster's members in order with
 * the first at depth zero, so a new cluster starts wherever the depth resets.
 * Working it out again here would be a second definition of "these overlap",
 * and the two would disagree the day one of them changed.
 */
private fun clustersOf(slots: List<Slot>): List<List<Slot>> {
    val out = mutableListOf<MutableList<Slot>>()
    Lanes.cascade(slots, { it.startMinute }, { it.endMinute }).forEach { stacked ->
        if (stacked.depth == 0) out.add(mutableListOf())
        out.last().add(stacked.item)
    }
    return out
}

/**
 * Which of a clash's classes are drawn.
 *
 * The three kept are the three that recur across most of their own course, so
 * what survives a cap is what is most nearly always there, which is the
 * question a typical week is asking. Ties go to the earlier start, so the
 * choice does not move between syncs.
 */
private fun capStacks(clusters: List<List<Slot>>, opened: Int?): Capped {
    val kept = mutableListOf<Slot>()
    val marks = mutableListOf<Overflow>()
    clusters.forEach { cluster ->
        val start = cluster.minOf { it.startMinute }
        if (cluster.size <= MAX_STACK) {
            kept += cluster
            return@forEach
        }
        val open = start == opened
        kept += if (open) {
            cluster
        } else {
            cluster
                .sortedWith(compareByDescending<Slot> { it.share }.thenBy { it.startMinute })
                .take(MAX_STACK)
        }
        marks += Overflow(start, cluster.maxOf { it.endMinute }, cluster.size - MAX_STACK, open)
    }
    return Capped(kept, marks)
}

/**
 * One weekday's classes, arranged the way the week view arranges a day: honest
 * lanes while a lane still holds a word, and a stack once it does not. The
 * decision is [sideBySideFits] on the width there actually is, so it follows
 * the screen rather than branching on a device.
 */
@Composable
private fun TypicalColumn(
    slots: List<Slot>,
    columnWidth: Dp,
    window: DayWindow,
    hourHeight: Float,
    palette: CoursePalette,
) {
    val c = skema
    val density = LocalDensity.current

    val clusters = remember(slots) { clustersOf(slots) }
    // Which clash is open, by its start minute. One at a time, because opening
    // a stack is a look at one thing rather than a mode the column is left in.
    var opened by remember(slots) { mutableStateOf<Int?>(null) }
    val capped = remember(clusters, opened) { capStacks(clusters, opened) }
    val drawn = capped.slots

    val placed = remember(drawn) { Lanes.layout(drawn, { it.startMinute }, { it.endMinute }) }
    val stacked = remember(drawn) { Lanes.cascade(drawn, { it.startMinute }, { it.endMinute }) }

    val air = (columnWidth * BLOCK_AIR).coerceIn(1.dp, Space.tight)
    val inner = columnWidth - air * 2
    val widest = placed.maxOfOrNull { it.lanes } ?: 1
    val cascading = !sideBySideFits(inner, widest)
    val header = with(density) { Type.small.toDp() } * (TEXT_LINE * 2) + Space.tight

    Box(Modifier.width(columnWidth).height(window.height(hourHeight))) {
        if (cascading) {
            stacked.forEach { s ->
                val slot = s.item
                val span = window.spanOf(s.startMinute, s.endMinute, hourHeight)
                val steps = (s.cluster - 1).coerceAtLeast(1)
                val slice = (inner * STACK_REVEAL / steps).coerceAtLeast(STACK_FLOOR)
                val left = air + slice * s.depth
                val width = (columnWidth - air - left).coerceAtLeast(STACK_FLOOR)
                val push = (header * s.headerSteps).coerceAtMost((span - header).coerceAtLeast(0.dp))
                val top = window.offsetOf(s.startMinute, hourHeight) + push
                val height = span - push

                if (s.depth > 0) {
                    Box(
                        Modifier
                            .offset(x = left - STACK_SEAM, y = top - STACK_SEAM)
                            .width(width + STACK_SEAM)
                            .height(height + STACK_SEAM)
                            .clip(SquircleShape(Radius.chip))
                            .background(c.background),
                    )
                }
                TypicalBlock(slot, palette, height, width, Modifier.offset(x = left, y = top))
            }
        } else {
            placed.forEach { p ->
                val laneWidth = (inner * p.widthFraction - LANE_SEAM).coerceAtLeast(1.dp)
                TypicalBlock(
                    slot = p.item,
                    palette = palette,
                    height = window.spanOf(p.startMinute, p.endMinute, hourHeight),
                    width = laneWidth,
                    modifier = Modifier.offset(
                        x = air + inner * p.offsetFraction,
                        y = window.offsetOf(p.startMinute, hourHeight),
                    ),
                )
            }
        }

        // Last, so a count is never buried under the blocks it is counting.
        // The box is only a frame to hang the tag off the bottom of the clash,
        // where a block has run out of things to say; it takes no clicks of
        // its own, so the blocks under it stay reachable.
        capped.marks.forEach { mark ->
            Box(
                Modifier
                    .offset(x = air, y = window.offsetOf(mark.startMinute, hourHeight))
                    .width(inner)
                    .height(window.spanOf(mark.startMinute, mark.endMinute, hourHeight)),
                contentAlignment = Alignment.BottomStart,
            ) {
                Tag(
                    label = if (mark.open) "fewer" else "+" + mark.hidden,
                    modifier = Modifier
                        .clip(SquircleShape(Radius.chip))
                        .clickable { opened = if (mark.open) null else mark.startMinute },
                    background = c.surfaceSunken,
                    foreground = c.inkMuted,
                )
            }
        }
    }
}

/**
 * One class in the typical week.
 *
 * [EventBlock] draws it, off a real entry from the run, so the course name,
 * the room and the clock are the ones the feed gave rather than anything
 * invented here. The height comes from the slot's usual end rather than that
 * entry's, so a week where one lecture overran does not stretch the shape of
 * every week. Nothing is marked as a clash: a conflict is two entries in one
 * moment, and a weekday column has no moment in it to collide in.
 *
 * A class that does not run in most of its course's weeks is drawn in the
 * quiet wash instead of the full fill. Nothing on this screen is in the past,
 * so the dimmed state is free to mean the other thing worth knowing about a
 * typical week: that this one is on it, but not every time.
 */
@Composable
private fun TypicalBlock(
    slot: Slot,
    palette: CoursePalette,
    height: Dp,
    width: Dp,
    modifier: Modifier = Modifier,
) {
    EventBlock(
        event = slot.event,
        tint = palette.tint(slot.event.courseCode),
        height = height,
        width = width,
        conflict = false,
        dimmed = !slot.everyWeek,
        modifier = modifier.width(width).height(height).clip(SquircleShape(Radius.chip)),
    )
}

// ---------------------------------------------------------------------------
// What the semester is made of
// ---------------------------------------------------------------------------

/** A course inside one semester: the code the chip carries and the name the feed gave it. */
private data class TermSubject(val code: String, val name: String)

private fun subjectsIn(events: List<CalendarEvent>): List<TermSubject> =
    events
        .filter { !it.courseCode.isNullOrBlank() }
        .groupBy { it.courseCode.orEmpty() }
        .map { (code, run) ->
            // The earliest entry, so the same course always answers with the
            // same name however the feed happened to arrive.
            val first = run.minBy { it.startEpochSeconds }
            TermSubject(code, courseName(first))
        }
        .sortedBy { it.code }

/**
 * The course's name, tidied the way a card's title is tidied.
 *
 * The leading code and the ", Fall 2026" tail come off: the chip already
 * carries the code, and the card it sits in is already headed Fall 2026, so
 * printing the term on all six chips inside it says the same thing seven
 * times. fromRoomBooking is forced true because the question being asked is
 * what the COURSE is called, not what this particular entry called itself.
 */
private fun courseName(event: CalendarEvent): String =
    textFor(event.summary, event.location.courseName, event.courseCode, fromRoomBooking = true).title

/** "Monday" off the enum, so no second table of weekday names has to be kept in step. */
private fun weekdayName(raw: String): String = raw.lowercase().replaceFirstChar { it.uppercase() }

/** One recurring class: a weekday, a time, a course, and how reliably it runs. */
private data class Slot(
    /** Monday is 0, matching [weekStart] and the order the columns are drawn in. */
    val dayIndex: Int,
    val startMinute: Int,
    val endMinute: Int,
    /** A real entry from the run, so the block can print the course, the room and the clock. */
    val event: CalendarEvent,
    /** Weeks this landed in, and weeks the course was taught at all. */
    val weeks: Int,
    val courseWeeks: Int,
) {
    /** True when it lands in more than half of the course's weeks: the ordinary case. */
    val everyWeek: Boolean get() = weeks * 2 > courseWeeks

    /** How much of its course's span it holds, which is how a crowded slot is ranked. */
    val share: Float get() = weeks.toFloat() / courseWeeks.coerceAtLeast(1)
}

private data class SlotKey(val dayIndex: Int, val startMinute: Int, val course: String?)

/** An entry's length in minutes, which is the axis both the grid and the filter work on. */
private fun CalendarEvent.lengthMinutes(): Int = endMinute() - startMinute()

/**
 * What is allowed to become part of a typical week.
 *
 * Three tests, in the order of how much each can be trusted.
 *
 * Whole day entries go first. They carry no time to plot, and the grid is a
 * clock.
 *
 * Then the booking test. DTU generates an entry from every room booking, and
 * those generated entries ARE the timetable; a good share of the hand-made
 * ones beside them are deadlines, availability windows and announcements,
 * which recur weekly and are not classes. What separates them in the feed is
 * LOCATION: a booking names a room before the course, a deadline names only
 * the course, so a room is the proxy for "somebody booked a place to teach in
 * this". It is applied per COURSE and not across the term: a course with no
 * booked entry anywhere in the semester is taught somewhere the feed never
 * names, and dropping it would delete a real subject from the week rather than
 * tidy it up, so a course like that keeps everything and leans on length
 * alone.
 *
 * Then length, which catches what the booking test cannot: a course with no
 * bookings to compare against, and a booking genuinely held all day.
 */
private fun eligible(events: List<CalendarEvent>): List<CalendarEvent> {
    val timed = events.filterNot { it.allDay }
    if (timed.isEmpty()) return emptyList()

    val booked = timed.filter { it.rooms.isNotEmpty() }
    val hasBooking = booked.map { it.courseCode }.toSet()
    val candidates = timed.filter { it.rooms.isNotEmpty() || it.courseCode !in hasBooking }

    // Read off the bookings when there are any, since those are the lengths
    // the university actually teaches in. A term with nothing booked has no
    // such distribution, so it reads the fence off everything it has.
    val fence = durationFence((if (booked.isNotEmpty()) booked else timed).map { it.lengthMinutes() })
    return candidates.filter { it.lengthMinutes() <= fence }
}

/**
 * The longest an entry can run and still be a class.
 *
 * Tukey's fence, the third quartile plus one and a half interquartile ranges,
 * which is the standard cut for a long tail and needs no duration decided in
 * advance. On a normal DTU term the quartiles land on the university's own
 * module lengths, two hours and four, which puts the fence around seven: a
 * four hour lab is inside it, an eleven hour availability window is nowhere
 * near. A term that really does teach in eight hour blocks moves its own
 * quartiles and keeps them, which a fixed cap could not do.
 *
 * Quantiles are nearest rank, so a handful of entries degrades quietly: with
 * every duration equal the fence is that duration and nothing is cut.
 */
private fun durationFence(minutes: List<Int>): Int {
    if (minutes.isEmpty()) return Int.MAX_VALUE
    val sorted = minutes.sorted()
    fun rank(p: Double): Int = sorted[(p * sorted.size).toInt().coerceIn(0, sorted.size - 1)]
    val low = rank(0.25)
    val high = rank(0.75)
    return high + (high - low) * 3 / 2
}

/**
 * How much of a course's own teaching span a slot has to recur across before
 * it counts as part of the ordinary week.
 *
 * Measured against the course rather than against the semester, which is the
 * whole trick. DTU's three week January course sits inside the Fall term, so a
 * Monday class that ran in all three of its weeks is every bit as typical as
 * one that ran in thirteen, and a share of the whole term would throw it away
 * for being short. A third is low enough to keep a fortnightly lecture, which
 * lands on half the weeks, and to survive a public holiday or two; it is high
 * enough that a single guest lecture in a thirteen week course, at one week in
 * thirteen, never reaches it. Nothing here is a count taken off this feed: the
 * span is whatever the data says it is, so a summer school of four weeks and a
 * semester of thirteen both get a threshold that means the same thing.
 */
private const val TYPICAL_SHARE = 1f / 3f

/**
 * And the floor: recurring means at least twice, whatever the share works out
 * to. Without it every one-off in a course taught in a single week would be
 * promoted to typical, since one out of one is all of them.
 */
private const val TYPICAL_MINIMUM = 2

/**
 * The classes that make up an ordinary week of this semester.
 *
 * [eligible] decides what is even a candidate; this decides which candidates
 * recur. Entries are grouped by weekday, start time and course, and a group
 * survives when it landed in enough separate WEEKS. Weeks are counted rather
 * than entries because the feed lists the same class twice, once booked and
 * once written by hand, so counting entries would make a fortnightly class
 * look weekly, and because two entries in one week are one week's worth of
 * evidence about what an ordinary week looks like.
 */
private fun typicalWeek(events: List<CalendarEvent>): List<Slot> {
    val timed = eligible(events)
    if (timed.isEmpty()) return emptyList()

    val taught = timed
        .groupBy { it.courseCode }
        .mapValues { (_, run) -> run.distinctBy { weekStart(it.start.date) }.size }

    return timed
        .groupBy { SlotKey(it.start.date.dayOfWeek.ordinal, it.startMinute(), it.courseCode) }
        .mapNotNull { (key, run) ->
            val weeks = run.distinctBy { weekStart(it.start.date) }.size
            val span = taught[key.course] ?: weeks
            val needed = maxOf(TYPICAL_MINIMUM, ceil(span * TYPICAL_SHARE).toInt())
            if (weeks < needed) return@mapNotNull null

            // A class that usually runs an hour and once ran two takes its
            // usual length. Ties go to the shorter one, so the grid never
            // claims more of an afternoon than the class reliably takes.
            val end = run
                .groupingBy { it.endMinute() }
                .eachCount()
                .entries
                .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
                .first()
                .key

            // The booked entry speaks for the slot when there is one. Its
            // title is the literal word "Lecture", which is what makes the
            // block lead with the course name instead of with whatever this
            // week's exercise sheet was called: on a typical week the course
            // is the thing being read.
            val ofLength = run.filter { it.endMinute() == end }
            Slot(
                dayIndex = key.dayIndex,
                startMinute = key.startMinute,
                endMinute = end,
                event = ofLength.firstOrNull { it.fromRoomBooking } ?: ofLength.first(),
                weeks = weeks,
                courseWeeks = span,
            )
        }
        .sortedBy { it.startMinute }
}
