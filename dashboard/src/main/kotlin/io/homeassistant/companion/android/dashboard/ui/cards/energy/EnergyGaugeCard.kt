package io.homeassistant.companion.android.dashboard.ui.cards.energy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.energy.EnergyGaugeModel
import io.homeassistant.companion.android.dashboard.energy.EnergyGaugeType
import io.homeassistant.companion.android.dashboard.energy.energyGauge
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.theme.resolveVariable

/**
 * An energy gauge card: self-sufficiency, grid neutrality, self-consumed solar or low-carbon energy, with the info
 * the frontend shows in a tooltip behind its info icon. Port of the rendering of the
 * `hui-energy-*-gauge-card`s and `ha-gauge` (frontend@20260624.6).
 */
@Composable
internal fun EnergyGaugeCard(
    card: CardConfig,
    hass: State<HassSnapshot?>,
    type: EnergyGaugeType,
    modifier: Modifier = Modifier,
) {
    val snapshot = hass.value ?: return
    // Like the frontend's, a gauge with nothing to show leaves no card
    val shown = snapshot.energy[card.collectionKey]?.data
    if (shown != null &&
        remember(shown, snapshot.formats) { snapshot.energyGauge(type, shown) } == EnergyGaugeModel.Hidden
    ) {
        return
    }
    EnergyCardFrame(card, hass, modifier) { data ->
        val model = remember(data, snapshot.formats) { snapshot.energyGauge(type, data) }
        val colors = LocalHAColorScheme.current
        when (model) {
            EnergyGaugeModel.Hidden -> Unit
            is EnergyGaugeModel.Message -> Text(
                model.text,
                style = HATextStyle.Body,
                color = colors.colorTextPrimary,
                modifier = Modifier.padding(HADimens.SPACE4),
            )
            is EnergyGaugeModel.Gauge -> GaugeContent(model)
        }
    }
}

@Composable
private fun GaugeContent(model: EnergyGaugeModel.Gauge) {
    val colors = LocalHAColorScheme.current
    var showInfo by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(HADimens.SPACE4),
        ) {
            Gauge(model, Modifier.widthIn(max = MAX_GAUGE_WIDTH).fillMaxWidth().aspectRatio(VIEW_WIDTH / VIEW_HEIGHT))
            Text(
                model.name,
                style = HATextStyle.Body,
                color = colors.colorTextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = HADimens.SPACE2),
            )
            if (showInfo) {
                model.info.forEach {
                    Text(
                        it,
                        style = HATextStyle.Body,
                        color = colors.colorTextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = HADimens.SPACE2),
                    )
                }
            }
        }
        DashboardIcon(
            INFO_ICON,
            colors.colorTextSecondary,
            Modifier
                .align(Alignment.TopEnd)
                .padding(HADimens.SPACE1)
                .clickable(role = Role.Button) { showInfo = !showInfo }
                .size(INFO_SIZE),
        )
    }
}

/** `ha-gauge`: an arc over a 100×55 view box, filled to the value or with coloured levels and a needle. */
@Composable
private fun Gauge(model: EnergyGaugeModel.Gauge, modifier: Modifier) {
    val dark = isSystemInDarkTheme()
    val colors = LocalHAColorScheme.current
    val measurer = rememberTextMeasurer()
    val base = remember(dark) { resolveVariable("primary-background-color", dark) ?: Color.LightGray }
    val fill = remember(dark, model.severity) {
        model.severity?.let { resolveVariable(it.variable, dark) }
            ?: colors.colorTextSecondary
    }
    val levels = remember(dark, model.levels) {
        model.levels.map { (level, variable) ->
            level to
                (resolveVariable(variable, dark) ?: Color.Gray)
        }
    }
    Canvas(modifier) {
        val scale = size.width / VIEW_WIDTH
        // The view box's origin, the arc's centre
        translate(left = size.width / 2, top = VIEW_TOP * scale) {
            val radius = ARC_RADIUS * scale
            val stroke = Stroke(width = STROKE_WIDTH * scale)
            fun arc(fromAngle: Float, sweep: Float, color: Color) = drawArc(
                color,
                HALF_TURN + fromAngle,
                sweep,
                false,
                Offset(-radius, -radius),
                Size(
                    2 * radius,
                    2 * radius,
                ),
                style = stroke,
            )
            arc(0f, HALF_TURN, base)
            if (model.needle) {
                levels.forEach { (level, color) ->
                    val start = angle(level, model)
                    arc(start, HALF_TURN - start, color)
                }
                drawNeedle(angle(model.value, model), scale, colors.colorTextPrimary)
            } else {
                arc(0f, angle(model.value, model), fill)
            }
        }
        // Fitted like ha-gauge's text: within 55 % of the width and 40 % of the height
        val reference = measurer.measure(
            model.text,
            TextStyle(color = colors.colorTextPrimary, fontSize = REFERENCE_SIZE),
        )
        val fit = minOf(
            size.width * TEXT_WIDTH / reference.size.width,
            size.height * TEXT_HEIGHT / reference.size.height,
        )
        val text = measurer.measure(
            model.text,
            TextStyle(
                color = colors.colorTextPrimary,
                fontSize =
                REFERENCE_SIZE * fit,
            ),
        )
        drawText(
            text,
            topLeft = Offset((size.width - text.size.width) / 2, size.height * TEXT_BOTTOM - text.size.height),
        )
    }
}

/** The gauge's angle for [value], 0 to 180 degrees. */
private fun angle(value: Double, model: EnergyGaugeModel.Gauge): Float =
    ((value.coerceIn(model.min, model.max) - model.min) / (model.max - model.min) * HALF_TURN).toFloat()

/** The frontend's needle path, turned to [angle] around the arc's centre. */
private fun DrawScope.drawNeedle(angle: Float, scale: Float, color: Color) {
    val path = PathParser().parsePathString(NEEDLE).toPath().apply {
        transform(
            Matrix().apply {
                rotateZ(angle)
                scale(scale, scale)
            },
        )
    }
    drawPath(path, color)
}

private const val NEEDLE = "M -34,-3 L -40,-1 A 1,1,0,0,0,-40,1 L -34,3 A 2,2,0,0,0,-34,-3 Z"
private const val INFO_ICON = "mdi:information-outline"
private const val VIEW_WIDTH = 100f
private const val VIEW_HEIGHT = 55f
private const val VIEW_TOP = 50f
private const val ARC_RADIUS = 40f
private const val STROKE_WIDTH = 12f
private const val HALF_TURN = 180f

// Where `.text` sits in ha-gauge: its largest share of the width and height, and its bottom
private const val TEXT_WIDTH = 0.55f
private const val TEXT_HEIGHT = 0.4f
private const val TEXT_BOTTOM = 0.9f
private val REFERENCE_SIZE = 100.sp
private val MAX_GAUGE_WIDTH = 250.dp
private val INFO_SIZE = 24.dp
