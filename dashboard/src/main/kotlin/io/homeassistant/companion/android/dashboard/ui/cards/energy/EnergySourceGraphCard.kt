package io.homeassistant.companion.android.dashboard.ui.cards.energy

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.dashboard.energy.SourceGraphKind
import io.homeassistant.companion.android.dashboard.energy.energySourceGraph
import io.homeassistant.companion.android.dashboard.energy.energyTooltip
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import java.math.BigDecimal

/**
 * The gas, water or solar graph: each source's consumption (or production) by period, with the total in the
 * header. Port of the rendering of `hui-energy-{gas,water,solar}-graph-card` (frontend@20260624.6), without the
 * solar forecast lines.
 */
@Composable
internal fun EnergySourceGraphCard(
    card: CardConfig,
    hass: State<HassSnapshot?>,
    kind: SourceGraphKind,
    modifier: Modifier = Modifier,
) {
    val snapshot = hass.value ?: return
    fun number(value: Double) = snapshot.formats.number(BigDecimal.valueOf(value), 0, 2)
    fun formatTotal(total: Double, unit: String) = when (kind) {
        SourceGraphKind.SOLAR -> snapshot.localize(
            "$CARDS.energy_solar_graph.total_produced",
            mapOf(
                "num" to number(total),
            ),
        )
        else -> snapshot.localize(
            "$CARDS.energy_${kind.kind}_graph.total_consumed",
            mapOf(
                "num" to number(total),
                "unit" to unit,
            ),
        )
    }
    EnergyCardFrame(
        card = card,
        hass = hass,
        modifier = modifier,
        headerEnd = { data ->
            val model = remember(data, snapshot.formats) { snapshot.energySourceGraph(data, kind) }
            if (model.total != 0.0) TotalChip("${number(model.total)} ${model.chart.unit}")
        },
    ) { data ->
        val model = remember(data, snapshot.formats) { snapshot.energySourceGraph(data, kind) }
        Box(Modifier.padding(HADimens.SPACE4)) {
            EnergyBarChartView(
                chart = model.chart,
                formats = snapshot.formats,
                hidden = emptySet(),
                tooltip = { series, start -> snapshot.formats.energyTooltip(model.chart, series, start) },
                formatTotal = { formatTotal(it, model.chart.unit) },
            )
        }
    }
}

private const val CARDS = "ui.panel.lovelace.cards.energy"
