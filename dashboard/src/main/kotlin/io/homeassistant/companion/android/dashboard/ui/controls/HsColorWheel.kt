package io.homeassistant.companion.android.dashboard.ui.controls

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.dashboard.color.hsv2rgb
import io.homeassistant.companion.android.dashboard.color.rgbw2rgb
import io.homeassistant.companion.android.dashboard.color.rgbww2rgb
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Port of `ha-hs-color-picker` (frontend@20260624.6 src/components/ha-hs-color-picker.ts): a wheel of hues,
 * white at the centre, tinted by the light's [channels]. The marker shows the colour at [hue] and [saturation]
 * (hidden while unknown); dragging moves it, reported to [onMoved] (with `null` when it ends), and [onChanged]
 * gets where it settles, the hue in whole degrees and the saturation to two decimals.
 */
@Composable
internal fun HsColorWheel(
    hue: Double?,
    saturation: Double?,
    channels: WheelChannels,
    label: String,
    enabled: Boolean,
    onChanged: (hue: Double, saturation: Double) -> Unit,
    onMoved: (Pair<Double, Double>?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf(false) }
    // Where the user put it, until the server reports a new colour
    var local by remember(hue, saturation) { mutableStateOf<Pair<Double, Double>?>(null) }
    val shown = local ?: hue?.let { h -> saturation?.let { h to it } }
    val changed by rememberUpdatedState(onChanged)
    val moved by rememberUpdatedState(onMoved)
    // The gestures outlive this composition: they call the latest callbacks
    val input by rememberUpdatedState(
        WheelCallbacks(
            onMove = {
                pressed = true
                local = it
                moved(it)
            },
            onEnd = { settled ->
                pressed = false
                moved(null)
                if (settled) local?.let { (h, s) -> changed(h, s) }
            },
            onTap = { (h, s) ->
                local = h to s
                changed(h, s)
            },
        ),
    )
    Box(
        modifier = modifier
            .size(STATE_CONTROL_HEIGHT)
            .aspectRatio(1f)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .semantics {
                contentDescription = label
                shown?.let { (h, s) -> stateDescription = "${h.roundToInt()}°, ${(s * PERCENT).roundToInt()}%" }
            },
    ) {
        Canvas(Modifier.fillMaxSize().clip(CircleShape).wheel(channels).wheelInput(enabled) { input }) {}
        if (shown != null) Marker(shown, channels, pressed)
    }
}

/** The marker at [value], in its colour, grown and lifted above the finger while [pressed]. */
@Composable
private fun Marker(value: Pair<Double, Double>, channels: WheelChannels, pressed: Boolean) {
    val (hue, saturation) = value
    val marker by animateOffsetAsState(
        targetValue = polar(min(saturation, 1.0), Math.toRadians(hue)),
        animationSpec = if (pressed) snap() else tween(MARKER_MOVE_MS),
        label = "marker",
    )
    val scale by animateFloatAsState(if (pressed) PRESSED_SCALE else 1f, tween(MARKER_SCALE_MS), label = "scale")
    val color = markerColor(hue, saturation, channels)
    Canvas(Modifier.fillMaxSize()) {
        val radius = size.minDimension / 2
        val lift = if (pressed) size.minDimension / MARKER_LIFT_DIVISOR else 0f
        val center = Offset(radius + marker.x * radius, radius + marker.y * radius - lift)
        val markerRadius = size.minDimension * MARKER_FRACTION * scale
        drawCircle(SHADOW, markerRadius + SHADOW_SPREAD.toPx(), center + Offset(0f, SHADOW_DROP.toPx()))
        drawCircle(color, markerRadius, center)
        drawCircle(Color.White, markerRadius, center, style = Stroke(MARKER_STROKE.toPx()))
    }
}

/** What the wheel's gestures report: the hue and saturation under the finger. */
private data class WheelCallbacks(
    val onMove: (Pair<Double, Double>) -> Unit,
    val onEnd: (settled: Boolean) -> Unit,
    val onTap: (Pair<Double, Double>) -> Unit,
)

/** Drags and taps on the wheel, reported to the latest [callbacks]; a drag is the wheel's, not the page's. */
private fun Modifier.wheelInput(enabled: Boolean, callbacks: () -> WheelCallbacks): Modifier {
    if (!enabled) return this
    return pointerInput(Unit) {
        detectDragGestures(
            onDragStart = { callbacks().onMove(valueAt(it, size)) },
            onDragEnd = { callbacks().onEnd(true) },
            onDragCancel = { callbacks().onEnd(false) },
        ) { change, _ ->
            change.consume()
            callbacks().onMove(valueAt(change.position, size))
        }
    }.pointerInput(Unit) { detectTapGestures { callbacks().onTap(valueAt(it, size)) } }
}

/** The hue (whole degrees) and saturation (0–1, two decimals) at [offset], port of `_getValueFromCoord`. */
private fun valueAt(offset: Offset, size: IntSize): Pair<Double, Double> {
    val x = 2 * offset.x / size.width - 1
    val y = 2 * offset.y / size.height - 1
    val degrees = Math.toDegrees(atan2(y.toDouble(), x.toDouble())).roundToInt() % FULL_TURN
    val hue = ((degrees + FULL_TURN) % FULL_TURN).toDouble()
    val saturation = Math.round(min(hypot(x.toDouble(), y.toDouble()), 1.0) * PERCENT) / PERCENT
    return hue to saturation
}

private fun polar(r: Double, phi: Double) = Offset((cos(phi) * r).toFloat(), (sin(phi) * r).toFloat())

/** Port of `drawColorWheel`: one wedge per degree, each a radial gradient from white to the full hue. */
private fun Modifier.wheel(channels: WheelChannels) = drawWithCache {
    val radius = size.minDimension / 2
    val center = Offset(radius, radius)
    val bounds = Rect(center, radius)
    val brightness = channels.colorBrightness ?: RGB_MAX
    val wedges = (0 until FULL_TURN).map { angle ->
        val path = Path().apply {
            moveTo(center.x, center.y)
            arcTo(bounds, angle - HALF_DEGREE, WEDGE_DEGREES, forceMoveTo = false)
            close()
        }
        val start = wheelColor(doubleArrayOf(angle.toDouble(), 0.0, brightness), channels)
        val end = wheelColor(doubleArrayOf(angle.toDouble(), 1.0, brightness), channels)
        path to Brush.radialGradient(listOf(start, end), center, radius)
    }
    onDrawBehind { wedges.forEach { (path, brush) -> drawPath(path, brush) } }
}

/** The marker's colour: the wheel's at the value. */
private fun markerColor(hue: Double, saturation: Double, channels: WheelChannels): Color =
    wheelColor(doubleArrayOf(hue, saturation, channels.colorBrightness ?: RGB_MAX), channels)

/** Port of `adjustRgb`: the colour shown for [hsv] once the light's whites are added. */
private fun wheelColor(hsv: DoubleArray, channels: WheelChannels): Color {
    val rgb = hsv2rgb(hsv)
    val shown = when {
        channels.white != null -> rgbw2rgb(rgb + channels.white)
        channels.coldWhite != null && channels.warmWhite != null ->
            rgbww2rgb(rgb + channels.coldWhite + channels.warmWhite, channels.minKelvin, channels.maxKelvin)
        else -> rgb
    }
    return Color(
        red = (shown[0] / RGB_MAX).toFloat().coerceIn(0f, 1f),
        green = (shown[1] / RGB_MAX).toFloat().coerceIn(0f, 1f),
        blue = (shown[2] / RGB_MAX).toFloat().coerceIn(0f, 1f),
    )
}

private const val FULL_TURN = 360
private const val HALF_DEGREE = 0.5f
private const val WEDGE_DEGREES = 2f
private const val RGB_MAX = 255.0
private const val PERCENT = 100.0
private const val DISABLED_ALPHA = 0.5f
private const val PRESSED_SCALE = 2.5f
private const val MARKER_MOVE_MS = 200
private const val MARKER_SCALE_MS = 100
private const val MARKER_LIFT_DIVISOR = 16
private const val MARKER_FRACTION = 16f / 400f
private val MARKER_STROKE = 2.dp
private val SHADOW = Color.Black.copy(alpha = 0.3f)
private val SHADOW_SPREAD = 1.dp
private val SHADOW_DROP = 1.dp
