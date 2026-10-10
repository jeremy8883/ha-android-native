package io.homeassistant.companion.android.dashboard.ui.controls

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import io.homeassistant.companion.android.dashboard.moreinfo.VacuumVisual

/** Draws [frame] in upstream's 240 unit square: the SVG of `ha-state-control-vacuum-status` and its keyframes. */
internal class VacuumDrawing(private val frame: VacuumFrame) {
    private val color = frame.color
    private val motion = frame.motion
    private val visual = frame.visual

    fun draw(scope: DrawScope) = with(scope) {
        drawCircle(color, GLOW_RADIUS, CENTER, alpha = glowAlpha())
        rotate(frame.turn, CENTER) {
            val lift = if (visual == VacuumVisual.Returning) DRIFT_FROM + (DRIFT_TO - DRIFT_FROM) * motion.drift else 0f
            translate(top = lift) {
                rotate(motion.wanderAngle, CENTER) {
                    translate(top = motion.wanderLift) { drawBody(bodyAlpha()) }
                }
            }
        }
        if (visual == VacuumVisual.Cleaning) drawParticles()
        if (visual == VacuumVisual.Docked) drawDock()
        if (visual == VacuumVisual.Returning) drawReturnArrow()
    }

    private fun glowAlpha() = when (visual) {
        VacuumVisual.Docked -> lerp(DOCKED_GLOW_LOW, DOCKED_GLOW_HIGH, motion.pulse)
        VacuumVisual.Error -> lerp(ERROR_GLOW_LOW, ERROR_GLOW_HIGH, motion.pulse)
        else -> GLOW_ALPHA
    }

    private fun bodyAlpha() = when (visual) {
        VacuumVisual.Docked -> DOCKED_ALPHA
        VacuumVisual.Idle -> IDLE_ALPHA
        else -> 1f
    }

    private fun DrawScope.drawBody(alpha: Float) {
        drawBrush(alpha * brushAlpha())
        drawCircle(frame.background, SHELL_RADIUS, CENTER, alpha = alpha)
        drawCircle(color, SHELL_RADIUS, CENTER, alpha = alpha, style = Stroke(2f))
        drawCircle(color, INNER_RADIUS, CENTER, alpha = alpha * INNER_ALPHA, style = Stroke(THIN))
        drawArc(
            color,
            BUMPER_START,
            BUMPER_SWEEP,
            useCenter = false,
            topLeft = Offset(CENTER.x - BUMPER_RADIUS, BUMPER_CENTER_Y - BUMPER_RADIUS),
            size = Size(BUMPER_RADIUS * 2, BUMPER_RADIUS * 2),
            alpha = alpha,
            style = Stroke(BUMPER_WIDTH, cap = StrokeCap.Round),
        )
        val nav = if (visual == VacuumVisual.Cleaning) lerp(NAV_LOW, NAV_HIGH, motion.pulse) else NAV_LOW
        NAV_LINES.forEach { (from, to) -> drawLine(color, from, to, NAV_WIDTH, alpha = alpha * nav) }
        drawCircle(frame.background, LIDAR_RADIUS, LIDAR, alpha = alpha)
        drawCircle(color, LIDAR_RADIUS, LIDAR, alpha = alpha, style = Stroke(2f))
        drawCircle(color, LIDAR_INNER, LIDAR, alpha = alpha * LIDAR_INNER_ALPHA, style = Stroke(THIN))
        val ring = if (visual == VacuumVisual.Docked) lerp(RING_LOW, RING_HIGH, motion.pulse) else RING_LOW
        drawCircle(color, RING_RADIUS, POWER, alpha = alpha * ring)
        drawCircle(color, DOT_RADIUS, POWER, alpha = alpha * DOT_ALPHA)
    }

    private fun brushAlpha() = when (visual) {
        VacuumVisual.Returning, VacuumVisual.Idle -> DIM_BRUSH
        VacuumVisual.Docked -> DOCKED_BRUSH
        else -> 1f
    }

    /** The side brush: four spokes, spinning while cleaning. */
    private fun DrawScope.drawBrush(alpha: Float) {
        rotate(motion.brushAngle, BRUSH) {
            SPOKES.forEach { (dx, dy) ->
                drawLine(
                    color,
                    BRUSH,
                    Offset(BRUSH.x + dx, BRUSH.y + dy),
                    SPOKE_WIDTH,
                    StrokeCap.Round,
                    alpha = alpha * BRUSH_ALPHA,
                )
            }
        }
        drawCircle(color, 2f, BRUSH, alpha = alpha * BRUSH_ALPHA)
    }

    /** `particle-suck`: dust drawn in from around the robot, each a step behind the last. */
    private fun DrawScope.drawParticles() {
        PARTICLES.forEachIndexed { index, (offset, radius) ->
            val t = (motion.particles + index * PARTICLE_PHASE) % 1f
            val alpha = when {
                t < FADE_IN -> lerp(0f, PARTICLE_ALPHA, t / FADE_IN)
                t < SETTLE -> lerp(PARTICLE_ALPHA, 0f, (t - FADE_IN) / (SETTLE - FADE_IN))
                else -> 0f
            }
            val travel = lerp(1f, PARTICLE_END, (t / SETTLE).coerceAtMost(1f))
            val scale = lerp(PARTICLE_SCALE_FROM, PARTICLE_SCALE_TO, (t / SETTLE).coerceAtMost(1f))
            drawCircle(color, radius * scale, CENTER + offset * travel, alpha = alpha)
        }
    }

    private fun DrawScope.drawDock() {
        drawRoundRect(frame.background, DOCK_TOP_LEFT, DOCK_SIZE, CornerRadius(DOCK_RADIUS))
        drawRoundRect(color, DOCK_TOP_LEFT, DOCK_SIZE, CornerRadius(DOCK_RADIUS), style = Stroke(2f))
    }

    /** `return-arrow`: an arrow below, bobbing and brightening. */
    private fun DrawScope.drawReturnArrow() {
        val shift = DRIFT_FROM + (DRIFT_TO - DRIFT_FROM) * motion.drift
        val arrow = Path().apply {
            moveTo(ARROW_TIP.x, ARROW_TIP.y + shift)
            lineTo(ARROW_TIP.x - ARROW_HALF, ARROW_BASE + shift)
            lineTo(ARROW_TIP.x + ARROW_HALF, ARROW_BASE + shift)
            close()
        }
        drawPath(arrow, color, alpha = ARROW_ALPHA * lerp(ARROW_LOW, ARROW_HIGH, motion.drift))
    }

    private fun lerp(from: Float, to: Float, fraction: Float) = from + (to - from) * fraction

    private companion object {
        val CENTER = Offset(120f, 120f)
        val LIDAR = Offset(120f, 108f)
        val POWER = Offset(120f, 140f)
        val BRUSH = Offset(174f, 76f)
        val SPOKES = listOf(0f to -12f, 0f to 12f, -12f to 0f, 12f to 0f)
        val NAV_LINES = listOf(
            Offset(120f, 56f) to Offset(120f, 74f),
            Offset(88f, 63f) to Offset(96f, 78f),
            Offset(152f, 63f) to Offset(144f, 78f),
        )
        val PARTICLES = listOf(
            Offset(-90f, -25f) to 2f,
            Offset(90f, 20f) to 1.5f,
            Offset(-85f, 40f) to 1.5f,
            Offset(85f, -35f) to 2f,
            Offset(-65f, 70f) to 1.5f,
            Offset(70f, -65f) to 1.5f,
            Offset(-75f, -55f) to 1f,
            Offset(80f, 55f) to 1f,
        )
        val DOCK_TOP_LEFT = Offset(76f, 188f)
        val DOCK_SIZE = Size(88f, 28f)
        val ARROW_TIP = Offset(120f, 220f)
        const val ARROW_BASE = 206f
        const val ARROW_HALF = 10f
        const val ARROW_ALPHA = 0.55f
        const val ARROW_LOW = 0.3f
        const val ARROW_HIGH = 0.85f
        const val DOCK_RADIUS = 8f
        const val GLOW_RADIUS = 110f
        const val SHELL_RADIUS = 72f
        const val INNER_RADIUS = 66f
        const val INNER_ALPHA = 0.2f
        const val THIN = 0.8f
        const val BUMPER_RADIUS = 68f
        const val BUMPER_CENTER_Y = 126f
        const val BUMPER_START = 208.07f
        const val BUMPER_SWEEP = 123.86f
        const val BUMPER_WIDTH = 3f
        const val NAV_WIDTH = 1.5f
        const val NAV_LOW = 0.15f
        const val NAV_HIGH = 0.35f
        const val LIDAR_RADIUS = 14f
        const val LIDAR_INNER = 9f
        const val LIDAR_INNER_ALPHA = 0.25f
        const val RING_RADIUS = 8f
        const val RING_LOW = 0.08f
        const val RING_HIGH = 0.25f
        const val DOT_RADIUS = 4f
        const val DOT_ALPHA = 0.25f
        const val SPOKE_WIDTH = 1.2f
        const val BRUSH_ALPHA = 0.5f
        const val DIM_BRUSH = 0.4f
        const val DOCKED_BRUSH = 0.5f
        const val GLOW_ALPHA = 0.06f
        const val DOCKED_GLOW_LOW = 0.06f
        const val DOCKED_GLOW_HIGH = 0.14f
        const val ERROR_GLOW_LOW = 0.08f
        const val ERROR_GLOW_HIGH = 0.35f
        const val DOCKED_ALPHA = 0.75f
        const val IDLE_ALPHA = 0.65f
        const val DRIFT_FROM = -6f
        const val DRIFT_TO = 4f
        const val PARTICLE_PHASE = 0.125f
        const val PARTICLE_ALPHA = 0.8f
        const val PARTICLE_END = 0.82f
        const val PARTICLE_SCALE_FROM = 1.2f
        const val PARTICLE_SCALE_TO = 0.6f
        const val FADE_IN = 0.25f
        const val SETTLE = 0.8f
    }
}
