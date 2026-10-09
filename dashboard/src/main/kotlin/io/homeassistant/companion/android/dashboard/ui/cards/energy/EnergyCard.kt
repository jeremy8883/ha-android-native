package io.homeassistant.companion.android.dashboard.ui.cards.energy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import io.homeassistant.companion.android.dashboard.energy.EnergyGaugeType
import io.homeassistant.companion.android.dashboard.energy.SourceGraphKind
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.ui.cards.CardInteractions
import java.time.ZonedDateTime

/** Renders the energy card [card] (one of [ENERGY_CARD_TYPES]). */
@Composable
internal fun EnergyCard(
    card: CardConfig,
    hass: State<HassSnapshot?>,
    now: State<ZonedDateTime?>,
    interactions: CardInteractions,
    modifier: Modifier = Modifier,
) {
    val type = card.type.orEmpty()
    when (type) {
        DATE_SELECTION -> EnergyDateSelectionCard(card, hass, now, interactions, modifier)
        COMPARE -> EnergyCompareCard(card, hass, interactions, modifier)
        GRID_BALANCE -> EnergyGridBalanceCard(card, hass, modifier)
        DISTRIBUTION -> EnergyDistributionCard(card, hass, now, modifier)
        USAGE_GRAPH -> EnergyUsageGraphCard(card, hass, now, modifier)
        SOURCES_TABLE -> EnergySourcesTableCard(card, hass, interactions, modifier)
        in GAUGES -> EnergyGaugeCard(card, hass, GAUGES.getValue(type), modifier)
        in SOURCE_GRAPHS -> EnergySourceGraphCard(card, hass, SOURCE_GRAPHS.getValue(type), modifier)
    }
}

private const val DATE_SELECTION = "energy-date-selection"
private const val COMPARE = "energy-compare"
private const val GRID_BALANCE = "energy-grid-balance"
private const val DISTRIBUTION = "energy-distribution"
private const val USAGE_GRAPH = "energy-usage-graph"
private const val SOURCES_TABLE = "energy-sources-table"
private val GAUGES = EnergyGaugeType.entries.associateBy { it.cardType }
private val SOURCE_GRAPHS = SourceGraphKind.entries.associateBy { "energy-${it.kind}-graph" }

/** The energy cards drawn natively. */
internal val ENERGY_CARD_TYPES: Set<String> =
    setOf(DATE_SELECTION, COMPARE, GRID_BALANCE, DISTRIBUTION, USAGE_GRAPH, SOURCES_TABLE) + GAUGES.keys +
        SOURCE_GRAPHS.keys
