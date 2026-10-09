package io.homeassistant.companion.android.dashboard.ui.cards.energy

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.energy.PowerSourcesGraphModel
import io.homeassistant.companion.android.dashboard.energy.PowerStack
import io.homeassistant.companion.android.dashboard.energy.powerSourcesGraph
import io.homeassistant.companion.android.dashboard.energy.powerTooltip
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.ui.theme.resolveVariable
import java.time.Instant
import java.time.ZonedDateTime

/**
 * The power sources graph: the power from and to solar, the battery and the grid over the period, and the home's
 * use, with a legend to hide sources. Port of the rendering of `hui-power-sources-graph-card`
 * (frontend@20260624.6).
 */
@Composable
internal fun PowerSourcesGraphCard(
    card: CardConfig,
    hass: State<HassSnapshot?>,
    now: State<ZonedDateTime?>,
    modifier: Modifier = Modifier,
) {
    val snapshot = hass.value ?: return
    val dark = isSystemInDarkTheme()
    var hidden by rememberSaveable { mutableStateOf(emptySet<String>()) }
    EnergyCardFrame(card, hass, modifier) { data ->
        val time = now.value?.toInstant() ?: Instant.EPOCH
        // Today's graph ends with the current states, so it follows them
        val graph = remember(data, snapshot.formats, snapshot.states, time) { snapshot.powerSourcesGraph(data, time) }
        if (graph.series.none { it.points.isNotEmpty() }) {
            Text(
                snapshot.localize(if (graph.today) NO_DATA else NO_DATA_PERIOD),
                style = HATextStyle.Body,
                color = LocalHAColorScheme.current.colorTextSecondary,
                modifier = Modifier.padding(HADimens.SPACE4),
            )
            return@EnergyCardFrame
        }
        Box(Modifier.padding(HADimens.SPACE4)) {
            PowerLineChart(
                graph = graph,
                formats = snapshot.formats,
                hidden = hidden,
                tooltip = { x -> snapshot.formats.powerTooltip(graph, hidden, x) },
                dark = dark,
            )
        }
        if (card.json.boolean("show_legend") != false) {
            LegendItems(legendEntries(graph, dark), hidden) { id ->
                hidden =
                    if (id in hidden) hidden - id else hidden + id
            }
        }
    }
}

/** A legend item per source and the use, in the series' colours. */
private fun legendEntries(graph: PowerSourcesGraphModel, dark: Boolean): List<LegendEntry> =
    graph.series.filter { it.stack != PowerStack.NEGATIVE }.mapNotNull { series ->
        resolveVariable(series.color, dark)?.let { LegendEntry(series.id, series.name, it) }
    }

private const val NO_DATA = "ui.panel.lovelace.cards.energy.no_data"
private const val NO_DATA_PERIOD = "ui.panel.lovelace.cards.energy.no_data_period"
