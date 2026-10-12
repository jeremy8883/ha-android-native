package io.homeassistant.companion.android.dashboard.feature

import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.history.GraphHistory
import io.homeassistant.companion.android.dashboard.history.SensorGraph
import io.homeassistant.companion.android.dashboard.history.historyKey
import io.homeassistant.companion.android.dashboard.history.isNumericFromAttributes
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonObject

// Port of `hui-trend-graph-card-feature` (frontend@20260624.6
// src/panels/lovelace/card-features/hui-trend-graph-card-feature.ts): a numeric sensor's recent history as the
// sensor card's line, the height of a feature.

/**
 * A trend graph of [graph]'s entity: its [history] (`null` until it loaded), drawn from the [current] state until
 * then, at [nowMillis] (`null` when unknown, which draws the loading line).
 */
data class TrendGraphFeature(
    val graph: SensorGraph,
    val history: GraphHistory?,
    val current: EntityState?,
    val nowMillis: Double?,
) : TileFeature

/** The trend graph of [state] for its [feature] config, for numeric sensors. */
internal fun HassSnapshot.trendGraph(state: EntityState, feature: JsonObject, nowMillis: Double?): TileFeature? {
    val graph = trendGraphOf(state, feature) ?: return null
    return TrendGraphFeature(graph, graphHistories[graph.historyKey], state, nowMillis)
}

private fun trendGraphOf(state: EntityState, feature: JsonObject): SensorGraph? {
    if (state.domain != "sensor" || !isNumericFromAttributes(state.attributes)) return null
    return SensorGraph(
        entityId = state.entityId,
        hoursToShow = number(feature["hours_to_show"]) ?: DEFAULT_HOURS_TO_SHOW,
        // High detail (a point per 5 pixels, keeping extremes) unless `detail: false`, which averages hourly
        detail = if (feature.boolean("detail") == false) 1 else 2,
        minY = null,
        maxY = null,
    )
}

/** The trend graphs of a tile [card], whose histories the dashboard subscribes to. */
fun HassSnapshot.trendGraphs(card: CardConfig): List<SensorGraph> {
    val state = card.entity?.takeIf { card.type == "tile" }?.let(states::get) ?: return emptyList()
    return card.json.objects("features").filter { it.string("type") == TREND_GRAPH }
        .mapNotNull { trendGraphOf(state, it) }
}

internal const val TREND_GRAPH = "trend-graph"
private const val DEFAULT_HOURS_TO_SHOW = 24.0
