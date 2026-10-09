package io.homeassistant.companion.android.dashboard.strategy.summary

import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The built-in panels that show one generated view: lights, climate, security and maintenance. Each panel's
 * component name, url path and view strategy type are the same.
 */
val SUMMARY_PANELS = setOf("light", "climate", "security", "maintenance")

/**
 * The dashboard [panel] (one of [SUMMARY_PANELS]) shows: its view strategy, titled with the panel's name as the
 * panel's top bar is. Port of `_setLovelace` and `render` (frontend@20260624.6
 * src/panels/{light,climate,security,maintenance}/ha-panel-*.ts), whose config is `{views: [{strategy}]}`.
 */
fun HassSnapshot.summaryPanelDashboard(panel: String): JsonObject {
    require(panel in SUMMARY_PANELS) { "Not a summary panel: $panel" }
    return buildJsonObject {
        put("title", localize("panel.$panel"))
        putJsonArray("views") {
            addJsonObject { putJsonObject("strategy") { put("type", panel) } }
        }
    }
}
