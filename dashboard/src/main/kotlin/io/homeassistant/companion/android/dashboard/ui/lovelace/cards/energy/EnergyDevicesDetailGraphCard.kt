package io.homeassistant.companion.android.dashboard.ui.lovelace.cards.energy

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.homeassistant.companion.android.dashboard.energy.COMPARE_PREFIX
import io.homeassistant.companion.android.dashboard.energy.energyDevicesDetailGraph
import io.homeassistant.companion.android.dashboard.energy.energyTooltip
import io.homeassistant.companion.android.dashboard.energy.isExternalStatistic
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.number
import io.homeassistant.companion.android.dashboard.ui.lovelace.common.CardInteractions
import io.homeassistant.companion.android.dashboard.ui.theme.Css
import java.math.BigDecimal

/**
 * The devices' consumption by period, stacked, with what no device accounts for. Port of the rendering of
 * `hui-energy-devices-detail-graph-card` (frontend@20260624.6).
 *
 * Upstream: `hui-energy-devices-detail-graph-card`
 * (src/panels/lovelace/cards/energy/hui-energy-devices-detail-graph-card.ts;
 * src/panels/lovelace/cards/energy/energy-devices-detail-graph-data.ts)
 */
@Composable
internal fun EnergyDevicesDetailGraphCard(
    card: CardConfig,
    hass: State<HassSnapshot?>,
    interactions: CardInteractions,
    modifier: Modifier = Modifier,
) {
    val snapshot = hass.value ?: return
    val maxDevices = card.json.number("max_devices")?.toInt()
    EnergyCardFrame(card, hass, modifier) { data ->
        val chart = remember(data, snapshot.formats, maxDevices) { snapshot.energyDevicesDetailGraph(data, maxDevices) }
        var hidden by rememberSaveable { mutableStateOf(emptySet<String>()) }
        Box(Modifier.padding(Css.Space.S4)) {
            EnergyBarChartView(
                chart = chart,
                formats = snapshot.formats,
                hidden = hidden,
                tooltip = { series, start -> snapshot.formats.energyTooltip(chart, series, start) },
                formatTotal = {
                    snapshot.localize(
                        "ui.panel.lovelace.cards.energy.energy_usage_graph.total_consumed",
                        mapOf("num" to snapshot.formats.number(BigDecimal.valueOf(it), 0, 2)),
                    )
                },
            )
        }
        Legend(
            chart,
            hidden,
            snapshot,
            onHiddenChange = { hidden = it },
            // A device's name opens its details (`click-label-for-more-info`), when it's an entity
            opensLabel = { id -> statIdOf(id).let { !isExternalStatistic(it) && it in snapshot.states } },
            onOpen = { id -> interactions.openMoreInfo(statIdOf(id)) },
        )
    }
}

/** The statistic of a series [id]: without its order suffix (`getStatIdFromId`). */
private fun statIdOf(id: String): String = id.removePrefix(COMPARE_PREFIX).replace(ORDER_SUFFIX, "")

private val ORDER_SUFFIX = Regex("-\\d+$")
