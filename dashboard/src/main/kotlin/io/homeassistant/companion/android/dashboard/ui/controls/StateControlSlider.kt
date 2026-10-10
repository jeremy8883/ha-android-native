package io.homeassistant.companion.android.dashboard.ui.controls

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.theme.HAFontSize
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.moreinfo.ControlSlider
import io.homeassistant.companion.android.dashboard.moreinfo.SliderBackground
import io.homeassistant.companion.android.dashboard.ui.theme.parseCssColor
import io.homeassistant.companion.android.dashboard.ui.theme.toColor
import kotlinx.coroutines.delay

/**
 * A [ControlSlider] as the details draw it: tall, in the middle (`ha-state-control-*` styles). With
 * [whileMoving], the value is also sent at most every half second while dragging, as the colour pickers do.
 */
@Composable
internal fun StateControlSlider(
    slider: ControlSlider,
    valueText: (Double) -> String,
    onAction: (CardAction) -> Unit,
    modifier: Modifier = Modifier,
    whileMoving: Boolean = false,
) {
    val colors = LocalHAColorScheme.current
    val color = slider.color?.toColor() ?: colors.colorFillPrimaryLoudResting
    val background = when (val track = slider.background) {
        is SliderBackground.Tint -> SolidColor(track.color?.toColor() ?: colors.colorFillDisabledLoudResting) to
            track.opacity
        is SliderBackground.Stripes -> SolidColor(track.color?.toColor() ?: colors.colorFillDisabledLoudResting) to
            track.opacity
        // The stops are evenly spaced, as upstream generates them
        is SliderBackground.Gradient -> Brush.verticalGradient(
            track.stops.map { (_, hex) -> parseCssColor(hex) ?: Color.Unspecified },
        ) to 1f
    }
    val overlay = (slider.background as? SliderBackground.Stripes)?.let { tiltStripes(color) }
    var moving by remember { mutableStateOf<Double?>(null) }
    val send by rememberUpdatedState { value: Double -> onAction(slider.service.withValue(value)) }
    if (whileMoving) {
        LaunchedEffect(moving != null) {
            var last: Double? = null
            while (moving != null) {
                moving?.takeIf { it != last }?.let {
                    last = it
                    send(it)
                }
                delay(THROTTLE_MS)
            }
        }
    }
    ControlSlider(
        value = slider.value,
        range = slider.min..slider.max,
        step = slider.step,
        label = slider.label,
        valueText = valueText,
        style = ControlSliderStyle(
            thickness = STATE_CONTROL_THICKNESS,
            cornerRadius = STATE_CONTROL_RADIUS,
            color = color,
            background = background.first,
            backgroundAlpha = background.second,
            tooltipFontSize = HAFontSize.XL,
            overlay = overlay,
            overlayAlpha = STRIPES_ALPHA,
        ),
        onChanged = send,
        onMoved = { moving = it },
        modifier = modifier.size(STATE_CONTROL_THICKNESS, STATE_CONTROL_HEIGHT),
        vertical = true,
        inverted = slider.inverted,
        mode = slider.mode,
        showHandle = slider.showHandle,
        enabled = slider.enabled,
    )
}

/**
 * Port of `generateTiltSliderTrackBackgroundGradient`: 24 stripes of [color] from the top, each wider than the
 * one before.
 */
private fun tiltStripes(color: Color): Brush {
    val stops = (0 until STRIPES).flatMap { i ->
        val start = i.toFloat() / STRIPES
        val end = start + i.toFloat() / (STRIPES * STRIPES) * (1 - MIN_STRIPE) + MIN_STRIPE / STRIPES
        listOf(start to Color.Transparent, start to color, end to color, end to Color.Transparent)
    }
    return object : ShaderBrush() {
        override fun createShader(size: Size): Shader = LinearGradientShader(
            from = Offset.Zero,
            to = Offset(0f, size.height),
            colors = stops.map { it.second },
            colorStops = stops.map { it.first },
        )
    }
}

/** The width of the details' tall controls (`--control-slider-thickness: 130px`). */
internal val STATE_CONTROL_THICKNESS = 130.dp

/** Their height (`45vh`, at most 320px). */
internal val STATE_CONTROL_HEIGHT = 320.dp

/** Their corner radius (`--ha-border-radius-6xl`). */
internal val STATE_CONTROL_RADIUS = 36.dp

private const val THROTTLE_MS = 500L
private const val STRIPES = 24
private const val MIN_STRIPE = 0.2f
private const val STRIPES_ALPHA = 0.6f
