package io.homeassistant.companion.android.dashboard.energy

import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/** The solar forecasts by config entry: the production forecast in Wh by hour (an ISO time). */
typealias SolarForecasts = Map<String, Map<String, Double>>

/** What the solar graph fetches when a solar source has forecasts. Port of `getEnergySolarForecasts`. */
val SOLAR_FORECAST_COMMAND = WsCommand("energy/solar_forecast")

/** Whether a solar source of [this] is forecast, so the solar graph fetches the forecasts. */
val EnergyPreferences.hasSolarForecast: Boolean
    get() = energySources.any { it is EnergySource.Solar && !it.configEntrySolarForecast.isNullOrEmpty() }

/** Read the `energy/solar_forecast` result; hours that aren't numbers are left out. */
fun parseSolarForecasts(json: JsonObject): SolarForecasts = json.mapValues { (_, forecast) ->
    ((forecast as? JsonObject)?.get("wh_hours") as? JsonObject).orEmpty()
        .mapNotNull { (time, wh) -> (wh as? JsonPrimitive)?.doubleOrNull?.let { time to it } }
        .toMap()
}

/**
 * A dashed line per forecast solar source over the period, in kWh by hour (by day or month for longer periods),
 * in the middle of the hours like the bars. Port of `processForecast` (frontend@20260624.6
 * src/panels/lovelace/cards/energy/energy-solar-graph-data.ts).
 */
internal fun HassSnapshot.solarForecastLines(data: EnergyData, period: StatisticPeriod): List<EnergyLineSeries> {
    val forecasts = data.solarForecasts ?: return emptyList()
    val zone = formats.zone
    val start = data.period.startInstant(zone)
    val end = data.period.endInstant(zone)
    return data.prefs.energySources.filterIsInstance<EnergySource.Solar>().mapNotNull { source ->
        val entries = source.configEntrySolarForecast ?: return@mapNotNull null
        val sums = linkedMapOf<Long, Double>()
        entries.forEach { entry ->
            forecasts[entry].orEmpty().forEach { (time, wh) ->
                val instant = parseTime(time)?.takeUnless { it.isBefore(start) || it.isAfter(end) } ?: return@forEach
                val bucket = bucket(instant, period, zone)
                sums[bucket] = (sums[bucket] ?: 0.0) + wh
            }
        }
        if (sums.isEmpty()) return@mapNotNull null
        val offset = periodMidpointOffset(period, sums.keys.sorted())
        val name = source.name?.ifEmpty { null }
            ?: statisticLabel(source.statEnergyFrom, data.statsMetadata[source.statEnergyFrom])
        EnergyLineSeries(
            id = "forecast-${source.statEnergyFrom}",
            name = localize("ui.panel.lovelace.cards.energy.energy_solar_graph.forecast", mapOf("name" to name)),
            color = FORECAST_COLOR,
            points = sums.map { (time, wh) -> EnergyLinePoint(time + offset, wh / WH_PER_KWH) },
        )
    }
}

/** The start of the hour, day or month (by [period]) [instant] is in. */
private fun bucket(instant: Instant, period: StatisticPeriod, zone: ZoneId): Long {
    val time = instant.atZone(zone)
    val start = when (period) {
        StatisticPeriod.MONTH -> time.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS)
        StatisticPeriod.DAY -> time.truncatedTo(ChronoUnit.DAYS)
        else -> time.truncatedTo(ChronoUnit.HOURS)
    }
    return start.toInstant().toEpochMilli()
}

private fun parseTime(time: String): Instant? = try {
    OffsetDateTime.parse(time).toInstant()
} catch (_: DateTimeParseException) {
    null
}

private const val WH_PER_KWH = 1000.0
private const val FORECAST_COLOR = "primary-text-color"
