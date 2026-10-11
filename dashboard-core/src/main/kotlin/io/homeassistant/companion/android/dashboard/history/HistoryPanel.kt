package io.homeassistant.companion.android.dashboard.history

import io.homeassistant.companion.android.dashboard.display.formatEntityAttributeValue
import io.homeassistant.companion.android.dashboard.display.formatEntityState
import io.homeassistant.companion.android.dashboard.display.jsNumberString
import io.homeassistant.companion.android.dashboard.energy.Statistics
import io.homeassistant.companion.android.dashboard.energy.WsCommand
import io.homeassistant.companion.android.dashboard.energy.isoString
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

// What the History panel loads for a period and what it does with it. Ports of `subscribeHistory`,
// `entityIdHistoryNeedsAttributes`, `convertStatisticsToHistory` and `mergeHistoryResults` (frontend@20260624.6
// src/data/history.ts), and of `ha-panel-history`'s `_getStats` and `_downloadHistory`
// (src/panels/history/ha-panel-history.ts).

/**
 * The subscription to [entityIds]' states from [start] to [end], kept up to date while [end] is ahead, without
 * attributes unless one of them needs them (see [historyWithoutAttributes]). Port of `subscribeHistory`.
 */
fun HassSnapshot.historyRangeCommand(entityIds: List<String>, start: Instant, end: Instant): WsCommand = WsCommand(
    "history/stream",
    buildJsonObject {
        putJsonArray("entity_ids") { entityIds.forEach { add(JsonPrimitive(it)) } }
        put("start_time", isoString(start))
        put("end_time", isoString(end))
        put("minimal_response", true)
        put("no_attributes", entityIds.all(::historyWithoutAttributes))
    },
)

/**
 * The hourly statistics (mean and state) of [entityIds] from an hour before [start] to [end]: the history draws the
 * end of each period, so a chart starting at 7:00 needs the one from 6:00. Port of `_getStats`'s `fetchStatistics`.
 */
fun historyPanelStatisticsCommand(entityIds: List<String>, start: Instant, end: Instant): WsCommand = WsCommand(
    "recorder/statistics_during_period",
    buildJsonObject {
        put("start_time", isoString(start.minus(1, ChronoUnit.HOURS)))
        put("end_time", isoString(end))
        putJsonArray("statistic_ids") { entityIds.forEach { add(JsonPrimitive(it)) } }
        put("period", "hour")
        putJsonArray("types") { STATISTIC_TYPES.forEach { add(JsonPrimitive(it)) } }
    },
)

/**
 * Port of `convertStatisticsToHistory`: [statistics] (in [statisticIds]' order) as lines, each period's mean (else
 * state) at its end, carried as the lines' [LineChartEntity.statistics].
 */
fun HassSnapshot.convertStatisticsToHistory(
    statistics: Statistics,
    statisticIds: List<String>,
    splitDeviceClasses: Boolean = false,
): HistoryResult {
    val states: HistoryStates = statisticIds.filter { it in statistics }.associateWith { id ->
        statistics.getValue(id).mapNotNull { period ->
            val value = period.mean ?: period.state ?: return@mapNotNull null
            val end = period.end / MILLIS
            HistoryState(jsNumberString(value), JsonObject(emptyMap()), end, end)
        }
    }
    val history = computeHistory(states, emptyList(), splitDeviceClasses, forceNumeric = true)
    return history.copy(
        line = history.line.map { unit ->
            unit.copy(data = unit.data.map { it.copy(statistics = it.states, states = emptyList()) })
        },
    )
}

/**
 * Port of `mergeHistoryResults`: [history]'s lines with [lts]'s statistics before each entity's first state, by
 * unit (and device class when [splitDeviceClasses]); a group or entity only one of them has is taken as it is.
 */
fun mergeHistoryResults(
    history: HistoryResult,
    lts: HistoryResult?,
    splitDeviceClasses: Boolean = true,
): HistoryResult {
    if (lts == null) return history
    val lookup = linkedMapOf<String, Pair<LineChartUnit?, LineChartUnit?>>()
    history.line.forEach { lookup[groupKey(it.unit, it.deviceClass, splitDeviceClasses)] = it to null }
    lts.line.forEach { item ->
        // Disabled entities have no unit: the history's line with the same entities has it
        val unit = if (item.unit == BLANK_UNIT) {
            history.line.firstOrNull { it.identifier == item.identifier }?.unit ?: BLANK_UNIT
        } else {
            item.unit
        }
        val fixed = item.copy(unit = unit)
        val key = groupKey(unit, item.deviceClass, splitDeviceClasses)
        lookup[key] = (lookup[key]?.first) to fixed
    }
    val lines = lookup.values.mapNotNull { (historyItem, ltsItem) ->
        if (historyItem != null && ltsItem != null) {
            historyItem.copy(data = mergedEntities(historyItem.data, ltsItem.data))
        } else {
            // Only one of them has the group: as it is
            historyItem ?: ltsItem
        }
    }
    return history.copy(line = lines)
}

/** Each entity's states with the statistics from before its first state. */
private fun mergedEntities(history: List<LineChartEntity>, lts: List<LineChartEntity>): List<LineChartEntity> {
    val historyById = history.associateBy { it.entityId }
    val ltsById = lts.associateBy { it.entityId }
    return (historyById.keys + ltsById.keys).mapNotNull { id ->
        val historyItem = historyById[id]
        val ltsItem = ltsById[id]
        if (historyItem == null || ltsItem == null) return@mapNotNull historyItem ?: ltsItem
        val statistics = ltsItem.statistics.orEmpty()
        // Without states, every statistic is kept
        val oldestState =
            historyItem.states.firstOrNull()?.lastChanged ?: ((statistics.lastOrNull()?.lastChanged ?: 0) + 1)
        val kept = statistics.takeWhile { it.lastChanged < oldestState }
        if (kept.isEmpty()) historyItem else historyItem.copy(statistics = kept)
    }
}

/**
 * Port of `_downloadHistory`: the history as CSV, a row per state (`entity_id,state,last_changed`), the lines' first
 * (with their statistics, and the climate, humidifier and water heater attributes their charts read as columns of
 * their own), then the timelines'.
 */
fun historyCsv(history: HistoryResult): String {
    val headers = mutableListOf("entity_id", "state", "last_changed")
    val columns = mutableMapOf<String, Map<String, Int>>()
    val rows = mutableListOf<String>()
    history.line.forEach { unit ->
        unit.data.forEach { entity ->
            val extra = DOMAIN_ATTRIBUTES[entity.domain]?.let { attributes ->
                columns.getOrPut(entity.domain) {
                    attributes.mapIndexed { index, attribute -> attribute to headers.size + index }.toMap()
                        .also { headers += attributes }
                }
            }
            entity.statistics?.forEach { rows += "${entity.entityId},${it.state},${isoTime(it.lastChanged)}\n" }
            entity.states.forEach { state -> rows += csvRow(entity.entityId, state, extra) }
        }
    }
    history.timeline.forEach { timeline ->
        timeline.data.forEach { state ->
            val safe = if (state.state.contains(',') || state.state.contains('"')) {
                "\"" + state.state.replace("\"", "\"\"") + "\""
            } else {
                state.state
            }
            rows += "${timeline.entityId},$safe,${isoTime(state.lastChanged)}\n"
        }
    }
    return headers.joinToString(",") + "\n" + rows.joinToString("")
}

/** A line's state as a CSV row, with its [extra] attributes in their columns (JavaScript's array join). */
private fun csvRow(entityId: String, state: LineChartState, extra: Map<String, Int>?): String {
    val data = mutableListOf(entityId, state.state, isoTime(state.lastChanged))
    extra?.forEach { (attribute, index) ->
        val value = state.attributes[attribute] ?: return@forEach
        while (data.size <= index) data += ""
        data[index] =
            (value as? JsonPrimitive)?.let { if (it.isString) it.content else jsonNumber(it) } ?: value.toString()
    }
    return data.joinToString(",") + "\n"
}

private fun jsonNumber(value: JsonPrimitive): String =
    value.content.toDoubleOrNull()?.let(::jsNumberString) ?: value.content

/** Port of `toISOString`: UTC with milliseconds. */
private fun isoTime(epochMillis: Long): String = isoString(Instant.ofEpochMilli(epochMillis))

private const val MILLIS = 1000.0
private const val BLANK_UNIT = " "
private val STATISTIC_TYPES = listOf("mean", "state")

/** The attributes `_downloadHistory` adds as columns, by domain. */
private val DOMAIN_ATTRIBUTES = mapOf(
    "climate" to listOf("current_temperature", "hvac_action", "target_temp_high", "target_temp_low", "temperature"),
    "humidifier" to listOf("action", "current_humidity", "humidity"),
    "water_heater" to listOf("current_temperature", "operation_mode", "temperature"),
)

/** An item of a line chart's legend: the series it toggles ([id]), its [name] and the entity's current [value]. */
data class HistoryLegendItem(val id: String, val entityId: String, val name: String, val value: String?)

/**
 * Port of `state-history-chart-line`'s custom legend: an item per line (areas have none), with the entity's current
 * state, or for a climate's temperature lines that temperature.
 */
fun HassSnapshot.historyLegend(chart: HistoryLineChart): List<HistoryLegendItem> =
    chart.series.filterNot { it.fill }.map { series ->
        val state = states[series.entityId]
        val attribute = TEMPERATURE_SERIES.entries.firstOrNull { (suffix, _) -> series.id.endsWith(suffix) }?.value
        val value = state?.let {
            attribute?.let { name -> formatEntityAttributeValue(it, name) } ?: formatEntityState(it)
        }
        HistoryLegendItem(series.id, series.entityId, series.name, value)
    }

/** The climate's temperature lines (by id suffix, the longest first) and the attribute their legend shows. */
private val TEMPERATURE_SERIES = linkedMapOf(
    "-current_temperature" to "current_temperature",
    "-target_temperature_mode_low" to "target_temp_low",
    "-target_temperature_mode" to "target_temp_high",
    "-target_temperature" to "temperature",
)
