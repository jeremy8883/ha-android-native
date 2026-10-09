package io.homeassistant.companion.android.dashboard.data

import io.homeassistant.companion.android.dashboard.energy.CompareMode
import io.homeassistant.companion.android.dashboard.energy.ENERGY_INFO_COMMAND
import io.homeassistant.companion.android.dashboard.energy.EnergyData
import io.homeassistant.companion.android.dashboard.energy.EnergyEnvironment
import io.homeassistant.companion.android.dashboard.energy.EnergyFetchPlan
import io.homeassistant.companion.android.dashboard.energy.EnergyFetchResults
import io.homeassistant.companion.android.dashboard.energy.EnergyInfo
import io.homeassistant.companion.android.dashboard.energy.EnergyPeriod
import io.homeassistant.companion.android.dashboard.energy.EnergyPreferences
import io.homeassistant.companion.android.dashboard.energy.EnergyRequest
import io.homeassistant.companion.android.dashboard.energy.StatisticsMetadata
import io.homeassistant.companion.android.dashboard.energy.WsCommand
import io.homeassistant.companion.android.dashboard.energy.assemble
import io.homeassistant.companion.android.dashboard.energy.metadataCommand
import io.homeassistant.companion.android.dashboard.energy.parseStatisticsMetadata
import io.homeassistant.companion.android.dashboard.energy.planEnergyFetch
import io.homeassistant.companion.android.dashboard.model.string
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaInstant
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber

/**
 * The energy dashboard's data for a period: the statistics `getEnergyData` fetches (frontend@20260624.6
 * src/data/energy.ts), loaded again every hour while the period includes now, as the frontend's energy collection
 * does.
 */
class EnergyRepository @Inject constructor(private val sessions: ServerSessions, private val clock: Clock) {
    /** What was loaded for periods that aren't cached, for the life of the screen. */
    private val shown = mutableMapOf<String, Kept<Parsed<EnergyData>>>()

    /**
     * The data of [request] for the energy preferences [prefs] (the `energy/get_prefs` result). Only the collection's default period ([cached]) is cached, so that the
     * dashboard shows it at once next time; the data of other periods is kept while the screen lives.
     */
    fun energyData(
        collectionKey: String,
        prefs: JsonObject,
        request: EnergyRequest,
        environment: EnergyEnvironment,
        cached: Boolean,
    ): Flow<Loadable<EnergyData>> = sessions.withServer { session ->
        val name = "energy-$collectionKey"
        val keeper = if (cached) {
            session.keeper(name, ParsedCodec(::parseEnergyBundle))
        } else {
            memoryKeeper("$name-${request.key}")
        }
        KeptData(name, keeper, session.connection)
            .fetched(hourlyRefreshes(request)) {
                session.fetchEnergy(prefs, request, environment).flatMap { raw ->
                    parseEnergyBundle(raw).map { Parsed(raw, it) }
                }
            }
            .map { loadable ->
                // The cached data can be another day's, from when the default period was another day
                loadable.map { it.value }.takeIf { it.valueOrNull?.matches(request) != false } ?: Loadable.Loading
            }
    }

    private fun memoryKeeper(key: String) = object : ValueKeeper<Parsed<EnergyData>> {
        override suspend fun get() = shown[key]

        override fun put(value: Parsed<EnergyData>) {
            shown[key] = Kept(value, clock.now())
        }
    }

    /**
     * An emission at 20 minutes past every hour (the statistics are compiled every hour) until the period is over.
     * Port of `scheduleHourlyRefresh`.
     */
    private fun hourlyRefreshes(request: EnergyRequest): Flow<Unit> = flow {
        val end = request.period.endInstant(request.zone)
        while (true) {
            val now = clock.now().toJavaInstant()
            if (!now.isBefore(end)) break
            val hour = now.atZone(request.zone).truncatedTo(ChronoUnit.HOURS)
            val next = hour.plusMinutes(REFRESH_MINUTE).let { if (it.toInstant().isAfter(now)) it else it.plusHours(1) }
            delay((next.toInstant().toEpochMilli() - now.toEpochMilli()).milliseconds)
            emit(Unit)
        }
    }

    private companion object {
        const val REFRESH_MINUTE = 20L
    }
}

/** The key telling the periods and comparisons apart. */
private val EnergyRequest.key: String get() = "${period.start}-${period.end}-${compareMode?.value}"

private fun EnergyData.matches(request: EnergyRequest) = period == request.period && compareMode == request.compareMode

/** The responses `getEnergyData` gathers for [request], with what they were planned from, as one bundle. */
private suspend fun ServerSession.fetchEnergy(
    prefsJson: JsonObject,
    request: EnergyRequest,
    environment: EnergyEnvironment,
): Fetched<JsonObject> = request(ENERGY_INFO_COMMAND).expect<JsonObject>().flatMap { infoJson ->
    val info = EnergyInfo.fromJson(infoJson)
    val prefs = EnergyPreferences.fromJson(prefsJson)
    val metadataCommand = prefs.metadataCommand(info)
    val metadata = metadataCommand?.let { request(it).expect<JsonArray>() } ?: Fetched.Success(JsonArray(emptyList()))
    metadata.flatMap { metadataJson ->
        val plan = planEnergyFetch(prefs, info, parseStatisticsMetadata(metadataJson), request, environment)
        val results = coroutineScope {
            PLAN_PARTS.map { (name, command) -> name to command(plan)?.let { async { request(it) } } }
        }.map { (name, result) -> name to (result?.await() ?: Fetched.Success(null)) }
        bundle(
            listOf(
                PREFS to Fetched.Success(prefsJson),
                INFO to Fetched.Success(infoJson),
                METADATA to Fetched.Success(metadataJson),
                PLAN to Fetched.Success(plan.toJson()),
            ) + results,
        )
    }
}

private suspend fun ServerSession.request(command: WsCommand): Fetched<JsonElement?> =
    request(command.type, command.params)

/** Each part of the plan, by the name its result is kept under. */
private val PLAN_PARTS: List<Pair<String, (EnergyFetchPlan) -> WsCommand?>> = listOf(
    "energy" to { it.energy },
    "power" to { it.power },
    "power_hour" to { it.powerHour },
    "water" to { it.water },
    "energy_compare" to { it.energyCompare },
    "water_compare" to { it.waterCompare },
    "fossil" to { it.fossil },
    "fossil_compare" to { it.fossilCompare },
)

/** The energy data of a bundle, whether just loaded or read back from the cache. */
internal fun parseEnergyBundle(bundle: JsonObject): Fetched<EnergyData> {
    val basis = bundle.basis()
    val plan = (bundle[PLAN] as? JsonObject)?.let(::planFromJson)
    if (basis == null || plan == null) return unexpected("energy data")
    fun part(name: String) = bundle[name] as? JsonObject
    val results = EnergyFetchResults(
        energy = part("energy"),
        power = part("power"),
        powerHour = part("power_hour"),
        water = part("water"),
        energyCompare = part("energy_compare"),
        waterCompare = part("water_compare"),
        fossil = part("fossil"),
        fossilCompare = part("fossil_compare"),
    )
    return Fetched.Success(plan.assemble(basis.prefs, basis.info, basis.metadata, results))
}

/** What the plan was made from. */
private class Basis(val prefs: EnergyPreferences, val info: EnergyInfo, val metadata: List<StatisticsMetadata>)

private fun JsonObject.basis(): Basis? {
    val prefs = (this[PREFS] as? JsonObject)?.let(EnergyPreferences::fromJson)
    val info = (this[INFO] as? JsonObject)?.let(EnergyInfo::fromJson)
    val metadata = (this[METADATA] as? JsonArray)?.let(::parseStatisticsMetadata)
    return if (prefs == null || info == null || metadata == null) null else Basis(prefs, info, metadata)
}

/** What the plan decided besides its commands, which assembling the data needs. */
private fun EnergyFetchPlan.toJson() = buildJsonObject {
    put("start", period.start.toString())
    put("end", period.end.toString())
    put("compare_start", comparePeriod?.start?.toString())
    put("compare_end", comparePeriod?.end?.toString())
    put("compare_mode", compareMode?.value)
    put("co2_signal_entity", co2SignalEntity)
    put("gas_unit", gasUnit)
    put("water_unit", waterUnit)
}

private fun planFromJson(json: JsonObject): EnergyFetchPlan? {
    val period = period(json.string("start"), json.string("end"))
    val gasUnit = json.string("gas_unit")
    val waterUnit = json.string("water_unit")
    if (period == null || gasUnit == null || waterUnit == null) return null
    return EnergyFetchPlan(
        period = period,
        comparePeriod = period(json.string("compare_start"), json.string("compare_end")),
        compareMode = CompareMode.entries.firstOrNull { it.value == json.string("compare_mode") },
        energy = null,
        power = null,
        powerHour = null,
        water = null,
        energyCompare = null,
        waterCompare = null,
        fossil = null,
        fossilCompare = null,
        co2SignalEntity = json.string("co2_signal_entity"),
        gasUnit = gasUnit,
        waterUnit = waterUnit,
    )
}

private fun period(start: String?, end: String?): EnergyPeriod? = try {
    if (start == null || end == null) null else EnergyPeriod(LocalDate.parse(start), LocalDate.parse(end))
} catch (e: DateTimeParseException) {
    null.also { Timber.w(e, "Ignoring an energy period that can't be read") }
}

private const val PREFS = "prefs"
private const val INFO = "info"
private const val METADATA = "metadata"
private const val PLAN = "plan"
