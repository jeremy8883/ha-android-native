package io.homeassistant.companion.android.dashboard.strategy.home

import io.homeassistant.companion.android.dashboard.entity.HassConfig
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Settings of the home dashboard, stored by the frontend in `frontend/get_system_data {key: "home"}`.
 * Values are kept raw and passed through to the views untouched.
 */
data class HomeDashboardConfig(
    val favoriteEntities: JsonElement? = null,
    val homePanel: Boolean = true,
    val hideWelcomeMessage: JsonElement? = null,
    val hideSuggestedEntities: JsonElement? = null,
    val shortcuts: JsonElement? = null,
) {
    companion object {
        /** The config the `/home` panel builds from its system data (src/panels/home/ha-panel-home.ts `_strategyConfig`). */
        fun fromSystemData(value: JsonObject?): HomeDashboardConfig = HomeDashboardConfig(
            favoriteEntities = value?.get("favorite_entities"),
            homePanel = true,
            hideWelcomeMessage = value?.get("hide_welcome_message"),
            hideSuggestedEntities = value?.get("hide_suggested_entities"),
            shortcuts = value?.get("shortcuts"),
        )
    }
}

/**
 * The home dashboard: an overview, one subview per area, then media players and other devices. Views are
 * returned as strategy views, expanded later by [resolveStrategyView].
 *
 * Port of `HomeDashboardStrategy.generate` (frontend@20260624.6
 * src/panels/lovelace/strategies/home/home-dashboard-strategy.ts).
 */
fun HassSnapshot.homeDashboard(config: HomeDashboardConfig): JsonObject {
    if (this.config.state == HassConfig.STATE_NOT_RUNNING) return singleCardDashboard("starting")
    if (this.config.recoveryMode) return singleCardDashboard("recovery-mode")

    return buildJsonObject {
        putJsonArray("views") {
            addJsonObject {
                put("icon", "mdi:home")
                put("path", "overview")
                putJsonObject("strategy") {
                    put("type", "home-overview")
                    putIfPresent("favorite_entities", config.favoriteEntities)
                    put("home_panel", config.homePanel)
                    putIfPresent("hide_welcome_message", config.hideWelcomeMessage)
                    putIfPresent("hide_suggested_entities", config.hideSuggestedEntities)
                    putIfPresent("shortcuts", config.shortcuts)
                }
            }
            registries.areas.values.forEach { area ->
                addJsonObject {
                    put("title", area.name)
                    put("path", "areas-${area.areaId}")
                    put("subview", true)
                    putJsonObject("strategy") {
                        put("type", "home-area")
                        put("area", area.areaId)
                        put("home_panel", config.homePanel)
                    }
                }
            }
            addJsonObject {
                put("title", HomeSummary.MEDIA_PLAYERS.label(localize))
                put("path", "media-players")
                put("subview", true)
                putJsonObject("strategy") { put("type", "home-media-players") }
                put("icon", HomeSummary.MEDIA_PLAYERS.icon)
            }
            addJsonObject {
                put("title", localize("ui.panel.lovelace.strategy.home.devices"))
                put("path", "other-devices")
                put("subview", true)
                putJsonObject("strategy") {
                    put("type", "home-other-devices")
                    put("home_panel", config.homePanel)
                }
                put("icon", "mdi:devices")
            }
        }
    }
}

private fun singleCardDashboard(cardType: String) = buildJsonObject {
    putJsonArray("views") {
        addJsonObject {
            put("type", "sections")
            putJsonArray("sections") {
                addJsonObject { putJsonArray("cards") { addJsonObject { put("type", cardType) } } }
            }
        }
    }
}

// Upstream passes undefined values, which JSON drops
private fun JsonObjectBuilder.putIfPresent(key: String, value: JsonElement?) {
    if (value != null) put(key, value)
}
