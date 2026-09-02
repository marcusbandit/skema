package com.marcusrosado.skemafordtu.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.marcusrosado.skemafordtu.data.ViewMode
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * How wide the rail is.
 *
 * Sized from what one row holds rather than picked: the pill is inset by
 * Space.small either side, pads itself by Space.medium either side again,
 * and then spends IconSizeLarge on the glyph and Space.medium on the gap.
 * That is about 94dp of furniture, so 176 leaves 82dp of label, which is
 * what "Semesters" needs at Type.normal with a little over.
 */
internal val RAIL_WIDTH = 176.dp

/**
 * How wide the drawer is, which is no longer how wide the rail is.
 *
 * The two used to share one number, on the argument that they are the same
 * list and a second width was the first step to them drifting apart. The
 * list is still shared; the width is not, because the two presentations are
 * paid for differently. A rail is width the calendar never gets back, so it
 * is as narrow as its longest label allows. A drawer is on screen only while
 * it is being used, and while it is used it is the thing being read, so it
 * takes the width a menu wants: a glyph, a gap and a label at a comfortable
 * pitch, with enough calendar left showing to say where you came from. 300dp
 * is about two thirds of the folded phone, the share a stock drawer takes.
 */
private val DRAWER_WIDTH = 300.dp

/**
 * Where the sidebar stops being a drawer and becomes a permanent rail.
 *
 * A rail is width the calendar never gets back, so the honest question is
 * what is left after it, not how big the screen is. Below RAIL_WIDTH + 426dp
 * the leftover is narrower than the folded phone, and a calendar that already
 * lives on 426dp has nothing spare to fund a permanent strip of chrome with.
 * Above it the rest of the app still has more room than folding ever gave it,
 * and what it then spends on a detail pane is the other branch's business.
 * That puts the line at 602dp, and 640 is that with margin.
 *
 * The margin is the point. Every unfolded state of both Fold generations is
 * above it, 852 and 841 lying down and 701 standing up, and every folded
 * state is far below, 426 and 411. So the sidebar answers the fold and never
 * the rotation: there is no posture where turning the phone swaps the chrome.
 *
 * Deliberately not Frame.dual. That break asks whether a detail pane fits
 * beside the calendar; this one asks what a permanent strip costs the
 * calendar, and at 600dp the two have different answers. A rail there would
 * leave 424dp to split between two panes, which is one pane's worth.
 */
internal val RAIL_FROM = 640.dp

/**
 * The most of a narrow frame the drawer will take. The strip of calendar left
 * showing is what says the calendar is still there beside the menu, so the
 * drawer gives up width before it gives up that strip. Only bites on a window
 * narrower than a phone; a phone is already clear of it.
 */
private const val DRAWER_MAX_SHARE = 0.8f

/**
 * The strip down the left edge that a drag has to start inside to count as
 * reaching for the drawer rather than paging the calendar.
 *
 * 48dp because Android's own back gesture owns roughly the first 20dp of the
 * display edge: a 20dp strip, which is what a stock drawer uses, would leave
 * almost nothing to grab on a phone with gesture navigation. Nothing is
 * drawn on it any more. The menu control in the top bar is what says the
 * drawer exists; this strip is where a finger that already knows pulls it in.
 */
private val EDGE_STRIP = Space.large * 2

/** One row of the sidebar. */
private class SidebarEntry(
    val ico: Ico,
    val label: String,
    val selected: Boolean,
    val onPick: () -> Unit,
)

/** The glyph each view answers to. See Icons.kt for why these four shapes. */
private fun glyphFor(mode: ViewMode): Ico = when (mode) {
    ViewMode.Month -> Ico.ViewMonth
    ViewMode.Week -> Ico.ViewWeek
    ViewMode.Day -> Ico.ViewDay
    ViewMode.Agenda -> Ico.ViewAgenda
}

/**
 * What the sidebar holds, in groups.
 *
 * The views are read off ViewMode.entries rather than written out, so a fifth
 * view arrives here as a row without anyone editing this file, and the rules
 * between groups are counted off the groups. The second group is the two
 * places that are not views: they leave the calendar rather than change it,
 * which is why nothing in it is ever the selected row.
 */
private fun sidebarGroups(model: SkemaModel, onNavigate: () -> Unit): List<List<SidebarEntry>> =
    listOf(
        ViewMode.entries.map { mode ->
            SidebarEntry(glyphFor(mode), mode.name, model.view == mode) {
                model.setView(mode)
                onNavigate()
            }
        },
        listOf(
            // Lucide's stacked layers: a list of semesters is a stack of terms,
            // and this is the one glyph in the set that draws a stack.
            SidebarEntry(Ico.Fold, "Semesters", false) {
                model.screen = Screen.Semesters
                onNavigate()
            },
            SidebarEntry(Ico.Settings, "Settings", false) {
                model.screen = Screen.Settings
                onNavigate()
            },
        ),
    )

/**
 * One row, in the shape a stock Android drawer draws it: a large glyph, a
 * gap, a label, and behind the current view a soft pill. Soft rather than
 * the solid accent the settings screen uses for a chosen option, because
 * this pill is a place marker that is up for as long as the drawer is, and a
 * solid red bar the height of a row is a lot of red to park beside a
 * calendar. The label and glyph go accent instead, which is what still says
 * chosen. The other rows are full ink, not muted: a menu is a list of
 * places to go, and none of them is a lesser place.
 *
 * The glyph carries no description: the label is right beside it and a screen
 * reader would otherwise say the same word twice.
 */
@Composable
private fun SidebarRow(entry: SidebarEntry) {
    val c = skema
    val fg = if (entry.selected) c.accent else c.ink
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.small)
            .clip(pillShape())
            .background(if (entry.selected) c.accentSoft else Color.Transparent)
            .clickable(onClick = entry.onPick)
            .padding(horizontal = Space.medium, vertical = Space.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.medium),
    ) {
        Glyph(entry.ico, tint = fg, size = IconSizeLarge)
        Text(
            entry.label,
            color = fg,
            fontSize = Type.normal,
            fontWeight = if (entry.selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

/** The title and the rows, shared by the rail and the drawer. */
@Composable
private fun SidebarList(model: SkemaModel, modifier: Modifier = Modifier, onNavigate: () -> Unit) {
    val c = skema
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(bottom = Space.small),
        verticalArrangement = Arrangement.spacedBy(Space.unit),
    ) {
        // The app's name, in the accent, the way a drawer names the thing it
        // belongs to. This is the only place the name is printed: the top bar
        // is spent on where in the calendar you are, not on what app this is.
        Text(
            "Skema",
            color = c.accent,
            fontSize = Type.large,
            maxLines = 1,
            modifier = Modifier.padding(
                start = Space.large,
                top = Space.large,
                end = Space.large,
                bottom = Space.small,
            ),
        )
        sidebarGroups(model, onNavigate).forEach { group ->
            // A rule before every group, which puts one under the title and
            // one between each pair of groups, and the count follows the
            // number of groups on its own.
            Rule()
            group.forEach { SidebarRow(it) }
        }
    }
}

/** A hairline across the sidebar, inset to the title's own margin. */
@Composable
private fun Rule() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.large, vertical = Space.small)
            .height(1.dp)
            .background(skema.hairline),
    )
}

/**
 * The sidebar as a strip of the window.
 *
 * Sits on the page rather than on its own surface, because a strip that is
 * part of the window is part of the page; the hairline is enough to say where
 * the calendar starts. That hairline is inside the strip's own width, so
 * RAIL_WIDTH is the whole cost of it and the shell can subtract it and be
 * right.
 *
 * The drawer draws this same composable rather than a card that resembles it,
 * which is the whole of what "the menu should be part of the side window"
 * asks for: on a wide screen it is always there, on a narrow one it is off to
 * the left until it is pulled in, and it is the same strip either way.
 */
@Composable
internal fun SidebarRail(
    model: SkemaModel,
    modifier: Modifier = Modifier,
    onNavigate: () -> Unit = {},
) {
    val c = skema
    Row(modifier) {
        SidebarList(model, Modifier.weight(1f).fillMaxHeight(), onNavigate)
        Box(Modifier.width(1.dp).fillMaxHeight().background(c.hairline))
    }
}

/**
 * The narrow layout: the sidebar off to the left of the calendar, and the
 * calendar pushed by exactly as much of it as is showing.
 *
 * Not a card over the content and not a scrim. The strip's right edge is the
 * calendar's left edge at every point of the animation, so the two move as
 * one piece of layout that happens to be wider than the window. One number
 * drives both offsets, which is what makes it impossible for them to drift
 * apart, and it is the same number a finger drags.
 *
 * Nothing here shrinks: the calendar keeps the width it measured for and
 * slides out of the frame on the right. Re-measuring a week grid on every
 * frame of a drag is how a drawer ends up stuttering, and the part that
 * leaves is the part the drawer is standing in front of anyway.
 */
@Composable
internal fun SidebarScaffold(
    model: SkemaModel,
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    // Back closes the drawer before anything under it gets a turn. Disabled
    // rather than absent while it is closed, so the handler under this one
    // takes the gesture instead of this one swallowing it.
    BackHandler(enabled = open) { onOpenChange(false) }

    // Read through updated state because the gesture detector below is
    // launched once and keeps the closure it was launched with; a captured
    // `open` would be the value it had when the finger last landed.
    val openNow by rememberUpdatedState(open)
    val changeNow by rememberUpdatedState(onOpenChange)

    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val width = DRAWER_WIDTH.coerceAtMost(maxWidth * DRAWER_MAX_SHARE)
        val density = LocalDensity.current
        val widthPx = with(density) { width.toPx() }
        val flickPx = with(density) { FLICK.toPx() }
        val edgePx = with(density) { EDGE_STRIP.toPx() }
        val scope = rememberCoroutineScope()

        // Same reason as the two above: the gesture detector holds the closure
        // it was launched with, and this is the one number in it that a fold
        // can change under its feet.
        val widthNow by rememberUpdatedState(widthPx)

        // 0 is closed, 1 is open, and everything in between is a finger.
        val slide = remember { Animatable(0f) }
        var dragging by remember { mutableStateOf(false) }

        // Keyed on the drag as well as on open, because a release that lands
        // back on the state it started from still has the last of the drag to
        // animate away and `open` would not have changed.
        LaunchedEffect(open, dragging, widthPx) {
            if (dragging) return@LaunchedEffect
            val to = if (open) 1f else 0f
            if (slide.value != to) slide.animateTo(to, Motion.settle)
        }

        /** Moves the pair as far as they will go. */
        fun take(dx: Float) {
            val to = (slide.value + dx / widthNow).coerceIn(0f, 1f)
            if (to != slide.value) scope.launch { slide.snapTo(to) }
        }

        /**
         * Where a release lands. A throw past the flick threshold decides
         * outright whichever way it was aimed; below it the nearer of the two
         * states wins, which for two states is the halfway mark.
         */
        fun land(velocity: Float) {
            val wanted = when {
                velocity > flickPx -> true
                velocity < -flickPx -> false
                else -> slide.value > 0.5f
            }
            dragging = false
            if (wanted != openNow) changeNow(wanted)
        }

        val drag = rememberDraggableState { dx -> take(dx) }

        SidebarRail(
            model,
            Modifier
                .offset { IntOffset((-widthPx * (1f - slide.value)).roundToInt(), 0) }
                .width(width)
                .fillMaxHeight()
                .draggable(
                    state = drag,
                    orientation = Orientation.Horizontal,
                    onDragStarted = { dragging = true },
                    onDragStopped = { velocity -> land(velocity) },
                ),
        ) { onOpenChange(false) }

        Box(
            Modifier
                .fillMaxSize()
                .offset { IntOffset((widthPx * slide.value).roundToInt(), 0) }
                // Declared before the plain draggable below it, so this one
                // sees the pointer first and the other only gets what this one
                // did not claim.
                .drawerDrag(
                    strip = edgePx,
                    modal = { openNow },
                    onStart = { dragging = true },
                    onDelta = { dx -> take(dx) },
                    onStop = { velocity -> land(velocity) },
                    onTap = { if (openNow) changeNow(false) },
                )
                // The catch-all, for a drag that starts anywhere at all. It
                // resolves in the main pass, after the views underneath have
                // had their turn, so a pager keeps every horizontal drag it
                // wants and this only ever fires where nothing else claimed
                // one. In practice that is Agenda, which scrolls vertically
                // and leaves sideways free.
                .draggable(
                    state = drag,
                    orientation = Orientation.Horizontal,
                    onDragStarted = { dragging = true },
                    onDragStopped = { velocity -> land(velocity) },
                ),
        ) {
            content()
        }
    }
}

/**
 * The drawer's half of every gesture that reaches the calendar.
 *
 * The rule, which is the whole design of this thing:
 *
 * While the drawer is CLOSED it only answers to a horizontal drag that starts
 * inside the edge strip, and it claims that one in the initial pass so it
 * beats the pagers underneath. Week, Day and Month all page sideways and a
 * drawer that stole those drags would be worse than no gesture at all; an
 * edge is the one place on the screen where a sideways drag cannot mean
 * "next week", so that is the only place this takes one. A drag that goes
 * vertical first is let go entirely, so scrolling still works over the strip.
 *
 * While it is OPEN the whole calendar is the strip: it is a peek of what is
 * behind the menu rather than something to work in, so any drag on it moves
 * the drawer and a tap on it puts the drawer away.
 */
private fun Modifier.drawerDrag(
    strip: Float,
    modal: () -> Boolean,
    onStart: () -> Unit,
    onDelta: (Float) -> Unit,
    onStop: (Float) -> Unit,
    onTap: () -> Unit,
): Modifier = pointerInput(strip) {
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val blocking = modal()
        if (!blocking && down.position.x > strip) return@awaitEachGesture
        if (blocking) down.consume()

        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position)
        var travelled = 0f
        var claimed = false

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) {
                if (blocking) change.consume()
                if (claimed) onStop(tracker.calculateVelocity().x) else if (blocking) onTap()
                break
            }
            tracker.addPosition(change.uptimeMillis, change.position)
            val delta = change.positionChangeIgnoreConsumed()
            if (claimed) {
                onDelta(delta.x)
                change.consume()
                continue
            }
            travelled += delta.x
            val sideways = abs(travelled)
            val upright = abs(change.position.y - down.position.y)
            if (sideways > slop && sideways > upright) {
                claimed = true
                onStart()
                change.consume()
            } else if (upright > slop && !blocking) {
                // Went up or down first, so it belongs to whatever is under
                // it. Nothing has been consumed, so the scroller never knew
                // this was in the running.
                break
            }
        }
    }
}
