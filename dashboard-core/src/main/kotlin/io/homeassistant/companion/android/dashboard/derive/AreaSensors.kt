package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.display.blankBeforeUnit
import io.homeassistant.companion.android.dashboard.display.formatEntityState
import io.homeassistant.companion.android.dashboard.entity.AreaEntry
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.history.isNumericFromAttributes
import io.homeassistant.companion.android.dashboard.model.jsNumber
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import java.math.BigDecimal
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

// The sensors line of the area card: `_computeSensorsDisplay` of `hui-area-card` (frontend@20260624.6
// src/panels/lovelace/cards/hui-area-card.ts).

/** The values of `sensor_classes` joined with dots, `null` when there are none. */
internal fun HassSnapshot.areaSensorsLine(json: JsonObject, area: AreaEntry, excluded: Set<String>): String? {
    val classes = (json["sensor_classes"] as? JsonArray)?.mapNotNull { it.stringOrNull } ?: return null
    val sensors = areaEntities(area.areaId, setOf("sensor"), classes.toSet(), excluded)
    return classes.mapNotNull { sensorClass ->
        when {
            sensorClass == TEMPERATURE && area.temperatureEntityId != null -> areaEntityValue(area.temperatureEntityId)
            sensorClass == HUMIDITY && area.humidityEntityId != null -> areaEntityValue(area.humidityEntityId)
            else -> aggregate(sensorClass, sensors, area.areaId, excluded)
        }
    }.filter { it.isNotEmpty() }.joinToString(SEPARATOR_DOT).ifEmpty { null }
}

/** The area's own temperature or humidity entity's state, empty while unavailable or unknown. */
private fun HassSnapshot.areaEntityValue(entityId: String): String {
    val state = states[entityId] ?: return ""
    return if (state.state == STATE_UNAVAILABLE || state.state == STATE_UNKNOWN) "" else formatEntityState(state)
}

/** The numbers a sensor class collected, in the first one's unit. */
private class Readings {
    val values = mutableListOf<Double>()
    var unit: String? = null
    val devices = mutableSetOf<String>()
}

/**
 * The sum (for energy and the like) or median of the class's numeric sensors in the first one's unit, plus the
 * attributes standing for it on thermostats and humidifiers of devices without such a sensor; `null` without any.
 */
private fun HassSnapshot.aggregate(
    sensorClass: String,
    sensors: List<String>,
    areaId: String,
    excluded: Set<String>,
): String? {
    val readings = Readings()
    sensors.forEach { entityId -> addSensorReading(readings, entityId, sensorClass) }
    SENSOR_ATTRIBUTE_SOURCES[sensorClass]?.let { sources ->
        addAttributeReadings(readings, sources, sensorClass, areaId, excluded)
    }
    if (readings.values.isEmpty()) return null
    val value = if (sensorClass in SUM_DEVICE_CLASSES) readings.values.sum() else median(readings.values)
    val unit = readings.unit?.ifEmpty { null }
    return formats.number(BigDecimal.valueOf(value), 0, 1) + unit?.let { blankBeforeUnit(it) + it }.orEmpty()
}

private fun HassSnapshot.addSensorReading(readings: Readings, entityId: String, sensorClass: String) {
    val state = states[entityId] ?: return
    val number = jsNumber(state.state)
    val usable = state.state != STATE_UNAVAILABLE &&
        state.state != STATE_UNKNOWN &&
        isNumericFromAttributes(state.attributes) &&
        !number.isNaN()
    if (!usable || state.attributes.string("device_class") != sensorClass) return
    val unit = state.attributes.string("unit_of_measurement")
    if (readings.unit.isNullOrEmpty()) readings.unit = unit
    if (unit == readings.unit) {
        readings.values.add(number)
        registries.entities[entityId]?.deviceId?.let(readings.devices::add)
    }
}

private fun HassSnapshot.addAttributeReadings(
    readings: Readings,
    sources: Map<String, String>,
    sensorClass: String,
    areaId: String,
    excluded: Set<String>,
) {
    areaEntities(areaId, sources.keys, deviceClasses = null, excluded).forEach { entityId ->
        val state = states[entityId] ?: return@forEach
        // A device whose sensor already counted isn't counted again through its thermostat
        if (registries.entities[entityId]?.deviceId?.let { it in readings.devices } == true) return@forEach
        val attribute = state.attributes[sources[state.domain]]
        val value = attribute?.takeIf { it !is JsonNull }?.let(::jsNumber)
        if (value == null || value.isNaN()) return@forEach
        if (readings.unit.isNullOrEmpty()) readings.unit = attributeUnit(sensorClass, state.domain)
        readings.values.add(value)
    }
}

/** `_getAttributeUnit`. */
private fun HassSnapshot.attributeUnit(sensorClass: String, domain: String): String = when {
    sensorClass == TEMPERATURE && domain == "climate" -> config.temperatureUnit.orEmpty()
    sensorClass == HUMIDITY -> "%"
    else -> ""
}

/** `_computeMedianValue`. */
private fun median(values: List<Double>): Double {
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 0) (sorted[middle] + sorted[middle - 1]) / 2 else sorted[middle]
}

private const val TEMPERATURE = "temperature"
private const val HUMIDITY = "humidity"
private const val SEPARATOR_DOT = " · "

/** `SUM_DEVICE_CLASSES`: summed rather than averaged. */
private val SUM_DEVICE_CLASSES = setOf(
    "power",
    "apparent_power",
    "reactive_power",
    "energy",
    "reactive_energy",
    "current",
    "gas",
    "monetary",
    "volume",
    "water",
)

/** `SENSOR_ATTRIBUTE_SOURCES`: by sensor class, the attribute standing for it, by domain. */
private val SENSOR_ATTRIBUTE_SOURCES = mapOf(
    TEMPERATURE to mapOf("climate" to "current_temperature"),
    HUMIDITY to mapOf("climate" to "current_humidity", "humidifier" to "current_humidity"),
)
