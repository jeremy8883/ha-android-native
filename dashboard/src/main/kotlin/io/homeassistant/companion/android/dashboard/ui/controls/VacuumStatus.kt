package io.homeassistant.companion.android.dashboard.ui.controls

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.moreinfo.VacuumVisual

/**
 * Port of `ha-state-control-vacuum-status` (frontend@20260624.6 src/state-control/vacuum/): the robot from above
 * in [color], wandering with its brush spinning and dust drawn in while cleaning, turned for home while returning,
 * on its glowing dock when docked, glowing a warning on error, and faded when idle.
 */
@Composable
internal fun VacuumStatus(visual: VacuumVisual, color: Color, modifier: Modifier = Modifier) {
    val background = LocalHAColorScheme.current.colorSurfaceDefault
    val motion = rememberVacuumMotion(visual)
    val turn by animateFloatAsState(
        if (visual ==
            VacuumVisual.Returning
        ) {
            HALF_TURN
        } else {
            0f
        },
        tween(TURN_MS),
        label = "turn",
    )
    Canvas(modifier.size(STATUS_SIZE)) {
        // The drawing is upstream's 240 unit square
        val unit = size.minDimension / VIEW
        scale(unit, pivot = Offset.Zero) {
            drawVacuum(VacuumFrame(visual, color, background, motion, turn))
        }
    }
}

/** The animated values of one frame. */
internal class VacuumMotion(
    val wanderAngle: Float,
    val wanderLift: Float,
    val brushAngle: Float,
    val particles: Float,
    val pulse: Float,
    val drift: Float,
)

@Composable
private fun rememberVacuumMotion(visual: VacuumVisual): VacuumMotion {
    val transition = rememberInfiniteTransition(label = "vacuum")
    val wander by transition.animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(WANDER_MS, easing = LinearEasing)),
        label = "wander",
    )
    val brush by transition.animateFloat(
        0f,
        FULL_TURN,
        infiniteRepeatable(tween(BRUSH_MS, easing = LinearEasing)),
        label = "brush",
    )
    val particles by transition.animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(PARTICLE_MS, easing = LinearEasing)),
        label = "dust",
    )
    val pulse by transition.animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(pulseMs(visual), easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    val drift by transition.animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(DRIFT_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "drift",
    )
    val cleaning = visual == VacuumVisual.Cleaning
    val (angle, lift) = if (cleaning) wanderAt(wander) else 0f to 0f
    return VacuumMotion(angle, lift, if (cleaning) brush else 0f, particles, pulse, drift)
}

/** How long a glow takes to brighten: upstream's keyframes are a full cycle, a reversing pulse is half of one. */
private fun pulseMs(visual: VacuumVisual) = when (visual) {
    VacuumVisual.Error -> ERROR_PULSE_MS
    VacuumVisual.Docked -> DOCKED_PULSE_MS
    else -> NAV_PULSE_MS
}

/** `vacuum-wander`: forward and back, turned left, then right, each a stretch of its twelve seconds. */
internal fun wanderAt(progress: Float): Pair<Float, Float> {
    val stops = WANDER_STOPS
    val index = stops.indexOfLast { it.first <= progress }.coerceIn(0, stops.size - 2)
    val (startAt, start) = stops[index].first to stops[index].second
    val (endAt, end) = stops[index + 1].first to stops[index + 1].second
    val fraction = FastOutSlowInEasing.transform(((progress - startAt) / (endAt - startAt)).coerceIn(0f, 1f))
    return (start.first + (end.first - start.first) * fraction) to
        (start.second + (end.second - start.second) * fraction)
}

/** `vacuum-wander`'s keyframes: (progress, (rotation, lift)). */
private val WANDER_STOPS = listOf(
    0f to (0f to 0f),
    0.15f to (0f to -30f),
    0.25f to (0f to 0f),
    0.32f to (-18f to 0f),
    0.47f to (-18f to -28f),
    0.57f to (-18f to 0f),
    0.63f to (12f to 0f),
    0.78f to (12f to -25f),
    0.88f to (12f to 0f),
    0.95f to (0f to 0f),
    1f to (0f to 0f),
)

/** Draws a frame of the robot (VacuumDrawing.kt). */
private fun DrawScope.drawVacuum(frame: VacuumFrame) = VacuumDrawing(frame).draw(this)

/** What one frame draws. */
internal class VacuumFrame(
    val visual: VacuumVisual,
    val color: Color,
    val background: Color,
    val motion: VacuumMotion,
    val turn: Float,
)

private const val VIEW = 240f
private const val FULL_TURN = 360f
private const val HALF_TURN = 180f
private const val WANDER_MS = 12_000
private const val BRUSH_MS = 600
private const val PARTICLE_MS = 2_400
private const val TURN_MS = 600
private const val DRIFT_MS = 1_500
private const val NAV_PULSE_MS = 1_000
private const val DOCKED_PULSE_MS = 2_000
private const val ERROR_PULSE_MS = 900
private val STATUS_SIZE = 200.dp
