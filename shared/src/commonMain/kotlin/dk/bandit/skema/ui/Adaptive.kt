package dk.bandit.skema.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * What the app knows about the space it has been given.
 *
 * The Pixel Fold this is built for is two devices behind one app: folded it
 * is 411 x 797 dp, unfolded it is 841 x 700 dp. Wider AND shorter, which is
 * the part that catches people out. A layout that only asks "is this a phone
 * or a tablet" gets the unfolded state right and then runs the week grid off
 * the bottom of the screen, because it assumed more width meant more height.
 *
 * So nothing in the app branches on a device or a posture. It branches on the
 * two numbers below, which means the desktop window gets the same treatment
 * for free and a resize is the same event as a fold.
 */
@Immutable
data class Frame(val width: Dp, val height: Dp) {

    /** Material's own break points, and the same ones the emulator lands either side of. */
    val compact: Boolean get() = width < 600.dp
    val expanded: Boolean get() = width >= 840.dp

    /**
     * A second pane from the medium break, not the expanded one.
     *
     * Measured on the Pixel Fold AVD at 420dpi: folded is 411 x 797 dp, and
     * unfolded is 841 x 701 dp in the panel's natural landscape. Rotate that
     * unfolded screen to portrait and it is 701 x 841, which is the same
     * device showing the same inner display. A 840dp threshold would give it
     * two panes lying down and one standing up, which is the fold working
     * only half the time. 600dp is where a list and a detail genuinely fit
     * side by side, and it puts both unfolded orientations on the same side
     * of the line while leaving the folded phone well clear of it.
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
