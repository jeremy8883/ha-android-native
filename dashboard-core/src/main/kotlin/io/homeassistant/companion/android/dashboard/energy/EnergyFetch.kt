package io.homeassistant.companion.android.dashboard.energy

import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

// What the energy dashboard fetches for a period, in the frontend's order. Port of `getEnergyData` and its helpers
// (frontend@20260624.6 src/data/energy.ts), split so the fetching itself stays outside: `energy/info` and the
// statistics' metadata first, then the statistics planned from them.

/** A WebSocket command: its [type] and the other fields of the message. */
data class WsCommand(val type: String, val params: JsonObject = JsonObject(emptyMap()))

/** The `energy/info` result: the cost statistics the energy integration creates, by the meter they price. */
data class EnergyInfo(val costSensors: Map<String, String>, val solarForecastDomains: List<String>) {
    companion object {
        fun fromJson(json: JsonObject) = EnergyInfo(
            costSensors = json.obj("cost_sensors")?.mapNotNull { (meter, cost) ->
                cost.stringOrNull?.let { meter to it }
            }
                ?.toMap().orEmpty(),
            solarForecastDomains = (json["solar_forecast_domains"] as? JsonArray)?.mapNotNull {
                it.stringOrNull
            }.orEmpty(),
        )
    }
}

/** The command reading the energy info. */
val ENERGY_INFO_COMMAND = WsCommand("energy/info")

/** The command reading the metadata of every statistic [prefs] use, `null` when there are none. */
fun EnergyPreferences.metadataCommand(info: EnergyInfo): WsCommand? = allStatisticIds(info).takeIf { it.isNotEmpty() }
    ?.let { ids -> WsCommand("recorder/get_statistics_metadata", buildJsonObject { putStrings("statistic_ids", ids) }) }

/** The statistics of each kind, in the order the frontend lists them. */
private fun EnergyPreferences.allStatisticIds(info: EnergyInfo) =
    referencedStatisticIds(info, ENERGY_TYPES) + referencedStatisticIds(info, setOf(WATER)) +
        referencedPowerStatisticIds()

/**
 * The statistics an energy period shows, with the commands fetching them. Each command is `null` when the frontend
 * doesn't send it.
 *
 * @property energy the change of the electricity and gas meters, costs and devices
 * @property power the mean of the power and flow rates, finely grained
 * @property powerHour the hourly power means filling in where 5-minute statistics are purged
 * @property water the change of the water meters, costs and devices
 * @property fossil the fossil fuel share of the grid electricity, with a CO2 signal entity
 */
data class EnergyFetchPlan(
    val period: EnergyPeriod,
    val comparePeriod: EnergyPeriod?,
    val compareMode: CompareMode?,
    val energy: WsCommand?,
    val power: WsCommand?,
    val powerHour: WsCommand?,
    val water: WsCommand?,
    val energyCompare: WsCommand?,
    val waterCompare: WsCommand?,
    val fossil: WsCommand?,
    val fossilCompare: WsCommand?,
    val co2SignalEntity: String?,
    val gasUnit: String,
    val waterUnit: String,
) {
    /** The commands to send, in the frontend's order. */
    val commands: List<WsCommand>
        get() = listOfNotNull(energy, power, powerHour, water, energyCompare, waterCompare, fossil, fossilCompare)
}

/** What [EnergyFetchPlan] needs besides the preferences: the period, and how dates are counted. */
data class EnergyRequest(val period: EnergyPeriod, val compareMode: CompareMode?, val zone: ZoneId)

/** Plan what to fetch for [request], knowing [info] and the [metadata] of the statistics (`[]` when none). */
fun planEnergyFetch(
    prefs: EnergyPreferences,
    info: EnergyInfo,
    metadata: List<StatisticsMetadata>,
    request: EnergyRequest,
    environment: EnergyEnvironment,
): EnergyFetchPlan {
    val metadataById = metadata.associateBy { it.statisticId }
    val energyIds = prefs.referencedStatisticIds(info, ENERGY_TYPES)
    val powerIds = prefs.referencedPowerStatisticIds()
    val waterIds = prefs.referencedStatisticIds(info, setOf(WATER))
    val gasUnit = environment.energyGasUnit(prefs, metadataById)
    val waterUnit = environment.energyWaterUnit(prefs, metadataById)
    val energyUnits = buildJsonObject {
        put("energy", "kWh")
        if (gasUnit in VOLUME_UNITS) put("volume", gasUnit)
    }
    val waterUnits = buildJsonObject { put("volume", waterUnit) }
    val zone = request.zone
    val period = request.period
    val coarse = suggestedPeriod(period, fine = false)
    val fine = suggestedPeriod(period, fine = true)
    val compare = when (request.compareMode) {
        CompareMode.PREVIOUS -> period.previous()
        CompareMode.YEAR_OVER_YEAR -> period.yearBefore()
        null -> null
    }
    fun statistics(ids: List<String>, on: EnergyPeriod, granularity: StatisticPeriod, units: JsonObject, type: String) =
        ids.takeIf { it.isNotEmpty() }?.let { statisticsCommand(it, TimeRange(on, zone), granularity, units, type) }
    val co2 = environment.co2SignalEntity
    val consumptionIds = prefs.energySources.filterIsInstance<EnergySource.Grid>().mapNotNull { it.statEnergyFrom }
    fun fossil(on: EnergyPeriod) = co2?.let { fossilCommand(TimeRange(on, zone), consumptionIds, it, coarse) }
    return EnergyFetchPlan(
        period = period,
        comparePeriod = compare,
        compareMode = request.compareMode,
        energy = statistics(energyIds, period, coarse, energyUnits, CHANGE),
        power = statistics(powerIds, period, fine, POWER_UNITS, MEAN),
        powerHour = statistics(powerIds, period, StatisticPeriod.HOUR, POWER_UNITS, MEAN)
            .takeIf { fine == StatisticPeriod.FIVE_MINUTES },
        water = statistics(waterIds, period, coarse, waterUnits, CHANGE),
        energyCompare = compare?.let { statistics(energyIds, it, coarse, energyUnits, CHANGE) },
        waterCompare = compare?.let { statistics(waterIds, it, coarse, waterUnits, CHANGE) },
        fossil = fossil(period),
        fossilCompare = compare?.let(::fossil),
        co2SignalEntity = co2,
        gasUnit = gasUnit,
        waterUnit = waterUnit,
    )
}

/**
 * The granularity statistics of [period] are fetched with: by month for whole months beyond 35 days, by day beyond
 * two days, else by hour. [fine] (power) is by 5 minutes up to 8 days, by hour up to 64. Port of
 * `getSuggestedPeriod`.
 */
fun suggestedPeriod(period: EnergyPeriod, fine: Boolean): StatisticPeriod {
    val days = period.dayDifference
    return when {
        fine && days > FINE_DAYS_BY_DAY -> StatisticPeriod.DAY
        fine && days > FINE_DAYS_BY_HOUR -> StatisticPeriod.HOUR
        fine -> StatisticPeriod.FIVE_MINUTES
        period.isWholeMonths && days > DAYS_BY_MONTH -> StatisticPeriod.MONTH
        days > DAYS_BY_DAY -> StatisticPeriod.DAY
        else -> StatisticPeriod.HOUR
    }
}

/** The instants of a period's start and end, as JavaScript writes them. */
private class TimeRange(period: EnergyPeriod, zone: ZoneId) {
    val start: String = isoString(period.startInstant(zone))
    val end: String = isoString(period.endInstant(zone))
}

private fun statisticsCommand(
    ids: List<String>,
    range: TimeRange,
    granularity: StatisticPeriod,
    units: JsonObject,
    type: String,
) = WsCommand(
    "recorder/statistics_during_period",
    buildJsonObject {
        put("start_time", range.start)
        put("end_time", range.end)
        putStrings("statistic_ids", ids)
        put("period", granularity.value)
        put("units", units)
        putJsonArray("types") { add(type) }
    },
)

private fun fossilCommand(
    range: TimeRange,
    consumptionIds: List<String>,
    co2Entity: String,
    granularity: StatisticPeriod,
) = WsCommand(
    "energy/fossil_energy_consumption",
    buildJsonObject {
        put("start_time", range.start)
        put("end_time", range.end)
        putStrings("energy_statistic_ids", consumptionIds)
        put("co2_statistic_id", co2Entity)
        put("period", granularity.value)
    },
)

private fun JsonObjectBuilder.putStrings(key: String, values: List<String>) =
    put(key, JsonArray(values.map(::JsonPrimitive)))

/** JavaScript's `Date.toISOString()`: UTC with milliseconds. */
internal fun isoString(instant: Instant): String = ISO_MILLIS.format(instant)

private val ISO_MILLIS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

internal const val GRID = "grid"
internal const val SOLAR = "solar"
internal const val BATTERY = "battery"
internal const val GAS = "gas"
internal const val WATER = "water"
internal const val DEVICE = "device"
private val ENERGY_TYPES = setOf(GRID, SOLAR, BATTERY, GAS, DEVICE)
private const val CHANGE = "change"
private const val MEAN = "mean"
private val POWER_UNITS = buildJsonObject { put("power", "kW") }
private const val FINE_DAYS_BY_DAY = 64
private const val FINE_DAYS_BY_HOUR = 8
private const val DAYS_BY_MONTH = 35
private const val DAYS_BY_DAY = 2
