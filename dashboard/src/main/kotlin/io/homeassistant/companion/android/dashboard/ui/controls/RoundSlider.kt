package io.homeassistant.companion.android.dashboard.ui.controls

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Port of `@thomasloven/round-slider` 0.6.0 as the light card uses it: a thin 270° arc open at the bottom, its
 * [track] colour behind a [bar] up to [value] (from [min] to [max]) ending in a round handle, as wide as it's given
 * and as tall as the arc. Dragging on the arc reports each value to [onChanging] and the last to [onChanged].
 */
@Composable
internal fun RoundSlider(
    value: Int,
    min: Int,
    max: Int,
    bar: Color,
    track: Color,
    enabled: Boolean,
    onChanging: (Int) -> Unit,
    onChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragged by remember { mutableStateOf<Int?>(null) }
    val changing by rememberUpdatedState(onChanging)
    val changed by rememberUpdatedState(onChanged)
    val range = min..max
    val input = if (enabled) {
        Modifier.pointerInput(range) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val geometry = ArcGeometry(Size(size.width.toFloat(), size.height.toFloat()), HANDLE.toPx())
                // Only a touch on the arc (or near it) moves the handle
                if (!geometry.nearArc(down.position, TOUCH.toPx())) return@awaitEachGesture
                down.consume()
                var position = down.position
                do {
                    geometry.valueAt(position, range)?.let {
                        dragged = it
                        changing(it)
                    }
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    change.consume()
                    position = change.position
                } while (change.pressed)
                dragged?.let(changed)
                dragged = null
            }
        }
    } else {
        Modifier
    }
    Canvas(modifier.then(input)) {
        val geometry = ArcGeometry(size, HANDLE.toPx())
        val shown = dragged ?: value
        val fraction = ((shown - min).toFloat() / (max - min).coerceAtLeast(1)).coerceIn(0f, 1f)
        drawArc(geometry, START_ANGLE, ARC_LENGTH, track, PATH.toPx())
        drawArc(geometry, START_ANGLE, ARC_LENGTH * fraction, bar, PATH.toPx())
        val handleSize = (if (dragged != null) HANDLE * HANDLE_ZOOM else HANDLE).toPx()
        drawCircle(bar, radius = handleSize, center = geometry.point(START_ANGLE + ARC_LENGTH * fraction))
    }
}

private fun DrawScope.drawArc(geometry: ArcGeometry, start: Float, sweep: Float, color: Color, width: Float) {
    drawArc(
        color = color,
        startAngle = start,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = Offset(geometry.center.x - geometry.radius, geometry.center.y - geometry.radius),
        size = Size(geometry.radius * 2, geometry.radius * 2),
        style = Stroke(width = width, cap = StrokeCap.Round),
    )
}

/** Where the arc sits in a box of [size], kept [margin] in from its sides for the handle. */
private class ArcGeometry(size: Size, margin: Float) {
    // The arc's bounds are a unit circle's from its top to the ends' height, sin(45°) below the centre
    val radius = minOf((size.width - 2 * margin) / 2, (size.height - 2 * margin) / (1 + END_DEPTH))
    val center = Offset(size.width / 2, margin + radius)

    fun point(degrees: Float): Offset {
        val radians = degrees * PI / HALF_TURN
        return Offset(center.x + radius * cos(radians).toFloat(), center.y + radius * sin(radians).toFloat())
    }

    fun nearArc(position: Offset, tolerance: Float): Boolean =
        kotlin.math.abs(hypot(position.x - center.x, position.y - center.y) - radius) <= tolerance &&
            angleOnArc(position) != null

    /** The value at [position]'s angle, `null` in the gap at the bottom. */
    fun valueAt(position: Offset, range: IntRange): Int? {
        val along = angleOnArc(position) ?: return null
        return (range.first + along / ARC_LENGTH * (range.last - range.first)).roundToInt()
    }

    /** How far along the arc [position]'s angle is, in degrees from its start; `null` in the gap. */
    private fun angleOnArc(position: Offset): Float? {
        val degrees = (atan2(position.y - center.y, position.x - center.x) * HALF_TURN / PI).toFloat()
        val along = ((degrees - START_ANGLE) % FULL_TURN + FULL_TURN) % FULL_TURN
        return along.takeIf { it <= ARC_LENGTH }
    }
}

private const val START_ANGLE = 135f
private const val ARC_LENGTH = 270f
private const val HALF_TURN = 180.0
private const val FULL_TURN = 360f
private val END_DEPTH = sqrt(2f) / 2
private const val HANDLE_ZOOM = 1.5f
private val HANDLE = 6.dp
private val PATH = 3.dp
private val TOUCH = 24.dp
