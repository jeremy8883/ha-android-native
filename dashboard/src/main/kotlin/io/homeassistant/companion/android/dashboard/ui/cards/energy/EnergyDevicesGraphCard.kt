package io.homeassistant.companion.android.dashboard.ui.cards.energy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.energy.DeviceSlice
import io.homeassistant.companion.android.dashboard.energy.DevicesChartType
import io.homeassistant.companion.android.dashboard.energy.DevicesGraphOptions
import io.homeassistant.companion.android.dashboard.energy.EnergyDevicesGraphModel
import io.homeassistant.companion.android.dashboard.energy.energyDevicesGraph
import io.homeassistant.companion.android.dashboard.energy.isExternalStatistic
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.array
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.number
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import io.homeassistant.companion.android.dashboard.ui.cards.CardInteractions
import io.homeassistant.companion.android.dashboard.ui.cards.DEVICES_CHART_TYPE_KEY
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.cards.LocalCardPreferences
import java.math.BigDecimal
import kotlin.math.max

/**
 * The devices' consumption over the period, largest first, as bars or as a donut (the button in the header
 * switches). Port of the rendering of `hui-energy-devices-graph-card` (frontend@20260624.6).
 */
@Composable
internal fun EnergyDevicesGraphCard(
    card: CardConfig,
    hass: State<HassSnapshot?>,
    interactions: CardInteractions,
    modifier: Modifier = Modifier,
) {
    val snapshot = hass.value ?: return
    val modes = remember(card) { allowedModes(card) }
    // Kept for every devices graph, like the frontend's local storage; the first allowed mode otherwise
    val preferences = LocalCardPreferences.current
    val type = modes.firstOrNull { it.value == preferences.values[DEVICES_CHART_TYPE_KEY] } ?: modes.first()
    val options = remember(card) {
        DevicesGraphOptions(card.json.number("max_devices")?.toInt(), card.json.boolean("hide_compound_stats") == true)
    }
    val colors = LocalHAColorScheme.current
    EnergyCardFrame(
        card = card,
        hass = hass,
        modifier = modifier,
        headerEnd = {
            if (modes.size > 1) {
                val label = snapshot.localize("$DEVICES.change_chart_type")
                IconButton(
                    onClick = {
                        preferences.set(DEVICES_CHART_TYPE_KEY, modes[(modes.indexOf(type) + 1) % modes.size].value)
                    },
                    modifier = Modifier.semantics {
                        contentDescription =
                            label
                    },
                ) {
                    DashboardIcon(
                        if (type ==
                            DevicesChartType.PIE
                        ) {
                            "mdi:chart-bar"
                        } else {
                            "mdi:chart-donut"
                        },
                        colors.colorTextSecondary,
                    )
                }
            }
        },
    ) { data ->
        val model = remember(data, snapshot.formats, type, options) { snapshot.energyDevicesGraph(data, type, options) }
        val onOpen = { slice: DeviceSlice ->
            if (slice.colorIndex != null && !isExternalStatistic(slice.id) && slice.id in snapshot.states) {
                interactions.openMoreInfo(slice.id)
            }
        }
        when (type) {
            DevicesChartType.BAR -> DeviceBars(model, onOpen)
            DevicesChartType.PIE -> DeviceDonut(model, snapshot.localize("$DEVICES.total_energy_usage"), snapshot)
        }
    }
}

/** The modes the card allows (`modes`, all by default), bars first. */
private fun allowedModes(card: CardConfig): List<DevicesChartType> = card.json.array("modes")?.mapNotNull { mode ->
    DevicesChartType.entries.firstOrNull { it.value == mode.stringOrNull }
}
    ?.ifEmpty { null } ?: DevicesChartType.entries

/** A row per device: its name, and its bar (and the compared period's below it) against the largest. */
@Composable
private fun DeviceBars(model: EnergyDevicesGraphModel, onOpen: (DeviceSlice) -> Unit) {
    val colors = LocalHAColorScheme.current
    val dark = isSystemInDarkTheme()
    val largest = model.slices.maxOfOrNull { max(it.value, it.compareValue ?: 0.0) }?.takeIf { it > 0 } ?: 1.0
    Column(
        Modifier.fillMaxWidth().padding(HADimens.SPACE4),
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
    ) {
        model.slices.forEach { slice ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
            ) {
                Text(
                    slice.name,
                    style = HATextStyle.Body,
                    color = colors.colorTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(LABEL_WIDTH).clickable(role = Role.Button) { onOpen(slice) },
                )
                BoxWithConstraints(Modifier.weight(1f)) {
                    val barWidth = maxWidth
                    Column(verticalArrangement = Arrangement.spacedBy(BAR_GAP)) {
                        Bar(slice, dark, slice.value / largest, barWidth, compare = false, thick = !model.compare)
                        slice.compareValue?.let {
                            Bar(slice, dark, it / largest, barWidth, compare = true, thick = false)
                        }
                    }
                }
                Text(slice.valueText, style = HATextStyle.Body, color = colors.colorTextSecondary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Bar(slice: DeviceSlice, dark: Boolean, share: Double, width: Dp, compare: Boolean, thick: Boolean) {
    val shape = RoundedCornerShape(topEnd = HARadius.S, bottomEnd = HARadius.S)
    Box(
        Modifier
            .width(width * share.toFloat().coerceIn(0f, 1f))
            .height(if (thick) THICK else THIN)
            .background(sliceColor(slice, dark, background = true, compare), shape)
            .border(1.dp, sliceColor(slice, dark, background = false, compare), shape),
    )
}

/** The donut: the devices' shares around the total, the compared period's inside; and a legend with their amounts. */
@Composable
private fun DeviceDonut(model: EnergyDevicesGraphModel, totalLabel: String, hass: HassSnapshot) {
    val colors = LocalHAColorScheme.current
    val dark = isSystemInDarkTheme()
    Column(Modifier.fillMaxWidth().padding(HADimens.SPACE4), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.widthIn(max = DONUT).fillMaxWidth().height(DONUT),
        ) {
            val slices = model.slices.map {
                Triple(it.value, sliceColor(it, dark, true, false), sliceColor(it, dark, false, false))
            }
            val previous = model.slices.map {
                Triple(it.compareValue ?: 0.0, sliceColor(it, dark, true, true), sliceColor(it, dark, false, true))
            }
            Canvas(Modifier.size(DONUT)) {
                val radius = size.minDimension / 2
                fun ring(values: List<Triple<Double, Color, Color>>, inner: Float, outer: Float) {
                    val total = values.sumOf { it.first }.takeIf { it > 0 } ?: return
                    var angle = START_ANGLE
                    values.forEach { (value, fill, border) ->
                        val sweep = (value / total * FULL_TURN).toFloat()
                        val sector = sector(center, inner * radius, outer * radius, angle, sweep)
                        drawPath(sector, fill)
                        drawPath(sector, border, style = Stroke(1.dp.toPx()))
                        angle += sweep
                    }
                }
                if (model.compare) {
                    ring(slices, COMPARED_OUTER, OUTER)
                    ring(previous, INNER_COMPARED, COMPARED_OUTER)
                } else {
                    ring(slices, INNER, OUTER)
                }
            }
            model.total?.let {
                Text(
                    "$totalLabel\n${hass.formats.number(BigDecimal.valueOf(it), 0, 2)} kWh",
                    style = HATextStyle.Body,
                    fontWeight = FontWeight.Bold,
                    color = colors.colorTextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
        model.slices.forEach { slice ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
                modifier = Modifier.fillMaxWidth().padding(top = HADimens.SPACE1),
            ) {
                Box(
                    Modifier.size(
                        MARKER,
                    ).background(sliceColor(slice, dark, false, false), RoundedCornerShape(HARadius.S)),
                )
                Text(
                    slice.name,
                    style = HATextStyle.Body,
                    color = colors.colorTextPrimary,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.weight(1f),
                )
                Text(slice.valueText, style = HATextStyle.Body, color = colors.colorTextSecondary)
            }
        }
    }
}

/** A slice of a ring: between [inner] and [outer], from [start] degrees for [sweep] degrees, clockwise from the right. */
private fun sector(center: Offset, inner: Float, outer: Float, start: Float, sweep: Float) = Path().apply {
    fun box(radius: Float) = Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius)
    arcTo(box(outer), start, sweep, forceMoveTo = true)
    arcTo(box(inner), start + sweep, -sweep, forceMoveTo = false)
    close()
}

private fun sliceColor(slice: DeviceSlice, dark: Boolean, background: Boolean, compare: Boolean): Color =
    energyColor(slice.colorIndex?.let(::graphColorVariable) ?: UNTRACKED_COLOR, dark, null, background, compare)

private const val DEVICES = "ui.panel.lovelace.cards.energy.energy_devices_graph"
private val LABEL_WIDTH = 100.dp
private val THICK = 20.dp
private val THIN = 10.dp
private val BAR_GAP = 2.dp
private val DONUT = 260.dp
private val MARKER = 12.dp
private const val START_ANGLE = -90f
private const val FULL_TURN = 360

// The rings' radii as shares of the donut's (ECharts' `radius`)
private const val OUTER = 0.7f
private const val INNER = 0.4f
private const val COMPARED_OUTER = 0.5f
private const val INNER_COMPARED = 0.3f
