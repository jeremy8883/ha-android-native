package io.homeassistant.companion.android.dashboard.strategy

import kotlinx.serialization.json.JsonObject

/**
 * Server data that upstream strategies fetch themselves while generating. Fetched ahead of time so the ported
 * strategies stay pure functions.
 *
 * @property energyPrefs the `energy/get_prefs` result, or `null` when energy is not configured
 * @property commonControls entity ids from `usage_prediction/common_control`, or `null` when unavailable
 */
data class StrategyData(val energyPrefs: JsonObject?, val commonControls: List<String>?) {
    companion object {
        val NONE = StrategyData(energyPrefs = null, commonControls = null)
    }
}
