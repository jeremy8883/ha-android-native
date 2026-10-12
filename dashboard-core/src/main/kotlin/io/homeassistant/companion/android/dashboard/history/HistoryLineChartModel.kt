package io.homeassistant.companion.android.dashboard.history

import io.homeassistant.companion.android.dashboard.derive.jsParseFloat
import io.homeassistant.companion.android.dashboard.energy.shownYAxisFractionDigits
import io.homeassistant.companion.android.dashboard.energy.yAxisFractionDigits
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.jsTruthy
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

// The line chart of entities' numeric histories. Port of `generateStateHistoryChartLineData`
// (frontend@20260624.6 src/components/chart/state-history-chart-line-data.ts).

/** The lines of a history chart in its unit, with their y axis's fraction digits. */
data class HistoryLineChart(val series: List<HistoryLineSeries>, val yFractionDigits: Int) {
    /** [this] with the y axis's fraction digits of only the series not in [hidden] (by id). */
    fun showing(hidden: Set<String>): HistoryLineChart {
        val digits = shownYAxisFractionDigits(yFractionDigits, series.any { it.id in hidden }) {
            series.filterNot { it.id in hidden }.flatMap { s -> s.points.mapNotNull { it.y } }
        }
        return copy(yFractionDigits = digits)
    }
}

/**
 * A stepped line (each value holds until the next), or with [fill] an area under it and no line (the climate's
 * heating, the humidifier's on), in [color]. A `null` value is a gap. The part before [dimBefore] (epoch ms) comes
 * from long-term statistics and is drawn half transparent.
 */
data class HistoryLineSeries(
    val id: String,
    val entityId: String,
    val name: String,
    val color: SeriesColor,
    val fill: Boolean,
    val points: List<HistoryPoint>,
    val dimBefore: Double? = null,
)

/** [y] at [x] (epoch ms), `null` for a gap. */
data class HistoryPoint(val x: Double, val y: Double?)

/** A series' colour: the graph palette's at [Palette.index], or a theme [Variable]. */
sealed interface SeriesColor {
    data class Palette(val index: Int) : SeriesColor

    data class Variable(val name: String) : SeriesColor
}

/**
 * The lines of [entities] up to [end] at [now] (epoch ms); a sensor shown up to now ends with its current state. With
 * [showNames], a climate's or humidifier's lines say whose they are ("Hvac current temperature").
 */
fun HassSnapshot.historyLineChart(
    entities: List<LineChartEntity>,
    end: Double,
    now: Double,
    showNames: Boolean = false,
): HistoryLineChart {
    val builder = LineBuilder(end, showNames)
    entities.forEach { entity ->
        val series = when (entity.domain) {
            "thermostat", "climate", "water_heater" -> builder.climate(this, entity)
            "humidifier" -> builder.humidifier(this, entity)
            else -> builder.plain(entity)
        }
        // A last point at the end, and the sensor's current state when the chart ends now
        series.pushData(end, series.previous)
        val upToNow = now - end <= NOW_LEEWAY
        val current = states[entity.entityId]?.state?.let(::safeParseFloat)?.takeIf { upToNow }
        if (entity.domain == "sensor" && series.lines.size == 1 && current != null) {
            series.lines.single().points += HistoryPoint(now, current)
            builder.track(current)
        }
        // Statistics are drawn half transparent, up to the first state
        val dimBefore = entity.statistics?.takeIf { it.isNotEmpty() }?.let {
            entity.states.firstOrNull()?.lastChanged?.toDouble() ?: end
        }
        builder.series += series.lines.map { it.toSeries().copy(dimBefore = dimBefore) }
    }
    return HistoryLineChart(builder.series, yAxisFractionDigits(builder.yMin, builder.yMax))
}

/** The series being built for the chart, and the range of their values. */
private class LineBuilder(val end: Double, val showNames: Boolean) {
    val series = mutableListOf<HistoryLineSeries>()
    var yMin = Double.POSITIVE_INFINITY
    var yMax = Double.NEGATIVE_INFINITY
    var colorIndex = 0

    fun track(value: Double?) {
        if (value != null && value.isFinite()) {
            yMin = minOf(yMin, value)
            yMax = maxOf(yMax, value)
        }
    }

    fun newPalette() = SeriesColor.Palette(colorIndex++)

    /** A line's name: [named] with the entity's [name] when names show, else the attribute's or state's [plain]. */
    fun label(hass: HassSnapshot, named: String, plain: String, name: String, extra: Map<String, String> = emptyMap()) =
        if (showNames) hass.localize(named, mapOf("name" to name) + extra) else hass.localize(plain)
}

/** An entity's lines being built: each data point has a value per line. */
private class EntitySeries(private val builder: LineBuilder, private val entityId: String) {
    val lines = mutableListOf<LineDraft>()
    var previous: List<Double?>? = null

    fun add(id: String, name: String, color: SeriesColor? = null, fill: Boolean = false) {
        lines += LineDraft(id, entityId, name, color ?: builder.newPalette(), fill)
    }

    /** Port of `pushData`: a gap closes at the previous value before opening. */
    fun pushData(time: Double, values: List<Double?>?) {
        if (values == null || time > builder.end) return
        val prev = previous
        lines.forEachIndexed { i, line ->
            val value = values.getOrNull(i)
            if (value == null && prev != null && prev.getOrNull(i) != null) line.points += HistoryPoint(time, prev[i])
            line.points += HistoryPoint(time, value)
            builder.track(value)
        }
        previous = values
    }
}

private class LineDraft(
    val id: String,
    val entityId: String,
    val name: String,
    val color: SeriesColor,
    val fill: Boolean,
) {
    val points = mutableListOf<HistoryPoint>()

    fun toSeries() = HistoryLineSeries(id, entityId, name, color, fill, points.toList())
}

/** A number's line, broken where the state isn't a number. */
private fun LineBuilder.plain(entity: LineChartEntity): EntitySeries {
    val series = EntitySeries(this, entity.entityId)
    series.add(entity.entityId, entity.name)
    var lastValue: Double? = null
    var lastDate = 0.0
    var lastNullDate: Double? = null
    // The statistics up to the first state, then the states
    val stopTime = entity.states.firstOrNull()?.lastChanged
    val statistics = entity.statistics.orEmpty().takeWhile { stopTime == null || it.lastChanged < stopTime }
    (statistics + entity.states).forEach { state ->
        val value = safeParseFloat(state.state)
        val date = state.lastChanged.toDouble()
        val nullDate = lastNullDate
        val last = lastValue
        when {
            value != null && nullDate != null && last != null -> {
                // Where the line would have been when it broke off
                series.pushData(nullDate, listOf((value - last) * ((nullDate - lastDate) / (date - lastDate)) + last))
                series.pushData(nullDate + 1, listOf(null))
                series.pushData(date, listOf(value))
                lastDate = date
                lastValue = value
                lastNullDate = null
            }
            value != null && nullDate == null -> {
                series.pushData(date, listOf(value))
                lastDate = date
                lastValue = value
            }
            value == null && nullDate == null && last != null -> lastNullDate = date
        }
    }
    lastNullDate?.let { series.pushData(it, listOf(null)) }
    return series
}

/** The climate's current and target temperatures, with an area while it heats, cools, dries or fans. */
private fun LineBuilder.climate(hass: HassSnapshot, entity: LineChartEntity): EntitySeries {
    val series = EntitySeries(this, entity.entityId)
    val id = entity.entityId
    val hasHvacAction = entity.states.any { jsTruthy(it.attributes["hvac_action"]) }
    val modes = CLIMATE_MODES.map { mode ->
        val active: (LineChartState) -> Boolean = if (entity.domain == "climate" && hasHvacAction) {
            { HVAC_ACTION_TO_MODE[it.attributes.text("hvac_action")] == mode.mode }
        } else {
            { it.state == mode.mode }
        }
        mode to active
    }.filter { (_, active) -> entity.states.any(active) }
    val hasTargetRange = entity.states.any { it.attributes["target_temp_high"] != it.attributes["target_temp_low"] }
    val name = entity.name
    series.add(
        "$id-current_temperature",
        label(hass, "$CLIMATE_CARD.current_temperature", "$CLIMATE_ATTRIBUTES.current_temperature.name", name),
    )
    modes.forEach { (mode, _) ->
        series.add(
            "$id-${mode.action}",
            label(hass, "$CLIMATE_CARD.${mode.action}", "$CLIMATE_ATTRIBUTES.hvac_action.state.${mode.action}", name),
            SeriesColor.Variable(mode.colorVariable),
            fill = true,
        )
    }
    climateTargets(hass, series, entity, hasTargetRange)
    entity.states.forEach { state ->
        val attributes = state.attributes
        val current = safeParseFloat(attributes["current_temperature"])
        val values = mutableListOf(current)
        modes.forEach { (_, active) -> values += current.takeIf { active(state) } }
        if (hasTargetRange) {
            values += safeParseFloat(attributes["target_temp_high"])
            values += safeParseFloat(attributes["target_temp_low"])
        } else {
            values += safeParseFloat(attributes["temperature"])
        }
        series.pushData(state.lastChanged.toDouble(), values)
    }
    return series
}

/** The climate's target temperature line, or its high and low target lines when it has a range. */
private fun LineBuilder.climateTargets(
    hass: HassSnapshot,
    series: EntitySeries,
    entity: LineChartEntity,
    hasTargetRange: Boolean,
) {
    val id = entity.entityId
    val name = entity.name
    if (hasTargetRange) {
        val high = mapOf("mode" to hass.localize("$CLIMATE_CARD.high"))
        val low = mapOf("mode" to hass.localize("$CLIMATE_CARD.low"))
        series.add(
            "$id-target_temperature_mode",
            label(
                hass,
                "$CLIMATE_CARD.target_temperature_mode",
                "$CLIMATE_ATTRIBUTES.target_temp_high.name",
                name,
                high,
            ),
        )
        series.add(
            "$id-target_temperature_mode_low",
            label(hass, "$CLIMATE_CARD.target_temperature_mode", "$CLIMATE_ATTRIBUTES.target_temp_low.name", name, low),
        )
    } else {
        series.add(
            "$id-target_temperature",
            label(hass, "$CLIMATE_CARD.target_temperature_entity", "$CLIMATE_ATTRIBUTES.temperature.name", name),
        )
    }
}

/** The humidifier's target and current humidity, with an area while it humidifies, dries or is on. */
private fun LineBuilder.humidifier(hass: HassSnapshot, entity: LineChartEntity): EntitySeries {
    val series = EntitySeries(this, entity.entityId)
    val id = entity.entityId
    val hasAction = entity.states.any { jsTruthy(it.attributes["action"]) }
    val hasCurrent = entity.states.any { jsTruthy(it.attributes["current_humidity"]) }
    val shaded = listOf("humidifying", "drying").firstOrNull { action ->
        hasAction && entity.states.any { it.attributes.text("action") == action }
    }
    val name = entity.name
    series.add(
        "$id-target_humidity",
        label(hass, "$HUMIDIFIER_CARD.target_humidity_entity", "$HUMIDIFIER_ATTRIBUTES.humidity.name", name),
    )
    if (hasCurrent) {
        series.add(
            "$id-current_humidity",
            label(
                hass,
                "$HUMIDIFIER_CARD.current_humidity_entity",
                "$HUMIDIFIER_ATTRIBUTES.current_humidity.name",
                name,
            ),
        )
    }
    if (shaded != null) {
        series.add(
            "$id-$shaded",
            label(hass, "$HUMIDIFIER_CARD.$shaded", "$HUMIDIFIER_ATTRIBUTES.action.state.$shaded", name),
            SeriesColor.Variable(HUMIDIFIER_ON_COLOR),
            fill = true,
        )
    } else {
        series.add(
            "$id-on",
            label(hass, "$HUMIDIFIER_CARD.on_entity", "component.humidifier.entity_component._.state.on", name),
            fill = true,
        )
    }
    entity.states.forEach { state ->
        val target = safeParseFloat(state.attributes["humidity"])
        // Without the current humidity, the area fills up to the target
        val current = if (hasCurrent) safeParseFloat(state.attributes["current_humidity"]) else target
        val values = mutableListOf(target)
        if (hasCurrent) values += current
        values += if (shaded != null) {
            current.takeIf { state.attributes.text("action") == shaded }
        } else {
            current.takeIf { state.state == "on" }
        }
        series.pushData(state.lastChanged.toDouble(), values)
    }
    return series
}

/** A climate mode and its action, with the colour of its area. */
private class ClimateMode(val mode: String, val action: String, val colorVariable: String)

private val CLIMATE_MODES = listOf(
    ClimateMode("heat", "heating", "state-climate-heat-color"),
    ClimateMode("cool", "cooling", "state-climate-cool-color"),
    ClimateMode("dry", "drying", "state-climate-dry-color"),
    ClimateMode("fan_only", "fan", "state-climate-fan_only-color"),
)

/** Port of `CLIMATE_HVAC_ACTION_TO_MODE` (src/data/climate.ts). */
internal val HVAC_ACTION_TO_MODE = mapOf(
    "cooling" to "cool",
    "defrosting" to "heat",
    "drying" to "dry",
    "fan" to "fan_only",
    "heating" to "heat",
    "idle" to "off",
    "off" to "off",
    "preheating" to "heat",
)

/** Port of `safeParseFloat`: JavaScript's `parseFloat`, `null` unless finite. */
private fun safeParseFloat(value: String?): Double? = value?.let(::jsParseFloatOrNull)

private fun safeParseFloat(value: JsonElement?): Double? = (value as? JsonPrimitive)?.content?.let(::jsParseFloatOrNull)

private fun jsParseFloatOrNull(text: String): Double? = jsParseFloat(text).takeIf { it.isFinite() }

private fun Map<String, JsonElement>.text(key: String): String? = (this[key] as? JsonPrimitive)?.content

private const val NOW_LEEWAY = 1000.0
private const val CLIMATE_ATTRIBUTES = "component.climate.entity_component._.state_attributes"
private const val HUMIDIFIER_ATTRIBUTES = "component.humidifier.entity_component._.state_attributes"
private const val CLIMATE_CARD = "ui.card.climate"
private const val HUMIDIFIER_CARD = "ui.card.humidifier"
private const val HUMIDIFIER_ON_COLOR = "state-humidifier-on-color"
