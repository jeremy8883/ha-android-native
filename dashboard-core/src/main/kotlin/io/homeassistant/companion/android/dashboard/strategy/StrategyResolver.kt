package io.homeassistant.companion.android.dashboard.strategy

import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.ViewConfig
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.strategy.energy.electricityView
import io.homeassistant.companion.android.dashboard.strategy.energy.energyOverviewView
import io.homeassistant.companion.android.dashboard.strategy.energy.gasView
import io.homeassistant.companion.android.dashboard.strategy.energy.powerView
import io.homeassistant.companion.android.dashboard.strategy.energy.waterView
import io.homeassistant.companion.android.dashboard.strategy.home.homeAreaView
import io.homeassistant.companion.android.dashboard.strategy.home.homeMediaPlayersView
import io.homeassistant.companion.android.dashboard.strategy.home.homeOtherDevicesView
import io.homeassistant.companion.android.dashboard.strategy.home.homeOverviewView
import io.homeassistant.companion.android.dashboard.strategy.summary.climateView
import io.homeassistant.companion.android.dashboard.strategy.summary.lightView
import io.homeassistant.companion.android.dashboard.strategy.summary.maintenanceView
import io.homeassistant.companion.android.dashboard.strategy.summary.securityView
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
fun HassSnapshot.expandView(view: ViewConfig, data: StrategyData): ViewConfig? =
    resolveStrategyView(view, data)?.let { expanded ->
        val sections = expanded.json["sections"] as? JsonArray
        val newSections = sections?.map { section ->
            (section as? JsonObject)?.let { expandSection(it, data) }
                ?: section
        }
        if (newSections ==
            null
        ) {
            expanded
        } else {
            ViewConfig(JsonObject(expanded.json + ("sections" to JsonArray(newSections))))
        }
    }

/**
 * Expand a strategy view: the view's other keys (title, path, subview, ...) overlaid by the generated ones, and
 * a markdown error card when generation fails. Port of `generateLovelaceViewStrategy`.
 */
fun HassSnapshot.resolveStrategyView(view: ViewConfig, data: StrategyData = StrategyData.NONE): ViewConfig? {
    val strategy = view.strategy ?: return view
    return viewGenerator(strategy, data)?.let { generate ->
        val generated = try {
            generate()
        } catch (e: IllegalArgumentException) {
            errorContent("view", e.message)
        }
        ViewConfig(JsonObject(view.json.filterKeys { it != KEY_STRATEGY } + generated))
    }
}

/** What generates the view of [strategy], `null` when its type is not ported yet. */
private fun HassSnapshot.viewGenerator(strategy: JsonObject, data: StrategyData): (() -> JsonObject)? =
    when (strategy.string("type")) {
        "home-area" -> { -> homeAreaView(strategy.string("area"), strategy.boolean("home_panel") == true) }
        "home-overview" -> { -> homeOverviewView(strategy, data) }
        "home-media-players" -> { -> homeMediaPlayersView() }
        "home-other-devices" -> { -> homeOtherDevicesView(strategy.boolean("home_panel") == true) }
        "light" -> { -> lightView() }
        "climate" -> { -> climateView() }
        "security" -> { -> securityView() }
        "maintenance" -> { -> maintenanceView() }
        else -> energyViewGenerator(strategy, data)
    }

/** What generates the energy panel's view of [strategy], `null` when it is not one. */
private fun HassSnapshot.energyViewGenerator(strategy: JsonObject, data: StrategyData): (() -> JsonObject)? =
    when (strategy.string("type")) {
        "energy-overview" -> { -> energyOverviewView(strategy, data.energyPreferences()) }
        "energy" -> { -> electricityView(strategy, data.energyPreferences()) }
        "gas" -> { -> gasView(strategy, data.energyPreferences()) }
        "water" -> { -> waterView(strategy, data.energyPreferences()) }
        "power" -> { -> powerView(strategy, data.energyPreferences()) }
        else -> null
    }

/** Port of `generateLovelaceSectionStrategy`; unported strategy types are returned unchanged. */
private fun HassSnapshot.expandSection(section: JsonObject, data: StrategyData): JsonElement {
    val strategy = section.obj(KEY_STRATEGY)?.takeIf { it.string("type") == "common-controls" } ?: return section
    val generated = try {
        commonControlsSection(strategy, data.commonControls)
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
