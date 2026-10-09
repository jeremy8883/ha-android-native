package io.homeassistant.companion.android.dashboard.display

import io.homeassistant.companion.android.dashboard.derive.STATE_UNAVAILABLE
import io.homeassistant.companion.android.dashboard.derive.STATE_UNKNOWN
import io.homeassistant.companion.android.dashboard.derive.TIMESTAMP_STATE_DOMAINS
import io.homeassistant.companion.android.dashboard.entity.EntityEntry
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.jsNumber
import io.homeassistant.companion.android.dashboard.model.jsTruthy
import io.homeassistant.companion.android.dashboard.model.string
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Currency
import kotlin.math.floor
import kotlinx.serialization.json.JsonObject

/**
 * The translated, formatted state of [state]: "On", "21.3 °C", "January 1, 2020", or [stateValue] instead of
 * the current state.
 *
 * Port of `computeStateDisplay` (frontend@20260624.6 src/common/entity/compute_state_display.ts).
 */
fun HassSnapshot.formatEntityState(state: EntityState, stateValue: String? = null): String {
    val value = stateValue ?: state.state
    val deviceClass = state.attributes.string("device_class")
    return when {
        value == STATE_UNKNOWN || value == STATE_UNAVAILABLE -> localize("state.default.$value")
        isNumericState(state, deviceClass) -> formatNumericState(state, value, registries.entities[state.entityId])
        state.domain in DATE_TIME_DOMAINS -> formatDateTimeState(value)
        isTimestampState(state, deviceClass) -> parseJsDate(value, formats.zone)?.let(formats::dateTime) ?: value
        else -> entityTranslation(state, "state.$value") ?: value
    }
}

private fun isNumericState(state: EntityState, deviceClass: String?): Boolean =
    isNumericFromAttributes(state.attributes) ||
        state.domain in NUMERICAL_DOMAINS ||
        (state.domain == "sensor" && deviceClass in SENSOR_NUMERIC_DEVICE_CLASSES)

private fun isTimestampState(state: EntityState, deviceClass: String?): Boolean =
    state.domain in TIMESTAMP_STATE_DOMAINS ||
        (state.domain == "sensor" && deviceClass in SENSOR_TIMESTAMP_DEVICE_CLASSES)

/** Port of `isNumericFromAttributes`. */
private fun isNumericFromAttributes(attributes: JsonObject): Boolean =
    jsTruthy(attributes["unit_of_measurement"]) || jsTruthy(attributes["state_class"])

/**
 * The entity's translation of [key] (such as `state.on`): its platform's for its translation key, else its domain's
 * for its device class, else its domain's default; `null` when none has it.
 */
internal fun HassSnapshot.entityTranslation(state: EntityState, key: String): String? {
    val entry = registries.entities[state.entityId]
    val domain = state.domain
    val deviceClass = state.attributes.string("device_class")?.ifEmpty { null }
    return entry?.translationKey?.let {
        localize("component.${entry.platform}.entity.$domain.$it.$key").ifEmpty { null }
    }
        ?: deviceClass?.let { localize("component.$domain.entity_component.$it.$key").ifEmpty { null } }
        ?: localize("component.$domain.entity_component._.$key").ifEmpty { null }
}

private fun HassSnapshot.formatNumericState(state: EntityState, value: String, entry: EntityEntry?): String {
    val unitAttribute = state.attributes.string("unit_of_measurement")
    val deviceClass = state.attributes.string("device_class")
    val durationUnit = unitAttribute?.takeIf { deviceClass == "duration" && it in DURATION_UNITS }
    val (minDigits, maxDigits) = numberFormatOptions(state, value, entry)
    val monetary = if (deviceClass == "monetary") formatMonetary(value, unitAttribute, minDigits, maxDigits) else null
    return durationUnit?.let { formatDuration(value, it, entry?.displayPrecision) }
        ?: monetary
        ?: numberWithUnit(state, formatNumber(value, minDigits, maxDigits), entry)
}

/** [number] with the entity's unit: its translated unit, else its `unit_of_measurement`. */
private fun HassSnapshot.numberWithUnit(state: EntityState, number: String, entry: EntityEntry?): String {
    val unit = entry?.translationKey?.let {
        localize("component.${entry.platform}.entity.${state.domain}.$it.unit_of_measurement").ifEmpty { null }
    } ?: state.attributes.string("unit_of_measurement")?.ifEmpty { null }
    return if (unit != null) number + blankBeforeUnit(unit) + unit else number
}

private fun HassSnapshot.formatMonetary(value: String, currency: String?, minDigits: Int?, maxDigits: Int?): String? {
    val number = jsNumber(value).takeUnless { it.isNaN() || it.isInfinite() }
    val code = currency?.let { runCatching { Currency.getInstance(it) }.getOrNull() }
    if (number == null || code == null) return null
    val min = minDigits ?: MONETARY_FRACTION_DIGITS
    return formats.currency(BigDecimal.valueOf(number), code, min, maxOf(min, maxDigits ?: min))
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

private const val MONETARY_FRACTION_DIGITS = 2
private const val HOURS_PER_DAY = 24
private const val MINUTES_PER_HOUR = 60
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
