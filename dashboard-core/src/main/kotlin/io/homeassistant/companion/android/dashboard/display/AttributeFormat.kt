package io.homeassistant.companion.android.dashboard.display

import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.jsString
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The formatted value of an attribute of [state] (or [value] in its place): numbers with their unit, dates,
 * translated values.
 *
 * Port of `computeAttributeValueDisplay` (src/common/entity/compute_attribute_display.ts).
 */
fun HassSnapshot.formatEntityAttributeValue(
    state: EntityState,
    attribute: String,
    value: JsonElement? = state.attributes[attribute],
): String = if (value == null || value is JsonNull) {
    localize("state.default.unknown")
} else {
    deviceClassName(state, attribute, value) ?: formatPresentAttributeValue(state, attribute, value)
}

/** The translated name of a `device_class` attribute's value, `null` when it has none. */
private fun HassSnapshot.deviceClassName(state: EntityState, attribute: String, value: JsonElement): String? =
    (value as? JsonPrimitive)?.takeIf { attribute == "device_class" && it.isString }
        ?.let { localize("component.${state.domain}.entity_component.${it.content}.name").ifEmpty { null } }

private fun HassSnapshot.formatPresentAttributeValue(
    state: EntityState,
    attribute: String,
    value: JsonElement,
): String {
    val primitive = value as? JsonPrimitive
    val number = primitive?.takeUnless { it.isString }?.content?.toDoubleOrNull()
    return when {
        number != null -> formatAttributeNumber(state, attribute, number)
        value is JsonObject || (value is JsonArray && value.any { it is JsonObject || it is JsonArray }) ->
            value.toString()
        value is JsonArray -> value.joinToString(", ") { formatEntityAttributeValue(state, attribute, it) }
        else -> primitive?.takeIf { it.isString }?.content?.let(::formatAttributeDate)
            ?: value.jsString().let { text ->
                entityTranslation(state, "state_attributes.$attribute.state.$text") ?: text
            }
    }
}

private fun HassSnapshot.formatAttributeNumber(state: EntityState, attribute: String, number: Double): String {
    val domain = state.domain
    val formatted = when {
        domain == "light" && attribute == "brightness" -> jsRound(number / BRIGHTNESS_MAX * PERCENT).toString()
        domain == "media_player" && attribute == "volume_level" -> jsRound(number * PERCENT).toString()
        domain == "input_datetime" && attribute == "year" -> jsNumberString(number)
        else -> formatNumber(number)
    }
    val unit = when {
        domain == "weather" -> weatherUnit(state, attribute)
        attribute in TEMPERATURE_ATTRIBUTES -> config.temperatureUnit
        else -> DOMAIN_ATTRIBUTES_UNITS[domain]?.get(attribute)
    }?.ifEmpty { null }
    return if (unit != null) formatted + blankBeforeUnit(unit) + unit else formatted
}

/** A date or timestamp attribute, formatted; `null` when [value] isn't one. */
private fun HassSnapshot.formatAttributeDate(value: String): String? =
    parseJsDate(value, formats.zone)?.takeIf { DATE_PREFIX.containsMatchIn(value) }?.let { instant ->
        if (TIMESTAMP.matches(value)) formats.dateTimeWithSeconds(instant) else formats.date(instant)
    }

/** Port of `getWeatherUnit` (src/data/weather.ts): the entity's own unit, else one from the unit system. */
private fun HassSnapshot.weatherUnit(state: EntityState, measure: String): String =
    WEATHER_UNIT_ATTRIBUTES[measure]?.let { state.attributes.string(it)?.ifEmpty { null } }
        ?: systemWeatherUnit(measure)

private fun HassSnapshot.systemWeatherUnit(measure: String): String {
    val length = config.unitSystem["length"].orEmpty()
    val metric = length == UNIT_KM
    return when (measure) {
        "visibility" -> length
        "precipitation" -> if (metric) "mm" else "in"
        "pressure" -> if (metric) "hPa" else "inHg"
        "apparent_temperature", "dew_point", "temperature", "templow" -> config.temperatureUnit.orEmpty()
        "wind_speed" -> "$length/h"
        "cloud_coverage", "humidity", "precipitation_probability" -> "%"
        else -> config.unitSystem[measure].orEmpty()
    }
}

/** The attribute holding each weather measure's own unit. */
private val WEATHER_UNIT_ATTRIBUTES = mapOf(
    "visibility" to "visibility_unit",
    "precipitation" to "precipitation_unit",
    "pressure" to "pressure_unit",
    "apparent_temperature" to "temperature_unit",
    "dew_point" to "temperature_unit",
    "temperature" to "temperature_unit",
    "templow" to "temperature_unit",
    "wind_speed" to "wind_speed_unit",
)

private const val UNIT_KM = "km"
private const val BRIGHTNESS_MAX = 255.0
private const val PERCENT = 100

/** Port of `TEMPERATURE_ATTRIBUTES` (src/data/entity/entity_attributes.ts). */
private val TEMPERATURE_ATTRIBUTES = setOf(
    "temperature", "current_temperature", "target_temperature", "target_temp_temp", "target_temp_high",
    "target_temp_low", "target_temp_step", "min_temp", "max_temp",
)

/** Port of `DOMAIN_ATTRIBUTES_UNITS`. */
private val DOMAIN_ATTRIBUTES_UNITS: Map<String, Map<String, String>> = mapOf(
    "climate" to listOf(
        "humidity",
        "current_humidity",
        "target_humidity_low",
        "target_humidity_high",
        "target_humidity_step",
        "min_humidity",
        "max_humidity",
    ).associateWith { "%" },
    "cover" to mapOf("current_position" to "%", "current_tilt_position" to "%"),
    "fan" to mapOf("percentage" to "%"),
    "humidifier" to listOf("humidity", "current_humidity", "min_humidity", "max_humidity", "target_humidity_step")
        .associateWith { "%" },
    "light" to mapOf(
        "color_temp" to "mired",
        "max_mireds" to "mired",
        "min_mireds" to "mired",
        "color_temp_kelvin" to "K",
        "min_color_temp_kelvin" to "K",
        "max_color_temp_kelvin" to "K",
        "brightness" to "%",
    ),
    "sun" to mapOf("azimuth" to "°", "elevation" to "°"),
    "vacuum" to mapOf("battery_level" to "%"),
    "valve" to mapOf("current_position" to "%"),
    "sensor" to mapOf("battery_level" to "%"),
    "media_player" to mapOf("volume_level" to "%"),
)

// Ports of isDate (with characters allowed after the date) and isTimestamp (src/common/string/)
private val DATE_PREFIX = Regex("""^\d{4}-(0[1-9]|1[0-2])-([12]\d|0[1-9]|3[01])""")
private val TIMESTAMP = Regex(
    """^\d{4}-(0[1-9]|1[0-2])-([12]\d|0[1-9]|3[01])[T| ](((([01]\d|2[0-3])((:?)[0-5]\d)?|24:?00)([.,]\d+(?!:))?)""" +
        """(\8[0-5]\d([.,]\d+)?)?([zZ]|([+-])([01]\d|2[0-3]):?([0-5]\d)?)?)$""",
)
