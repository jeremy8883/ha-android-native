package io.homeassistant.companion.android.dashboard.ui

import io.homeassistant.companion.android.dashboard.data.EnergyRepository
import io.homeassistant.companion.android.dashboard.data.Loadable
import io.homeassistant.companion.android.dashboard.data.valueOrNull
import io.homeassistant.companion.android.dashboard.energy.CompareMode
import io.homeassistant.companion.android.dashboard.energy.EnergyCollection
import io.homeassistant.companion.android.dashboard.energy.EnergyData
import io.homeassistant.companion.android.dashboard.energy.EnergyEnvironment
import io.homeassistant.companion.android.dashboard.energy.EnergyPeriod
import io.homeassistant.companion.android.dashboard.energy.EnergyRequest
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.ViewConfig
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.strategy.energy.DEFAULT_ENERGY_COLLECTION_KEY
import io.homeassistant.companion.android.dashboard.strategy.energy.DEFAULT_POWER_COLLECTION_KEY
import io.homeassistant.companion.android.dashboard.strategy.energy.HOME_ENERGY_COLLECTION_KEY
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonObject

/**
 * The energy collections of the shown view: the period and comparison each one's date selection chose, and their
 * data. Like the frontend's, a collection shows the default period (today) until another is chosen, following the
 * day as it changes.
 */
internal class EnergyCollections(private val repository: EnergyRepository) {
    private val selections = MutableStateFlow<Map<String, Selection>>(emptyMap())

    /** The last data of each collection, shown while another period loads. */
    private val lastData = ConcurrentHashMap<String, EnergyData>()

    /**
     * The collections [keys] name, for the energy preferences [prefs] (the `energy/get_prefs` result, `null` when
     * not set up), at the time [now].
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun collections(
        keys: Flow<Set<String>>,
        prefs: Flow<JsonObject?>,
        environment: Flow<EnergyEnvironment?>,
        now: Flow<ZonedDateTime?>,
    ): Flow<Map<String, EnergyCollection>> = combine(keys, selections, prefs, environment, now.map { it?.toDay() }) {
            shownKeys,
            chosen,
            prefsJson,
            env,
            day,
        ->
        if (prefsJson == null || env == null || day == null) {
            null
        } else {
            Inputs(shownKeys.associateWith { key -> request(key, chosen[key], day) }, prefsJson, env, day)
        }
    }
        .distinctUntilChanged()
        .flatMapLatest { inputs ->
            if (inputs == null || inputs.requests.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(inputs.requests.map { (key, request) -> collection(key, request, inputs) }) { it.toMap() }
            }
        }

    private fun collection(key: String, request: EnergyRequest, inputs: Inputs): Flow<Pair<String, EnergyCollection>> {
        val cached = request.compareMode == null && request.period == defaultPeriod(key, inputs.day)
        return repository.energyData(key, inputs.prefs, request, inputs.environment, cached).map { loadable ->
            loadable.valueOrNull?.let { lastData[key] = it }
            key to EnergyCollection(
                period = request.period,
                compareMode = request.compareMode,
                data = loadable.valueOrNull ?: lastData[key],
                loading = loadable is Loadable.Loading || (loadable as? Loadable.Ready)?.refreshing == true,
                failed = loadable is Loadable.Failed,
            )
        }
    }

    /** Show [period] in the collection [key]; the default period follows the day again. */
    fun change(key: String, change: EnergyChange, now: ZonedDateTime) = when (change) {
        is EnergyChange.Period -> setPeriod(key, change.period, now)
        is EnergyChange.Compare -> setCompare(key, change.mode)
    }

    private fun setPeriod(key: String, period: EnergyPeriod, now: ZonedDateTime) {
        val chosen = period.takeUnless { it == defaultPeriod(key, now.toDay()) }
        selections.update { it + (key to (it[key] ?: Selection()).copy(period = chosen)) }
    }

    /** Compare the collection [key]'s period with the one before ([mode] `null` not to compare). */
    private fun setCompare(key: String, mode: CompareMode?) {
        selections.update { it + (key to (it[key] ?: Selection()).copy(compareMode = mode)) }
    }

    private fun request(key: String, selection: Selection?, day: Day) = EnergyRequest(
        period = selection?.period ?: defaultPeriod(key, day),
        compareMode = selection?.compareMode,
        zone = day.time.zone,
    )

    /**
     * What a collection shows until another period is chosen: port of `getEnergyDataCollection`'s default, and today
     * for the home dashboard's summary, which sets it so.
     */
    private fun defaultPeriod(key: String, day: Day) = if (key == HOME_ENERGY_COLLECTION_KEY) {
        EnergyPeriod.day(day.time.toLocalDate())
    } else {
        EnergyPeriod.default(
            day.time.toLocalDate(),
            day.time.hour,
            midnightRollover =
            key == DEFAULT_POWER_COLLECTION_KEY,
        )
    }

    /** A chosen period (`null` for the default) and comparison. */
    private data class Selection(val period: EnergyPeriod? = null, val compareMode: CompareMode? = null)

    /** The current hour, which is all the default periods depend on. */
    private data class Day(val time: ZonedDateTime)

    private fun ZonedDateTime.toDay() = Day(truncatedTo(java.time.temporal.ChronoUnit.HOURS))

    private data class Inputs(
        val requests: Map<String, EnergyRequest>,
        val prefs: JsonObject,
        val environment: EnergyEnvironment,
        val day: Day,
    )
}

/** What an energy date selection changes in its collection. */
internal sealed interface EnergyChange {
    /** Show [period]. */
    data class Period(val period: EnergyPeriod) : EnergyChange

    /** Compare with another period ([mode] `null` not to compare). */
    data class Compare(val mode: CompareMode?) : EnergyChange
}

/** The energy collections [cards] and the [view]'s footer and badges read. */
internal fun energyCollectionKeys(cards: List<CardConfig>, view: ViewConfig?): Set<String> {
    val footer = listOfNotNull(view?.json?.obj("footer")?.obj("card"))
    val badges = view?.json?.objects("badges").orEmpty()
    return (cards.map { it.json } + footer + badges).mapNotNull(::energyCollectionKey).toSet()
}

/** The collection an energy card reads: its `collection_key`, by default the energy dashboard's. */
private fun energyCollectionKey(card: JsonObject): String? {
    val type = card.string("type").orEmpty()
    if (type == HOME_SUMMARY && card.string("summary") == ENERGY_SUMMARY) return HOME_ENERGY_COLLECTION_KEY
    val isEnergy = ENERGY_CARD_PREFIXES.any(type::startsWith) || type in ENERGY_BADGES
    return if (isEnergy) card.string("collection_key") ?: DEFAULT_ENERGY_COLLECTION_KEY else null
}

private const val HOME_SUMMARY = "home-summary"
private const val ENERGY_SUMMARY = "energy"
private val ENERGY_CARD_PREFIXES = listOf("energy-", "power-", "water-")
private val ENERGY_BADGES = setOf("gas-total")
