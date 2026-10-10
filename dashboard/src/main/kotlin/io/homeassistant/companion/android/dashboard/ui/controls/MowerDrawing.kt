package io.homeassistant.companion.android.dashboard.ui.controls

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import io.homeassistant.companion.android.dashboard.moreinfo.MowerVisual
import kotlin.math.PI
import kotlin.math.cos

/** Draws the mower at [seconds] in upstream's 240 unit square: its SVG and keyframes. */
internal class MowerDrawing(
    private val visual: MowerVisual,
    private val color: Color,
    private val background: Color,
    seconds: Float,
    private val turn: Float,
) {
    private val clock = AnimationClock(seconds)

    /** Draws the frame. */
    fun draw(scope: DrawScope) = with(scope) {
        drawCircle(color, GLOW_RADIUS, CENTER, alpha = glowAlpha())
        if (visual == MowerVisual.Docked) drawDock()
        if (visual == MowerVisual.Returning) drawReturnArrow()
        rotate(turn, CENTER) {
            val drift = if (visual ==
                MowerVisual.Returning
            ) {
                lerp(DRIFT_FROM, DRIFT_TO, clock.pulse(DRIFT_SECONDS))
            } else {
                0f
            }
            val (angle, lift) = if (visual == MowerVisual.Mowing) wanderAt(clock.phase(WANDER_SECONDS)) else 0f to 0f
            translate(top = drift) {
                rotate(angle, CENTER) { translate(top = lift) { drawBody() } }
            }
        }
    }

    private fun glowAlpha() = when (visual) {
        MowerVisual.Docked -> lerp(DOCKED_GLOW_LOW, DOCKED_GLOW_HIGH, clock.pulse(DOCKED_GLOW_SECONDS))
        MowerVisual.Error -> lerp(ERROR_GLOW_LOW, ERROR_GLOW_HIGH, clock.pulse(ERROR_GLOW_SECONDS))
        else -> GLOW_ALPHA
    }

    private fun bodyAlpha() = when (visual) {
        MowerVisual.Docked -> DOCKED_ALPHA
        MowerVisual.Paused -> PAUSED_ALPHA
        MowerVisual.Idle -> IDLE_ALPHA
        else -> 1f
    }

    private fun DrawScope.drawBody() {
        val alpha = bodyAlpha()
        drawBladeDisc(alpha)
        drawWheel(LEFT_WHEEL, alpha)
        drawWheel(RIGHT_WHEEL, alpha)
        drawPath(SHELL, background, alpha = alpha)
        drawPath(SHELL, color, alpha = alpha, style = Stroke(2f))
        drawPath(INNER, color, alpha = alpha * INNER_ALPHA, style = Stroke(THIN))
        drawPath(BUMPER, color, alpha = alpha, style = Stroke(BUMPER_WIDTH, cap = StrokeCap.Round))
        drawPath(HIGHLIGHT, color, alpha = alpha * FAINT, style = Stroke(1f, cap = StrokeCap.Round))
        SENSORS.forEach { drawCircle(color, SENSOR_RADIUS, it, alpha = alpha * FAINT) }
        drawCircle(color, LED_RING, LED, alpha = alpha * LED_RING_ALPHA)
        drawCircle(color, LED_DOT, LED, alpha = alpha * ledAlpha())
        val ring = if (visual ==
            MowerVisual.Docked
        ) {
            lerp(CHARGE_LOW, CHARGE_HIGH, clock.pulse(CHARGE_SECONDS))
        } else {
            POWER_RING_ALPHA
        }
        drawCircle(color, POWER_RING, POWER, alpha = alpha * ring)
        drawCircle(color, POWER_DOT, POWER, alpha = alpha * POWER_DOT_ALPHA)
        if (visual == MowerVisual.Mowing) MowerGrass.draw(this, color, clock)
    }

    private fun ledAlpha() = when (visual) {
        MowerVisual.Mowing -> lerp(LED_LOW, LED_HIGH, clock.pulse(LED_SECONDS))
        MowerVisual.Docked -> lerp(CHARGE_LOW, CHARGE_HIGH, clock.pulse(CHARGE_SECONDS))
        MowerVisual.Error -> lerp(ERROR_LED_LOW, ERROR_LED_HIGH, clock.pulse(ERROR_LED_SECONDS))
        else -> LED_ALPHA
    }

    /** The blade disc under the body: three arms in a dashed ring, spinning while it mows. */
    private fun DrawScope.drawBladeDisc(alpha: Float) {
        val disc = when (visual) {
            MowerVisual.Mowing -> MOWING_DISC
            MowerVisual.Returning -> RETURNING_DISC
            MowerVisual.Paused -> PAUSED_DISC
            else -> return
        }
        val spin = if (visual == MowerVisual.Mowing) clock.phase(BLADE_SECONDS) * FULL_TURN else 0f
        rotate(spin, BLADE) {
            drawCircle(
                color,
                BLADE_RADIUS,
                BLADE,
                alpha = alpha * disc,
                style = Stroke(BLADE_STROKE, pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH, GAP))),
            )
            drawCircle(color, HUB_RADIUS, BLADE, alpha = alpha * disc)
            ARMS.forEach { drawLine(color, BLADE, it, 2f, StrokeCap.Round, alpha = alpha * disc) }
        }
    }

    /** A rear wheel, its tread scrolling forward while it mows and back while it heads home. */
    private fun DrawScope.drawWheel(x: Float, alpha: Float) {
        val topLeft = Offset(x, WHEEL_TOP)
        drawRoundRect(background, topLeft, WHEEL_SIZE, CornerRadius(WHEEL_RADIUS), alpha = alpha)
        drawRoundRect(
            color,
            topLeft,
            WHEEL_SIZE,
            CornerRadius(WHEEL_RADIUS),
            alpha = alpha * WHEEL_ALPHA,
            style = Stroke(BLADE_STROKE),
        )
        val offset = when (visual) {
            MowerVisual.Mowing -> -clock.phase(TREAD_SECONDS) * TREAD_CYCLE
            MowerVisual.Returning -> clock.phase(TREAD_BACK_SECONDS) * TREAD_CYCLE
            else -> 0f
        }
        val center = x + WHEEL_SIZE.width / 2
        drawLine(
            color,
            Offset(center, TREAD_TOP),
            Offset(center, TREAD_BOTTOM),
            TREAD_WIDTH,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(TREAD_DASH, TREAD_DASH), offset),
            alpha = alpha * TREAD_ALPHA,
        )
    }

    private fun DrawScope.drawDock() {
        drawRoundRect(background, DOCK_TOP_LEFT, DOCK_SIZE, CornerRadius(DOCK_RADIUS))
        drawRoundRect(color, DOCK_TOP_LEFT, DOCK_SIZE, CornerRadius(DOCK_RADIUS), style = Stroke(2f))
    }

    private fun DrawScope.drawReturnArrow() {
        val bob = clock.pulse(ARROW_SECONDS)
        val shift = lerp(DRIFT_FROM, DRIFT_TO, bob)
        val arrow = Path().apply {
            moveTo(ARROW_TIP.x, ARROW_TIP.y + shift)
            lineTo(ARROW_TIP.x - ARROW_HALF, ARROW_BASE + shift)
            lineTo(ARROW_TIP.x + ARROW_HALF, ARROW_BASE + shift)
            close()
        }
        drawPath(arrow, color, alpha = ARROW_ALPHA * lerp(ARROW_LOW, ARROW_HIGH, bob))
    }

    private companion object {
        val CENTER = Offset(120f, 120f)
        val BLADE = Offset(120f, 130f)
        val LED = Offset(120f, 78f)
        val POWER = Offset(120f, 148f)
        val ARMS = listOf(Offset(120f, 88f), Offset(156f, 151f), Offset(84f, 151f))
        val SENSORS = listOf(Offset(92f, 68f), Offset(120f, 60f), Offset(148f, 68f))
        val WHEEL_SIZE = Size(12f, 40f)
        val DOCK_TOP_LEFT = Offset(80f, 188f)
        val DOCK_SIZE = Size(80f, 24f)
        val ARROW_TIP = Offset(120f, 220f)

        val SHELL =
            svgPath("M64,98 C64,50 176,50 176,98 L176,168 C176,180 168,186 158,186 L82,186 C72,186 64,180 64,168 Z")
        val INNER =
            svgPath("M74,102 C74,60 166,60 166,102 L166,162 C166,172 160,176 152,176 L88,176 C80,176 74,172 74,162 Z")
        val SILHOUETTE =
            svgPath("M60,98 C60,46 180,46 180,98 L180,170 C180,182 172,190 160,190 L80,190 C68,190 60,182 60,170 Z")
        val BUMPER = svgPath("M64,102 C64,52 176,52 176,102")
        val HIGHLIGHT = svgPath("M72,100 C72,58 168,58 168,100")

        const val GLOW_RADIUS = 110f
        const val GLOW_ALPHA = 0.06f
        const val DOCKED_GLOW_LOW = 0.06f
        const val DOCKED_GLOW_HIGH = 0.14f
        const val DOCKED_GLOW_SECONDS = 4f
        const val ERROR_GLOW_LOW = 0.08f
        const val ERROR_GLOW_HIGH = 0.35f
        const val ERROR_GLOW_SECONDS = 1.8f
        const val DOCKED_ALPHA = 0.75f
        const val PAUSED_ALPHA = 0.7f
        const val IDLE_ALPHA = 0.65f
        const val WANDER_SECONDS = 12f
        const val DRIFT_SECONDS = 3f
        const val DRIFT_FROM = -6f
        const val DRIFT_TO = 4f
        const val INNER_ALPHA = 0.2f
        const val THIN = 0.8f
        const val BUMPER_WIDTH = 4f
        const val FAINT = 0.15f
        const val SENSOR_RADIUS = 1.5f
        const val LED_RING = 4f
        const val LED_RING_ALPHA = 0.08f
        const val LED_DOT = 2.5f
        const val LED_ALPHA = 0.5f
        const val LED_LOW = 0.3f
        const val LED_HIGH = 0.8f
        const val LED_SECONDS = 2f
        const val ERROR_LED_LOW = 0.2f
        const val ERROR_LED_HIGH = 0.9f
        const val ERROR_LED_SECONDS = 0.8f
        const val CHARGE_LOW = 0.08f
        const val CHARGE_HIGH = 0.25f
        const val CHARGE_SECONDS = 2.5f
        const val POWER_RING = 8f
        const val POWER_RING_ALPHA = 0.06f
        const val POWER_DOT = 4f
        const val POWER_DOT_ALPHA = 0.2f
        const val MOWING_DISC = 0.15f
        const val RETURNING_DISC = 0.06f
        const val PAUSED_DISC = 0.04f
        const val BLADE_SECONDS = 0.8f
        const val FULL_TURN = 360f
        const val BLADE_RADIUS = 46f
        const val BLADE_STROKE = 1.5f
        const val DASH = 8f
        const val GAP = 6f
        const val HUB_RADIUS = 6f
        const val LEFT_WHEEL = 50f
        const val RIGHT_WHEEL = 178f
        const val WHEEL_TOP = 144f
        const val WHEEL_RADIUS = 5f
        const val WHEEL_ALPHA = 0.7f
        const val TREAD_TOP = 148f
        const val TREAD_BOTTOM = 180f
        const val TREAD_WIDTH = 6f
        const val TREAD_DASH = 3f
        const val TREAD_CYCLE = 6f
        const val TREAD_ALPHA = 0.12f
        const val TREAD_SECONDS = 0.4f
        const val TREAD_BACK_SECONDS = 0.6f
        const val DOCK_RADIUS = 7f
        const val ARROW_BASE = 208f
        const val ARROW_HALF = 10f
        const val ARROW_ALPHA = 0.55f
        const val ARROW_LOW = 0.3f
        const val ARROW_HIGH = 0.85f
        const val ARROW_SECONDS = 1.6f
    }
}

/** Repeating animations at [seconds] on one clock. */
internal class AnimationClock(private val seconds: Float) {
    /** Where a repeating animation of [period] seconds is, 0 to 1, started [delay] seconds late. */
    fun phase(period: Float, delay: Float = 0f): Float = ((seconds - delay) % period + period) % period / period

    /** An ease-in-out swing 0 → 1 → 0 over [period] seconds, as upstream's 0%/50%/100% keyframes. */
    fun pulse(period: Float): Float = ((1 - cos(2 * PI * phase(period))) / 2).toFloat()
}

/** [from] to [to] at [fraction]. */
internal fun lerp(from: Float, to: Float, fraction: Float): Float = from + (to - from) * fraction

/** A path from upstream's SVG data, of absolute `M`, `L`, `C` and `Z` commands. */
internal fun svgPath(data: String): Path = Path().apply {
    SVG_COMMAND.findAll(data).forEach { command ->
        val points = command.groupValues[2].split(' ', ',').mapNotNull {
            it.toFloatOrNull()
        }.chunked(2) { Offset(it[0], it[1]) }
        when (command.groupValues[1]) {
            "M" -> moveTo(points[0].x, points[0].y)
            "L" -> lineTo(points[0].x, points[0].y)
            "C" -> cubicTo(points[0].x, points[0].y, points[1].x, points[1].y, points[2].x, points[2].y)
            else -> close()
        }
    }
}

private val SVG_COMMAND = Regex("([MLCZ])([^MLCZ]*)")
