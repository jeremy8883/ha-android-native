package io.homeassistant.companion.android.dashboard.display

import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.jsString
import io.homeassistant.companion.android.dashboard.model.yamlDump
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// Port of `ha-attribute-value` (frontend@20260624.6 src/components/ha-attribute-value.ts) and the options lists of
// src/common/entity/get_states.ts.

/**
 * An attribute of [state] as `ha-attribute-value` shows it: an object as YAML, an options list ("Off, Heat, Cool")
 * by its values' translations, else [formatEntityAttributeValue] (without the unit with [hideUnit]).
 */
fun HassSnapshot.attributeValueDisplay(state: EntityState, attribute: String, hideUnit: Boolean = false): String {
    val value = state.attributes[attribute]
    val valueAttribute = valueAttribute(state.domain, attribute)
    return when {
        value != null && isObjectValue(value) -> yamlDump(value, singleQuotes = true).trimEnd('\n')
        value is JsonArray && valueAttribute != null -> value.joinToString(", ") { item ->
            if (valueAttribute == MAIN_STATE) {
                formatEntityState(state, item.jsString())
            } else {
                formatEntityAttributeValue(state, valueAttribute, item)
            }
        }
        hideUnit -> formatEntityAttributeValueParts(state, attribute).value
        else -> formatEntityAttributeValue(state, attribute)
    }
}

/** Port of `isObjectValue`: an object, or a list holding one. */
private fun isObjectValue(value: JsonElement): Boolean =
    value is JsonObject || (value is JsonArray && value.any { it is JsonObject || it is JsonArray })

/** Port of `getValueAttribute`: the attribute (or [MAIN_STATE]) whose options [optionsAttribute] lists. */
internal fun valueAttribute(domain: String, optionsAttribute: String): String? =
    DOMAIN_OPTIONS_ATTRIBUTES[domain]?.entries?.lastOrNull { it.value == optionsAttribute }?.key

/** The key of the main state in [DOMAIN_OPTIONS_ATTRIBUTES]. */
internal const val MAIN_STATE = "_"

/** Port of `DOMAIN_OPTIONS_ATTRIBUTES`: each domain's value attributes (or main state) and their options lists. */
private val DOMAIN_OPTIONS_ATTRIBUTES: Map<String, Map<String, String>> = mapOf(
    "climate" to mapOf(
        MAIN_STATE to "hvac_modes",
        "fan_mode" to "fan_modes",
        "preset_mode" to "preset_modes",
        "swing_mode" to "swing_modes",
        "swing_horizontal_mode" to "swing_horizontal_modes",
    ),
    "event" to mapOf("event_type" to "event_types"),
    "fan" to mapOf("preset_mode" to "preset_modes"),
    "humidifier" to mapOf("mode" to "available_modes"),
    "input_select" to mapOf(MAIN_STATE to "options"),
    "select" to mapOf(MAIN_STATE to "options"),
    "light" to mapOf("effect" to "effect_list", "color_mode" to "supported_color_modes"),
    "media_player" to mapOf("sound_mode" to "sound_mode_list", "source" to "source_list"),
    "remote" to mapOf("current_activity" to "activity_list"),
    "sensor" to mapOf(MAIN_STATE to "options"),
    "vacuum" to mapOf("fan_speed" to "fan_speed_list"),
    "water_heater" to mapOf(MAIN_STATE to "operation_list", "operation_mode" to "operation_list"),
)
