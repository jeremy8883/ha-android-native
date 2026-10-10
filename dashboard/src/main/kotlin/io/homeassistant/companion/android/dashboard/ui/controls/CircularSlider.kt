package io.homeassistant.companion.android.dashboard.ui.controls

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.moreinfo.CircularMode
import io.homeassistant.companion.android.dashboard.moreinfo.CircularSlider
import io.homeassistant.companion.android.dashboard.moreinfo.CircularTarget
import io.homeassistant.companion.android.dashboard.moreinfo.CircularTargets
import io.homeassistant.companion.android.dashboard.ui.theme.toColor
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port of `ha-control-circular-slider` (frontend@20260624.6 src/components/ha-control-circular-slider.ts): a 270°
 * arc, coloured from the start (or to the end) up to the value, or between a low and a high value; a dot shows the
 * current reading. Shows [targets] (the user's, while set); a drag or tap on the arc reports the target it moves
 * to [onChanging] (with `null` when it ends) and [onChanged].
 */
@Composable
internal fun CircularSliderView(
    slider: CircularSlider,
    targets: CircularTargets,
    onChanging: (CircularTarget, Double?) -> Unit,
    onChanged: (CircularTarget, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHAColorScheme.current
    val palette = SliderPalette(
        value = slider.color?.toColor() ?: colors.colorFillDisabledLoudResting,
        low = slider.lowColor?.toColor() ?: colors.colorFillDisabledLoudResting,
        high = slider.highColor?.toColor() ?: colors.colorFillDisabledLoudResting,
        background = colors.colorFillDisabledLoudResting,
        clear = colors.colorSurfaceDefault,
        current = colors.colorTextPrimary,
        action = slider.actionColor?.toColor(),
    )
    val latestTargets by rememberUpdatedState(targets)
    val changing by rememberUpdatedState(onChanging)
    val changed by rememberUpdatedState(onChanged)
    val callbacks = CircularInputCallbacks({ latestTargets }, { t, v -> changing(t, v) }, { t, v -> changed(t, v) })
    Canvas(modifier = modifier.circularInput(slider) { callbacks }) {
        ArcPainter(this, slider, palette).paint(targets)
    }
}

/** The colours of a slider's parts. */
private data class SliderPalette(
    val value: Color,
    val low: Color,
    val high: Color,
    val background: Color,
    val clear: Color,
    val current: Color,
    val action: Color?,
)

/** Draws a slider in the 320 view box, scaled to the canvas. */
private class ArcPainter(val scope: DrawScope, val slider: CircularSlider, val palette: SliderPalette) {
    private val scale = scope.size.minDimension / VIEW_BOX

    fun paint(targets: CircularTargets) {
        palette.action?.let { glow ->
            scope.drawCircle(
                brush = Brush.radialGradient(listOf(glow.copy(alpha = GLOW_ALPHA), Color.Transparent)),
                radius = scope.size.minDimension / 2 * GLOW_EXTENT,
            )
        }
        stroke(0f, MAX_ANGLE, ARC_STROKE, palette.background.copy(alpha = BACKGROUND_ALPHA))
        slider.current?.let { dot(it, CURRENT_STROKE, palette.current.copy(alpha = CURRENT_ALPHA)) }
        if (slider.disabled) return
        if (slider.dual) {
            targets.low?.let { arc(it, CircularMode.Start, palette.low) }
            targets.high?.let { arc(it, CircularMode.End, palette.high) }
        } else if (targets.value != null || slider.mode == CircularMode.Full) {
            arc(targets.value, slider.mode, palette.value)
        }
    }

    /** Port of `renderArc`: the coloured range, the active arc from the current reading, and the handle. */
    private fun arc(value: Double?, mode: CircularMode, color: Color) {
        val limit = if (mode == CircularMode.End) slider.max else slider.min
        val target = value ?: limit
        val current = slider.current ?: limit
        val showActive = showsActive(mode, target, current)
        // Hidden while the entity is off, as upstream fades the arcs out
        if (!slider.inactive) {
            val (from, to) = when (mode) {
                CircularMode.Full -> slider.min to slider.max
                CircularMode.End -> target to limit
                CircularMode.Start -> limit to target
            }
            segment(from, to, palette.clear)
            segment(from, to, color.copy(alpha = COLORED_ALPHA))
            if (value != null) active(mode, target to current, showActive, color)
            slider.current
                ?.takeIf { it in slider.min..slider.max && (showActive || slider.mode == CircularMode.Full) }
                ?.let { dot(it, CURRENT_STROKE, palette.clear) }
        }
        if (value != null) {
            dot(target, ARC_STROKE, color)
            dot(target, HANDLE_STROKE, Color.White)
        }
    }

    /** The full-colour arc between the current reading and the target, or the target's dot. */
    private fun active(mode: CircularMode, targetAndCurrent: Pair<Double, Double>, showActive: Boolean, color: Color) {
        val (target, current) = targetAndCurrent
        when {
            !showActive -> dot(target, ARC_STROKE, color)
            mode == CircularMode.End -> segment(target, current, color)
            else -> segment(current, target, color)
        }
    }

    private fun showsActive(mode: CircularMode, target: Double, current: Double) = when (mode) {
        CircularMode.End -> target <= current
        CircularMode.Start -> current <= target
        CircularMode.Full -> false
    }

    private fun segment(from: Double, to: Double, color: Color) {
        val start = fraction(from)
        val end = fraction(to)
        if (end >= start) stroke(start * MAX_ANGLE, (end - start) * MAX_ANGLE, ARC_STROKE, color)
    }

    /** A round dot of [width] at [value], as upstream draws a zero-length dash with round caps. */
    private fun dot(value: Double, width: Float, color: Color) {
        val angle = Math.toRadians((START_ANGLE + fraction(value) * MAX_ANGLE).toDouble())
        val radius = RADIUS * scale
        val center = scope.center + Offset((cos(angle) * radius).toFloat(), (sin(angle) * radius).toFloat())
        scope.drawCircle(color, radius = width * scale / 2, center = center)
    }

    private fun stroke(from: Float, sweep: Float, width: Float, color: Color) {
        val radius = RADIUS * scale
        scope.drawArc(
            color,
            START_ANGLE + from,
            sweep,
            false,
            scope.center - Offset(radius, radius),
            Size(radius * 2, radius * 2),
            style = Stroke(width * scale, cap = StrokeCap.Round),
        )
    }

    /** Port of `_valueToPercentage`. */
    private fun fraction(value: Double): Float =
        ((value.coerceIn(slider.min, slider.max) - slider.min) / (slider.max - slider.min)).toFloat()
}

private const val START_ANGLE = 135f
private const val ARC_STROKE = 24f
private const val HANDLE_STROKE = 18f
private const val CURRENT_STROKE = 8f
private const val BACKGROUND_ALPHA = 0.3f
private const val COLORED_ALPHA = 0.5f
private const val CURRENT_ALPHA = 0.5f
private const val GLOW_ALPHA = 0.15f
private const val GLOW_EXTENT = 1.2f
