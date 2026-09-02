package com.marcusrosado.skemafordtu.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.marcusrosado.skemafordtu.data.ViewMode
import com.marcusrosado.skemafordtu.ics.CalendarEvent
import com.marcusrosado.skemafordtu.ics.Season
import com.marcusrosado.skemafordtu.ics.Terms
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
internal object Motion {

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
internal val FLICK = 400.dp

/**
 * The chrome around whichever calendar is showing, and the one place that
 * decides how the screen is divided.
 *
 * Two branches on size in the whole app and both are here. The first spends
 * width on the sidebar: wide enough and it is a strip that is always there,
 * otherwise the same strip parked off the left edge until it is pulled in.
 * The second spends what is left on panes. Both read the same model, so unfolding
 * the phone moves the detail from a sheet into a rail and the menu from a
 * drawer into a strip, and changes nothing else: same day, same zoom, same
 * selection, no reload. That is what makes the fold seamless rather than a
 * restart.
 */
@Composable
fun CalendarShell(model: SkemaModel, palette: CoursePalette) {
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
        val window = Frame(maxWidth, maxHeight)

        // A rail is width the calendar never gets back, so everything after
        // this measures itself against the frame MINUS the rail. Deciding two
        // panes on the window and then drawing them into less than the window
        // is exactly how a day column ends up off the edge.
        val railed = window.width >= RAIL_FROM
        val frame = if (railed) window.copy(width = window.width - RAIL_WIDTH) else window

        // Whether the month picker is up. It lives here rather than in the top
        // bar because it covers the whole frame, and on a two pane screen the
        // top bar is a third of it.
        var jumping by remember { mutableStateOf(false) }

        // Whether the calendar's settings sheet is up. Same place and same
        // reason as the picker: it covers the frame, not the top bar.
        var tuning by remember { mutableStateOf(false) }

        // The drawer is the narrow presentation of the same sidebar. Growing
        // into a rail closes it, so folding back never reveals a menu left
        // open in a posture that had no menu to open.
        var menuOpen by remember { mutableStateOf(false) }
        LaunchedEffect(railed) { if (railed) menuOpen = false }

        CompositionLocalProvider(LocalFrame provides frame) {
            if (railed) {
                // The strip is always there, so it is simply a column of the
                // layout and there is nothing to open.
                Row(Modifier.fillMaxSize()) {
                    SidebarRail(model, Modifier.width(RAIL_WIDTH).fillMaxHeight())
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        Panes(
                            model, palette, frame,
                            onJump = { jumping = true },
                            onMenu = null,
                            onTune = { tuning = true },
                        )
                    }
                }
            } else {
                // The same strip, off to the left until it is pulled in. It
                // pushes the calendar rather than covering it, so the frame
                // the calendar measured itself against never changes.
                SidebarScaffold(model, open = menuOpen, onOpenChange = { menuOpen = it }) {
                    Panes(
                        model, palette, frame,
                        onJump = { jumping = true },
                        onMenu = { menuOpen = true },
                        onTune = { tuning = true },
                    )
                }
            }
        }

        // No back handler for the picker here on purpose. The panel wires up
        // its own, which registers later and therefore wins, and which goes
        // out through the panel's exit animation. One here would only ever
        // fire while that exit is already running, and all it could do is drop
        // the panel mid slide, which is the exact jank the animation exists to
        // avoid.

        // Composed only while it is wanted. The panel plays its own way out
        // before it calls back, which is why dismissing it is a callback and
        // not this flag going false on the tap.
        if (jumping) {
            val years = jumpYears(model)
            MonthYearSheet(
                // The day view is anchored on the selected day rather than on
                // the anchor, so the picker opens on the month you can see.
                initial = if (model.view == ViewMode.Day) model.selected else model.anchor,
                minYear = years.first,
                maxYear = years.last,
                onPick = { model.select(it) },
                onDismiss = { jumping = false },
            )
        }
        if (tuning) {
            DisplaySheet(model, onDismiss = { tuning = false })
        }
    }
}

/**
 * Which years the jump picker offers.
 *
 * Bounded by what the student actually has: the enrolment date and the feed's
 * own semesters, widened to take in today and wherever the calendar is parked,
 * so the year you are on is always in the list. A Fall term runs into January,
 * so it reaches one year past its own. Nobody is scrolling to 1970.
 */
private fun jumpYears(model: SkemaModel): IntRange {
    val marks = buildList {
        add(today().year)
        add(model.anchor.year)
        add(model.selected.year)
        model.enrolledFrom?.let { add(it.year) }
        model.terms.firstOrNull()?.let { add(it.year) }
        model.terms.lastOrNull()?.let { add(if (it.season == Season.Fall) it.year + 1 else it.year) }
    }
    return marks.min()..marks.max()
}

/**
 * One pane or two, which is a question about the frame and nothing else.
 *
 * onMenu is null when the sidebar is a rail: there is nothing to open, so
 * the top bar draws no control for it.
 */
@Composable
private fun Panes(
    model: SkemaModel,
    palette: CoursePalette,
    frame: Frame,
    onJump: () -> Unit,
    onMenu: (() -> Unit)?,
    onTune: () -> Unit,
) {
    if (frame.dual) {
        DualPane(model, palette, frame, onJump, onMenu, onTune)
    } else {
        SinglePane(model, palette, onJump, onMenu, onTune)
    }
}

@Composable
private fun DualPane(
    model: SkemaModel,
    palette: CoursePalette,
    frame: Frame,
    onJump: () -> Unit,
    onMenu: (() -> Unit)?,
    onTune: () -> Unit,
) {
    val c = skema
    // A third of the width, floored so the rail can hold a room list and
    // capped so the week never loses the space it actually needs.
    val rail = (frame.width * 0.32f).coerceIn(260.dp, 380.dp)

    // The detail has no close button any more, so back is what puts the rail
    // back to the day it was showing. Disabled when nothing is picked, which
    // is what lets the handler under it have the gesture instead.
    BackHandler(enabled = model.picked != null) { model.picked = null }

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            TopBar(model, onJump, onMenu, onTune)
            Calendar(model, palette, Modifier.weight(1f))
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(c.hairline))
        Box(Modifier.width(rail).fillMaxHeight().background(c.surface)) {
            val picked = model.picked
            if (picked != null) {
                EventDetail(
                    event = picked,
                    tint = palette.tint(picked.courseCode),
                    conflict = picked.startEpochSeconds in model.conflicts,
                )
            } else {
                DaySummary(model, palette)
            }
        }
    }
}

@Composable
private fun SinglePane(
    model: SkemaModel,
    palette: CoursePalette,
    onJump: () -> Unit,
    onMenu: (() -> Unit)?,
    onTune: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(model, onJump, onMenu, onTune)
            Calendar(model, palette, Modifier.weight(1f))
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

/**
 * The bar above the calendar. Left to right: the menu control while the
 * sidebar is a drawer, the two line headline, and three controls at the
 * right end. Those three are the ones every Android user already knows from
 * that corner, today, tune and more, drawn with Material's own glyphs for
 * the same reason nobody redraws the back arrow.
 *
 * Today stays in the row and dims when today is already on screen. It used
 * to vanish instead, and a control that comes and goes moves everything
 * beside it under the finger reaching for it.
 *
 * The bar's own side padding is Space.tight rather than Space.medium, so a
 * control's glyph, which sits Space.small inside its touch target, lands
 * about where the headline used to start. The headline itself keeps that
 * old margin by padding when there is no menu control ahead of it.
 */
@Composable
private fun TopBar(
    model: SkemaModel,
    onJump: () -> Unit,
    onMenu: (() -> Unit)?,
    onTune: () -> Unit,
) {
    val c = skema

    // The headline is a date, and a date is somewhere you can go. Agenda is
    // the exception: it is not showing a month, so it has no month to jump to.
    val jumpable = model.view != ViewMode.Agenda

    Column(Modifier.fillMaxWidth().padding(horizontal = Space.tight, vertical = Space.small)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onMenu != null) {
                BarButton(Ico.Menu, "Open the menu", onMenu)
            }
            Column(Modifier.weight(1f).padding(start = Space.small)) {
                Row(
                    // Pulled back by its own padding, so the headline still
                    // starts on the same line as the subhead under it: the
                    // padding is there to give the tap something to land on,
                    // not to indent the title.
                    Modifier
                        .offset(x = -Space.tight)
                        .clip(pillShape())
                        .clickable(enabled = jumpable, onClick = onJump)
                        .padding(horizontal = Space.tight, vertical = Space.unit),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        headline(model),
                        // Weighted so the chevron is measured first. Without
                        // this a long label takes the whole row and the control
                        // that makes the title tappable is squeezed to nothing.
                        modifier = Modifier.weight(1f, fill = false),
                        color = c.ink,
                        fontSize = Type.large,
                        fontWeight = FontWeight.SemiBold,
                        // Nothing here is ever abbreviated, so a line that
                        // will not fit takes a second one instead. The longest
                        // this line gets is the countdown before a block
                        // opens, and that is three weeks at most.
                        maxLines = 2,
                    )
                    if (jumpable) {
                        Spacer(Modifier.width(Space.tight))
                        // What makes a title read as a control rather than a
                        // label. It carries the description because the words
                        // beside it say where you are, not what pressing does.
                        Glyph(
                            Ico.Expand,
                            tint = c.inkMuted,
                            size = IconSizeQuiet,
                            description = "Choose a month and year",
                        )
                    }
                }
                Text(subhead(model), color = c.inkMuted, fontSize = Type.small, maxLines = 1)
            }

            Spacer(Modifier.width(Space.tight))
            // Live only when pressing it would move something. Asking whether
            // the anchor is today was the wrong question: stepping a day
            // forward and switching to week left the anchor on tomorrow, same
            // week, so the control was live and did nothing.
            BarButton(Ico.Today, "Go to today", { model.goToday() }, enabled = !model.showingToday)
            // Tune is the calendar's settings, a sheet over the calendar, not
            // the app's, which are a screen behind the sidebar.
            BarButton(Ico.Tune, "Calendar settings", onTune)
            Overflow(model)
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
                modifier = Modifier.padding(start = Space.small),
            )
        }
    }
}

/**
 * The three dots and what drops out of them: the two things worth reaching
 * from the calendar that are neither a view nor a setting. Sync is here
 * because the automatic one is silent and there are moments you want to
 * know it ran; Semesters because it is the other screen. Anything the
 * sidebar already offers as a view stays out, or the menu becomes a second
 * sidebar.
 *
 * A plain Popup rather than Material's DropdownMenu, which arrives with its
 * own corner shape, elevation and colours, all somebody else's app. Focusable
 * so a tap anywhere else, or back, is the way out.
 */
@Composable
private fun Overflow(model: SkemaModel) {
    val c = skema
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    val shape = SquircleShape(Radius.card)

    Box {
        BarButton(Ico.More, "More", { open = true })
        if (open) {
            Popup(
                alignment = Alignment.TopEnd,
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    Modifier
                        .width(IntrinsicSize.Max)
                        .background(c.surface, shape)
                        .border(1.dp, c.hairline, shape)
                        .clip(shape)
                        .padding(vertical = Space.small),
                ) {
                    MenuRow(if (model.busy) "Syncing" else "Sync now", Ico.Sync, enabled = !model.busy) {
                        open = false
                        scope.launch { model.refresh() }
                    }
                    MenuRow("Semesters", Ico.Fold) {
                        open = false
                        model.screen = Screen.Semesters
                    }
                }
            }
        }
    }
}

/** One line of the overflow, in the sidebar's own idiom of glyph then label. */
@Composable
private fun MenuRow(label: String, ico: Ico, enabled: Boolean = true, onClick: () -> Unit) {
    val c = skema
    val fg = if (enabled) c.ink else c.inkMuted
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Space.medium, vertical = Space.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.medium),
    ) {
        Glyph(ico, tint = fg)
        Text(label, color = fg, fontSize = Type.normal, maxLines = 1)
    }
}

/** Where the detail sheet is allowed to come to rest, top to bottom. */
private enum class Stop { Full, Partial, Gone }

/**
 * The detail on a narrow frame.
 *
 * Three resting places and nothing in between: the whole frame, about half of
 * it, and gone. A drag moves the panel continuously, and a release always
 * lands on one of the three, animated. The old rule only settled the panel
 * when something flung it, so a slow drag over the text let go of a sheet
 * standing halfway up the screen and left it there.
 *
 * There is no close button and no fullscreen button. Up is bigger, down is
 * gone, and every way out of this panel is the same downward slide: a drag, a
 * tap on the calendar behind it, the back gesture, and picking a different
 * event all set the same target and the panel travels there from wherever it
 * happens to be. Nothing here is ever removed from the tree without moving
 * first, which is what a sheet vanishing on the spot used to be.
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

    // Back closes the sheet before the drawer or the screen under it, and is
    // disabled the moment the model lets go, so the rest of the way out is an
    // animation rather than another thing back has to be pressed through.
    BackHandler(enabled = picked != null) { model.picked = null }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val framePx = with(density) { maxHeight.toPx() }
        val flickPx = with(density) { FLICK.toPx() }
        val scope = rememberCoroutineScope()

        // Held past the dismissal so there is still something to draw on the
        // way out. model.picked is null the moment the sheet starts leaving,
        // and an exit animation over nothing is a blank rectangle sliding down.
        var shown by remember { mutableStateOf<CalendarEvent?>(null) }

        // The panel's own content height, which is what lets the partial stop
        // be the content rather than half a screen with a band of empty
        // surface below it. Measured against the full height panel, so it is
        // the true height whenever the detail is shorter than the frame.
        var contentPx by remember { mutableStateOf(0) }
        val restPx = if (contentPx > 0) min(framePx * PARTIAL, contentPx.toFloat()) else framePx * PARTIAL

        /** How far the panel's top edge sits below the top of the frame. */
        fun positionOf(stop: Stop): Float = when (stop) {
            Stop.Full -> 0f
            Stop.Partial -> framePx - restPx
            Stop.Gone -> framePx
        }

        var stop by remember { mutableStateOf(Stop.Gone) }
        var dragging by remember { mutableStateOf(false) }
        val offset = remember { Animatable(framePx) }

        // What the model wants, turned into a stop. Nothing here animates:
        // this decides where the panel belongs and the effect below is the
        // only thing that ever moves it.
        //
        // Keyed on the stop as well, so it is re-asked every time the target
        // moves. That is what makes it converge from anywhere: a finger that
        // catches a panel mid exit leaves the model and the panel disagreeing,
        // and this settles the argument on the next pass rather than leaving
        // the sheet holding an event nobody picked.
        LaunchedEffect(picked, shown, stop) {
            when {
                picked == null -> if (shown != null) stop = Stop.Gone
                shown == null -> {
                    shown = picked
                    stop = Stop.Partial
                }
                // A different event while the sheet is up: out through the
                // bottom first, and the pass after this one brings the new one
                // back up. Swapping the text under a stationary panel is the
                // one exit this sheet is not allowed to have.
                shown != picked -> stop = Stop.Gone
                else -> Unit
            }
        }

        // The one place the panel moves on its own.
        //
        // Keyed on the drag, so a release settles even when it lands on the
        // stop it started from; on the measured height, so a panel that turns
        // out to be shorter than half the frame slides to its true resting
        // place instead of jumping there; and on the frame, so a fold or a
        // rotation re-seats it. A drag interrupts this by snapping the same
        // Animatable, which cancels this coroutine, which is exactly right: an
        // exit a finger caught halfway is no longer an exit.
        LaunchedEffect(stop, dragging, restPx, framePx) {
            if (dragging) return@LaunchedEffect
            val to = positionOf(stop)
            if (offset.value != to) {
                val spec: AnimationSpec<Float> = when {
                    stop == Stop.Gone -> tween(Motion.SHEET_OUT, easing = Motion.leave)
                    // Coming up from off the bottom edge is an arrival and
                    // worth the longer curve; moving between two stops it can
                    // already see is a settle.
                    offset.value >= framePx -> tween(Motion.SHEET_IN, easing = Motion.arrive)
                    else -> Motion.settle
                }
                offset.animateTo(to, spec)
            }
            // Only once the panel is all the way out does the event stop being
            // drawn. Anything earlier is a disappearance.
            if (stop == Stop.Gone) shown = null
        }

        /** Moves the panel as far as it will go, and reports the part used. */
        fun take(dy: Float): Float {
            val from = offset.value
            val to = (from + dy).coerceIn(positionOf(Stop.Full), positionOf(Stop.Gone))
            if (to != from) scope.launch { offset.snapTo(to) }
            return to - from
        }

        /**
         * Where a release lands. Velocity beats position past the flick
         * threshold, so a throw goes where it was aimed however far the sheet
         * had actually travelled; below it the nearest stop wins, which is
         * also what makes a release with no movement in it stay put.
         */
        fun landing(velocity: Float): Stop = when {
            velocity > flickPx -> Stop.Gone
            velocity < -flickPx -> Stop.Full
            else -> Stop.entries.minBy { abs(positionOf(it) - offset.value) }
        }

        fun settle(velocity: Float) {
            // A panel the model has already let go of has one place left to
            // be, whichever way the finger was going when it left the glass.
            // Without this, catching a sheet on its way out and throwing it
            // back up would leave it showing an event nothing has selected.
            val next = if (model.picked == null) Stop.Gone else landing(velocity)
            dragging = false
            stop = next
            // Dismissing by drag takes the same path as dismissing by back or
            // by a tap outside: the model lets go, and the panel is already
            // heading for the bottom edge by the time it hears about it.
            if (next == Stop.Gone) model.picked = null
        }

        // The gesture code below outlives the composition that created it, so
        // it reaches the current versions of these rather than the ones that
        // were in scope when a finger first landed.
        val takeNow by rememberUpdatedState({ dy: Float -> take(dy) })
        val settleNow by rememberUpdatedState({ velocity: Float -> settle(velocity) })

        val drag = rememberDraggableState { dy -> take(dy) }

        // The detail scrolls inside the panel, so a drag over the text has two
        // possible readings and something has to arbitrate. The rule is the
        // one every sheet uses: upward, the sheet moves first and the text
        // only scrolls once the sheet has nowhere left to go; downward, the
        // text scrolls first and the sheet only moves once the text is back at
        // its top.
        //
        // Without this the only draggable part of the sheet is the handle,
        // because a scroller claims the gesture whether or not it has anywhere
        // to scroll, and this detail usually has nowhere.
        val nested = remember {
            object : NestedScrollConnection {

                /** Whether this gesture has actually moved the panel. */
                private var moved = false

                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (available.y >= 0f) return Offset.Zero
                    val used = takeNow(available.y)
                    if (used != 0f) moved = true
                    return Offset(0f, used)
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (available.y <= 0f) return Offset.Zero
                    val used = takeNow(available.y)
                    if (used != 0f) moved = true
                    return Offset(0f, used)
                }

                override suspend fun onPreFling(available: Velocity): Velocity = release(available)

                override suspend fun onPostFling(
                    consumed: Velocity,
                    available: Velocity,
                ): Velocity = release(available)

                /**
                 * The end of any gesture that moved the panel settles it, with
                 * or without a fling behind it. Waiting for a fling was the
                 * bug: a slow drag over the text ends with a velocity of
                 * nearly nothing, and the sheet was left standing between two
                 * stops until something else touched it.
                 */
                private fun release(available: Velocity): Velocity {
                    if (!moved) return Velocity.Zero
                    moved = false
                    settleNow(available.y)
                    return available
                }
            }
        }

        val ink = c.ink
        if (shown != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        // Barely there at the partial stop, still gentle at
                        // full screen. A flat 0.32 read as the calendar being
                        // switched off; this only says which of the two layers
                        // is in front. Read in the draw phase, so a drag
                        // repaints one rectangle instead of recomposing, and
                        // it reaches zero on its own as the panel leaves.
                        val openness = ((framePx - offset.value) / framePx).coerceIn(0f, 1f)
                        drawRect(ink, alpha = SCRIM_MAX * openness)
                    }
                    .clickable(onClick = { model.picked = null }),
            )
        }

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
                    onDragStopped = { velocity -> settle(velocity) },
                ),
        ) {
            val event = shown
            // Everything the panel actually contains, measured as one piece:
            // this height is what the partial stop is capped to. Only reported
            // while there is an event, so an empty panel on its way out does
            // not tell the next one it is four pixels tall.
            Column(Modifier.onSizeChanged { if (event != null) contentPx = it.height }) {
                // The grab strip. A whole row rather than the bar itself,
                // because the bar is 4dp tall and a thumb is not. It is the
                // only thing left saying this panel is draggable, now that the
                // two buttons that said it in words are gone.
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
                    )
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
internal const val SCRIM_MAX = 0.18f

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
internal fun reducedMotion(): Boolean {
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
 * The date the two header lines are talking about.
 *
 * Day is anchored on the selected day rather than on the anchor, the same way
 * the jump picker is, so the header names the day you can see. Everything
 * else follows the anchor, which is what paging moves.
 */
private fun focusOf(model: SkemaModel): LocalDate =
    if (model.view == ViewMode.Day) model.selected else model.anchor

/**
 * A teaching week straddles a month boundary about once a month, and calling
 * the week of 31 August to 4 September "August" is wrong for four of its five
 * days. So the label is built from the days actually on screen and names both
 * months when there are two.
 *
 * Both names are printed in full, always, here and everywhere else in the app.
 * There used to be a fallback that measured the space and dropped one of the
 * two months when the pair would not fit; it is gone. This line is set at
 * Type.small, where the longest pair a week can produce, "September to
 * October", is about 130dp, and the narrowest header in the app has more than
 * twice that.
 */
private fun weekLabel(model: SkemaModel): String {
    val from = weekStart(model.anchor)
    val days = Grid.dayCount(model.settings.showWeekend)
    val to = from.shift(days - 1)
    if (from.month == to.month) return monthLong(from.month)
    return monthLong(from.month) + " to " + monthLong(to.month)
}

/**
 * The top line: which week of the block you are in.
 *
 * DTU teaches in thirteen week blocks and the whole university counts in
 * them. A course says what is due in week 5, a reading list is written in
 * them, and nobody says "the week of 5 October". So that is the headline, and
 * the calendar's own week number moves down a line to the date.
 *
 * The count keeps rising past the block, which is honest arithmetic and a
 * useless thing to print: "Semester week 19" is a week nobody has ever been
 * told to turn up for. Past the teaching weeks it is the exam period and it
 * says so. Before a block opens the count is zero or negative, which is a
 * countdown rather than an error: week 0 is the week before teaching starts,
 * so the weeks left over are always one more than the count is short.
 */
private fun semesterLine(date: LocalDate): String {
    val week = Terms.semesterWeek(date)
    return when {
        week in 1..Terms.TEACHING_WEEKS -> "Semester week " + week
        week > Terms.TEACHING_WEEKS -> "Exam period"
        week == 0 -> "Semester starts next week"
        else -> "Semester starts in " + (1 - week) + " weeks"
    }
}

private fun headline(model: SkemaModel): String = semesterLine(focusOf(model))

/**
 * The second line: the month, the real ISO week, then the year. The month
 * comes first because it is the coarsest of the three and the one you read
 * without meaning to; the year comes last because it changes once a year.
 */
private fun subhead(model: SkemaModel): String {
    val focus = focusOf(model)
    val months = if (model.view == ViewMode.Week) weekLabel(model) else monthLong(focus.month)
    return months + ", week " + isoWeek(focus) + ", " + focus.year
}
