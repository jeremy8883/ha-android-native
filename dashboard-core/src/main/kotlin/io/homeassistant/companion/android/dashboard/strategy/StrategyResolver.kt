package io.homeassistant.companion.android.dashboard.strategy

import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.ViewConfig
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.strategy.home.homeAreaView
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Expand a strategy view into a regular view: the view's other keys (title, path, subview, ...) overlaid by the
 * generated ones, and a markdown error card when generation fails. Returns [view] unchanged when it is not a
 * strategy view, and `null` when its strategy type is not ported yet.
 *
 * Port of `generateLovelaceViewStrategy` (frontend@20260624.6 src/panels/lovelace/strategies/get-strategy.ts).
 */
fun HassSnapshot.resolveStrategyView(view: ViewConfig): ViewConfig? {
    val strategy = view.strategy ?: return view
    val generated = try {
        when (strategy.string("type")) {
            "home-area" -> homeAreaView(strategy.string("area"), strategy.boolean("home_panel") == true)
            else -> return null
        }
    } catch (e: IllegalArgumentException) {
        strategyErrorView(e.message)
    }
    return ViewConfig(JsonObject(view.json.filterKeys { it != "strategy" } + generated))
}

private fun strategyErrorView(message: String?) = buildJsonObject {
    putJsonArray("cards") {
        addJsonObject {
            put("type", "markdown")
            put("content", "Error loading the view strategy:\n> Error: $message")
        }
    }
}
