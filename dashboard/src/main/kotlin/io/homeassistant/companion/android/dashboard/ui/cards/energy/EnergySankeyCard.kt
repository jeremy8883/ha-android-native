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
import io.homeassistant.companion.android.dashboard.energy.EnergyData
import io.homeassistant.companion.android.dashboard.energy.SankeyData
import io.homeassistant.companion.android.dashboard.energy.energySankey
import io.homeassistant.companion.android.dashboard.energy.waterSankey
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.ui.cards.CardInteractions
import java.math.BigDecimal

/**
 * The energy sankey: where the energy came from and where it went, down to floors, areas and devices. Port of the
 * rendering of `hui-energy-sankey-card` (frontend@20260624.6).
 */
@Composable
internal fun EnergySankeyCard(
    card: CardConfig,
    hass: State<HassSnapshot?>,
    interactions: CardInteractions,
    modifier: Modifier = Modifier,
) {
    val groupByFloor = card.json.boolean("group_by_floor") == true
    val groupByArea = card.json.boolean("group_by_area") == true
    SankeyCard(card, hass, interactions, modifier, unit = { KWH }) { snapshot, data ->
        snapshot.energySankey(data, groupByFloor, groupByArea)
    }
}

/**
 * The water sankey: from the water sources to the devices, by floor and area. Port of the rendering of
 * `hui-water-sankey-card` (frontend@20260624.6).
 */
@Composable
internal fun WaterSankeyCard(
    card: CardConfig,
    hass: State<HassSnapshot?>,
    interactions: CardInteractions,
    modifier: Modifier = Modifier,
) {
    val groupByFloor = card.json.boolean("group_by_floor") == true
    val groupByArea = card.json.boolean("group_by_area") == true
    SankeyCard(card, hass, interactions, modifier, unit = { it.waterUnit }) { snapshot, data ->
        snapshot.waterSankey(data, groupByFloor, groupByArea)
    }
}

/** A sankey card for [data] of the collection, with the frontend's orientation and no-data message. */
@Composable
internal fun SankeyCard(
    card: CardConfig,
    hass: State<HassSnapshot?>,
    interactions: CardInteractions,
    modifier: Modifier,
    unit: (EnergyData) -> String,
    data: (HassSnapshot, EnergyData) -> SankeyData,
) {
    val snapshot = hass.value ?: return
    // Upstream lays it out vertically on small screens (MobileAwareMixin's query) unless the card says otherwise
    val configuration = LocalConfiguration.current
    val small = configuration.screenWidthDp <= MOBILE_WIDTH || configuration.screenHeightDp <= MOBILE_HEIGHT
    val layout = card.json.string("layout")
    val vertical = layout == "vertical" || layout != "horizontal" && small
    EnergyCardFrame(card, hass, modifier) { energy ->
        val sankey = remember(energy, snapshot.formats) { data(snapshot, energy) }
        if (sankey.nodes.any { it.value > 0 }) {
            SankeyChart(
                data = sankey,
                vertical = vertical,
                formatValue = { value ->
                    val digits = if (value < SMALL) SMALL_DIGITS else DEFAULT_DIGITS
                    "${snapshot.formats.number(BigDecimal.valueOf(value), 0, digits)} ${unit(energy)}"
                },
                onOpen = { interactions.openMoreInfo(it) },
                modifier = Modifier.padding(HADimens.SPACE4),
            )
        } else {
            Text(
                snapshot.localize("ui.panel.lovelace.cards.energy.no_data_period"),
                style = HATextStyle.Body,
                color = LocalHAColorScheme.current.colorTextSecondary,
                modifier = Modifier.padding(HADimens.SPACE4),
            )
        }
    }
}

private const val KWH = "kWh"
private const val MOBILE_WIDTH = 450
private const val MOBILE_HEIGHT = 500
private const val SMALL = 0.1
private const val SMALL_DIGITS = 3
private const val DEFAULT_DIGITS = 2
