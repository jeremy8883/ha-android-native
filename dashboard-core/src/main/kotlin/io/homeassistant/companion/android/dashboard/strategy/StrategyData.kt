package io.homeassistant.companion.android.dashboard.strategy

import io.homeassistant.companion.android.dashboard.energy.EnergyPreferences
import kotlinx.serialization.json.JsonObject

/**
 * Server data that upstream strategies fetch themselves while generating. Fetched ahead of time so the ported
 * strategies stay pure functions.
 *
 * @property energyPrefs the `energy/get_prefs` result, or `null` when energy is not configured
 * @property commonControls entity ids from `usage_prediction/common_control`, or `null` when unavailable
 * @property energyHiddenCards the energy cards the user hid (the `energy` system data's `hidden_cards`), which the
 * energy panel passes to its strategy
 */
data class StrategyData(
    val energyPrefs: JsonObject?,
    val commonControls: List<String>?,
    val energyHiddenCards: List<String>? = null,
) {
    /** [energyPrefs] read as [EnergyPreferences]. */
    fun energyPreferences(): EnergyPreferences? = energyPrefs?.let(EnergyPreferences::fromJson)

    companion object {
        val NONE = StrategyData(energyPrefs = null, commonControls = null)
    }
}
