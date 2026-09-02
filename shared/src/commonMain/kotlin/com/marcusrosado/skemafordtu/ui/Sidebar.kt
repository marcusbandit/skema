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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.marcusrosado.skemafordtu.data.ViewMode
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * How wide the sidebar is, in both of its presentations.
 *
 * Sized from what one row holds rather than picked: the pill is inset by
 * Space.small either side, pads itself by Space.small either side again, and
 * then spends IconSize on the glyph and Space.small on the gap. That is 68dp
 * of furniture, so 176 leaves 108dp of label, comfortably more than
 * "Semesters" needs at Type.normal.
 *
 * The drawer is this same width. It is the same list in a different place,
 * not a second menu that happens to hold the same rows, and giving it its own
 * width would be the first step towards the two drifting apart.
 */
internal val RAIL_WIDTH = 176.dp

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
 * The grab handle's touch target, and with it the width of the strip down the
 * left edge that a drag has to start inside to count as reaching for the
 * drawer rather than paging the calendar.
 *
 * 48dp because that is the smallest thing a thumb hits reliably, and because
 * Android's own back gesture owns roughly the first 20dp of the display edge:
 * a 20dp drawer strip, which is what a stock drawer uses, would leave almost
 * nothing to grab on a phone with gesture navigation. The strip and the
 * handle are the same number on purpose, so the affordance IS the place the
 * gesture starts rather than a picture of one somewhere near it.
 */
private val HANDLE_TOUCH = Space.large * 2

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
 * One row. Selection is marked the way the app marks it everywhere else, with
 * a filled accent pill, so the current view reads the same here as a chosen
 * option does in the settings screen.
 *
 * The glyph carries no description: the label is right beside it and a screen
 * reader would otherwise say the same word twice.
 */
@Composable
private fun SidebarRow(entry: SidebarEntry) {
    val c = skema
    val fg = if (entry.selected) c.onAccent else c.inkMuted
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.small)
            .background(if (entry.selected) c.accent else Color.Transparent, pillShape())
            .clickable(onClick = entry.onPick)
            .padding(horizontal = Space.small, vertical = Space.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.small),
    ) {
        Glyph(entry.ico, tint = fg)
        Text(
            entry.label,
            color = fg,
            fontSize = Type.normal,
            fontWeight = if (entry.selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

/** The rows themselves, shared by the rail and the drawer. */
@Composable
private fun SidebarList(model: SkemaModel, modifier: Modifier = Modifier, onNavigate: () -> Unit) {
    val c = skema
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(vertical = Space.small),
        verticalArrangement = Arrangement.spacedBy(Space.unit),
    ) {
        sidebarGroups(model, onNavigate).forEachIndexed { index, group ->
            // A rule between the groups rather than around them, so the number
            // of rules follows the number of groups on its own.
            if (index > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.medium, vertical = Space.small)
                        .height(1.dp)
                        .background(c.hairline),
                )
            }
            group.forEach { SidebarRow(it) }
        }
    }
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
        val width = RAIL_WIDTH.coerceAtMost(maxWidth * DRAWER_MAX_SHARE)
        val density = LocalDensity.current
        val widthPx = with(density) { width.toPx() }
        val flickPx = with(density) { FLICK.toPx() }
        val edgePx = with(density) { HANDLE_TOUCH.toPx() }
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
            // Rides with the calendar and fades out as the drawer arrives: it
            // is the thing you pulled, so it has no business still being there
            // once the strip it belongs to is on screen.
            DrawerHandle(
                enabled = !open,
                fade = { 1f - slide.value },
                onOpen = { onOpenChange(true) },
            )
        }
    }
}

/**
 * The one control, and the only reason the gesture is discoverable.
 *
 * Pinned to the middle of the left edge rather than sitting in the top corner
 * the old menu button lived in. On a folded Fold the top corner is a stretch
 * from anywhere a thumb rests, and a corner glyph says "press me" where this
 * says "pull me", which is what the drawer actually wants. The bar is the
 * detail sheet's grab handle turned on its side, on purpose: one idiom for
 * "there is more of this if you drag".
 */
@Composable
private fun BoxScope.DrawerHandle(enabled: Boolean, fade: () -> Float, onOpen: () -> Unit) {
    val c = skema
    Box(
        Modifier
            .align(Alignment.CenterStart)
            .size(width = HANDLE_TOUCH, height = HANDLE_TOUCH * 2)
            .graphicsLayer { alpha = fade() }
            .semantics { contentDescription = "Views and settings" }
            .clickable(enabled = enabled, onClick = onOpen)
            .padding(start = Space.tight),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .width(Space.unit)
                .height(Space.large + Space.medium)
                .background(c.inkMuted, pillShape()),
        )
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
