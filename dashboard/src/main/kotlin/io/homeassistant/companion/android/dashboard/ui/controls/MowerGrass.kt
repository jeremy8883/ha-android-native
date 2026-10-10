package io.homeassistant.companion.android.dashboard.ui.controls

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate

/**
 * The mower's `grass-eject`: clippings flicked out from under the body while it mows, each on its own timing, drawn
 * only outside the body as upstream masks them.
 */
internal object MowerGrass {
    /** Draws the clippings at [clock]'s time. */
    fun draw(scope: DrawScope, color: Color, clock: AnimationClock) = with(scope) {
        clipPath(SILHOUETTE, ClipOp.Difference) {
            BLADES.forEach { blade ->
                val flight = blade.flight
                val frame = frameAt(clock.phase(flight.duration, flight.delay))
                translate(flight.offset.x * frame.travel, flight.offset.y * frame.travel) {
                    rotate(flight.spin * frame.turn, blade.start) {
                        scale(frame.size, blade.start) {
                            drawPath(
                                blade.path,
                                color,
                                alpha = frame.alpha,
                                style = Stroke(blade.width, cap = StrokeCap.Round),
                            )
                        }
                    }
                }
            }
        }
    }

    /** Where a clipping is at [t] of its flight: `grass-eject`'s keyframes at 0%, 12%, 55% and 100%. */
    private fun frameAt(t: Float): GrassFrame = when {
        t < RISE -> GrassFrame.between(START, RISEN, t / RISE)
        t < MID -> GrassFrame.between(RISEN, HALFWAY, (t - RISE) / (MID - RISE))
        else -> GrassFrame.between(HALFWAY, LANDED, (t - MID) / (1 - MID))
    }

    /** Where a clipping flies (`--g-x`/`--g-y`), how far it turns (`--g-rot`), and its timing (`--g-dur`, `--g-delay`). */
    private class Flight(val offset: Offset, val spin: Float, val duration: Float, val delay: Float)

    /** One clipping: its curve (`M start q control end`), thickness and flight. */
    private class Blade(val start: Offset, control: Offset, end: Offset, val width: Float, val flight: Flight) {
        val path = Path().apply {
            moveTo(start.x, start.y)
            quadraticTo(start.x + control.x, start.y + control.y, start.x + end.x, start.y + end.y)
        }
    }

    private val SILHOUETTE =
        svgPath("M60,98 C60,46 180,46 180,98 L180,170 C180,182 172,190 160,190 L80,190 C68,190 60,182 60,170 Z")

    private val START = GrassFrame(alpha = 0f, travel = 0f, turn = 0f, size = 0.7f)
    private val RISEN = GrassFrame(alpha = 0.65f, travel = 0.12f, turn = 0.1f, size = 1f)
    private val HALFWAY = GrassFrame(alpha = 0.35f, travel = 0.7f, turn = 0.7f, size = 0.8f)
    private val LANDED = GrassFrame(alpha = 0f, travel = 1f, turn = 1f, size = 0.4f)
    private const val RISE = 0.12f
    private const val MID = 0.55f

    private val BLADES = listOf(
        Blade(Offset(64f, 110f), Offset(-3f, 4f), Offset(-1f, 8f), 1.4f, Flight(Offset(-36f, -14f), -25f, 1.8f, 0f)),
        Blade(Offset(63f, 126f), Offset(-4f, 3f), Offset(-2f, 8f), 1.2f, Flight(Offset(-40f, 6f), 18f, 2.1f, -0.7f)),
        Blade(Offset(64f, 140f), Offset(-4f, 3f), Offset(-2f, 8f), 1.5f, Flight(Offset(-34f, 16f), 32f, 1.9f, -1.3f)),
        Blade(Offset(64f, 154f), Offset(-3f, 4f), Offset(-1f, 8f), 1.1f, Flight(Offset(-30f, 22f), -15f, 2.3f, -1.8f)),
        Blade(Offset(66f, 168f), Offset(-3f, 3f), Offset(-1f, 8f), 1.3f, Flight(Offset(36f, -14f), 25f, 1.8f, -0.3f)),
        Blade(Offset(176f, 110f), Offset(3f, 4f), Offset(1f, 8f), 1.4f, Flight(Offset(40f, 6f), -18f, 2.1f, -1f)),
        Blade(Offset(177f, 126f), Offset(4f, 3f), Offset(2f, 8f), 1.2f, Flight(Offset(34f, 16f), -32f, 1.9f, -1.6f)),
        Blade(Offset(176f, 140f), Offset(4f, 3f), Offset(2f, 8f), 1.5f, Flight(Offset(30f, 22f), 15f, 2.3f, -0.5f)),
        Blade(Offset(176f, 154f), Offset(3f, 4f), Offset(1f, 8f), 1.1f, Flight(Offset(-28f, -30f), -40f, 2f, -0.4f)),
        Blade(Offset(174f, 168f), Offset(3f, 3f), Offset(1f, 8f), 1.3f, Flight(Offset(-12f, -34f), -20f, 1.8f, -1.1f)),
        Blade(Offset(90f, 68f), Offset(-3f, -4f), Offset(-1f, -8f), 1.3f, Flight(Offset(3f, -36f), 8f, 1.7f, -1.7f)),
        Blade(Offset(120f, 54f), Offset(1f, -4f), Offset(-1f, -8f), 1.2f, Flight(Offset(14f, -34f), 20f, 1.8f, -0.2f)),
        Blade(Offset(150f, 68f), Offset(3f, -4f), Offset(1f, -8f), 1.3f, Flight(Offset(28f, -30f), 40f, 2f, -0.9f)),
        Blade(Offset(92f, 186f), Offset(-3f, 4f), Offset(-1f, 8f), 1.3f, Flight(Offset(-16f, 28f), 22f, 2.2f, -0.8f)),
        Blade(Offset(120f, 186f), Offset(2f, 4f), Offset(0f, 9f), 1.1f, Flight(Offset(5f, 30f), -8f, 1.6f, -1.5f)),
        Blade(Offset(148f, 186f), Offset(3f, 4f), Offset(1f, 8f), 1.3f, Flight(Offset(16f, 28f), -22f, 2.2f, -0.4f)),
    )
}

/** A clipping's look at one keyframe: how visible, how far along its flight and turn, and how big. */
internal data class GrassFrame(val alpha: Float, val travel: Float, val turn: Float, val size: Float) {
    companion object {
        /** Between [from] and [to] at [fraction]. */
        fun between(from: GrassFrame, to: GrassFrame, fraction: Float) = GrassFrame(
            lerp(from.alpha, to.alpha, fraction),
            lerp(from.travel, to.travel, fraction),
            lerp(from.turn, to.turn, fraction),
            lerp(from.size, to.size, fraction),
        )
    }
}
