package io.homeassistant.companion.android.dashboard.ui.lovelace.cards.energy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import io.homeassistant.companion.android.dashboard.ui.components.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.components.chart.ChartLegendItem
import io.homeassistant.companion.android.dashboard.ui.lovelace.common.CardInteractions
import io.homeassistant.companion.android.dashboard.ui.lovelace.common.DEVICES_CHART_TYPE_KEY
import io.homeassistant.companion.android.dashboard.ui.lovelace.common.LocalCardPreferences
import io.homeassistant.companion.android.dashboard.ui.theme.Css
import java.math.BigDecimal
import kotlin.math.atan2
import kotlin.math.max

/**
 * The devices' consumption over the period, largest first, as bars or as a donut (the button in the header
 * switches). Port of the rendering of `hui-energy-devices-graph-card` (frontend@20260624.6).
 *
 * Upstream: `hui-energy-devices-graph-card` (src/panels/lovelace/cards/energy/hui-energy-devices-graph-card.ts)
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
    // The devices hidden from the legend (`_hiddenStats`), kept when the chart type changes
    var hidden by rememberSaveable { mutableStateOf(emptySet<String>()) }
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
        val model = remember(data, snapshot.formats, type, options, hidden) {
            snapshot.energyDevicesGraph(data, type, options, hidden)
        }
        val opens = { slice: DeviceSlice ->
            slice.colorIndex != null && !isExternalStatistic(slice.id) && slice.id in snapshot.states
        }
        val onOpen = { slice: DeviceSlice -> if (opens(slice)) interactions.openMoreInfo(slice.id) }
        when (type) {
            DevicesChartType.BAR -> DeviceBars(model, onOpen)
            DevicesChartType.PIE -> {
                DeviceDonut(model, snapshot.localize("$DEVICES.total_energy_usage"), snapshot, onOpen)
                DonutLegend(model, hidden, snapshot, opens, onHiddenChange = { hidden = it }, onOpen = onOpen)
            }
        }
    }
}

/** The modes the card allows (`modes`, all by default), bars first. */
private fun allowedModes(card: CardConfig): List<DevicesChartType> = card.json.array("modes")?.mapNotNull { mode ->
    DevicesChartType.entries.firstOrNull { it.value == mode.stringOrNull }
}
    ?.ifEmpty { null } ?: DevicesChartType.entries

/**
 * A row per device: its name, and its bar (and the compared period's below it) against the largest.
 *
 * Upstream: part of `hui-energy-devices-graph-card` (src/panels/lovelace/cards/energy/hui-energy-devices-graph-card.ts)
 */
@Composable
private fun DeviceBars(model: EnergyDevicesGraphModel, onOpen: (DeviceSlice) -> Unit) {
    val colors = LocalHAColorScheme.current
    val dark = isSystemInDarkTheme()
    val largest = model.slices.maxOfOrNull { max(it.value, it.compareValue ?: 0.0) }?.takeIf { it > 0 } ?: 1.0
    Column(
        Modifier.fillMaxWidth().padding(Css.Space.S4),
        verticalArrangement = Arrangement.spacedBy(Css.Space.S2),
    ) {
        model.slices.forEach { slice ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Css.Space.S2),
            ) {
                Text(
                    slice.name,
                    style = Css.Text.Body,
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
                Text(slice.valueText, style = Css.Text.Body, color = colors.colorTextSecondary, maxLines = 1)
            }
        }
    }
}

/**
 * A device's bar: [share] of [width], in its colour, thinner for the compared period.
 *
 * Upstream: part of `hui-energy-devices-graph-card` (src/panels/lovelace/cards/energy/hui-energy-devices-graph-card.ts)
 */
@Composable
private fun Bar(slice: DeviceSlice, dark: Boolean, share: Double, width: Dp, compare: Boolean, thick: Boolean) {
    val shape = RoundedCornerShape(topEnd = Css.Radius.Sm, bottomEnd = Css.Radius.Sm)
    Box(
        Modifier
            .width(width * share.toFloat().coerceIn(0f, 1f))
            .height(if (thick) THICK else THIN)
            .background(sliceColor(slice, dark, background = true, compare), shape)
            .border(1.dp, sliceColor(slice, dark, background = false, compare), shape),
    )
}

/**
 * The donut: the devices' shares around the total, the compared period's inside. A tap on a device's slice opens it
 * ([onOpen]), as a tap on its label does upstream.
 *
 * Upstream: part of `hui-energy-devices-graph-card` (src/panels/lovelace/cards/energy/hui-energy-devices-graph-card.ts)
 */
@Composable
private fun DeviceDonut(
    model: EnergyDevicesGraphModel,
    totalLabel: String,
    hass: HassSnapshot,
    onOpen: (DeviceSlice) -> Unit,
) {
    val colors = LocalHAColorScheme.current
    val dark = isSystemInDarkTheme()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.padding(start = Css.Space.S4, end = Css.Space.S4, top = Css.Space.S4)
            .fillMaxWidth().height(DONUT),
    ) {
        val slices = model.slices.map {
            Triple(it.value, sliceColor(it, dark, true, false), sliceColor(it, dark, false, false))
        }
        val previous = model.slices.map {
            Triple(it.compareValue ?: 0.0, sliceColor(it, dark, true, true), sliceColor(it, dark, false, true))
        }
        val rings = if (model.compare) {
            listOf(Ring(slices, COMPARED_OUTER, OUTER), Ring(previous, INNER_COMPARED, COMPARED_OUTER))
        } else {
            listOf(Ring(slices, INNER, OUTER))
        }
        Canvas(
            Modifier.size(DONUT).pointerInput(model) {
                detectTapGestures { tap ->
                    sliceAt(tap, Offset(size.width / 2f, size.height / 2f), size.width / 2f, rings)
                        ?.let { model.slices.getOrNull(it) }
                        ?.let(onOpen)
                }
            },
        ) {
            val radius = size.minDimension / 2
            rings.forEach { ring ->
                ring.sectors().forEach { (index, angle, sweep) ->
                    val (_, fill, border) = ring.values[index]
                    val sector = sector(center, ring.inner * radius, ring.outer * radius, angle, sweep)
                    drawPath(sector, fill)
                    drawPath(sector, border, style = Stroke(1.dp.toPx()))
                }
            }
        }
        model.total?.let {
            Text(
                "$totalLabel\n${hass.formats.number(BigDecimal.valueOf(it), 0, 2)} kWh",
                style = Css.Text.Body,
                fontWeight = FontWeight.Bold,
                color = colors.colorTextSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * The donut's legend (`ha-chart-base`'s, with `click-label-for-more-info`): every device with its amount, hidden or
 * not; a device's name opens it when it's an entity ([opens]), else hides or shows it.
 *
 * Upstream: part of `hui-energy-devices-graph-card` (src/panels/lovelace/cards/energy/hui-energy-devices-graph-card.ts)
 */
@Composable
private fun DonutLegend(
    model: EnergyDevicesGraphModel,
    hidden: Set<String>,
    hass: HassSnapshot,
    opens: (DeviceSlice) -> Boolean,
    onHiddenChange: (Set<String>) -> Unit,
    onOpen: (DeviceSlice) -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val items = model.legend.map { slice ->
        ChartLegendItem(
            slice.id,
            slice.name,
            sliceColor(slice, dark, background = true, compare = false),
            value = slice.valueText,
            opensLabel = opens(slice),
        )
    }
    LegendItems(items, hidden, hass, onHiddenChange) { id -> model.legend.firstOrNull { it.id == id }?.let(onOpen) }
}

/** A ring of the donut: its slices' values and colours (fill, border), between [inner] and [outer] radius shares. */
private class Ring(val values: List<Triple<Double, Color, Color>>, val inner: Float, val outer: Float) {
    /** Each slice's index, start angle and sweep, degrees clockwise from the right; none when it totals nothing. */
    fun sectors(): List<Triple<Int, Float, Float>> {
        val total = values.sumOf { it.first }.takeIf { it > 0 } ?: return emptyList()
        var angle = START_ANGLE
        return values.mapIndexed { index, (value) ->
            val sweep = (value / total * FULL_TURN).toFloat()
            Triple(index, angle, sweep).also { angle += sweep }
        }
    }
}

/** The index of the slice of [rings] at [tap], on a donut at [center] of [radius] px, if any. */
private fun sliceAt(tap: Offset, center: Offset, radius: Float, rings: List<Ring>): Int? {
    val delta = tap - center
    val distance = delta.getDistance() / radius
    val ring = rings.firstOrNull { distance >= it.inner && distance <= it.outer } ?: return null
    // Degrees clockwise from the right, from the donut's start
    val degrees = Math.toDegrees(atan2(delta.y, delta.x).toDouble()).toFloat()
    val fromStart = ((degrees - START_ANGLE) % FULL_TURN + FULL_TURN) % FULL_TURN
    return ring.sectors().firstOrNull { (_, start, sweep) ->
        val offset = ((start - START_ANGLE) % FULL_TURN + FULL_TURN) % FULL_TURN
        fromStart >= offset && fromStart < offset + sweep
    }?.first
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
private const val START_ANGLE = -90f
private const val FULL_TURN = 360

// The rings' radii as shares of the donut's (ECharts' `radius`)
private const val OUTER = 0.7f
private const val INNER = 0.4f
private const val COMPARED_OUTER = 0.5f
private const val INNER_COMPARED = 0.3f
