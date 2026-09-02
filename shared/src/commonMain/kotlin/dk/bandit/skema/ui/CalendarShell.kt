package dk.bandit.skema.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import dk.bandit.skema.data.ViewMode
import dk.bandit.skema.ics.CalendarEvent
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.minutes

/**
 * How the chrome moves.
 *
 * One easing pair and a short list of durations, so every transition in the
 * app is recognisably the same motion instead of each call site inventing its
 * own. Things arriving decelerate into place and take their time; things
 * leaving accelerate away and take less. An entrance is worth watching, an
 * exit is only in the way of what you asked for.
 */
private object Motion {

    /** Emphasised decelerate: most of the distance early, the last bit slow. */
    val arrive = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Accelerate: reluctant to start, then gone. */
    val leave = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /**
     * The sheet travels the height of a phone, so it needs longer than a
     * fade to read as one object moving rather than a jump cut. 320ms in and
     * 200ms out is roughly the ratio a physical drawer has.
     */
    const val SHEET_IN = 320
    const val SHEET_OUT = 200

    /** The sheet's own fade, kept short so it is solid for most of the slide. */
    const val SHEET_FADE = 180

    /** A view swap moves nothing across the screen, so it can be quicker. */
    const val VIEW_IN = 220
    const val VIEW_OUT = 130

    /** Plain cross fade, used when the platform has asked for less movement. */
    const val FADE = 140

    /**
     * How far off its own size a view starts or ends. Six percent is enough
     * to read as a direction and small enough that text never looks resized.
     */
    const val VIEW_SCALE = 0.06f

    /** No overshoot: a calendar sheet is a panel, not a spring toy. */
    val settle = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
}

/**
 * How often the feed is refetched while the calendar is on screen.
 *
 * A timetable changes on the scale of a working day: a room moves, a lecture
 * is cancelled, next week gets published. Five minutes is already far finer
 * than that, and it means a cancellation posted during a break is on screen
 * before the next lecture starts. Going below a minute would be a lot of
 * requests for a file that answers the same thing all morning, and a fetch
 * costs the radio; going above ten and you would start catching changes after
 * they mattered.
 */
private val SYNC_EVERY = 5.minutes

/** The sheet's resting share of the frame when it is not expanded. */
private const val PARTIAL = 0.5f

/**
 * A release still moving faster than this counts as a throw, and the
 * direction of the throw decides where the sheet lands whatever its position.
 * In dp per second so it means the same thing at any density. 400 is well
 * above the drift left at the end of a careful drag and well below a
 * deliberate flick.
 */
private val FLICK = 400.dp

/**
 * The chrome around whichever calendar is showing, and the one place that
 * decides how the screen is divided.
 *
 * There is exactly one branch on size in the whole app and it is here. A wide
 * frame gets two panes, a narrow one gets a single pane with a sheet. Both
 * read the same model, so unfolding the phone moves the detail from a sheet
 * into a rail and changes nothing else: same day, same zoom, same selection,
 * no reload. That is what makes the fold seamless rather than a restart.
 */
@Composable
fun CalendarShell(model: SkemaModel) {
    val dark = skema.isDark

    // Syncing is not a button any more. An app that knows its data has a
    // shelf life should not make you press refresh to find out; it fetches
    // when it opens and then on a timer, and the status line under the
    // headline is where you see that it happened.
    LaunchedEffect(model.hasUrl) {
        while (model.hasUrl) {
            // busy means either the settings screen's own "Sync now" or the
            // open-the-app refresh is already in flight. Two fetches of the
            // same file at once help nobody.
            if (!model.busy) model.refresh()
            delay(SYNC_EVERY)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val frame = Frame(maxWidth, maxHeight)

        // Rebuilt only when the course set, the pinned hues or the theme
        // change. The hue assignment re-spreads every course, so doing this
        // per frame would be visible work for no reason.
        val palette = remember(model.courseCodes, model.settings.courseHues, dark) {
            CoursePalette(model.courseCodes, model.settings.courseHues, dark)
        }

        CompositionLocalProvider(LocalFrame provides frame) {
            if (frame.dual) DualPane(model, palette, frame) else SinglePane(model, palette)
        }
    }
}

@Composable
private fun DualPane(model: SkemaModel, palette: CoursePalette, frame: Frame) {
    val c = skema
    // A third of the width, floored so the rail can hold a room list and
    // capped so the week never loses the space it actually needs.
    val rail = (frame.width * 0.32f).coerceIn(260.dp, 380.dp)

    // The top bar lives in the LEFT PANE, not the window, so what fits in it
    // is the pane's width minus the rail. Unfolded that is about 571dp, and a
    // four way switcher plus two buttons leaves the month name roughly 60dp,
    // which is how "Aug to Sep" became "Aug to". Below this the switcher goes
    // back to the bottom where it has a row of its own.
    val pane = frame.width - rail - 1.dp
    val inline = pane >= 640.dp

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            TopBar(model, inlineSwitcher = inline)
            Calendar(model, palette, Modifier.weight(1f))
            if (!inline) ViewSwitcher(model, Modifier.fillMaxWidth().padding(Space.small))
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(c.hairline))
        Box(Modifier.width(rail).fillMaxHeight().background(c.surface)) {
            val picked = model.picked
            if (picked != null) {
                EventDetail(
                    event = picked,
                    tint = palette.tint(picked.courseCode),
                    conflict = picked.startEpochSeconds in model.conflicts,
                    onClose = { model.picked = null },
                )
            } else {
                DaySummary(model, palette)
            }
        }
    }
}

@Composable
private fun SinglePane(model: SkemaModel, palette: CoursePalette) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(model, inlineSwitcher = false)
            Calendar(model, palette, Modifier.weight(1f))
            // The switcher sits at the bottom on a phone because that is
            // where a thumb is, and folded this app is a tall phone.
            ViewSwitcher(model, Modifier.fillMaxWidth().padding(Space.small))
        }
        // Always composed, never conditional: a sheet that is removed from
        // the tree the instant it is dismissed has nothing left to animate.
        DetailSheet(model, palette)
    }
}

/**
 * Switching view animates along the zoom axis it represents: going to a
 * narrower view grows, going wider shrinks. The motion tells you which way
 * you moved, which matters when the gesture that got you here was a pinch
 * that could have gone either way.
 */
@Composable
private fun Calendar(model: SkemaModel, palette: CoursePalette, modifier: Modifier = Modifier) {
    // Re-measure here rather than reusing the window frame.
    //
    // The shell decides one pane or two from the size of the WINDOW, but a
    // view then has to lay its columns out inside the pane it was actually
    // given, which on a two pane screen is about two thirds of that. Reading
    // the window frame down here sizes five day columns for 841dp and then
    // draws them into 571dp, and Friday goes off the edge.
    BoxWithConstraints(modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalFrame provides Frame(maxWidth, maxHeight)) {
            CalendarBody(model, palette)
        }
    }
}

@Composable
private fun CalendarBody(model: SkemaModel, palette: CoursePalette) {
    val reduced = reducedMotion()
    AnimatedContent(
        targetState = model.view,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            // Views are ordered month to agenda, so the direction of the
            // change already says whether more detail or less was asked for.
            // Going narrower the arriving view grows into place from slightly
            // small; going wider it settles down from slightly large. That
            // repeats the pinch that usually caused it.
            //
            // The outgoing view leaves in well under half the time the new
            // one takes to arrive, because two grids at half opacity over
            // each other is legible for exactly as long as it takes to read
            // as a smear.
            val transform = if (reduced) {
                fadeIn(tween(Motion.FADE)) togetherWith fadeOut(tween(Motion.FADE))
            } else {
                val deeper = targetState.ordinal > initialState.ordinal
                val from = if (deeper) 1f - Motion.VIEW_SCALE else 1f + Motion.VIEW_SCALE
                val to = if (deeper) 1f + Motion.VIEW_SCALE else 1f - Motion.VIEW_SCALE
                val enter = tween<Float>(Motion.VIEW_IN, easing = Motion.arrive)
                val exit = tween<Float>(Motion.VIEW_OUT, easing = Motion.leave)
                (fadeIn(enter) + scaleIn(enter, initialScale = from)) togetherWith
                    (fadeOut(exit) + scaleOut(exit, targetScale = to))
            }
            // Both views fill the pane, so there is no size to animate; the
            // point of saying so is to stop the scale being clipped to the
            // smaller of the two while they overlap.
            transform using SizeTransform(clip = false)
        },
        label = "view",
    ) { view ->
        when (view) {
            ViewMode.Month -> MonthView(model, palette, Modifier.fillMaxSize())
            ViewMode.Week -> WeekView(model, palette, Modifier.fillMaxSize())
            ViewMode.Day -> DayView(model, palette, Modifier.fillMaxSize())
            ViewMode.Agenda -> AgendaView(model, palette, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun TopBar(model: SkemaModel, inlineSwitcher: Boolean) {
    val c = skema

    Column(Modifier.fillMaxWidth().padding(horizontal = Space.medium, vertical = Space.small)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    headline(model),
                    color = c.ink,
                    fontSize = Type.large,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(subhead(model), color = c.inkMuted, fontSize = Type.small, maxLines = 1)
            }

            if (inlineSwitcher) {
                ViewSwitcher(model, Modifier.width(280.dp))
                Spacer(Modifier.width(Space.small))
            }
            // Offered only when pressing it would move something. Asking
            // whether the anchor is today was the wrong question: stepping a
            // day forward and switching to week left the anchor on tomorrow,
            // same week, so the control appeared and did nothing.
            if (!model.showingToday) {
                GlyphButton(Ico.Today, "Go to today", { model.goToday() })
                Spacer(Modifier.width(Space.tight))
            }
            GlyphButton(Ico.Settings, "Settings", { model.screen = Screen.Settings })
        }

        // "Nothing moved, 736 entries" is worth saying once and then not
        // worth a permanent line of a 700dp tall screen. A failure stays put,
        // because that one you have to act on. This is also the only place a
        // background sync becomes visible, which is the point of keeping it.
        if (model.status.isNotBlank()) {
            if (!model.failed && !model.busy) {
                LaunchedEffect(model.status) {
                    delay(6_000)
                    model.clearStatus()
                }
            }
            Spacer(Modifier.height(Space.unit))
            Text(
                model.status,
                color = if (model.failed) c.accentStrong else c.inkMuted,
                fontSize = Type.small,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ViewSwitcher(model: SkemaModel, modifier: Modifier = Modifier) {
    Segmented(
        options = ViewMode.entries,
        selected = model.view,
        label = { it.name },
        onSelect = { model.setView(it) },
        modifier = modifier,
    )
}

/**
 * The detail on a narrow frame.
 *
 * Three resting places: gone, about half the frame, and all of it. A drag
 * moves the sheet continuously and a release snaps to whichever stop it ended
 * up nearest, unless the release was still moving fast, in which case the
 * throw decides outright. That last rule is what makes a hard flick down
 * dismiss from near the top instead of stopping halfway.
 *
 * Deliberately not a ModalBottomSheet: that one brings Material's corner
 * shape, its scrim and its drag handle, and all three are somebody else's app.
 *
 * The geometry is one full height panel pushed down by `offset`, not a panel
 * whose height changes. The content is anchored to the panel's top edge, so at
 * the partial stop the rest of the panel is below the screen and expanding
 * reveals it by moving one thing, which is also the one thing a finger drags.
 */
@Composable
private fun DetailSheet(model: SkemaModel, palette: CoursePalette) {
    val c = skema
    val picked = model.picked

    // Held past the dismissal so there is still something to draw on the way
    // out. model.picked is null the moment the sheet starts leaving, and an
    // exit animation over nothing is a blank rectangle sliding down.
    var shown by remember { mutableStateOf<CalendarEvent?>(null) }
    val event = picked ?: shown
    val open = picked != null

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val framePx = with(density) { maxHeight.toPx() }
        val flickPx = with(density) { FLICK.toPx() }
        val scope = rememberCoroutineScope()
        val reduced = reducedMotion()

        // The panel's own content height, which is what lets the partial stop
        // be the content rather than half a screen with a band of empty
        // surface below it. Measured against the full height panel, so it is
        // the true height whenever the detail is shorter than the frame.
        var contentPx by remember { mutableStateOf(0) }
        val restPx = if (contentPx > 0) min(framePx * PARTIAL, contentPx.toFloat()) else framePx * PARTIAL

        val full = 0f
        val partial = framePx - restPx
        val gone = framePx

        var expanded by remember { mutableStateOf(false) }
        var dragging by remember { mutableStateOf(false) }

        // How far the panel's top edge sits below the top of the frame.
        val offset = remember { Animatable(gone) }

        LaunchedEffect(picked) {
            if (picked != null) {
                shown = picked
                expanded = false
                // The last sheet may have been left mid drag on its way out.
                // Start this one from its resting place, not from that one's.
                offset.snapTo(partial)
            }
        }

        // A change of measured height is the layout catching up with itself
        // rather than anything the user did, so it lands without motion. It
        // happens on the frame after the first layout, while the panel is
        // still below the screen on its way in.
        LaunchedEffect(restPx) { if (!expanded && !dragging) offset.snapTo(framePx - restPx) }

        /** Moves the panel as far as it will go, and reports the part used. */
        fun take(dy: Float): Float {
            val from = offset.value
            val to = (from + dy).coerceIn(full, gone)
            if (to != from) scope.launch { offset.snapTo(to) }
            return to - from
        }

        // Where a release lands. Velocity beats position past the flick
        // threshold, so a throw goes where it was aimed however far the sheet
        // had actually travelled; below it the nearest stop wins.
        val land: suspend (Float) -> Unit = { velocity ->
            val landing = when {
                velocity > flickPx -> gone
                velocity < -flickPx -> full
                else -> listOf(full, partial, gone).minBy { abs(it - offset.value) }
            }
            if (landing == gone) {
                // Hand the rest of the travel to the exit transition rather
                // than animating there and then playing a second animation
                // over the top of it.
                model.picked = null
            } else {
                expanded = landing == full
                offset.animateTo(landing, Motion.settle)
            }
        }
        val landNow by rememberUpdatedState(land)

        val drag = rememberDraggableState { dy -> take(dy) }

        // The detail scrolls inside the panel, so a drag over the text has two
        // possible readings and something has to arbitrate. The rule is the
        // one every sheet uses: upward, the sheet moves first and the text
        // only scrolls once the sheet has nowhere left to go; downward, the
        // text scrolls first and the sheet only moves once the text is back at
        // its top. A fling that reaches either boundary carries over.
        //
        // Without this the only draggable part of the sheet is the handle,
        // because a scroller claims the gesture whether or not it has anywhere
        // to scroll, and this detail usually has nowhere.
        //
        // Keyed on the frame height, since that is the one thing it closes
        // over that a rotation changes.
        val nested = remember(gone) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                    if (available.y < 0f) Offset(0f, take(available.y)) else Offset.Zero

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset = if (available.y > 0f) Offset(0f, take(available.y)) else Offset.Zero

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (available.y >= 0f || offset.value <= full) return Velocity.Zero
                    landNow(available.y)
                    return available
                }

                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    if (available.y <= 0f) return Velocity.Zero
                    landNow(available.y)
                    return available
                }
            }
        }

        val ink = c.ink
        AnimatedVisibility(
            visible = open,
            enter = fadeIn(tween(Motion.SHEET_FADE)),
            exit = fadeOut(tween(Motion.SHEET_FADE)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        // Barely there at the partial stop, still gentle at
                        // full screen. A flat 0.32 read as the calendar being
                        // switched off; this only says which of the two layers
                        // is in front. Read in the draw phase, so a drag
                        // repaints one rectangle instead of recomposing.
                        val openness = ((framePx - offset.value) / framePx).coerceIn(0f, 1f)
                        drawRect(ink, alpha = SCRIM_MAX * openness)
                    }
                    .clickable(onClick = { model.picked = null }),
            )
        }

        AnimatedVisibility(
            visible = open,
            modifier = Modifier.fillMaxSize(),
            // The slide starts exactly at the frame's bottom edge, so the
            // panel enters from where it looks like it came from rather than
            // from its own full height, which for a half open sheet would be
            // twice the distance at twice the speed.
            enter = slideInVertically(tween(Motion.SHEET_IN, easing = Motion.arrive)) {
                if (reduced) 0 else restPx.roundToInt()
            } + fadeIn(tween(Motion.SHEET_FADE)),
            // Leaves from wherever the drag left it, so a dismissal that began
            // as a gesture finishes as one continuous movement.
            exit = slideOutVertically(tween(Motion.SHEET_OUT, easing = Motion.leave)) {
                if (reduced) 0 else (framePx - offset.value).roundToInt()
            } + fadeOut(tween(Motion.SHEET_FADE)),
            label = "sheet",
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, offset.value.roundToInt()) }
                    .clip(SquircleShape(Radius.sheet))
                    .background(c.surface)
                    .nestedScroll(nested)
                    .draggable(
                        state = drag,
                        orientation = Orientation.Vertical,
                        onDragStarted = { dragging = true },
                        onDragStopped = { velocity ->
                            dragging = false
                            land(velocity)
                        },
                    ),
            ) {
                // Everything the panel actually contains, measured as one
                // piece: this height is what the partial stop is capped to.
                Column(Modifier.onSizeChanged { contentPx = it.height }) {
                    // The grab strip. A whole row rather than the bar itself,
                    // because the bar is 4dp tall and a thumb is not.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = Space.medium),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .width(Space.large + Space.medium)
                                .height(Space.unit)
                                .background(c.hairline, pillShape()),
                        )
                    }
                    if (event != null) {
                        EventDetail(
                            event = event,
                            tint = palette.tint(event.courseCode),
                            conflict = event.startEpochSeconds in model.conflicts,
                            onClose = { model.picked = null },
                            expanded = expanded,
                            onToggleExpand = {
                                val next = !expanded
                                expanded = next
                                scope.launch {
                                    offset.animateTo(if (next) full else partial, Motion.settle)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * A hint that something is in front, not a curtain. This is the value at full
 * screen; the partial stop scales it down to about half, which is as close to
 * invisible as a scrim can get and still separate two surfaces.
 */
private const val SCRIM_MAX = 0.18f

/**
 * Whether the platform has been asked to stop animating.
 *
 * Compose already scales every tween and spring by the same factor, so an
 * animation here is instant when the system says so without any help. This
 * exists to drop the large translations as well: a reduced motion setting is
 * a complaint about things flying across the screen, and a fade in place is
 * still welcome. One read, once, at composition.
 */
@Composable
private fun reducedMotion(): Boolean {
    var reduced by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        reduced = currentCoroutineContext()[MotionDurationScale]?.scaleFactor == 0f
    }
    return reduced
}

/** ISO week number: the week owning this week's Thursday, which is the definition. */
fun isoWeek(d: LocalDate): Int {
    val thursday = d.shift(3 - d.dayOfWeek.ordinal)
    return (thursday.dayOfYear - 1) / 7 + 1
}

/**
 * A teaching week straddles a month boundary about once a month, and calling
 * the week of 31 August to 4 September "Aug" is wrong for four of its five
 * days. So the label is built from the days actually on screen and names both
 * months when there are two. The year moves to the subhead: it changes once a
 * year and it was the first thing to be truncated in the narrow pane.
 */
private fun weekLabel(model: SkemaModel): String {
    val from = weekStart(model.anchor)
    val to = from.shift(Grid.dayCount(model.settings.showWeekend) - 1)
    val first = monthShort(from.month)
    val last = monthShort(to.month)
    return if (first == last) first else "$first to $last"
}

private fun headline(model: SkemaModel): String = when (model.view) {
    ViewMode.Month -> monthShort(model.anchor.month)
    ViewMode.Week -> weekLabel(model)
    ViewMode.Day -> relativeDay(model.selected) ?: dateLabel(model.selected)
    ViewMode.Agenda -> "Agenda"
}

private fun subhead(model: SkemaModel): String = when (model.view) {
    ViewMode.Month -> model.anchor.year.toString()
    ViewMode.Week -> "Week " + isoWeek(model.anchor) + ", " + weekStart(model.anchor).year
    ViewMode.Day -> "Week " + isoWeek(model.selected) + ", " + model.selected.year
    ViewMode.Agenda -> "Everything ahead"
}
