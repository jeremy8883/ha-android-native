package io.homeassistant.companion.android.dashboard.strategy.home

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

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
