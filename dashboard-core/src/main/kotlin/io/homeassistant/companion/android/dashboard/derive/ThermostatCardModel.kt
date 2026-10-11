package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.feature.TileFeature
import io.homeassistant.companion.android.dashboard.feature.tileFeatures
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.moreinfo.CircularControl
import io.homeassistant.companion.android.dashboard.moreinfo.CircularSecondary
import io.homeassistant.companion.android.dashboard.moreinfo.climateMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.climateSecondary
import io.homeassistant.companion.android.dashboard.moreinfo.climateTemperature
import io.homeassistant.companion.android.dashboard.moreinfo.waterHeaterMoreInfo

/**
 * A thermostat card: the entity's name above its temperature dial (the details' dial, with the current temperature
 * under the target for a thermostat, or above the targets with `show_current_as_primary`), a button opening its
 * details, and its features. Port of `hui-thermostat-card` (frontend@20260624.6
 * src/panels/lovelace/cards/hui-thermostat-card.ts).
 */
sealed interface ThermostatCardModel {
    /** A warning in its place: the entity doesn't exist, or isn't a thermostat or water heater. */
    data class Warning(val text: String) : ThermostatCardModel

    /**
     * The entity's dial, for its [state].
     *
     * @property waterHeater whether it's a water heater's dial, which sets a single target
     * @property secondary under the big number (`show-secondary`): the current temperature, or the targets when the
     *   current temperature is the big number
     * @property features the card's features (`hui-card-features`), in [featureColor]
     * @property available whether the entity is available, for the features
     */
    data class Shown(
        val state: EntityState,
        val name: String,
        val control: CircularControl,
        val waterHeater: Boolean,
        val secondary: CircularSecondary?,
        val moreInfoLabel: String,
        val features: List<TileFeature> = emptyList(),
        val featureColor: DisplayColor? = null,
        val available: Boolean = true,
    ) : ThermostatCardModel
}

/** The thermostat card of [card]. */
fun HassSnapshot.thermostatCardModel(card: CardConfig): ThermostatCardModel {
    val json = card.json
    val entityId = json.string("entity")
    val state = entityId?.let(states::get)
    val domain = entityId?.substringBefore('.')
    val currentAsPrimary = json.boolean("show_current_as_primary") == true
    val control = state?.let {
        if (domain == CLIMATE) {
            climateMoreInfo(it)?.let { _ -> climateTemperature(it, currentAsPrimary) }
        } else {
            waterHeaterMoreInfo(it)?.temperature
        }
    }
    return when {
        domain != CLIMATE && domain != WATER_HEATER ->
            ThermostatCardModel.Warning(localize("ui.errors.config.configuration_error"))
        state == null || control == null -> ThermostatCardModel.Warning(localize("ui.card.common.entity_not_found"))
        else -> ThermostatCardModel.Shown(
            state = state,
            name = entityNameDisplay(state, json["name"]),
            control = control,
            waterHeater = domain == WATER_HEATER,
            secondary = if (domain == CLIMATE) climateSecondary(state, currentAsPrimary) else null,
            moreInfoLabel = localize("ui.panel.lovelace.cards.show_more_info"),
            features = tileFeatures(card),
            featureColor = stateColor(state),
            available = state.state != STATE_UNAVAILABLE,
        )
    }
}

private const val CLIMATE = "climate"
private const val WATER_HEATER = "water_heater"
