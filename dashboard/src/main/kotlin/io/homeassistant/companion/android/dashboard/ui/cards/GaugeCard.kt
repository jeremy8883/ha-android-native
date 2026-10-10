package io.homeassistant.companion.android.dashboard.ui.cards

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.style.TextOverflow
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.derive.GaugeCardModel
import io.homeassistant.companion.android.dashboard.derive.gaugeCardModel
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.ui.theme.toColor

/**
 * A gauge card, port of `hui-gauge-card` with `ha-gauge` (frontend@20260624.6 src/panels/lovelace/cards/,
 * src/components/ha-gauge.ts): a half circle filled to the value in its severity's colour, or a needle over the
 * coloured levels, the value under it and the name below.
 */
@Composable
internal fun GaugeCard(
    card: CardConfig,
    hass: State<HassSnapshot?>,
    interactions: CardInteractions,
    modifier: Modifier = Modifier,
) {
    val gauge by remember(card) { derivedStateOf { hass.value?.gaugeCardModel(card) } }
    val model = gauge ?: return
    model.warning?.let { return CardWarning(it, modifier) }
    val colors = LocalHAColorScheme.current
    DashboardCardSurface(modifier = modifier.elementGestures(model.actions, interactions)) {
        Column(Modifier.fillMaxWidth().padding(HADimens.SPACE3), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(VIEW_WIDTH / VIEW_HEIGHT),
                contentAlignment = Alignment.BottomCenter,
            ) {
                GaugeArc(model, Modifier.fillMaxWidth().aspectRatio(VIEW_WIDTH / VIEW_HEIGHT))
                Text(model.valueText, style = HATextStyle.HeadlineMedium, color = colors.colorTextPrimary, maxLines = 1)
            }
            Text(
                model.name,
                style = HATextStyle.Body,
                color = colors.colorTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** `ha-gauge`'s half circle in its 100 × 55 box: the base, the levels, then the value's fill or the needle. */
@Composable
private fun GaugeArc(model: GaugeCardModel, modifier: Modifier) {
    val colors = LocalHAColorScheme.current
    val base = colors.colorFillDisabledLoudResting
    val fill = model.color?.toColor() ?: colors.colorFillPrimaryLoudResting
    val levels = model.levels.sortedBy { it.level }.map { it.level to (it.color.toColor() ?: base) }
    val needleColor = colors.colorTextPrimary
    val angle by animateFloatAsState(angleOf(model.value, model.min, model.max), tween(ANIMATION_MS), label = "gauge")
    Canvas(modifier) {
        val unit = size.width / VIEW_WIDTH
        val center = Offset(size.width / 2, RADIUS_OFFSET * unit)
        val ring = Ring(center, RADIUS * unit, STROKE * unit)
        arc(ring, base, 0f, HALF)
        levels.forEachIndexed { i, (level, color) ->
            val start = angleOf(level, model.min, model.max)
            val end = levels.getOrNull(i + 1)?.first?.let { angleOf(it, model.min, model.max) } ?: HALF
            arc(ring, color, start, end - start)
        }
        if (model.needle) {
            rotate(angle, center) { drawPath(needle(center, unit), needleColor) }
        } else {
            arc(ring, fill, 0f, angle)
        }
    }
}

/** Where the gauge's arcs run: around [center] at [radius], [stroke] thick. */
private class Ring(val center: Offset, val radius: Float, val stroke: Float)

/** A stretch of the ring from [from]° (from the left) over [sweep]°. */
private fun DrawScope.arc(ring: Ring, color: Color, from: Float, sweep: Float) {
    if (sweep <= 0f) return
    drawArc(
        color,
        startAngle = HALF + from,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = Offset(ring.center.x - ring.radius, ring.center.y - ring.radius),
        size = Size(ring.radius * 2, ring.radius * 2),
        style = Stroke(ring.stroke),
    )
}

/** The needle pointing left (0°), as `M -34,-3 L -40,-1 A 1,1 … L -34,3 A 2,2 … Z`, rotated by the value. */
private fun needle(center: Offset, unit: Float) = Path().apply {
    moveTo(center.x - NEEDLE_BASE * unit, center.y - NEEDLE_HALF * unit)
    lineTo(center.x - RADIUS * unit, center.y - unit)
    lineTo(center.x - RADIUS * unit, center.y + unit)
    lineTo(center.x - NEEDLE_BASE * unit, center.y + NEEDLE_HALF * unit)
    close()
}

/** `getAngle`: the value's place between [min] and [max], as 0–180°. */
private fun angleOf(value: Double, min: Double, max: Double): Float {
    val span = (max - min).takeIf { it != 0.0 } ?: 1.0
    return ((value.coerceIn(minOf(min, max), maxOf(min, max)) - min) / span * HALF).toFloat()
}

private const val HALF = 180f
private const val VIEW_WIDTH = 100f
private const val VIEW_HEIGHT = 55f
private const val RADIUS = 40f
private const val RADIUS_OFFSET = 50f
private const val STROKE = 12f
private const val NEEDLE_BASE = 34f
private const val NEEDLE_HALF = 3f
private const val ANIMATION_MS = 1000
