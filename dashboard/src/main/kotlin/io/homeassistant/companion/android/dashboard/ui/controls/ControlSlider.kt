package io.homeassistant.companion.android.dashboard.ui.controls

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.moreinfo.SliderMode
import kotlin.math.roundToInt

/**
 * Port of `ha-control-slider` (frontend@20260624.6 src/components/ha-control-slider.ts): a thick rounded bar,
 * filled from the start (the bottom, when [vertical]) up to the value, or a cursor at the value. Dragging and
 * tapping set it at the finger; [onMoved] follows a drag (with `null` when it ends) and [onChanged] gets the value
 * it settles on. It shows the value it was set to until [value] changes.
 */
@Composable
internal fun ControlSlider(
    value: Double?,
    range: ClosedFloatingPointRange<Double>,
    step: Double,
    label: String,
    valueText: (Double) -> String,
    style: ControlSliderStyle,
    onChanged: (Double) -> Unit,
    modifier: Modifier = Modifier,
    onMoved: (Double?) -> Unit = {},
    vertical: Boolean = false,
    inverted: Boolean = false,
    mode: SliderMode = SliderMode.Start,
    showHandle: Boolean = false,
    enabled: Boolean = true,
) {
    var dragging by remember { mutableStateOf(false) }
    // What the user set, until the server reports a new value
    var local by remember(value) { mutableStateOf<Double?>(null) }
    val shown = local ?: value
    val fraction = shown?.let { ((it - range.start) / (range.endInclusive - range.start)).toFloat().coerceIn(0f, 1f) }
    val animated by animateFloatAsState(
        targetValue = fraction ?: 0f,
        animationSpec = if (dragging) snap() else tween(TRANSITION_MS),
        label = "slider",
    )
    val stepped = { v: Double -> steppedValue(v, range, step) }
    val changed by rememberUpdatedState(onChanged)
    val moved by rememberUpdatedState(onMoved)
    val set: (Double) -> Unit = { v ->
        stepped(v).also { local = it }.let(changed)
    }
    // The gestures outlive this composition: they call the latest callbacks
    val input by rememberUpdatedState(
        SliderCallbacks(
            onMove = { v ->
                dragging = true
                local = v
                moved(stepped(v))
            },
            onEnd = {
                dragging = false
                moved(null)
                local?.let(set)
            },
            onTap = set,
        ),
    )
    Box(modifier = modifier.sliderSemantics(label, shown?.let(stepped), valueText, range, set.takeIf { enabled })) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(style.cornerRadius))
                .sliderInput(enabled, vertical, inverted, range) { input },
        ) {
            val cursorLine = LocalHAColorScheme.current.colorTextSecondary
            Canvas(Modifier.fillMaxSize()) {
                drawRect(style.background, alpha = style.backgroundAlpha)
                val visual = if (inverted) 1 - animated else animated
                when (mode) {
                    SliderMode.Start -> drawBar(visual, vertical, showHandle, style, Color.White)
                    SliderMode.Cursor -> if (fraction != null) drawCursor(visual, vertical, style, cursorLine)
                }
            }
        }
        if (dragging && shown != null) {
            Tooltip(valueText(stepped(shown)), if (inverted) 1 - animated else animated, vertical, style)
        }
    }
}

/**
 * What accessibility services read and set: [label], the [shown] value as [valueText], and [onSet] to change it
 * (`null` while disabled).
 */
private fun Modifier.sliderSemantics(
    label: String,
    shown: Double?,
    valueText: (Double) -> String,
    range: ClosedFloatingPointRange<Double>,
    onSet: ((Double) -> Unit)?,
) = semantics {
    contentDescription = label
    shown?.let {
        stateDescription = valueText(it)
        progressBarRangeInfo = ProgressBarRangeInfo(it.toFloat(), range.start.toFloat()..range.endInclusive.toFloat())
    }
    if (onSet != null) {
        setProgress { target ->
            onSet(target.toDouble())
            true
        }
    } else {
        disabled()
    }
}

/** What the slider's gestures report: the value under the finger. */
private data class SliderCallbacks(val onMove: (Double) -> Unit, val onEnd: () -> Unit, val onTap: (Double) -> Unit)

/**
 * Drags along the slider's axis and taps, reported to the latest [callbacks] as the value under the finger:
 * `onMove` while dragging, then `onEnd`, and `onTap` for a tap. A drag along the axis is the slider's, not the page's.
 */
private fun Modifier.sliderInput(
    enabled: Boolean,
    vertical: Boolean,
    inverted: Boolean,
    range: ClosedFloatingPointRange<Double>,
    callbacks: () -> SliderCallbacks,
): Modifier {
    if (!enabled) return this
    return pointerInput(vertical, inverted, range) {
        val toValue = { position: Offset -> valueAt(position, size, vertical, inverted, range) }
        val start: (Offset) -> Unit = { callbacks().onMove(toValue(it)) }
        val end: () -> Unit = { callbacks().onEnd() }
        val drag = { change: PointerInputChange ->
            change.consume()
            callbacks().onMove(toValue(change.position))
        }
        if (vertical) {
            detectVerticalDragGestures(onDragStart = start, onDragEnd = end, onDragCancel = end) { c, _ -> drag(c) }
        } else {
            detectHorizontalDragGestures(onDragStart = start, onDragEnd = end, onDragCancel = end) { c, _ -> drag(c) }
        }
    }.pointerInput(vertical, inverted, range) {
        detectTapGestures { callbacks().onTap(valueAt(it, size, vertical, inverted, range)) }
    }
}

/** The value at [position]: from the start (the bottom, when [vertical]), or from the end when [inverted]. */
private fun valueAt(
    position: Offset,
    size: IntSize,
    vertical: Boolean,
    inverted: Boolean,
    range: ClosedFloatingPointRange<Double>,
): Double {
    val raw = if (vertical) 1 - position.y / size.height else position.x / size.width
    val visual = raw.coerceIn(0f, 1f)
    return range.start + (if (inverted) 1 - visual else visual) * (range.endInclusive - range.start)
}

/** The bar from the start to [fraction] of the length, ending in a handle when [showHandle]. */
private fun DrawScope.drawBar(
    fraction: Float,
    vertical: Boolean,
    showHandle: Boolean,
    style: ControlSliderStyle,
    handleColor: Color,
) {
    val length = if (vertical) size.height else size.width
    val thickness = if (vertical) size.width else size.height
    val handleSize = HANDLE_SIZE.toPx()
    val handleMargin = thickness / HANDLE_MARGIN_DIVISOR
    val handleSpacing = if (showHandle) 2 * handleMargin + handleSize else 0f
    val end = fraction * (length - handleSpacing) + handleSpacing
    if (end <= 0f) return
    val radius = minOf(style.cornerRadius, BAR_RADIUS).toPx()
    // The bar is the slider's full length, moved back so that only its end shows
    if (vertical) {
        drawRoundRect(style.color, Offset(0f, length - end), Size(thickness, length), CornerRadius(radius))
    } else {
        drawRoundRect(style.color, Offset(end - length, 0f), Size(length, thickness), CornerRadius(radius))
    }
    if (!showHandle) return
    val handleLength = thickness / 2
    val handleOffset = end - handleMargin - handleSize
    val topLeft = if (vertical) {
        Offset((thickness - handleLength) / 2, length - end + handleMargin)
    } else {
        Offset(handleOffset, (thickness - handleLength) / 2)
    }
    val handle = if (vertical) Size(handleLength, handleSize) else Size(handleSize, handleLength)
    drawRoundRect(handleColor, topLeft, handle, CornerRadius(handleSize))
}

/** A white block at [fraction] of the length, with a line across it. */
private fun DrawScope.drawCursor(fraction: Float, vertical: Boolean, style: ControlSliderStyle, line: Color) {
    val length = if (vertical) size.height else size.width
    val thickness = if (vertical) size.width else size.height
    val cursor = thickness / CURSOR_DIVISOR
    val position = fraction * (length - cursor)
    val radius = CornerRadius(minOf(HANDLE_SIZE, style.cornerRadius).toPx())
    val handleSize = HANDLE_SIZE.toPx()
    if (vertical) {
        val top = length - position - cursor
        drawRoundRect(SHADOW, Offset(0f, top + SHADOW_OFFSET.toPx()), Size(thickness, cursor), radius)
        drawRoundRect(Color.White, Offset(0f, top), Size(thickness, cursor), radius)
        drawRoundRect(
            line,
            Offset(thickness / CURSOR_DIVISOR, top + (cursor - handleSize) / 2),
            Size(thickness / 2, handleSize),
            CornerRadius(handleSize),
        )
    } else {
        drawRoundRect(SHADOW, Offset(position, SHADOW_OFFSET.toPx()), Size(cursor, thickness), radius)
        drawRoundRect(Color.White, Offset(position, 0f), Size(cursor, thickness), radius)
        drawRoundRect(
            line,
            Offset(position + (cursor - handleSize) / 2, thickness / CURSOR_DIVISOR),
            Size(handleSize, thickness / 2),
            CornerRadius(handleSize),
        )
    }
}

/** The value while dragging: above a horizontal slider, left of a vertical one, following the value. */
@Composable
private fun Tooltip(text: String, fraction: Float, vertical: Boolean, style: ControlSliderStyle) {
    val colors = LocalHAColorScheme.current
    Text(
        text = text,
        style = HATextStyle.Body.copy(fontSize = style.tooltipFontSize),
        color = colors.colorTextPrimary,
        modifier = Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(0, 0) {
                    val margin = TOOLTIP_MARGIN.roundToPx()
                    val offset = if (vertical) {
                        IntOffset(
                            -placeable.width - margin,
                            ((1 - fraction) * constraints.maxHeight - placeable.height / 2f).roundToInt(),
                        )
                    } else {
                        IntOffset(
                            (fraction * constraints.maxWidth - placeable.width / 2f).roundToInt(),
                            -placeable.height - margin,
                        )
                    }
                    placeable.place(offset)
                }
            }
            .shadow(TOOLTIP_ELEVATION, RoundedCornerShape(TOOLTIP_RADIUS))
            .background(colors.colorSurfaceDefault, RoundedCornerShape(TOOLTIP_RADIUS))
            .padding(horizontal = HADimens.SPACE2, vertical = HADimens.SPACE1),
    )
}

/** Port of `steppedValue`: [value] on the nearest step, within [range]. */
internal fun steppedValue(value: Double, range: ClosedFloatingPointRange<Double>, step: Double): Double =
    (Math.round(value / step) * step).coerceIn(range)

private const val TRANSITION_MS = 180
private const val HANDLE_MARGIN_DIVISOR = 8
private const val CURSOR_DIVISOR = 4
private val HANDLE_SIZE = HADimens.SPACE1
private val BAR_RADIUS = HARadius.M
private val SHADOW = Color.Black.copy(alpha = 0.2f)
private val SHADOW_OFFSET = 2.dp
private val TOOLTIP_MARGIN = HADimens.SPACE1
private val TOOLTIP_RADIUS = HARadius.L
private val TOOLTIP_ELEVATION = 2.dp
