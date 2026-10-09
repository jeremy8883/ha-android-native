package io.homeassistant.companion.android.dashboard.strategy.home

import io.homeassistant.companion.android.dashboard.entity.ENTITY_CATEGORY_NONE
import io.homeassistant.companion.android.dashboard.entity.EntityFilter
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.entity.filterEntities
import io.homeassistant.companion.android.dashboard.entity.findEntities
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.strategy.StrategyData
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

// The summaries part of the home overview (HomeOverviewViewStrategy.generate, frontend@20260624.6
// src/panels/lovelace/strategies/home/home-overview-view-strategy.ts)

/** Repairs, updates and discovered devices, then the summaries and custom shortcuts in the user's order. */
internal fun HassSnapshot.summaryCards(
    config: JsonObject,
    allEntities: List<String>,
    data: StrategyData,
    homePanel: Boolean,
): List<JsonObject> {
    val cards = mutableListOf(
        adminCard("repairs", "/config/repairs?historyBack=1"),
        adminCard("updates", "/config/updates?historyBack=1"),
        buildJsonObject {
            put("type", "discovered-devices")
            put("hide_empty", true)
        },
    )
    for (item in resolveShortcutItems(config["shortcuts"] as? JsonArray)) {
        if (item.string("type") == "summary") {
            if (item.boolean("hidden") == true) continue
            summaryCard(item.string("key"), allEntities, data, homePanel)?.let(cards::add)
        } else {
            cards += buildJsonObject {
                put("type", "shortcut")
                item.string("label")?.let { put("label", it) }
                item.string("icon")?.let { put("icon", it) }
                item.string("color")?.let { put("color", it) }
                putJsonObject("tap_action") {
                    put("action", "navigate")
                    item["path"]?.let { put("navigation_path", it) }
                }
            }
        }
    }
    return cards
}

private fun adminCard(type: String, path: String) = buildJsonObject {
    put("type", type)
    put("hide_empty", true)
    putJsonObject("tap_action") {
        put("action", "navigate")
        put("navigation_path", path)
    }
}

/** Port of the `summaryCardBuilders` entries: the card for a summary key, or `null` when it has nothing to show. */
private fun HassSnapshot.summaryCard(
    key: String?,
    allEntities: List<String>,
    data: StrategyData,
    homePanel: Boolean,
): JsonObject? {
    val backToHome = if (homePanel) "&backPath=/home" else ""
    val panelSummary = PANEL_SUMMARIES[key]
    return when {
        key == null -> null
        panelSummary != null -> {
            val path = "/$key?historyBack=1" + if (panelSummary == HomeSummary.MAINTENANCE) backToHome else ""
            homeSummaryCard(key, path).takeIf {
                key in panels && findEntities(allEntities, panelSummary.filters).isNotEmpty()
            }
        }
        key == "media_players" -> homeSummaryCard(key, "media-players")
            .takeIf { findEntities(allEntities, HomeSummary.MEDIA_PLAYERS.filters).isNotEmpty() }
        key == "weather" -> weatherEntity()?.let(::weatherCard)
        key == "energy" -> homeSummaryCard(key, "/energy?historyBack=1$backToHome")
            .takeIf { "energy" in panels && hasGridEnergy(data) }
        else -> null
    }
}

/** The summaries shown when their panel exists, by key (which is also the panel's path). */
private val PANEL_SUMMARIES = mapOf(
    "light" to HomeSummary.LIGHT,
    "climate" to HomeSummary.CLIMATE,
    "security" to HomeSummary.SECURITY,
    "maintenance" to HomeSummary.MAINTENANCE,
)

private fun homeSummaryCard(name: String, path: String) = buildJsonObject {
    put("type", "home-summary")
    put("summary", name)
    putJsonObject("tap_action") {
        put("action", "navigate")
        put("navigation_path", path)
    }
}

private fun HassSnapshot.weatherCard(entityId: String) = buildJsonObject {
    put("type", "tile")
    put("entity", entityId)
    put("name", localize("ui.panel.lovelace.strategy.home.summary_list.weather"))
    putJsonArray("state_content") {
        add("temperature")
        add("state")
    }
}

private fun HassSnapshot.weatherEntity(): String? = filterEntities(
    states.keys.toList(),
    EntityFilter(domains = setOf("weather"), entityCategories = setOf(ENTITY_CATEGORY_NONE)),
)
    .minOrNull()

private fun HassSnapshot.hasGridEnergy(data: StrategyData): Boolean {
    // Upstream only asks for the prefs when the energy integration is loaded
    if ("energy" !in config.components) return false
    return data.energyPrefs?.objects("energy_sources")
        ?.any { it.string("type") == "grid" && !it.string("stat_energy_from").isNullOrEmpty() } == true
}
