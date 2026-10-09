package io.homeassistant.companion.android.dashboard.ui.charts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path

/** A path through [points], smoothed like ECharts' lines with `smooth: 0.4`. */
internal fun smoothPath(points: List<Offset>): Path = Path().also { appendSmooth(it, points, moveFirst = true) }

/**
 * Appends [points] to [path] as ECharts' `drawSegment` does: Bézier curves whose control points follow the
 * neighbours, kept within each segment so they don't overshoot; points closer than about a pixel are skipped.
 */
internal fun appendSmooth(path: Path, points: List<Offset>, moveFirst: Boolean) {
    var previous: Offset? = null
    var control0 = Offset.Zero
    points.forEachIndexed { index, point ->
        val prev = previous
        if (prev == null) {
            if (moveFirst) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            control0 = point
            previous = point
            return@forEachIndexed
        }
        val d = point - prev
        if (d.x * d.x + d.y * d.y < MIN_SEGMENT) return@forEachIndexed
        val (control1, next) = controlPoints(prev, point, nextDistinct(points, index))
        path.cubicTo(control0.x, control0.y, control1.x, control1.y, point.x, point.y)
        control0 = next
        previous = point
    }
}

/**
 * The control point before [point] and the one after it, from its neighbours. Port of the default (not monotone)
 * branch of `drawSegment` (echarts src/chart/line/poly.ts).
 */
private fun controlPoints(prev: Offset, point: Offset, next: Offset?): Pair<Offset, Offset> {
    if (next == null) return point to point
    val lenPrev = (point - prev).getDistance()
    val lenNext = (next - point).getDistance()
    val ratio = lenNext / (lenNext + lenPrev)
    val v = next - prev
    var after = point + v * (SMOOTH * ratio)
    after = Offset(after.x.within(next.x, point.x), after.y.within(next.y, point.y))
    var before = point - (after - point) * (lenPrev / lenNext)
    before = Offset(before.x.within(prev.x, point.x), before.y.within(prev.y, point.y))
    after = point + (point - before) * (lenNext / lenPrev)
    return before to after
}

/** The first point after [index] that isn't the same point, as ECharts ignores duplicate points. */
private fun nextDistinct(points: List<Offset>, index: Int): Offset? =
    (index + 1 until points.size).firstNotNullOfOrNull { i -> points[i].takeIf { it != points[index] } }

/** [this] kept between [a] and [b]. */
private fun Float.within(a: Float, b: Float): Float = coerceIn(minOf(a, b), maxOf(a, b))

private const val SMOOTH = 0.4f
private const val MIN_SEGMENT = 0.5f
