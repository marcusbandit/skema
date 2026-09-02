package com.marcusrosado.skemafordtu.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.marcusrosado.skemafordtu.data.ViewMode
import com.marcusrosado.skemafordtu.ics.CalendarEvent
import com.marcusrosado.skemafordtu.ics.Season
import com.marcusrosado.skemafordtu.ics.Terms
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
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

/** The most of the frame the detail pane may take. A shorter entry takes less. */
private const val PARTIAL = 0.5f

/**
 * How much of its own height the detail pane has to be pulled down before
 * letting go dismisses it. A share, so the gesture is the same for a three
 * line entry and a half screen one.
 */
private const val DISMISS_SHARE = 0.3f

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
 * width on the sidebar: on a compact window it is Material's modal drawer,
 * off screen until the menu control or an edge swipe pulls it in; on
 * anything wider it is a rail that is always there. The second spends what
 * is left on panes. Both read the same model, so unfolding the phone moves
 * the detail from a pane under the grid into a rail beside it and the menu
 * from a drawer into a rail, and changes nothing else: same day, same zoom,
 * same selection, no reload. That is what makes the fold seamless rather
 * than a restart.
 *
 * The width question is put to the window size class, Material's own break
 * points, and the panes then measure themselves inside whatever the scaffold
 * and the rail leave, so a day column can never be sized for a width it does
 * not have.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun CalendarShell(model: SkemaModel, palette: CoursePalette) {
    // Syncing is not a button any more. An app that knows its data has a
    // shelf life should not make you press refresh to find out; it fetches
    // when it opens and then on a timer, and the bar under the headline is
    // where you see that it is happening.
    LaunchedEffect(model.hasUrl) {
        while (model.hasUrl) {
            // busy means either the settings screen's own "Sync now" or the
            // open-the-app refresh is already in flight. Two fetches of the
            // same file at once help nobody.
            if (!model.busy) model.refresh()
            delay(SYNC_EVERY)
        }
    }

    // No activity means a preview or a test host, and those get the drawer.
    val activity = LocalActivity.current
    val railed = activity != null &&
        calculateWindowSizeClass(activity).widthSizeClass != WindowWidthSizeClass.Compact

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
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    LaunchedEffect(railed) { if (railed && drawer.isOpen) drawer.close() }

    // "Nothing moved, 736 entries" is worth saying once and then not worth a
    // permanent line of the screen; a failure has to be acknowledged. Both
    // are what a snackbar is for: the first goes by itself, the second waits
    // for its dismiss. Fetching is not a message but a bar under the top
    // bar, so it is skipped here.
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(model.status, model.failed, model.busy) {
        if (model.busy || model.status.isBlank()) return@LaunchedEffect
        snackbar.showSnackbar(
            message = model.status,
            withDismissAction = model.failed,
            duration = if (model.failed) SnackbarDuration.Indefinite else SnackbarDuration.Short,
        )
        model.clearStatus()
    }

    val body: @Composable (onMenu: (() -> Unit)?) -> Unit = { onMenu ->
        Scaffold(
            topBar = {
                Column {
                    TopBar(model, onJump = { jumping = true }, onMenu = onMenu, onTune = { tuning = true })
                    // The one place a sync is visible while it runs.
                    if (model.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            // Measured inside the scaffold, so the frame the panes divide is
            // what is actually left under the bar and beside the rail.
            // Deciding two panes on the window and then drawing them into
            // less than the window is exactly how a day column ends up off
            // the edge.
            BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
                val frame = Frame(maxWidth, maxHeight)
                CompositionLocalProvider(LocalFrame provides frame) {
                    Panes(model, palette, frame)
                }
            }
        }
    }

    if (railed) {
        Row(Modifier.fillMaxSize()) {
            SidebarRail(model, Modifier.fillMaxHeight())
            Box(Modifier.weight(1f).fillMaxHeight()) { body(null) }
        }
    } else {
        SidebarDrawer(model, drawer) {
            body { scope.launch { drawer.open() } }
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

/** One pane or two, which is a question about the frame and nothing else. */
@Composable
private fun Panes(model: SkemaModel, palette: CoursePalette, frame: Frame) {
    if (frame.dual) DualPane(model, palette, frame) else SinglePane(model, palette)
}

@Composable
private fun DualPane(model: SkemaModel, palette: CoursePalette, frame: Frame) {
    val c = skema
    // A third of the width, floored so the rail can hold a room list and
    // capped so the week never loses the space it actually needs.
    val rail = (frame.width * 0.32f).coerceIn(260.dp, 380.dp)

    // The detail has no close button any more, so back is what puts the rail
    // back to the day it was showing. Disabled when nothing is picked, which
    // is what lets the handler under it have the gesture instead.
    BackHandler(enabled = model.picked != null) { model.picked = null }

    Row(Modifier.fillMaxSize()) {
        // The rail has the day, so the month view does not list it too.
        CompositionLocalProvider(LocalDayAside provides true) {
            Calendar(model, palette, Modifier.weight(1f).fillMaxHeight())
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
private fun SinglePane(model: SkemaModel, palette: CoursePalette) {
    Column(Modifier.fillMaxSize()) {
        // The calendar takes what the pane leaves. The pane animates its
        // height, so the grid resizes with it rather than being covered.
        Calendar(model, palette, Modifier.weight(1f))
        // Always composed, never conditional: a pane that is removed from
        // the tree the instant it is dismissed has nothing left to animate.
        DetailPane(model, palette)
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
 * The bar above the calendar, Material's top app bar. Left to right: the
 * menu control while the sidebar is a drawer, the two line headline, and
 * three controls at the right end. Those three are the ones every Android
 * user already knows from that corner, today, tune and more, drawn with
 * Material's own glyphs for the same reason nobody redraws the back arrow.
 *
 * Today stays in the row and dims when today is already on screen. It used
 * to vanish instead, and a control that comes and goes moves everything
 * beside it under the finger reaching for it.
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    TopAppBar(
        navigationIcon = {
            if (onMenu != null) {
                IconButton(onClick = onMenu) {
                    Glyph(Ico.Menu, tint = LocalContentColor.current, size = IconSizeLarge, description = "Open the menu")
                }
            }
        },
        title = {
            Column {
                Row(
                    Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable(enabled = jumpable, onClick = onJump)
                        .padding(horizontal = Space.unit),
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
                        maxLines = 1,
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
                Text(
                    subhead(model),
                    color = c.inkMuted,
                    fontSize = Type.small,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = Space.unit),
                )
            }
        },
        actions = {
            // Live only when pressing it would move something. Asking whether
            // the anchor is today was the wrong question: stepping a day
            // forward and switching to week left the anchor on tomorrow, same
            // week, so the control was live and did nothing.
            IconButton(onClick = { model.goToday() }, enabled = !model.showingToday) {
                Glyph(Ico.Today, tint = LocalContentColor.current, size = IconSizeLarge, description = "Go to today")
            }
            // Tune is the calendar's settings, a sheet over the calendar, not
            // the app's, which are a screen behind the sidebar.
            IconButton(onClick = onTune) {
                Glyph(Ico.Tune, tint = LocalContentColor.current, size = IconSizeLarge, description = "Calendar settings")
            }
            Overflow(model)
        },
    )
}

/**
 * The three dots and what drops out of them: the two things worth reaching
 * from the calendar that are neither a view nor a setting. Sync is here
 * because the automatic one is silent and there are moments you want to
 * know it ran; Semesters because it is the other screen. Anything the
 * sidebar already offers as a view stays out, or the menu becomes a second
 * sidebar.
 */
@Composable
private fun Overflow(model: SkemaModel) {
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { open = true }) {
            Glyph(Ico.More, tint = LocalContentColor.current, size = IconSizeLarge, description = "More")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(if (model.busy) "Syncing" else "Sync now") },
                leadingIcon = { Glyph(Ico.Sync, tint = LocalContentColor.current) },
                enabled = !model.busy,
                onClick = {
                    open = false
                    scope.launch { model.refresh() }
                },
            )
            DropdownMenuItem(
                text = { Text("Semesters") },
                leadingIcon = { Glyph(Ico.Fold, tint = LocalContentColor.current) },
                onClick = {
                    open = false
                    model.screen = Screen.Semesters
                },
            )
        }
    }
}

/**
 * The detail on a narrow frame: a pane under the calendar, not a sheet over it.
 *
 * It used to be a sheet that rose from the bottom edge and covered the grid,
 * with three resting places and a scrim. The month view puts the day under
 * its grid as a second pane, and the week should read the same way: tap a
 * class and the grid gives up the lower part of the screen to it, the way a
 * split pane does, and takes it back when the detail goes. The pane is as
 * tall as its content up to half the frame, so a short entry takes a short
 * pane and a long one scrolls inside the half.
 *
 * Nothing here is ever removed from the tree without moving first. The pane
 * grows up from the bottom edge as it arrives and shrinks back into it as it
 * leaves, on the sheet's old timings, and the grid above resizes with it.
 * Two ways out, neither a button: pull the pane down past a third of its
 * height or flick it, or press back. Picking another class swaps the content
 * in place with a fade.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailPane(model: SkemaModel, palette: CoursePalette) {
    val picked = model.picked
    val reduced = reducedMotion()
    val frame = LocalFrame.current
    val scope = rememberCoroutineScope()
    val flickPx = with(LocalDensity.current) { FLICK.toPx() }

    // Back closes the pane before the drawer or the screen under it, and is
    // disabled the moment the model lets go, so the rest of the way out is an
    // animation rather than another thing back has to be pressed through.
    BackHandler(enabled = picked != null) { model.picked = null }

    // Held past the dismissal so there is still something to draw while the
    // pane shrinks away; model.picked is null the moment the exit starts.
    var last by remember { mutableStateOf<CalendarEvent?>(null) }
    LaunchedEffect(picked) { if (picked != null) last = picked }
    val shown = picked ?: last

    // How far a drag has pulled the pane down. Visual only: the layout does
    // not change under a finger. The pane slides on a layer and either comes
    // back or goes, and a new pick starts from rest.
    val pulled = remember { Animatable(0f) }
    var paneHeight by remember { mutableIntStateOf(0) }
    LaunchedEffect(picked) { if (picked != null) pulled.snapTo(0f) }

    /** Moves the pane as far as it will go, and reports the part used. */
    fun take(dy: Float): Float {
        val from = pulled.value
        val to = (from + dy).coerceAtLeast(0f)
        if (to != from) scope.launch { pulled.snapTo(to) }
        return to - from
    }

    /** A release: past the flick or past the share it goes, otherwise it comes back. */
    fun settle(velocity: Float) {
        val far = paneHeight > 0 && pulled.value > paneHeight * DISMISS_SHARE
        if (velocity > flickPx || far) {
            model.picked = null
        } else {
            scope.launch { pulled.animateTo(0f, Motion.settle) }
        }
    }

    val takeNow by rememberUpdatedState({ dy: Float -> take(dy) })
    val settleNow by rememberUpdatedState({ velocity: Float -> settle(velocity) })

    // The detail scrolls inside the pane, so a drag over the text has two
    // readings. Downward with the text at its top, the pane moves; upward
    // while the pane is pulled, the pane comes back before the text scrolls.
    // Without this the only draggable part would be the grab strip.
    val nested = remember {
        object : NestedScrollConnection {
            private var moved = false

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y >= 0f) return Offset.Zero
                val used = takeNow(available.y)
                if (used != 0f) moved = true
                return Offset(0f, used)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y <= 0f) return Offset.Zero
                val used = takeNow(available.y)
                if (used != 0f) moved = true
                return Offset(0f, used)
            }

            override suspend fun onPreFling(available: Velocity): Velocity = release(available)

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = release(available)

            private fun release(available: Velocity): Velocity {
                if (!moved) return Velocity.Zero
                moved = false
                settleNow(available.y)
                return available
            }
        }
    }

    AnimatedVisibility(
        visible = picked != null,
        enter = if (reduced) {
            fadeIn(tween(Motion.FADE))
        } else {
            expandVertically(tween(Motion.SHEET_IN, easing = Motion.arrive), expandFrom = Alignment.Top) +
                fadeIn(tween(Motion.SHEET_FADE))
        },
        exit = if (reduced) {
            fadeOut(tween(Motion.FADE))
        } else {
            shrinkVertically(tween(Motion.SHEET_OUT, easing = Motion.leave), shrinkTowards = Alignment.Top) +
                fadeOut(tween(Motion.SHEET_FADE))
        },
        label = "detail",
    ) {
        val event = shown ?: return@AnimatedVisibility
        // A Material surface a step above the page, with Material's own
        // drag handle: the pane is the same object a bottom sheet is, in a
        // split rather than over it, and it should look like one.
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = frame.height * PARTIAL)
                .padding(horizontal = Space.small)
                .padding(bottom = Space.small)
                .graphicsLayer { translationY = pulled.value }
                .onSizeChanged { paneHeight = it.height }
                .nestedScroll(nested)
                .draggable(
                    state = rememberDraggableState { dy -> take(dy) },
                    orientation = Orientation.Vertical,
                    onDragStopped = { velocity -> settle(velocity) },
                ),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 2.dp,
        ) {
            Column {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    BottomSheetDefaults.DragHandle()
                }
                Crossfade(targetState = event, animationSpec = tween(Motion.FADE), label = "picked") { e ->
                    EventDetail(
                        event = e,
                        tint = palette.tint(e.courseCode),
                        conflict = e.startEpochSeconds in model.conflicts,
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
