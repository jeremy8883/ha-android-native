package io.homeassistant.companion.android.dashboard.energy

import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.number
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

// The recorder's long-term statistics as the frontend reads them. Port of the types of frontend@20260624.6
// src/data/recorder.ts.

/**
 * One period of a statistic: [start] and [end] in epoch milliseconds, and the values that were asked for.
 *
 * @property change how much a total grew in the period
 */
data class StatisticValue(
    val start: Long,
    val end: Long,
    val change: Double? = null,
    val mean: Double? = null,
    val min: Double? = null,
    val max: Double? = null,
    val sum: Double? = null,
    val state: Double? = null,
)

/** The periods of each statistic, by statistic id, as `recorder/statistics_during_period` returns them. */
typealias Statistics = Map<String, List<StatisticValue>>

/** What the recorder knows of a statistic. */
data class StatisticsMetadata(
    val statisticId: String,
    val unitOfMeasurement: String?,
    val source: String?,
    val name: String?,
    val hasSum: Boolean,
    val unitClass: String?,
)

/** The granularity statistics are fetched with. */
enum class StatisticPeriod(val value: String) {
    FIVE_MINUTES("5minute"),
    HOUR("hour"),
    DAY("day"),
    MONTH("month"),
}

/** The volume units statistics can be converted to. Port of `VOLUME_UNITS`. */
val VOLUME_UNITS = listOf("L", "gal", "ft³", "m³", "CCF", "MCF")

/** Whether [statisticId] is external (`source:id`) rather than an entity's. Port of `isExternalStatistic`. */
fun isExternalStatistic(statisticId: String): Boolean = ':' in statisticId

/** Read a `recorder/statistics_during_period` result; rows without times are skipped. */
fun parseStatistics(result: JsonObject): Statistics = result.mapValues { (_, rows) ->
    (rows as? JsonArray).orEmpty().filterIsInstance<JsonObject>().mapNotNull { row ->
        val start = row.number("start")?.toLong() ?: return@mapNotNull null
        val end = row.number("end")?.toLong() ?: return@mapNotNull null
        StatisticValue(
            start = start,
            end = end,
            change = row.number("change"),
            mean = row.number("mean"),
            min = row.number("min"),
            max = row.number("max"),
            sum = row.number("sum"),
            state = row.number("state"),
        )
    }
}

/** Read a `recorder/get_statistics_metadata` result. */
fun parseStatisticsMetadata(result: JsonArray): List<StatisticsMetadata> =
    result.filterIsInstance<JsonObject>().mapNotNull { row ->
        row.string("statistic_id")?.let { id ->
            StatisticsMetadata(
                statisticId = id,
                unitOfMeasurement = row.string("statistics_unit_of_measurement"),
                source = row.string("source"),
                name = row.string("name"),
                hasSum = row.boolean("has_sum") == true,
                unitClass = row.string("unit_class"),
            )
        }
    }
