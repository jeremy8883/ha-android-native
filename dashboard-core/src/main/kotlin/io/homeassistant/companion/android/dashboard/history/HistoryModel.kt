package io.homeassistant.companion.android.dashboard.history

import io.homeassistant.companion.android.dashboard.display.SENSOR_NUMERIC_DEVICE_CLASSES
import io.homeassistant.companion.android.dashboard.display.formatEntityState
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.jsTruthy
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// What the history charts show of entities' histories: lines of numbers by unit, and timelines of other states.
// Port of `computeHistory` and its helpers (frontend@20260624.6 src/data/history.ts).

/** The lines and timelines of a history. */
data class HistoryResult(val line: List<LineChartUnit>, val timeline: List<TimelineEntity>)

/** The entities whose lines share a [unit] (and chart). */
data class LineChartUnit(
    val unit: String,
    val deviceClass: String?,
    val identifier: String,
    val data: List<LineChartEntity>,
)

/**
 * An entity's line: its states over time.
 *
 * @property states the states, with the attributes the line charts read for climate, humidifiers and water heaters
 * @property statistics its long-term statistics (hourly means) before its states, when they were merged in
 */
data class LineChartEntity(
    val domain: String,
    val name: String,
    val entityId: String,
    val states: List<LineChartState>,
    val statistics: List<LineChartState>? = null,
)

/** A state of a line at [lastChanged] (epoch ms). */
data class LineChartState(val state: String, val lastChanged: Long, val attributes: JsonObject)

/** An entity's timeline: a band per state. */
data class TimelineEntity(val name: String, val entityId: String, val data: List<TimelineState>)

/** A band of a timeline from [lastChanged] (epoch ms), [stateLocalize] being how the state reads. */
data class TimelineState(val stateLocalize: String, val state: String, val lastChanged: Long)

/**
 * The lines and timelines of [history] for [entityIds] and the entities it has, those without history shown with
 * their current state. Entities with numbers (a unit, a state class, a numeric domain or device class, or every one
 * when [forceNumeric]) are lines grouped by unit (and device class when [splitDeviceClasses]), the others timelines.
 */
fun HassSnapshot.computeHistory(
    history: HistoryStates,
    entityIds: List<String>,
    splitDeviceClasses: Boolean = false,
    forceNumeric: Boolean = false,
): HistoryResult {
    val local = linkedMapOf<String, List<HistoryState>>()
    (entityIds + history.keys).distinct().forEach { id ->
        val states = history[id] ?: this.states[id]?.let(::limitedHistory)
        if (states != null) local[id] = states
    }
    val lineDevices = linkedMapOf<String, LinkedHashMap<String, List<HistoryState>>>()
    val groups = mutableMapOf<String, Pair<String, String?>>()
    val timeline = mutableListOf<TimelineEntity>()
    local.forEach { (id, states) ->
        if (states.isEmpty()) return@forEach
        val unit = historyUnit(id, states, forceNumeric)
        if (unit.isNullOrEmpty()) {
            timeline += timelineEntity(id, states)
        } else {
            val deviceClass = historyDeviceClass(id, states)
            val key = groupKey(unit, deviceClass, splitDeviceClasses)
            groups[key] = unit to deviceClass?.takeIf { splitDeviceClasses }
            val group = lineDevices.getOrPut(key) { linkedMapOf() }
            group[id] = group[id].orEmpty() + states
        }
    }
    val lines = lineDevices.map { (key, entities) ->
        val (unit, deviceClass) = groups.getValue(key)
        lineChartUnit(unit, deviceClass, entities)
    }
    return HistoryResult(lines, timeline)
}

/** The unit of [id]'s line, `null` (or empty) when it's a timeline. */
private fun HassSnapshot.historyUnit(id: String, states: List<HistoryState>, forceNumeric: Boolean): String? {
    val domain = id.substringBefore('.')
    val current = this.states[id]
    val numericFromHistory = numericStateFromHistory(domain, current, states)
    val numeric = forceNumeric || isNumericEntity(domain, current) || numericFromHistory != null
    return if (numeric) {
        current?.attributes?.unit()
            ?: numericFromHistory?.attributes?.unit()
            ?: BLANK_UNIT
    } else {
        when (domain) {
            "zone" -> localize("ui.dialogs.more_info_control.zone.graph_unit")
            "climate", "water_heater" -> config.temperatureUnit
            "humidifier" -> "%"
            else -> null
        }
    }
}

/** The unit attribute when JavaScript would use it (`||`). */
private fun JsonObject.unit(): String? = (this["unit_of_measurement"] as? JsonPrimitive)?.takeIf(::jsTruthy)?.content

/** Port of `processTimelineEntity`: a band per change of state. */
private fun HassSnapshot.timelineEntity(id: String, states: List<HistoryState>): TimelineEntity {
    val first = states.first()
    val current = this.states[id]
    val data = mutableListOf<TimelineState>()
    states.forEach { state ->
        if (data.lastOrNull()?.state == state.state) return@forEach
        val deviceClass = current?.attributes?.get("device_class")?.takeIf(::jsTruthy)
        val attributes = JsonObject(
            (state.attributes ?: first.attributes).orEmpty() + listOfNotNull(deviceClass?.let { "device_class" to it }),
        )
        val display =
            formatEntityState(EntityState(id, state.state, attributes, null, state.changed, state.lastUpdated))
        data += TimelineState(display, state.state, (state.changed * MILLIS).toLong())
    }
    return TimelineEntity(stateName(id, current?.attributes ?: first.attributes.orEmpty()), id, data)
}

/** Port of `processLineChartEntities`: each entity's states, without the middle of runs of equal states. */
private fun HassSnapshot.lineChartUnit(
    unit: String,
    deviceClass: String?,
    entities: Map<String, List<HistoryState>>,
): LineChartUnit {
    val data = entities.map { (id, states) ->
        val domain = id.substringBefore('.')
        val useLastUpdated = domain in DOMAINS_USE_LAST_UPDATED
        val processed = mutableListOf<LineChartState>()
        states.forEach { state ->
            val line = if (useLastUpdated) {
                val kept = state.attributes.orEmpty().filterKeys { it in LINE_ATTRIBUTES_TO_KEEP }
                LineChartState(state.state, (state.lastUpdated * MILLIS).toLong(), JsonObject(kept))
            } else {
                LineChartState(state.state, (state.changed * MILLIS).toLong(), JsonObject(emptyMap()))
            }
            val size = processed.size
            if (size > 1 && equalState(line, processed[size - 1]) && equalState(line, processed[size - 2])) {
                return@forEach
            }
            processed += line
        }
        val first = states.first()
        val attributes = this.states[id]?.attributes
            ?: first.attributes?.takeIf { "friendly_name" in it }
        LineChartEntity(domain, stateName(id, attributes.orEmpty()), id, processed)
    }
    return LineChartUnit(unit, deviceClass, entities.keys.joinToString(""), data)
}

private fun equalState(a: LineChartState, b: LineChartState): Boolean =
    a.state == b.state && LINE_ATTRIBUTES_TO_KEEP.all { attr -> a.attributes[attr] == b.attributes[attr] }

/** Port of `computeStateNameFromEntityAttributes`: the friendly name, or the object id with spaces. */
internal fun stateName(entityId: String, attributes: Map<String, JsonElement>): String {
    val friendly = attributes["friendly_name"]
    return if (friendly == null) {
        entityId.substringAfter('.').replace('_', ' ')
    } else {
        (friendly as? JsonPrimitive)?.content.orEmpty()
    }
}

/** Port of `isNumericEntity` without history: whether [current]'s states are numbers, charted as lines. */
internal fun isNumericEntity(domain: String, current: EntityState?): Boolean = domain in NUMERICAL_DOMAINS ||
    current?.attributes?.let(::isNumericFromAttributes) == true ||
    (domain == "sensor" && current?.attributes?.string("device_class") in SENSOR_NUMERIC_DEVICE_CLASSES)

/** Port of `limitedHistoryFromStateObj`: the current state as the only one. */
private fun limitedHistory(state: EntityState) =
    listOf(HistoryState(state.state, state.attributes, null, state.lastUpdated))

internal fun isNumericFromAttributes(attributes: JsonObject) =
    "unit_of_measurement" in attributes || "state_class" in attributes

internal val NUMERICAL_DOMAINS = setOf("counter", "input_number", "number")

private val DOMAINS_USE_LAST_UPDATED = setOf("climate", "humidifier", "water_heater")
private val LINE_ATTRIBUTES_TO_KEEP = listOf(
    "temperature",
    "current_temperature",
    "target_temp_low",
    "target_temp_high",
    "hvac_action",
    "humidity",
    "mode",
    "action",
    "current_humidity",
)
private const val BLANK_UNIT = " "
private const val MILLIS = 1000
