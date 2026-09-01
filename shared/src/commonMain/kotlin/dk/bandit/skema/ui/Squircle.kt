package dk.bandit.skema.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * The one rounded-corner primitive in this app. Every card, chip, button and
 * field goes through it, and nothing uses RoundedCornerShape.
 *
 * A plain rounded rectangle is G1: the straight edge meets the arc with the
 * curvature jumping from 0 to 1/r in one step, and the eye reads that step as
 * a pinch. This is G2, the Figma and Apple construction: shorten the arc,
 * then spend the leftover corner on a cubic each side that ramps curvature up
 * to the arc and back down again.
 *
 * The radius passed in is the corner's REACH along the edge, not the arc
 * radius, so a squircle and a plain rounded rect asking for 16 are the same
 * size and can actually be compared. Reach gets clamped to half the shortest
 * side, which keeps smoothing fixed: the usual trick of degrading smoothing
 * when a corner will not fit hands back a plain arc for exactly the shapes
 * that want a squircle most, a pill being the obvious one.
 */
class SquircleShape(
    private val radius: Dp,
    private val smoothing: Float = DEFAULT_SMOOTHING,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(squirclePath(size, with(density) { radius.toPx() }, smoothing))

    companion object {
        /** Matches iOS. Higher only for large, deliberately soft shapes. */
        const val DEFAULT_SMOOTHING = 0.6f
    }
}

/** A pill: the reach clamp turns any oversized radius into half the height. */
fun pillShape(): Shape = SquircleShape(9_999.dp)

private const val DEG = (PI / 180.0).toFloat()

fun squirclePath(size: Size, requestedReach: Float, smoothing: Float): Path {
    val path = Path()
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return path

    // Budget: a corner may spend at most half of each side it touches.
    val p = min(requestedReach, min(w, h) / 2f)
    if (p <= 0.01f) {
        path.moveTo(0f, 0f)
        path.lineTo(w, 0f)
        path.lineTo(w, h)
        path.lineTo(0f, h)
        path.close()
        return path
    }

    val s = smoothing.coerceIn(0f, 1f)
    val r = p / (1f + s)
    val arcMeasure = 90f * (1f - s)
    val arcSection = sin(arcMeasure / 2f * DEG) * r * sqrt(2f)
    val alpha = (90f - arcMeasure) / 2f
    val p3p4 = r * tan(alpha / 2f * DEG)
    val beta = 45f * s
    val c = p3p4 * cos(beta * DEG)
    val d = c * tan(beta * DEG)
    val b = (p - arcSection - c - d) / 3f
    val a = 2f * b

    // One construction, four corners. Each is described by where the corner
    // is, the direction we arrive along the edge, and the direction we leave.
    // Clockwise from the top left.
    val corners = listOf(
        Corner(Offset(0f, 0f), Offset(0f, -1f), Offset(1f, 0f)),
        Corner(Offset(w, 0f), Offset(1f, 0f), Offset(0f, 1f)),
        Corner(Offset(w, h), Offset(0f, 1f), Offset(-1f, 0f)),
        Corner(Offset(0f, h), Offset(-1f, 0f), Offset(0f, -1f)),
    )

    corners.forEachIndexed { i, corner ->
        val (v, din, dout) = corner
        val start = v - din * p
        if (i == 0) path.moveTo(start.x, start.y) else path.lineTo(start.x, start.y)

        // Curvature ramps up from nothing.
        val in1 = start + din * a
        val in2 = start + din * (a + b)
        val arcStart = start + din * (a + b + c) + dout * d
        path.cubicTo(in1.x, in1.y, in2.x, in2.y, arcStart.x, arcStart.y)

        // The shortened true arc. At the default smoothing it sweeps 36
        // degrees, which a single cubic matches to well under a pixel.
        val arcEnd = arcStart + din * arcSection + dout * arcSection
        arcAsCubic(path, arcStart, arcEnd, r, arcMeasure, (dout - din).unit())

        // And back down to nothing.
        val out1 = arcEnd + din * d + dout * c
        val out2 = arcEnd + din * d + dout * (b + c)
        val end = arcEnd + din * d + dout * (a + b + c)
        path.cubicTo(out1.x, out1.y, out2.x, out2.y, end.x, end.y)
    }

    path.close()
    return path
}

private data class Corner(val vertex: Offset, val arriving: Offset, val leaving: Offset)

/**
 * Approximate a circular arc with one cubic. The centre sits off the chord's
 * midpoint by r*cos(half the sweep), on the side the shape's interior is on,
 * and the control points ride the tangents at k = 4/3 tan(sweep/4).
 */
private fun arcAsCubic(
    path: Path,
    from: Offset,
    to: Offset,
    r: Float,
    sweepDegrees: Float,
    inward: Offset,
) {
    if (sweepDegrees <= 0.01f) {
        path.lineTo(to.x, to.y)
        return
    }
    val centre = (from + to) / 2f + inward * (r * cos(sweepDegrees / 2f * DEG))
    val travel = to - from
    var t1 = (from - centre).rotated90()
    var t2 = (to - centre).rotated90()
    if (t1.x * travel.x + t1.y * travel.y < 0f) {
        t1 = -t1
        t2 = -t2
    }
    val k = 4f / 3f * tan(sweepDegrees * DEG / 4f)
    val c1 = from + t1 * k
    val c2 = to - t2 * k
    path.cubicTo(c1.x, c1.y, c2.x, c2.y, to.x, to.y)
}

private fun Offset.rotated90(): Offset = Offset(-y, x)

private fun Offset.unit(): Offset {
    val m = getDistance()
    return if (m == 0f) Offset.Zero else this / m
}
