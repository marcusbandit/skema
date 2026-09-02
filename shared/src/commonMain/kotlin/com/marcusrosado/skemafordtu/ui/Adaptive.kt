package com.marcusrosado.skemafordtu.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * What the app knows about the space it has been given.
 *
 * The device this is built for is two devices behind one app, and the two are
 * not related the way people assume. Measured on the Pixel 10 Pro Fold at
 * 390dpi, off the running device rather than off the profile: folded it is
 * 1080 x 2364 px, 443 x 970 dp; unfolded 2076 x 2152 px, 852 x 883 dp.
 * Unfolding buys 409dp of width and COSTS 87dp of height, so a layout that
 * treats "bigger" as one axis gets the unfolded state wrong. The previous generation is different again,
 * 411 x 797 folded and 841 x 701 unfolded, where unfolding buys width and
 * COSTS height.
 *
 * Between those two the only safe assumption is that there is none, which is
 * why nothing in the app branches on a device or a posture. It branches on
 * the two numbers below, so both foldables, a rotation and a desktop window
 * resize are all the same event.
 */
@Immutable
data class Frame(val width: Dp, val height: Dp) {

    /** Material's own break points, and the same ones the emulator lands either side of. */
    val compact: Boolean get() = width < 600.dp
    val expanded: Boolean get() = width >= 840.dp

    /**
     * A second pane from the medium break, not the expanded one.
     *
     * A 840dp threshold fails on both generations. On the older Fold the
     * unfolded screen is 841 x 701 lying down and 701 x 841 standing up, so
     * it would get two panes one way up and one the other, which is the fold
     * working half the time. On the Pixel 10 Pro Fold the unfolded screen is
     * 852 x 883, which clears 840 only just, and its folded screen is 443dp.
     * 600dp is where a list and a detail genuinely fit side by side, and it
     * puts every unfolded orientation of both devices on one side of the line
     * while leaving both folded screens well clear of the other.
     */
    val dual: Boolean get() = width >= 600.dp

    /** Short and wide is the unfolded posture, and it wants a flatter chrome. */
    val short: Boolean get() = height < 760.dp
}

val LocalFrame = staticCompositionLocalOf { Frame(411.dp, 797.dp) }

/**
 * How wide one day column is, and therefore how many days fit.
 *
 * Five columns is the honest week for a university: Saturday and Sunday are
 * empty almost every week, and spending two sevenths of a 411dp screen on
 * them costs the weekdays about 12dp each. Below MIN_COLUMN a column stops
 * being readable, so the week scrolls sideways instead of shrinking further.
 */
object Grid {

    /** Narrower than this and the time label inside a block stops fitting. */
    val MIN_COLUMN = 52.dp

    /** The clock gutter down the left. Sized for "08:00" at the small size. */
    val GUTTER = 44.dp

    /** Zoom range for one hour of the day, in dp. */
    val MIN_HOUR = 28f
    val MAX_HOUR = 240f

    fun dayCount(showWeekend: Boolean): Int = if (showWeekend) 7 else 5

    /**
     * How many of those days can be on screen at once. Derived from the width
     * rather than chosen per device, so a folded phone, an unfolded phone and
     * a desktop window all fall out of the same line.
     */
    fun visibleDays(frame: Frame, showWeekend: Boolean): Int {
        val total = dayCount(showWeekend)
        val usable = frame.width - GUTTER
        val fit = (usable / MIN_COLUMN).toInt()
        return fit.coerceIn(1, total)
    }

    fun columnWidth(frame: Frame, visible: Int): Dp =
        (frame.width - GUTTER) / visible.coerceAtLeast(1)

    fun clampHour(h: Float): Float = h.coerceIn(MIN_HOUR, MAX_HOUR)
}
