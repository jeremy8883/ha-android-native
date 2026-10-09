package io.homeassistant.companion.android.dashboard.ui.cards.energy

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.display.DisplayFormats
import io.homeassistant.companion.android.dashboard.energy.EnergyData
import io.homeassistant.companion.android.dashboard.energy.SankeyData
import io.homeassistant.companion.android.dashboard.energy.energySankey
import io.homeassistant.companion.android.dashboard.energy.flowRateShort
import io.homeassistant.companion.android.dashboard.energy.powerSankey
import io.homeassistant.companion.android.dashboard.energy.powerShort
import io.homeassistant.companion.android.dashboard.energy.waterFlowSankey
import io.homeassistant.companion.android.dashboard.energy.waterSankey
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.ui.cards.CardInteractions
import java.math.BigDecimal

/** The sankey cards drawn natively. */
internal val SANKEY_CARD_TYPES = setOf(SANKEY, WATER_SANKEY, POWER_SANKEY, WATER_FLOW_SANKEY)

/** Renders the sankey card [card] (one of [SANKEY_CARD_TYPES]). */
@Composable
internal fun AnySankeyCard(
    card: CardConfig,
    hass: State<HassSnapshot?>,
    interactions: CardInteractions,
    modifier: Modifier = Modifier,
) {
    when (card.type) {
        SANKEY -> EnergySankeyCard(card, hass, interactions, modifier)
        WATER_SANKEY -> WaterSankeyCard(card, hass, interactions, modifier)
        POWER_SANKEY -> PowerSankeyCard(card, hass, interactions, modifier)
        WATER_FLOW_SANKEY -> WaterFlowSankeyCard(card, hass, interactions, modifier)
    }
}

/**
 * The energy sankey: where the energy came from and where it went, down to floors, areas and devices. Port of the
 * rendering of `hui-energy-sankey-card` (frontend@20260624.6).
 */
@Composable
private fun EnergySankeyCard(
    card: CardConfig,
    hass: State<HassSnapshot?>,
    interactions: CardInteractions,
    modifier: Modifier = Modifier,
) {
    val groupByFloor = card.json.boolean("group_by_floor") == true
    val groupByArea = card.json.boolean("group_by_area") == true
    val spec = SankeySpec(
        format = { snapshot, _ -> { value -> snapshot.formats.amount(value, KWH) } },
        data = { snapshot, data -> snapshot.energySankey(data, groupByFloor, groupByArea) },
    )
    SankeyCard(card, hass, interactions, spec, modifier)
}

/**
 * The water sankey: from the water sources to the devices, by floor and area. Port of the rendering of
 * `hui-water-sankey-card` (frontend@20260624.6).
 */
@Composable
private fun WaterSankeyCard(
    card: CardConfig,
    hass: State<HassSnapshot?>,
    interactions: CardInteractions,
    modifier: Modifier = Modifier,
) {
    val groupByFloor = card.json.boolean("group_by_floor") == true
    val groupByArea = card.json.boolean("group_by_area") == true
    val spec = SankeySpec(
        format = { snapshot, data -> { value -> snapshot.formats.amount(value, data.waterUnit) } },
        data = { snapshot, data -> snapshot.waterSankey(data, groupByFloor, groupByArea) },
    )
    SankeyCard(card, hass, interactions, spec, modifier)
}

/**
 * The power sankey: where the power comes from and goes right now, down to floors, areas and devices. Port of the
 * rendering of `hui-power-sankey-card` (frontend@20260624.6).
 */
@Composable
private fun PowerSankeyCard(
    card: CardConfig,
    hass: State<HassSnapshot?>,
    interactions: CardInteractions,
    modifier: Modifier = Modifier,
) {
    val groupByFloor = card.json.boolean("group_by_floor") == true
    val groupByArea = card.json.boolean("group_by_area") == true
    val spec = SankeySpec(
        format = { snapshot, _ -> { value -> snapshot.formats.powerShort(value) } },
        data = { snapshot, data -> snapshot.powerSankey(data.prefs, groupByFloor, groupByArea) },
        live = true,
    )
    SankeyCard(card, hass, interactions, spec, modifier)
}

/**
 * The water flow sankey: where the water flows right now, from the sources to the devices. Port of the rendering of
 * `hui-water-flow-sankey-card` (frontend@20260624.6).
 */
@Composable
private fun WaterFlowSankeyCard(
    card: CardConfig,
    hass: State<HassSnapshot?>,
    interactions: CardInteractions,
    modifier: Modifier = Modifier,
) {
    val groupByFloor = card.json.boolean("group_by_floor") == true
    val groupByArea = card.json.boolean("group_by_area") == true
    val spec = SankeySpec(
        format = { snapshot, _ ->
            val metric = snapshot.config.unitSystem["length"] == METRIC_LENGTH
            { value -> snapshot.formats.flowRateShort(value, metric) }
        },
        data = { snapshot, data -> snapshot.waterFlowSankey(data.prefs, groupByFloor, groupByArea) },
        live = true,
    )
    SankeyCard(card, hass, interactions, spec, modifier)
}

/**
 * What a sankey card shows: the [data] of the collection, its values as [format] writes them. A [live] card is
 * built from the current states rather than the period's statistics, and says there is no data rather than none for
 * the period.
 */
internal class SankeySpec(
    val format: (HassSnapshot, EnergyData) -> (Double) -> String,
    val data: (HassSnapshot, EnergyData) -> SankeyData,
    val live: Boolean = false,
)

/** A sankey card for [data] of the collection, with the frontend's orientation and no-data message. */
@Composable
internal fun SankeyCard(
    card: CardConfig,
    hass: State<HassSnapshot?>,
    interactions: CardInteractions,
    spec: SankeySpec,
    modifier: Modifier,
) {
    val snapshot = hass.value ?: return
    // Upstream lays it out vertically on small screens (MobileAwareMixin's query) unless the card says otherwise
    val configuration = LocalConfiguration.current
    val small = configuration.screenWidthDp <= MOBILE_WIDTH || configuration.screenHeightDp <= MOBILE_HEIGHT
    val layout = card.json.string("layout")
    val vertical = layout == "vertical" || layout != "horizontal" && small
    EnergyCardFrame(card, hass, modifier) { energy ->
        // The live ones follow the states, the others only change with the collection
        val states = snapshot.states.takeIf { spec.live }
        val sankey = remember(energy, snapshot.formats, states) { spec.data(snapshot, energy) }
        if (sankey.nodes.any { it.value > 0 }) {
            SankeyChart(
                data = sankey,
                vertical = vertical,
                formatValue = spec.format(snapshot, energy),
                onOpen = { interactions.openMoreInfo(it) },
                modifier = Modifier.padding(HADimens.SPACE4),
            )
        } else {
            Text(
                snapshot.localize(if (spec.live) NO_DATA else NO_DATA_PERIOD),
                style = HATextStyle.Body,
                color = LocalHAColorScheme.current.colorTextSecondary,
                modifier = Modifier.padding(HADimens.SPACE4),
            )
        }
    }
}

/** An amount with up to 2 decimals, 3 below 0.1. */
private fun DisplayFormats.amount(value: Double, unit: String): String {
    val digits = if (value < SMALL) SMALL_DIGITS else DEFAULT_DIGITS
    return "${number(BigDecimal.valueOf(value), 0, digits)} $unit"
}

private const val SANKEY = "energy-sankey"
private const val WATER_SANKEY = "water-sankey"
private const val POWER_SANKEY = "power-sankey"
private const val WATER_FLOW_SANKEY = "water-flow-sankey"
private const val KWH = "kWh"
private const val METRIC_LENGTH = "km"
private const val NO_DATA = "ui.panel.lovelace.cards.energy.no_data"
private const val NO_DATA_PERIOD = "ui.panel.lovelace.cards.energy.no_data_period"
private const val MOBILE_WIDTH = 450
private const val MOBILE_HEIGHT = 500
private const val SMALL = 0.1
private const val SMALL_DIGITS = 3
private const val DEFAULT_DIGITS = 2
