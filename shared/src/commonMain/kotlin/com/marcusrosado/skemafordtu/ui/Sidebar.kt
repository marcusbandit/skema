package com.marcusrosado.skemafordtu.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.marcusrosado.skemafordtu.data.ViewMode
import kotlinx.coroutines.launch
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
 * The most of a narrow frame the drawer will cover. The strip of calendar
 * left showing is what says the menu is temporary and the calendar is still
 * there under it, so the drawer gives up width before it gives up that strip.
 * Only bites on a window narrower than a phone; a phone is already clear of it.
 */
private const val DRAWER_MAX_SHARE = 0.8f

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
 * The sidebar as a strip that is always there.
 *
 * Sits on the page rather than on its own surface, because a rail that is
 * never dismissed is part of the page; the hairline is enough to say where
 * the calendar starts. That hairline is inside the rail's own width, so
 * RAIL_WIDTH is the whole cost of the rail and the shell can subtract it and
 * be right.
 */
@Composable
internal fun SidebarRail(model: SkemaModel, modifier: Modifier = Modifier) {
    val c = skema
    Row(modifier) {
        SidebarList(model, Modifier.weight(1f).fillMaxHeight()) { }
        Box(Modifier.width(1.dp).fillMaxHeight().background(c.hairline))
    }
}

/**
 * The sidebar as a card that comes over the calendar and goes away again.
 *
 * The same panel the detail sheet is, arriving from the side instead of from
 * below, so it borrows that motion rather than inventing a second one. Three
 * ways out: the scrim, a drag to the left, and picking something. A drag
 * moves the card with the finger and a release either throws it out or puts
 * it back, which is the rule every sheet in the app already follows.
 *
 * Always composed, never conditional. A panel taken out of the tree the
 * instant it is dismissed has nothing left to animate on the way out.
 */
@Composable
internal fun SidebarDrawer(model: SkemaModel, open: Boolean, onDismiss: () -> Unit) {
    val c = skema
    val reduced = reducedMotion()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val panel = RAIL_WIDTH.coerceAtMost(maxWidth * DRAWER_MAX_SHARE)
        val density = LocalDensity.current

        // The card floats clear of the edge, so what it has to cross to be
        // gone is its own width plus that gap.
        val travelPx = with(density) { (panel + Space.small).toPx() }
        val flickPx = with(density) { FLICK.toPx() }
        val scope = rememberCoroutineScope()

        // How far the card sits left of where it rests: 0 open, -travelPx gone.
        val offset = remember { Animatable(0f) }
        LaunchedEffect(open) {
            // The last drawer may have been left mid drag on its way out.
            if (open) offset.snapTo(0f)
        }

        /** Moves the card as far as it will go, and reports the part used. */
        fun take(dx: Float): Float {
            val from = offset.value
            val to = (from + dx).coerceIn(-travelPx, 0f)
            if (to != from) scope.launch { offset.snapTo(to) }
            return to - from
        }

        // Velocity beats position past the flick threshold, so a throw goes
        // where it was aimed however far the card actually travelled; below it
        // the nearer of the two stops wins. Dismissal hands the rest of the
        // travel to the exit transition rather than animating there and then
        // playing a second animation over the top.
        val land: (Float) -> Unit = { velocity ->
            val gone = when {
                velocity < -flickPx -> true
                velocity > flickPx -> false
                else -> offset.value < -travelPx / 2f
            }
            if (gone) onDismiss() else scope.launch { offset.animateTo(0f, Motion.settle) }
        }

        val drag = rememberDraggableState { dx -> take(dx) }

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
                        // Fades out with the drag, so pulling the card away
                        // gives the calendar back rather than holding it dim
                        // until the finger lifts. Read in the draw phase: a
                        // drag repaints one rectangle instead of recomposing.
                        val openness = (1f + offset.value / travelPx).coerceIn(0f, 1f)
                        drawRect(ink, alpha = SCRIM_MAX * openness)
                    }
                    .clickable(onClick = onDismiss),
            )
        }

        AnimatedVisibility(
            visible = open,
            modifier = Modifier.fillMaxSize(),
            enter = slideInHorizontally(tween(Motion.SHEET_IN, easing = Motion.arrive)) {
                if (reduced) 0 else -travelPx.roundToInt()
            } + fadeIn(tween(Motion.SHEET_FADE)),
            // Leaves from wherever the drag left it, so a dismissal that began
            // as a gesture finishes as one continuous movement.
            exit = slideOutHorizontally(tween(Motion.SHEET_OUT, easing = Motion.leave)) {
                if (reduced) 0 else (-travelPx - offset.value).roundToInt()
            } + fadeOut(tween(Motion.SHEET_FADE)),
            label = "sidebar",
        ) {
            Box(Modifier.fillMaxSize().padding(Space.small)) {
                Column(
                    Modifier
                        .offset { IntOffset(offset.value.roundToInt(), 0) }
                        .width(panel)
                        .fillMaxHeight()
                        .clip(SquircleShape(Radius.sheet))
                        .background(c.surface)
                        .draggable(
                            state = drag,
                            orientation = Orientation.Horizontal,
                            onDragStopped = { velocity -> land(velocity) },
                        ),
                ) {
                    SidebarList(model, Modifier.fillMaxHeight()) { onDismiss() }
                }
            }
        }
    }
}
