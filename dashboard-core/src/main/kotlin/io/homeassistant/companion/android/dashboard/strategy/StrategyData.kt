package io.homeassistant.companion.android.dashboard.strategy

import io.homeassistant.companion.android.dashboard.energy.EnergyPreferences
import kotlinx.serialization.json.JsonObject

/**
 * Server data that upstream strategies fetch themselves while generating. Fetched ahead of time so the ported
 * strategies stay pure functions.
 *
 * @property energyPrefs the `energy/get_prefs` result, or `null` when energy is not configured
 * @property commonControls the entities `usage_prediction/common_control` predicts
 * @property energyHiddenCards the energy cards the user hid (the `energy` system data's `hidden_cards`), which the
 * energy panel passes to its strategy
 */
data class StrategyData(
    val energyPrefs: JsonObject?,
    val commonControls: CommonControls,
    val energyHiddenCards: List<String>? = null,
) {
    /** [energyPrefs] read as [EnergyPreferences]. */
    fun energyPreferences(): EnergyPreferences? = energyPrefs?.let(EnergyPreferences::fromJson)

    companion object {
        val NONE = StrategyData(energyPrefs = null, commonControls = CommonControls.NotLoaded)
    }
}

/** What the common controls section knows of the entities the user controls most often. */
sealed interface CommonControls {
    /** The `usage_prediction` integration isn't loaded. */
    data object NotLoaded : CommonControls

    /** The predicted [entities], most used first. */
    data class Predicted(val entities: List<String>) : CommonControls

    /** The prediction couldn't be loaded, with the server's [message]: the section shows the error, as upstream's. */
    data class Failed(val message: String) : CommonControls
}
