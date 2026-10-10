package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.display.formatEntityAttributeValue
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.feature.number
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.moreinfo.CircularControl
import io.homeassistant.companion.android.dashboard.moreinfo.climateMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.waterHeaterMoreInfo
import kotlinx.serialization.json.JsonNull

/**
 * A thermostat card: the entity's name above its temperature dial (the details' dial, with the current temperature
 * under the target for a thermostat), and a button opening its details. Port of `hui-thermostat-card`
 * (frontend@20260624.6 src/panels/lovelace/cards/hui-thermostat-card.ts), without its features and
 * `show_current_as_primary` yet.
 */
sealed interface ThermostatCardModel {
    /** A warning in its place: the entity doesn't exist, or isn't a thermostat or water heater. */
    data class Warning(val text: String) : ThermostatCardModel

    /**
     * The entity's dial, for its [state].
     *
     * @property waterHeater whether it's a water heater's dial, which sets a single target
     * @property secondary the current temperature shown under the target (`show-secondary`)
     */
    data class Shown(
        val state: EntityState,
        val name: String,
        val control: CircularControl,
        val waterHeater: Boolean,
        val secondary: String?,
        val moreInfoLabel: String,
    ) : ThermostatCardModel
}

/** The thermostat card of [card]. */
fun HassSnapshot.thermostatCardModel(card: CardConfig): ThermostatCardModel {
    val json = card.json
    val entityId = json.string("entity")
    val state = entityId?.let(states::get)
    val domain = entityId?.substringBefore('.')
    val control = state?.let { climateMoreInfo(it)?.temperature ?: waterHeaterMoreInfo(it)?.temperature }
    return when {
        domain != CLIMATE && domain != WATER_HEATER ->
            ThermostatCardModel.Warning(localize("ui.errors.config.configuration_error"))
        state == null || control == null -> ThermostatCardModel.Warning(localize("ui.card.common.entity_not_found"))
        else -> ThermostatCardModel.Shown(
            state = state,
            name = entityNameDisplay(state, json["name"]),
            control = control,
            waterHeater = domain == WATER_HEATER,
            secondary = state.attributes[CURRENT_TEMPERATURE]
                // `currentTemperature &&`: 0 shows nothing
                ?.takeIf { domain == CLIMATE && it !is JsonNull && number(it) != 0.0 }
                ?.let { formatEntityAttributeValue(state, CURRENT_TEMPERATURE, it) },
            moreInfoLabel = localize("ui.panel.lovelace.cards.show_more_info"),
        )
    }
}

private const val CLIMATE = "climate"
private const val WATER_HEATER = "water_heater"
private const val CURRENT_TEMPERATURE = "current_temperature"
