package com.marcusrosado.skemafordtu.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import kotlin.math.ln

/**
 * The gestures the calendar is driven by.
 *
 * The rule this app follows is that a gesture comes first and a button is the
 * fallback, so these have to coexist with the scrolling and paging Compose is
 * already doing. The trick throughout is to claim as little as possible: a
 * handler that swallows every drag makes the grid feel dead, and one that
 * swallows none never fires.
 */

/**
 * Pinch to zoom, without stealing the one finger gestures underneath.
 *
 * A transform detector normally consumes pan as well as zoom, which kills both
 * the vertical scroll of the grid and the horizontal paging between weeks. So
 * this only looks at events that have two or more pointers down, and only
 * consumes them once the pinch has actually changed the scale. Single finger
 * drags are never touched and reach the scroller and the pager untouched.
 */
fun Modifier.pinchZoom(
    enabled: Boolean = true,
    onZoom: (factor: Float) -> Unit,
): Modifier = if (!enabled) this else pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var pinching = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val down = event.changes.count { it.pressed }
            if (down < 2) {
                if (down == 0) break
                continue
            }
            val zoom = event.calculateZoom()
            // A dead band, because two fingers landing a millisecond apart
            // report a scale change that the user did not mean.
            if (pinching || abs(zoom - 1f) > 0.01f) {
                pinching = true
                if (zoom > 0f && zoom != 1f) onZoom(zoom)
                event.changes.forEach { it.consume() }
            }
        }
    }
}

/**
 * Turns continuous pinching at the end of the zoom range into a view change.
 *
 * Pinching in past the smallest hour should not just stop; the natural reading
 * of "keep zooming out" is "show me more time", which is the next view up.
 * Pressure accumulates in log space so that the same physical pinch counts the
 * same whether it started at 30dp or 200dp an hour, and it has to build past a
 * threshold before firing, so a hard flick does not skip two views at once.
 */
class ZoomPressure(private val threshold: Float = 1.1f) {

    private var pressure = 0f

    /**
     * Feed every pinch factor here along with whether the zoom is pinned at
     * its limit. Returns -1 to go one view wider, +1 one view narrower, 0 for
     * nothing yet.
     */
    fun feed(factor: Float, atMin: Boolean, atMax: Boolean): Int {
        if (factor <= 0f) return 0
        val push = ln(factor)
        val blocked = (push < 0f && atMin) || (push > 0f && atMax)
        if (!blocked) {
            // The zoom itself is still moving, so nothing is being held
            // against a wall and any built up pressure is stale.
            pressure = 0f
            return 0
        }
        pressure += push
        return when {
            pressure <= -threshold -> { pressure = 0f; -1 }
            pressure >= threshold -> { pressure = 0f; 1 }
            else -> 0
        }
    }

    fun reset() { pressure = 0f }
}
