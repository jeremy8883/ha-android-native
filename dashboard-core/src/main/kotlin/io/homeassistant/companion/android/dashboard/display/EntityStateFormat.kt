package io.homeassistant.companion.android.dashboard.display

import io.homeassistant.companion.android.dashboard.derive.STATE_UNAVAILABLE
import io.homeassistant.companion.android.dashboard.derive.STATE_UNKNOWN
import io.homeassistant.companion.android.dashboard.derive.TIMESTAMP_STATE_DOMAINS
import io.homeassistant.companion.android.dashboard.entity.EntityEntry
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.jsNumber
import io.homeassistant.companion.android.dashboard.model.jsString
import io.homeassistant.companion.android.dashboard.model.jsTruthy
import io.homeassistant.companion.android.dashboard.model.string
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Currency
import kotlin.math.floor
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The translated, formatted state of [state]: "On", "21.3 °C", "January 1, 2020", or [stateValue] instead of
 * the current state.
 *
 * Port of `computeStateDisplay` (frontend@20260624.6 src/common/entity/compute_state_display.ts).
 */
fun HassSnapshot.formatEntityState(state: EntityState, stateValue: String? = null): String {
    val value = stateValue ?: state.state
    if (value == STATE_UNKNOWN || value == STATE_UNAVAILABLE) return localize("state.default.$value")

    val entry = registries.entities[state.entityId]
    val domain = state.domain
    val attributes = state.attributes
    val deviceClass = attributes.string("device_class")
    if (isNumericFromAttributes(attributes) ||
        domain in NUMERICAL_DOMAINS ||
        (domain == "sensor" && deviceClass in SENSOR_NUMERIC_DEVICE_CLASSES)
    ) {
        return formatNumericState(state, value, entry)
    }
    if (domain in DATE_TIME_DOMAINS) return formatDateTimeState(value)
    if (domain in TIMESTAMP_STATE_DOMAINS || (domain == "sensor" && deviceClass in SENSOR_TIMESTAMP_DEVICE_CLASSES)) {
        return parseJsDate(value, formats.zone)?.let(formats::dateTime) ?: value
    }
    return entry?.translationKey?.let {
        localize("component.${entry.platform}.entity.$domain.$it.state.$value").ifEmpty { null }
    }
        ?: deviceClass?.ifEmpty { null }?.let {
            localize("component.$domain.entity_component.$it.state.$value").ifEmpty { null }
        }
        ?: localize("component.$domain.entity_component._.state.$value").ifEmpty { null }
        ?: value
}

private fun HassSnapshot.formatNumericState(state: EntityState, value: String, entry: EntityEntry?): String {
    val attributes = state.attributes
    val unitAttribute = attributes.string("unit_of_measurement")
    val deviceClass = attributes.string("device_class")
    if (deviceClass == "duration" && unitAttribute in DURATION_UNITS) {
        formatDuration(value, unitAttribute!!, entry?.displayPrecision)?.let { return it }
    }
    val (minDigits, maxDigits) = numberFormatOptions(state, value, entry)
    if (deviceClass == "monetary") formatMonetary(value, unitAttribute, minDigits, maxDigits)?.let { return it }

    val number = formatNumber(value, minDigits, maxDigits)
    val unit = entry?.translationKey?.let {
        localize("component.${entry.platform}.entity.${state.domain}.$it.unit_of_measurement").ifEmpty { null }
    } ?: unitAttribute?.ifEmpty { null }
    return if (unit != null) number + blankBeforeUnit(unit) + unit else number
}

/** Port of `getNumberFormatOptions` (src/common/number/format_number.ts) as (minimum, maximum) fraction digits. */
private fun numberFormatOptions(state: EntityState, value: String, entry: EntityEntry?): Pair<Int?, Int?> {
    entry?.displayPrecision?.let { return it to it }
    val step = jsNumber(state.attributes["step"])
    return if (step.isWholeNumber() && jsNumber(value).isWholeNumber()) null to 0 else null to null
}

/**
 * Port of `formatNumber` for a string value: digits of the string are kept unless fraction digits are given, at
 * most 2 by default. Values that are not numbers are returned unchanged.
 */
internal fun HassSnapshot.formatNumber(value: String, minDigits: Int?, maxDigits: Int?): String {
    val number = jsNumber(value)
    if (number.isNaN() || number.isInfinite()) return value
    var min = minDigits ?: 0
    var max = maxDigits ?: DEFAULT_MAX_FRACTION_DIGITS
    if (minDigits == null && maxDigits == null) {
        val digits = if ('.' in value) value.substringAfter('.').length else 0
        min = digits
        max = digits
    }
    return formats.number(BigDecimal(number), min, maxOf(min, max))
}

/** Port of `formatNumber` for a number value (an attribute): at most 2 fraction digits. */
internal fun HassSnapshot.formatNumber(value: Double): String =
    if (value.isNaN() || value.isInfinite()) jsNumberString(value) else formats.number(BigDecimal(value), 0, 2)

private fun HassSnapshot.formatMonetary(value: String, currency: String?, minDigits: Int?, maxDigits: Int?): String? {
    val number = jsNumber(value)
    val code = currency?.let { runCatching { Currency.getInstance(it) }.getOrNull() } ?: return null
    if (number.isNaN() || number.isInfinite()) return null
    val min = minDigits ?: MONETARY_FRACTION_DIGITS
    return formats.currency(BigDecimal(number), code, min, maxOf(min, maxDigits ?: min))
}

/**
 * Port of `formatDuration` (src/common/datetime/format_duration.ts) for the `narrow` style: "2d 5h", "3h 20m",
 * "12m 30s". `null` when the value is not a number, so the caller falls back to numeric formatting.
 */
private fun formatDuration(value: String, unit: String, precision: Int?): String? {
    var number = value.trim().toDoubleOrNull() ?: return null
    if (precision != null) number = jsRoundTo(number, precision)
    val whole = floor(number)
    val (big, small) = when (unit) {
        "d" -> "d" to "h"
        "h" -> "h" to "m"
        else -> "m" to "s"
    }
    val parts = floor((number - whole) * if (unit == "d") HOURS_PER_DAY else MINUTES_PER_HOUR).toLong()
    return listOfNotNull("${whole.toLong()}$big", "$parts$small".takeIf { parts != 0L }).joinToString(" ")
}

/** Port of the date/time domain branch: these states are timezone-agnostic, so are shown as they are. */
private fun HassSnapshot.formatDateTimeState(value: String): String {
    val utc = ZoneOffset.UTC
    val components = value.split(" ")
    return runCatching {
        when {
            components.size == 2 -> formats.dateTime(
                LocalDateTime.parse(components.joinToString("T")).toInstant(utc),
                utc,
            )
            components.size == 1 && '-' in value -> formats.date(
                LocalDate.parse(value).atStartOfDay().toInstant(utc),
                utc,
            )
            components.size == 1 && ':' in value -> formats.time(
                LocalTime.parse(value).atDate(EPOCH_DAY).toInstant(utc),
                utc,
            )
            else -> value
        }
    }.getOrDefault(value)
}

/**
 * The formatted value of an attribute of [state] (or [value] in its place): numbers with their unit, dates,
 * translated values.
 *
 * Port of `computeAttributeValueDisplay` (src/common/entity/compute_attribute_display.ts). Weather units are not
 * ported yet, so weather attributes show without a unit.
 */
fun HassSnapshot.formatEntityAttributeValue(
    state: EntityState,
    attribute: String,
    value: JsonElement? = state.attributes[attribute],
): String {
    if (value == null || value is JsonNull) return localize("state.default.unknown")
    val domain = state.domain
    if (attribute == "device_class" && value is JsonPrimitive && value.isString) {
        localize("component.$domain.entity_component.${value.content}.name").ifEmpty { null }?.let { return it }
    }
    if (value is JsonPrimitive && !value.isString && value.content.toDoubleOrNull() != null) {
        return formatAttributeNumber(state, attribute, value.content.toDouble())
    }
    if (value is JsonPrimitive && value.isString && DATE_PREFIX.containsMatchIn(value.content)) {
        formatAttributeDate(value.content)?.let { return it }
    }
    if (value is JsonObject || (value is JsonArray && value.any { it is JsonObject || it is JsonArray })) {
        return value.toString()
    }
    if (value is JsonArray) return value.joinToString(", ") { formatEntityAttributeValue(state, attribute, it) }

    val text = value.jsString()
    val entry = registries.entities[state.entityId]
    val deviceClass = state.attributes.string("device_class")
    return entry?.translationKey?.let {
        localize("component.${entry.platform}.entity.$domain.$it.state_attributes.$attribute.state.$text").ifEmpty {
            null
        }
    }
        ?: deviceClass?.ifEmpty { null }?.let {
            localize("component.$domain.entity_component.$it.state_attributes.$attribute.state.$text").ifEmpty { null }
        }
        ?: localize("component.$domain.entity_component._.state_attributes.$attribute.state.$text").ifEmpty { null }
        ?: text
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
        domain == "weather" -> null
        attribute in TEMPERATURE_ATTRIBUTES -> config.temperatureUnit
        else -> DOMAIN_ATTRIBUTES_UNITS[domain]?.get(attribute)
    }?.ifEmpty { null }
    return if (unit != null) formatted + blankBeforeUnit(unit) + unit else formatted
}

private fun HassSnapshot.formatAttributeDate(value: String): String? {
    val instant = parseJsDate(value, formats.zone) ?: return null
    return if (TIMESTAMP.matches(value)) formats.dateTimeWithSeconds(instant) else formats.date(instant)
}

/** Port of `isNumericFromAttributes`. */
private fun isNumericFromAttributes(attributes: JsonObject): Boolean =
    jsTruthy(attributes["unit_of_measurement"]) || jsTruthy(attributes["state_class"])

/** Port of `blankBeforeUnit` (src/common/translations/blank_before_unit.ts) for English. */
fun blankBeforeUnit(unit: String): String = if (unit == "°" || unit == "%") "" else " "

/**
 * JavaScript `new Date(text)` for the ISO forms Home Assistant uses: date-only strings are UTC, date-times
 * without an offset are in [zone]. `null` when invalid.
 */
internal fun parseJsDate(text: String, zone: ZoneId): Instant? = runCatching {
    val iso = text.trim().replace(' ', 'T')
    when {
        DATE_ONLY.matches(iso) -> LocalDate.parse(iso).atStartOfDay(ZoneOffset.UTC).toInstant()
        HAS_OFFSET.containsMatchIn(
            iso.substringAfter('T', ""),
        ) -> OffsetDateTime.parse(iso.normalizeOffset()).toInstant()
        else -> LocalDateTime.parse(iso).atZone(zone).toInstant()
    }
}.getOrNull()

/** "+0000" and "+00" offsets as `OffsetDateTime` accepts them. */
private fun String.normalizeOffset(): String = replace(Regex("""([+-]\d{2})(\d{2})$"""), "$1:$2")
    .replace(Regex("""([+-]\d{2})$"""), "$1:00")

private fun Double.isWholeNumber(): Boolean = !isNaN() && !isInfinite() && this == floor(this)

/** JavaScript `Math.round`. */
private fun jsRound(value: Double): Long = floor(value + HALF).toLong()

/** Port of `round`: [value] rounded to [precision] decimals the way upstream does it. */
private fun jsRoundTo(value: Double, precision: Int): Double {
    val factor = Math.pow(TEN, precision.toDouble())
    return floor(value * factor + HALF) / factor
}

/** JavaScript `String(number)` for whole and simple decimal values. */
private fun jsNumberString(value: Double): String = when {
    value.isNaN() -> "NaN"
    value.isInfinite() -> if (value > 0) "Infinity" else "-Infinity"
    value.isWholeNumber() -> value.toLong().toString()
    else -> value.toString()
}

private const val DEFAULT_MAX_FRACTION_DIGITS = 2
private const val MONETARY_FRACTION_DIGITS = 2
private const val HOURS_PER_DAY = 24
private const val MINUTES_PER_HOUR = 60
private const val BRIGHTNESS_MAX = 255.0
private const val PERCENT = 100
private const val HALF = 0.5
private const val TEN = 10.0
private val EPOCH_DAY: LocalDate = LocalDate.of(1970, 1, 1)

private val NUMERICAL_DOMAINS = setOf("counter", "input_number", "number")
private val DATE_TIME_DOMAINS = setOf("date", "input_datetime", "time")
private val DURATION_UNITS = setOf("min", "h", "d")

/** Port of `SENSOR_TIMESTAMP_DEVICE_CLASSES` (src/data/sensor.ts). */
internal val SENSOR_TIMESTAMP_DEVICE_CLASSES = setOf("timestamp", "uptime")

/** Port of `SENSOR_NUMERIC_DEVICE_CLASSES` (src/data/sensor_numeric_device_classes.ts, generated from core). */
private val SENSOR_NUMERIC_DEVICE_CLASSES = setOf(
    "absolute_humidity", "apparent_power", "aqi", "area", "atmospheric_pressure", "battery",
    "blood_glucose_concentration", "carbon_dioxide", "carbon_monoxide", "conductivity", "current", "data_rate",
    "data_size", "distance", "duration", "energy", "energy_distance", "energy_storage", "frequency", "gas",
    "humidity", "illuminance", "irradiance", "moisture", "monetary", "nitrogen_dioxide", "nitrogen_monoxide",
    "nitrous_oxide", "ozone", "ph", "pm1", "pm10", "pm25", "pm4", "power", "power_factor", "precipitation",
    "precipitation_intensity", "pressure", "reactive_energy", "reactive_power", "signal_strength",
    "sound_pressure", "speed", "sulphur_dioxide", "temperature", "temperature_delta",
    "volatile_organic_compounds", "volatile_organic_compounds_parts", "voltage", "volume", "volume_flow_rate",
    "volume_storage", "water", "weight", "wind_direction", "wind_speed",
)

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
private val DATE_ONLY = Regex("""^\d{4}-\d{2}-\d{2}$""")
private val HAS_OFFSET = Regex("""([zZ]|[+-]\d{2}(:?\d{2})?)$""")
