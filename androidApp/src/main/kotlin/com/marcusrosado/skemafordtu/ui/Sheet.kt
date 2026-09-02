package com.marcusrosado.skemafordtu.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The panel every sheet in the app rises out of: the scrim, the slide up and
 * the slide away, the drag down that dismisses, the back gesture, and the
 * grab strip that says which way it moves. The jump picker and the calendar's
 * settings are both this with different content, which is what makes them
 * arrive and leave as the same object.
 *
 * The panel owns its own way in and out. The caller composes it when it is
 * wanted and drops it when it is done, so an exit animation only exists if the
 * panel asks for the time itself: it goes visible on the first frame, and a
 * dismissal plays the slide before it tells the caller that it is over. The
 * timings are the detail sheet's, read off [Motion].
 *
 * Three ways out, none of them a button: push the panel down, tap the page
 * behind it, or press back. The content is handed a close of its own, so a
 * choice made inside can leave the same way a drag does.
 */
@Composable
fun BottomSheet(
    onDismiss: () -> Unit,
    label: String,
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    val c = skema
    val scope = rememberCoroutineScope()
    val feel = LocalFeel.current
    val reduced = reducedMotion()
    val flick = with(LocalDensity.current) { FLICK.toPx() }

    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    // True from the instant a dismissal starts rather than when it finishes.
    // The slide out has to play before the caller drops the panel, and during
    // those milliseconds nothing here may start a second exit over the top of
    // the first.
    var leaving by remember { mutableStateOf(false) }

    // How far the panel has been pushed below its resting place, and how tall
    // it turned out to be, which is what the push is measured against.
    val pushed = remember { Animatable(0f) }
    var panelPx by remember { mutableIntStateOf(0) }

    fun close() {
        if (leaving) return
        leaving = true
        shown = false
        scope.launch {
            delay(if (reduced) 0L else Motion.SHEET_OUT.toLong())
            onDismiss()
        }
    }

    // Back closes the panel and stops there: whatever is behind it does not
    // change and does not move. It goes through close(), so the panel leaves
    // down through the bottom of the screen the way a dragged one does rather
    // than blinking out. Disabled the moment it starts leaving, so a second
    // press falls through to the screen behind instead of being swallowed by
    // a panel already on its way out.
    BackHandler(enabled = !leaving) { close() }

    // Downwards only. There is nothing above the panel to pull into view, and
    // a drag that can go the wrong way offers a move that does nothing.
    val drag = rememberDraggableState { dy ->
        scope.launch { pushed.snapTo((pushed.value + dy).coerceAtLeast(0f)) }
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = shown,
            enter = fadeIn(tween(Motion.SHEET_FADE)),
            exit = fadeOut(tween(Motion.SHEET_FADE)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(c.ink.copy(alpha = SCRIM_MAX))
                    .clickable(onClick = { close() }),
            )
        }

        AnimatedVisibility(
            visible = shown,
            modifier = Modifier.align(Alignment.BottomCenter),
            // The panel is exactly as tall as its content, so its own height
            // is the distance from the bottom edge of the frame: it enters
            // from where it looks like it came from.
            enter = slideInVertically(tween(Motion.SHEET_IN, easing = Motion.arrive)) {
                if (reduced) 0 else it
            } + fadeIn(tween(Motion.SHEET_FADE)),
            // Leaves from wherever the drag left it, so a dismissal that
            // began as a gesture finishes as one continuous movement instead
            // of restarting from the panel's full height.
            exit = slideOutVertically(tween(Motion.SHEET_OUT, easing = Motion.leave)) {
                if (reduced) 0 else (it - pushed.value).roundToInt().coerceAtLeast(0)
            } + fadeOut(tween(Motion.SHEET_FADE)),
            label = label,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, pushed.value.roundToInt()) }
                    .padding(horizontal = Space.small)
                    .padding(bottom = Space.small)
                    .clip(SquircleShape(Radius.sheet))
                    .background(c.surface)
                    .onSizeChanged { panelPx = it.height }
                    // The whole panel is the grab surface, not only the strip
                    // drawn at the top of it. Whatever sits inside takes a
                    // tap and leaves a vertical drag alone, so nothing there
                    // is competing for this.
                    .draggable(
                        state = drag,
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity ->
                            // Velocity beats position past the flick, so a
                            // throw goes where it was aimed however far the
                            // panel actually travelled.
                            val far = panelPx > 0 && pushed.value > panelPx * DISMISS_SHARE
                            if (velocity > flick || far) {
                                feel.thud()
                                close()
                            } else {
                                pushed.animateTo(0f, Motion.settle)
                            }
                        },
                    )
                    .padding(Space.medium),
            ) {
                // The grab strip, the detail sheet's own. It is a mark saying
                // which way the panel moves rather than a control: the drag
                // works anywhere on the panel, and this is what tells you so.
                Box(
                    Modifier.fillMaxWidth().padding(bottom = Space.small),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .width(Space.large + Space.medium)
                            .height(Space.unit)
                            .background(c.hairline, pillShape()),
                    )
                }
                content { close() }
            }
        }
    }
}

/**
 * How much of its own height the panel has to be pushed down before letting go
 * dismisses it. A share rather than a distance, so the same gesture works
 * whatever the content adds up to on a given screen.
 */
private const val DISMISS_SHARE = 0.3f
