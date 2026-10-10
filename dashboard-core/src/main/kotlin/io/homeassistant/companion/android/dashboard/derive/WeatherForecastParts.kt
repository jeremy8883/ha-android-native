package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.display.formatEntityAttributeValue
import io.homeassistant.companion.android.dashboard.display.parseJsDate
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.feature.number
import io.homeassistant.companion.android.dashboard.model.string
import java.time.Instant
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// The parts of the weather forecast card. Ports of `getSecondaryWeatherAttribute`, `getWeatherExtrema`, `getWind`
// (frontend@20260624.6 src/data/weather.ts) and the card's `_renderForecastItem`.

/** The configured `secondary_info_attribute`: its icon (or label) and value, the wind with its direction. */
internal fun HassSnapshot.secondaryAttribute(state: EntityState, attribute: String, digits: Int?): SecondaryWeather {
    val raw = number(state.attributes[attribute])
    val value = when {
        attribute == "wind_speed" -> wind(state)
        digits == 0 && raw != null && attribute in TEMPERATURE_ATTRIBUTES ->
            formatEntityAttributeValue(state, attribute, JsonPrimitive(jsRound(raw, 0)))
        else -> formatEntityAttributeValue(state, attribute)
    }
    val icon = ATTRIBUTE_ICONS[attribute]
    return SecondaryWeather(icon, if (icon == null) localize("ui.card.weather.attributes.$attribute") else null, value)
}

/** Today's high and low from the forecast, else the first entry's precipitation, else the humidity. */
internal fun HassSnapshot.defaultSecondary(
    state: EntityState,
    forecast: List<JsonObject>,
    digits: Int?,
    now: Instant,
): SecondaryWeather? {
    val precipitation = forecast.firstOrNull()?.let { number(it["precipitation"]) }
    val humidity = number(state.attributes["humidity"]).takeIf { "humidity" in state.attributes }
    val measure = when {
        precipitation != null -> "precipitation" to precipitation
        humidity != null -> "humidity" to humidity
        else -> null
    }
    return extrema(state, forecast, digits, now)?.let { SecondaryWeather(null, null, it) }
        ?: measure?.let { (attribute, value) ->
            val shown = formatEntityAttributeValue(state, attribute, JsonPrimitive(jsRound(value, 1)))
            SecondaryWeather(ATTRIBUTE_ICONS[attribute], null, shown)
        }
}

/** The highest and lowest temperatures of today's first entries ("18 °C / 9 °C"); `null` without either. */
private fun HassSnapshot.extrema(state: EntityState, forecast: List<JsonObject>, digits: Int?, now: Instant): String? {
    val today = now.atZone(formats.zone).dayOfMonth
    val todays = forecast.takeWhile { entry ->
        entry.string("datetime")?.let { parseJsDate(it, formats.zone) }?.atZone(formats.zone)?.dayOfMonth == today
    }
    var high: Double? = null
    var low: Double? = null
    val rounded = { value: Double -> if (digits == null) value else jsRound(value, digits) }
    todays.forEach { entry ->
        // Missing temperatures compare as `undefined` would: never higher or lower
        val temperature = number(entry["temperature"]) ?: Double.NaN
        val templow = number(entry["templow"])
        if (exceeds(temperature, high) { a, b -> a > b }) high = rounded(temperature)
        if (templow != null && exceeds(templow, low) { a, b -> a < b }) low = rounded(templow)
        if (!truthy(templow) && exceeds(temperature, low) { a, b -> a < b }) low = rounded(temperature)
    }
    return listOfNotNull(high, low).filter(::truthy).takeIf { it.isNotEmpty() }
        ?.joinToString(" / ") { value -> formatEntityAttributeValue(state, "temperature", JsonPrimitive(value)) }
}

/** `!current || beats(value, current)`, with JavaScript's truthiness (0 counts as unset). */
private fun exceeds(value: Double, current: Double?, beats: (Double, Double) -> Boolean): Boolean =
    current == null || !truthy(current) || beats(value, current)

private fun truthy(value: Double?): Boolean = value != null && value != 0.0 && !value.isNaN()

/** Port of `getWind`: the speed, with the direction it comes from ("10 km/h (NW)"). */
private fun HassSnapshot.wind(state: EntityState): String {
    val speed = state.attributes["wind_speed"]?.takeUnless { it is JsonNull }
    val speedText = speed?.let { formatEntityAttributeValue(state, "wind_speed", it) } ?: "-"
    val bearing = state.attributes["wind_bearing"] as? JsonPrimitive ?: return speedText
    val direction = windDirection(bearing)
    val translated = localize("ui.card.weather.cardinal_direction.${direction.lowercase()}").ifEmpty { direction }
    return "$speedText ($translated)"
}

/** Port of `getWindBearingText`: the compass point of a bearing in degrees, else the bearing as given. */
private fun windDirection(bearing: JsonPrimitive): String {
    val degrees = bearing.content.toDoubleOrNull()?.takeIf { it.isFinite() }
        ?: bearing.content.trim().takeWhile { it.isDigit() || it == '-' }.toIntOrNull()?.toDouble()
    return degrees?.let { CARDINAL_DIRECTIONS[(((it + HALF_SECTOR) / SECTOR).toInt()) % COMPASS_POINTS] }
        ?: bearing.content
}

/** Port of `getWeatherUnit` for the temperature: the entity's own unit, else the unit system's. */
internal fun HassSnapshot.temperatureUnit(state: EntityState): String =
    state.attributes.string("temperature_unit")?.ifEmpty { null } ?: config.temperatureUnit.orEmpty()

/** Port of `round`: [value] rounded to [digits] decimals, halves up. */
private fun jsRound(value: Double, digits: Int): Double {
    val factor = Math.pow(TEN, digits.toDouble())
    return Math.round(value * factor) / factor
}

private const val TEN = 10.0
private const val SECTOR = 22.5
private const val HALF_SECTOR = 11.25
private const val COMPASS_POINTS = 16

/** Port of `WEATHER_TEMPERATURE_ATTRIBUTES`. */
private val TEMPERATURE_ATTRIBUTES = setOf("temperature", "apparent_temperature", "dew_point")

/** Port of `weatherAttrIcons`. */
private val ATTRIBUTE_ICONS = mapOf(
    "apparent_temperature" to "mdi:thermometer",
    "cloud_coverage" to "mdi:weather-cloudy",
    "dew_point" to "mdi:thermometer-water",
    "humidity" to "mdi:water-percent",
    "wind_bearing" to "mdi:weather-windy",
    "wind_speed" to "mdi:weather-windy",
    "pressure" to "mdi:gauge",
    "temperature" to "mdi:thermometer",
    "uv_index" to "mdi:sun-wireless",
    "visibility" to "mdi:weather-fog",
    "precipitation" to "mdi:weather-rainy",
)

/** Port of `cardinalDirections`. */
private val CARDINAL_DIRECTIONS = listOf(
    "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW", "N",
)
