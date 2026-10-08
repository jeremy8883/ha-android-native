package io.homeassistant.companion.android.dashboard.strategy

import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.ViewConfig
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.strategy.home.homeAreaView
import io.homeassistant.companion.android.dashboard.strategy.home.homeOverviewView
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * A view with its own strategy and those of its sections expanded, following upstream
 * `expandLovelaceConfigStrategies` (frontend@20260624.6 src/panels/lovelace/strategies/get-strategy.ts).
 * Returns `null` when the view's strategy is not ported yet; sections with unported strategies are kept as is.
 */
fun HassSnapshot.expandView(view: ViewConfig, data: StrategyData): ViewConfig? {
    val expanded = resolveStrategyView(view, data) ?: return null
    val sections = expanded.json["sections"] as? JsonArray ?: return expanded
    val newSections = sections.map { section ->
        (section as? JsonObject)?.let { expandSection(it, data) } ?: section
    }
    return ViewConfig(JsonObject(expanded.json + ("sections" to JsonArray(newSections))))
}

/**
 * Expand a strategy view: the view's other keys (title, path, subview, ...) overlaid by the generated ones, and
 * a markdown error card when generation fails. Port of `generateLovelaceViewStrategy`.
 */
fun HassSnapshot.resolveStrategyView(view: ViewConfig, data: StrategyData = StrategyData.NONE): ViewConfig? {
    val strategy = view.strategy ?: return view
    val generated = try {
        when (strategy.string("type")) {
            "home-area" -> homeAreaView(strategy.string("area"), strategy.boolean("home_panel") == true)
            "home-overview" -> homeOverviewView(strategy, data)
            else -> return null
        }
    } catch (e: IllegalArgumentException) {
        errorContent("view", e.message)
    }
    return ViewConfig(JsonObject(view.json.filterKeys { it != KEY_STRATEGY } + generated))
}

/** Port of `generateLovelaceSectionStrategy`; unported strategy types are returned unchanged. */
private fun HassSnapshot.expandSection(section: JsonObject, data: StrategyData): JsonElement {
    val strategy = section.obj(KEY_STRATEGY) ?: return section
    val generated = try {
        when (strategy.string("type")) {
            "common-controls" -> commonControlsSection(strategy, data.commonControls)
            else -> return section
        }
    } catch (e: IllegalArgumentException) {
        errorContent("section", e.message)
    }
    return JsonObject(section.filterKeys { it != KEY_STRATEGY } + generated)
}

private fun errorContent(level: String, message: String?) = buildJsonObject {
    putJsonArray("cards") {
        addJsonObject {
            put("type", "markdown")
            put("content", "Error loading the $level strategy:\n> Error: $message")
        }
    }
}

private const val KEY_STRATEGY = "strategy"
