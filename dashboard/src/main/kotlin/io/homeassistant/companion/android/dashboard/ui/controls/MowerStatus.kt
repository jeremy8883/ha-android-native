package io.homeassistant.companion.android.dashboard.ui.controls

import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.moreinfo.MowerVisual

/**
 * Port of `ha-state-control-lawn_mower-status` (frontend@20260624.6 src/state-control/lawn_mower/): the mower from
 * above in [color], wandering with its blades spinning, wheels turning and grass flicked out while mowing, turned
 * for home with a bobbing arrow while returning, on its dock with a pulsing glow when docked, faded while paused or
 * idle, and glowing a warning on error. Drawn by [MowerDrawing] from one clock in seconds.
 */
@Composable
internal fun MowerStatus(visual: MowerVisual, color: Color, modifier: Modifier = Modifier) {
    val background = LocalHAColorScheme.current.colorSurfaceDefault
    val seconds by rememberInfiniteTransition(label = "mower").animateFloat(
        initialValue = 0f,
        targetValue = CLOCK_SECONDS,
        animationSpec = infiniteRepeatable(tween((CLOCK_SECONDS * MILLIS).toInt(), easing = LinearEasing)),
        label = "clock",
    )
    val turn by animateFloatAsState(
        if (visual ==
            MowerVisual.Returning
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
        scale(size.minDimension / VIEW, pivot = Offset.Zero) {
            MowerDrawing(visual, color, background, seconds, turn).draw(this)
        }
    }
}

// Long enough that its wrapping round goes unnoticed, short enough to keep the float precise
private const val CLOCK_SECONDS = 600f
private const val MILLIS = 1000f
private const val VIEW = 240f
private const val HALF_TURN = 180f
private const val TURN_MS = 600
private val STATUS_SIZE = 200.dp
