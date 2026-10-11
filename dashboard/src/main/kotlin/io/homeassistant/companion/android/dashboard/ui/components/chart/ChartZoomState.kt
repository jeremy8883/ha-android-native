package io.homeassistant.companion.android.dashboard.ui.components.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.history.ChartZoom
import io.homeassistant.companion.android.dashboard.ui.components.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.theme.resolveVariable

/** The zoom of one chart, or of all the History panel's charts at once (`sync-charts`). */
@Stable
internal class ChartZoomState(initial: ChartZoom = ChartZoom()) {
    var zoom by mutableStateOf(initial)
}

/**
 * The frontend's touch zoom (`dataZoom` inside, x axis only): two fingers pinch to zoom around them, and while
 * zoomed in one finger drags to pan. Other one-finger drags are left to the page, so it still scrolls. [plot] is
 * where the plot is, left to right px, for the pinch's focus and the pan's distance.
 */
internal fun Modifier.chartZoomGestures(state: ChartZoomState, plot: () -> ClosedFloatingPointRange<Float>?) =
    pointerInput(state) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            val pinch = PinchTracker()
            do {
                val pressed = awaitPointerEvent().changes.filter { it.pressed }
                val bounds = plot()
                if (pressed.isNotEmpty() && bounds != null) pinch.onMove(state, pressed, bounds)
            } while (pressed.isNotEmpty())
        }
    }

/** Follows a gesture's fingers: two pinch, one pans while zoomed in. */
private class PinchTracker {
    private var lastSpread: Float? = null

    fun onMove(state: ChartZoomState, pressed: List<PointerInputChange>, bounds: ClosedFloatingPointRange<Float>) {
        val width = bounds.endInclusive - bounds.start
        if (pressed.size >= 2) {
            val spread = (pressed[0].position - pressed[1].position).getDistance()
            val previous = lastSpread
            lastSpread = spread
            if (previous != null && spread != previous && width > 0) {
                val centre = (pressed[0].position.x + pressed[1].position.x) / 2
                val focus = ((centre - bounds.start) / width).toDouble()
                state.zoom = state.zoom.pinched(focus, spreading = spread > previous)
            }
            pressed.forEach(PointerInputChange::consume)
        } else {
            lastSpread = null
            val change = pressed.single()
            val dx = change.position.x - change.previousPosition.x
            if (state.zoom.isZoomed && dx != 0f) {
                state.zoom = state.zoom.panned(dx.toDouble(), width.toDouble())
                change.consume()
            }
        }
    }
}

/**
 * Taps on a chart, at once as the frontend's: one within [DOUBLE_TAP_MILLIS] of the last is a double tap, which
 * zooms ([ChartZoom.doubleTapped] at its share of the chart's width); the others go to [onTap].
 */
internal fun Modifier.chartTaps(state: ChartZoomState, key: Any?, onTap: (Offset) -> Unit) = pointerInput(state, key) {
    var lastTap = 0L
    awaitEachGesture {
        awaitFirstDown()
        val up = waitForUpOrCancellation() ?: return@awaitEachGesture
        if (lastTap != 0L && up.uptimeMillis - lastTap < DOUBLE_TAP_MILLIS && size.width > 0) {
            state.zoom = state.zoom.doubleTapped((up.position.x / size.width).toDouble())
            lastTap = 0L
        } else {
            lastTap = up.uptimeMillis
            onTap(up.position)
        }
    }
}

/**
 * A chart's own "Reset zoom" button, top right while zoomed in (`.zoom-reset` of `ha-chart-base`).
 *
 * Upstream: part of `ha-chart-base` (src/components/chart/ha-chart-base.ts)
 */
@Composable
internal fun ChartZoomReset(state: ChartZoomState, label: String, modifier: Modifier = Modifier) {
    if (!state.zoom.isZoomed) return
    val colors = LocalHAColorScheme.current
    val dark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(HARadius.S)
    Box(
        modifier
            .size(RESET_SIZE)
            .clip(shape)
            .background(resolveVariable("card-background-color", dark) ?: colors.colorSurfaceDefault)
            .border(1.dp, resolveVariable("divider-color", dark) ?: colors.colorBorderNeutralQuiet, shape),
    ) {
        IconButton(
            onClick = { state.zoom = ChartZoom() },
            modifier = Modifier.size(RESET_SIZE).semantics { contentDescription = label },
        ) {
            DashboardIcon(
                "mdi:restart",
                resolveVariable("primary-color", dark) ?: colors.colorOnPrimaryNormal,
                Modifier.size(RESET_ICON),
            )
        }
    }
}

/** The frontend's `DOUBLE_TAP_TIME`. */
private const val DOUBLE_TAP_MILLIS = 300L
private val RESET_SIZE = 32.dp
private val RESET_ICON = 24.dp
