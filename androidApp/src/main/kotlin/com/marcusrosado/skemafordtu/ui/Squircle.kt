package com.marcusrosado.skemafordtu.ui

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
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
 * field goes through it, Material's parts included through the theme's shape
 * tokens, and nothing uses RoundedCornerShape.
 *
 * A plain rounded rectangle is G1: the straight edge meets the arc with the
 * curvature jumping from 0 to 1/r in one step, and the eye reads that step as
 * a pinch. This is G2, the Figma and Apple construction: shorten the arc,
 * then spend the leftover corner on a cubic each side that ramps curvature up
 * to the arc and back down again.
 *
 * The radius passed in is the corner's REACH along the edge, not the arc
 * radius, so a squircle and a plain rounded rect asking for 16 are the same
 * size and can actually be compared. Reach gets clamped to what the sides
 * leave it, which keeps smoothing fixed: the usual trick of degrading
 * smoothing when a corner will not fit hands back a plain arc for exactly the
 * shapes that want a squircle most, a pill being the obvious one.
 *
 * Each corner can have its own reach, for the shapes that are rounded on one
 * side only: a drawer sheet against the screen edge, the ends of a segmented
 * row. Corners are named for their place, not for reading direction.
 */
class SquircleShape(
    topStart: CornerSize,
    topEnd: CornerSize,
    bottomEnd: CornerSize,
    bottomStart: CornerSize,
    private val smoothing: Float = DEFAULT_SMOOTHING,
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {

    constructor(radius: Dp, smoothing: Float = DEFAULT_SMOOTHING) :
        this(CornerSize(radius), CornerSize(radius), CornerSize(radius), CornerSize(radius), smoothing)

    constructor(
        topStart: Dp,
        topEnd: Dp,
        bottomEnd: Dp,
        bottomStart: Dp,
        smoothing: Float = DEFAULT_SMOOTHING,
    ) : this(CornerSize(topStart), CornerSize(topEnd), CornerSize(bottomEnd), CornerSize(bottomStart), smoothing)

    // Material resolves the four corner sizes to pixels, scales a pair down
    // when it would not fit the shorter side, and hands them over in reading
    // order; the path wants them by place, so a right to left layout swaps
    // the sides here the way RoundedCornerShape does.
    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection,
    ): Outline {
        val ltr = layoutDirection == LayoutDirection.Ltr
        val reaches = floatArrayOf(
            if (ltr) topStart else topEnd,
            if (ltr) topEnd else topStart,
            if (ltr) bottomEnd else bottomStart,
            if (ltr) bottomStart else bottomEnd,
        )
        return Outline.Generic(squirclePath(size, reaches, smoothing))
    }

    // What lets Material's parts derive their own variants, a drawer sheet
    // squared on one side or a segment squared on both, and still get
    // squircles for whatever is left rounded.
    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize,
    ): CornerBasedShape = SquircleShape(topStart, topEnd, bottomEnd, bottomStart, smoothing)

    companion object {
        /** Matches iOS. Higher only for large, deliberately soft shapes. */
        const val DEFAULT_SMOOTHING = 0.6f

        /** Rounded on the leading side only: the first of a row of segments. */
        fun start(radius: Dp): SquircleShape = SquircleShape(radius, 0.dp, 0.dp, radius)

        /** Rounded on the trailing side only: a drawer sheet, the last segment. */
        fun end(radius: Dp): SquircleShape = SquircleShape(0.dp, radius, radius, 0.dp)
    }
}

/** A pill: the reach clamp turns any oversized radius into half the height. */
fun pillShape(): Shape = SquircleShape(9_999.dp)

private const val DEG = (PI / 180.0).toFloat()

fun squirclePath(size: Size, requestedReach: Float, smoothing: Float): Path =
    squirclePath(size, FloatArray(4) { requestedReach }, smoothing)

/**
 * The path, from four reaches in the order top start, top end, bottom end,
 * bottom start.
 *
 * Budget: along each side the two corners share the length, and where they
 * ask for more than there is both are scaled down together, so a pill's two
 * ends meet in the middle and a lone rounded corner may take the whole side.
 */
fun squirclePath(size: Size, reaches: FloatArray, smoothing: Float): Path {
    val path = Path()
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return path

    // Corner i touches side i (leaving) and side i-1 (arriving): sides run
    // top, end, bottom, start, and each is shared by the two corners at
    // its ends.
    val sideLength = floatArrayOf(w, h, w, h)
    val scale = FloatArray(4) { 1f }
    for (side in 0 until 4) {
        val a = side
        val b = (side + 1) % 4
        val asked = reaches[a] + reaches[b]
        if (asked > sideLength[side]) {
            val s = sideLength[side] / asked
            scale[a] = min(scale[a], s)
            scale[b] = min(scale[b], s)
        }
    }
    val p = FloatArray(4) { (reaches[it] * scale[it]).coerceAtLeast(0f) }
    val s = smoothing.coerceIn(0f, 1f)

    // One construction, four corners. Each is described by where the corner
    // is, the direction we arrive along the edge, and the direction we leave.
    // Clockwise from the top start.
    val corners = listOf(
        Corner(Offset(0f, 0f), Offset(0f, -1f), Offset(1f, 0f)),
        Corner(Offset(w, 0f), Offset(1f, 0f), Offset(0f, 1f)),
        Corner(Offset(w, h), Offset(0f, 1f), Offset(-1f, 0f)),
        Corner(Offset(0f, h), Offset(-1f, 0f), Offset(0f, -1f)),
    )

    corners.forEachIndexed { i, corner ->
        val (v, din, dout) = corner
        val reach = p[i]

        // A true corner: the edge simply turns.
        if (reach <= 0.01f) {
            if (i == 0) path.moveTo(v.x, v.y) else path.lineTo(v.x, v.y)
            return@forEachIndexed
        }

        val r = reach / (1f + s)
        val arcMeasure = 90f * (1f - s)
        val arcSection = sin(arcMeasure / 2f * DEG) * r * sqrt(2f)
        val alpha = (90f - arcMeasure) / 2f
        val p3p4 = r * tan(alpha / 2f * DEG)
        val beta = 45f * s
        val c = p3p4 * cos(beta * DEG)
        val d = c * tan(beta * DEG)
        val b = (reach - arcSection - c - d) / 3f
        val a = 2f * b

        val start = v - din * reach
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
