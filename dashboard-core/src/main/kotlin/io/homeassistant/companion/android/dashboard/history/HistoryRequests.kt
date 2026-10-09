package io.homeassistant.companion.android.dashboard.history

import io.homeassistant.companion.android.dashboard.energy.WsCommand
import io.homeassistant.companion.android.dashboard.energy.isoString
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.jsTruthy
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

// What the more-info dialog's history section loads. Ports of `ha-more-info-history._getStateHistory`,
// `subscribeHistoryStatesTimeWindow` and `computeShowHistoryComponent` (frontend@20260624.6
// src/dialogs/more-info/ha-more-info-history.ts, src/data/history.ts, src/dialogs/more-info/const.ts).

/** Whether [entityId]'s details show its history: when the server records it, except for cameras and configurators. */
fun HassSnapshot.showsHistory(entityId: String): Boolean =
    HISTORY in config.components && entityId.substringBefore('.') !in NO_HISTORY_DOMAINS

/**
 * Whether [entityId]'s history is its 5-minute statistics rather than its states: for sensors with a state class,
 * when the server records statistics (and has them for it, which the metadata tells).
 */
fun HassSnapshot.historyUsesStatistics(entityId: String): Boolean = RECORDER in config.components &&
    entityId.substringBefore('.') == "sensor" &&
    jsTruthy(states[entityId]?.attributes?.get("state_class"))

/** Whether [entityId]'s history can go without attributes: only the domains whose charts read them need them. */
fun HassSnapshot.historyWithoutAttributes(entityId: String): Boolean =
    entityId in states && entityId.substringBefore('.') !in NEED_ATTRIBUTE_DOMAINS

/**
 * The subscription to [entityId]'s states over the [HISTORY_HOURS] before [now], kept up to date, without
 * attributes when [withoutAttributes] (see [historyWithoutAttributes]).
 */
fun historyStreamCommand(entityId: String, withoutAttributes: Boolean, now: Instant): WsCommand = WsCommand(
    "history/stream",
    buildJsonObject {
        putJsonArray("entity_ids") { add(JsonPrimitive(entityId)) }
        put("start_time", isoString(now.minus(HISTORY_HOURS, ChronoUnit.HOURS)))
        put("minimal_response", true)
        put("significant_changes_only", true)
        put("no_attributes", withoutAttributes)
    },
)

/** The metadata of [entityId]'s statistics, which says whether it has any. */
fun statisticsMetadataCommand(entityId: String): WsCommand = WsCommand(
    "recorder/get_statistics_metadata",
    buildJsonObject { putJsonArray("statistic_ids") { add(JsonPrimitive(entityId)) } },
)

/** [entityId]'s 5-minute statistics over the [HISTORY_HOURS] before [now]. */
fun historyStatisticsCommand(entityId: String, now: Instant): WsCommand = WsCommand(
    "recorder/statistics_during_period",
    buildJsonObject {
        put("start_time", isoString(now.minus(HISTORY_HOURS, ChronoUnit.HOURS)))
        putJsonArray("statistic_ids") { add(JsonPrimitive(entityId)) }
        put("period", "5minute")
        putJsonArray("types") { STATISTIC_TYPES.forEach { add(JsonPrimitive(it)) } }
    },
)

/** How many hours back the details show. */
const val HISTORY_HOURS = 24L

private const val HISTORY = "history"
private const val RECORDER = "recorder"
private val NO_HISTORY_DOMAINS = setOf("camera", "configurator")
private val NEED_ATTRIBUTE_DOMAINS =
    setOf("climate", "humidifier", "input_datetime", "water_heater", "person", "device_tracker")
private val STATISTIC_TYPES = listOf("state", "min", "mean", "max")
