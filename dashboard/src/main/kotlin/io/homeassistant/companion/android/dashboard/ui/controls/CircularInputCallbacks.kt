package io.homeassistant.companion.android.dashboard.ui.controls

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.IntSize
import io.homeassistant.companion.android.dashboard.moreinfo.CircularSlider
import io.homeassistant.companion.android.dashboard.moreinfo.CircularTarget
import io.homeassistant.companion.android.dashboard.moreinfo.CircularTargets
import kotlin.math.atan2
import kotlin.math.hypot

/** What a circular slider's gestures report: the target moved and its value, `null` when the gesture ends. */
internal class CircularInputCallbacks(
    val targets: () -> CircularTargets,
    val onChanging: (CircularTarget, Double?) -> Unit,
    val onChanged: (CircularTarget, Double) -> Unit,
)

/**
 * Drags and taps on [slider]'s arc (ports of `ha-control-circular-slider`'s pan and tap handlers): each moves the
 * nearer target to the finger, reported to the latest [callbacks] as it moves and where it ends. Touches off the
 * arc are left to the page, so the middle stays free and the page scrolls.
 */
internal fun Modifier.circularInput(slider: CircularSlider, callbacks: () -> CircularInputCallbacks): Modifier =
    if (slider.disabled || slider.readonly) {
        this
    } else {
        pointerInput(slider) {
            awaitEachGesture {
                val down = awaitFirstDown()
                if (!onTrack(down.position, size)) return@awaitEachGesture
                down.consume()
                val target = activeTarget(slider, callbacks().targets(), valueAt(down.position, size, slider))
                var last = bounded(slider, callbacks().targets(), target, valueAt(down.position, size, slider))
                callbacks().onChanging(target, stepped(last, slider))
                var change: PointerInputChange? = down
                while (change?.pressed == true) {
                    change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                    change?.takeIf { it.pressed }?.let {
                        if (it.positionChange() != Offset.Zero) it.consume()
                        last = bounded(slider, callbacks().targets(), target, valueAt(it.position, size, slider))
                        callbacks().onChanging(target, stepped(last, slider))
                    }
                }
                callbacks().onChanging(target, null)
                callbacks().onChanged(target, stepped(last, slider))
            }
        }
    }

/** Whether [position] is on the arc's track, within upstream's interaction margin. */
private fun onTrack(position: Offset, size: IntSize): Boolean {
    val scale = minOf(size.width, size.height) / VIEW_BOX
    val distance = hypot(position.x - size.width / 2f, position.y - size.height / 2f) / scale
    return distance in (RADIUS - TOUCH_HALF_WIDTH)..(RADIUS + TOUCH_HALF_WIDTH)
}

/** Port of `_getPercentageFromEvent` and `_percentageToValue`: the value at [position] on the arc. */
private fun valueAt(position: Offset, size: IntSize, slider: CircularSlider): Double {
    val x = 2 * (position.x - size.width / 2f) / size.width
    val y = 2 * (position.y - size.height / 2f) / size.height
    val degrees = Math.toDegrees(atan2(y.toDouble(), x.toDouble()))
    val offset = (FULL_TURN - MAX_ANGLE) / 2
    val angle = ((degrees + offset - ROTATE_ANGLE + FULL_TURN) % FULL_TURN) - offset
    val percentage = (angle / MAX_ANGLE).coerceIn(0.0, 1.0)
    return (slider.max - slider.min) * percentage + slider.min
}

/** Port of `_findActiveSlider`: the single target, or the nearer end of a range. */
private fun activeTarget(slider: CircularSlider, targets: CircularTargets, value: Double): CircularTarget {
    if (!slider.dual) return CircularTarget.Value
    val low = maxOf(targets.low ?: slider.min, slider.min)
    val high = minOf(targets.high ?: slider.max, slider.max)
    return when {
        low >= value -> CircularTarget.Low
        high <= value -> CircularTarget.High
        Math.abs(value - low) <= Math.abs(value - high) -> CircularTarget.Low
        else -> CircularTarget.High
    }
}

/** Port of `_boundedValue`: within the range, and not past the other end of a pair. */
private fun bounded(slider: CircularSlider, targets: CircularTargets, target: CircularTarget, value: Double): Double {
    val min = if (target == CircularTarget.High) targets.low ?: slider.max else slider.min
    val max = if (target == CircularTarget.Low) targets.high ?: slider.min else slider.max
    return minOf(maxOf(value, min), max)
}

/** Port of `_steppedValue`. */
private fun stepped(value: Double, slider: CircularSlider): Double = Math.round(value / slider.step) * slider.step

/** The view box upstream draws the slider in, and its geometry in it. */
internal const val VIEW_BOX = 320f
internal const val RADIUS = 145f
internal const val MAX_ANGLE = 270f
private const val ROTATE_ANGLE = 135.0
private const val FULL_TURN = 360.0
private const val TOUCH_HALF_WIDTH = 24f
