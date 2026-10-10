package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.supportsFeature
import io.homeassistant.companion.android.dashboard.display.formatEntityAttributeValue
import io.homeassistant.companion.android.dashboard.display.formatEntityState
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.feature.attributeName
import io.homeassistant.companion.android.dashboard.feature.entityData
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Port of `more-info-climate` (frontend@20260624.6 src/dialogs/more-info/controls/more-info-climate.ts); its dials
// are in ClimateTemperature.kt and ClimateHumidity.kt.

/**
 * What a thermostat's details show: the current readings, the temperature dial (or the humidity dial, when
 * chosen), and the mode, preset, fan and swing menus.
 *
 * @property current the current temperature and humidity, as label and value
 * @property humidity the humidity dial, for thermostats with a target humidity; a pair of buttons then switches
 * between the dials, labelled [temperatureLabel] and [humidityLabel]
 */
data class ClimateMoreInfo(
    val current: List<Pair<String, String>>,
    val temperature: CircularControl,
    val humidity: CircularControl?,
    val temperatureLabel: String,
    val humidityLabel: String,
    val menus: List<SelectMenu>,
)

/** The details of a thermostat, or `null` for another entity. */
fun HassSnapshot.climateMoreInfo(state: EntityState): ClimateMoreInfo? {
    if (state.domain != CLIMATE) return null
    val current = listOf(CURRENT_TEMPERATURE, CURRENT_HUMIDITY).mapNotNull { attribute ->
        state.attributes[attribute]?.takeIf { it !is JsonNull }
            ?.let { attributeName(state, attribute) to formatEntityAttributeValue(state, attribute) }
    }
    return ClimateMoreInfo(
        current = current,
        temperature = climateTemperature(state),
        humidity = if (state.supportsFeature(FEATURE_TARGET_HUMIDITY)) climateHumidity(state) else null,
        temperatureLabel = localize("$CLIMATE_STRINGS.temperature"),
        humidityLabel = localize("$CLIMATE_STRINGS.humidity"),
        menus = listOf(hvacModeMenu(state)) + ATTRIBUTE_MENUS.mapNotNull { menu ->
            if (state.supportsFeature(menu.feature)) {
                attributeMenu(state, menu.attribute, menu.listAttribute, menu.service, menu.icon)
            } else {
                null
            }
        },
    )
}

/** A menu of an attribute, shown when the entity supports [feature]. */
private data class AttributeMenuSpec(
    val feature: Int,
    val attribute: String,
    val listAttribute: String,
    val service: String,
    val icon: String,
)

/** The mode menu: the modes in upstream's order, each with its icon. */
private fun HassSnapshot.hvacModeMenu(state: EntityState): SelectMenu = SelectMenu(
    label = localize("ui.card.climate.mode"),
    icon = hvacModeIcon(state.state),
    value = state.state,
    enabled = state.state != UNAVAILABLE,
    options = state.attributes.stringList("hvac_modes").sortedBy { HVAC_MODES.indexOf(it) }.map { mode ->
        MenuOption(
            value = mode,
            label = formatEntityState(state, mode),
            icon = hvacModeIcon(mode),
            action = CardAction.CallService(
                CLIMATE,
                "set_hvac_mode",
                JsonObject(mapOf("hvac_mode" to JsonPrimitive(mode)) + entityData(state)),
                target = null,
            ),
        )
    },
)

/** Port of `climateHvacModeIcon`. */
fun hvacModeIcon(mode: String): String = HVAC_MODE_ICONS[mode] ?: "mdi:thermostat"

internal const val CLIMATE = "climate"
internal const val CURRENT_TEMPERATURE = "current_temperature"
internal const val CURRENT_HUMIDITY = "current_humidity"
private const val UNAVAILABLE = "unavailable"
private const val CLIMATE_STRINGS = "ui.dialogs.more_info_control.climate"
private const val OSCILLATING = "mdi:arrow-oscillating"
private const val FEATURE_TARGET_HUMIDITY = 4

/** The menus after the mode's, as upstream orders them: preset, fan, swing and horizontal swing. */
private val ATTRIBUTE_MENUS = listOf(
    AttributeMenuSpec(16, "preset_mode", "preset_modes", "set_preset_mode", "mdi:tune-variant"),
    AttributeMenuSpec(8, "fan_mode", "fan_modes", "set_fan_mode", "mdi:fan"),
    AttributeMenuSpec(32, "swing_mode", "swing_modes", "set_swing_mode", OSCILLATING),
    AttributeMenuSpec(512, "swing_horizontal_mode", "swing_horizontal_modes", "set_swing_horizontal_mode", OSCILLATING),
)

/** `HVAC_MODES`, in upstream's order. */
private val HVAC_MODES = listOf("auto", "heat_cool", "heat", "cool", "dry", "fan_only", "off")

/** `CLIMATE_HVAC_MODE_ICONS`. */
private val HVAC_MODE_ICONS = mapOf(
    "cool" to "mdi:snowflake",
    "dry" to "mdi:water-percent",
    "fan_only" to "mdi:fan",
    "auto" to "mdi:thermostat-auto",
    "heat" to "mdi:fire",
    "off" to "mdi:power",
    "heat_cool" to "mdi:sun-snowflake-variant",
)
