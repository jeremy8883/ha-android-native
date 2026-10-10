package io.homeassistant.companion.android.dashboard.data

import io.homeassistant.companion.android.dashboard.energy.StatisticValue
import io.homeassistant.companion.android.dashboard.energy.StatisticsMetadata
import io.homeassistant.companion.android.dashboard.energy.WsCommand
import io.homeassistant.companion.android.dashboard.energy.parseStatistics
import io.homeassistant.companion.android.dashboard.energy.parseStatisticsMetadata
import io.homeassistant.companion.android.dashboard.history.HISTORY_HOURS
import io.homeassistant.companion.android.dashboard.history.HistoryStates
import io.homeassistant.companion.android.dashboard.history.historyStatisticsCommand
import io.homeassistant.companion.android.dashboard.history.merge
import io.homeassistant.companion.android.dashboard.history.parseHistoryStates
import io.homeassistant.companion.android.dashboard.history.statisticsMetadataCommand
import io.homeassistant.companion.android.dashboard.model.obj
import java.time.Instant
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaInstant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import timber.log.Timber

/**
 * The recent history of an entity, as the more-info dialog's history section loads it: the recorder's history
 * stream, or for sensors with statistics their 5-minute statistics. Kept in memory only, while shown.
 */
class HistoryRepository @Inject constructor(private val sessions: ServerSessions, private val clock: Clock) {

    /**
     * The states of the history stream [command] subscribes to, kept up to date: each message is added to the
     * history, and states older than [hours] (by default [HISTORY_HOURS]) are dropped as it goes. After a
     * reconnection the stream starts again from its first message. Port of `HistoryStream`.
     */
    fun stream(
        description: String,
        hours: Double = HISTORY_HOURS.toDouble(),
        command: () -> WsCommand,
    ): Flow<Loadable<HistoryStates>> = sessions.withServer { session ->
        KeptData<HistoryStates>(description, NotKept(), session.connection).subscribed<JsonElement>(
            subscribe = {
                val subscription = command()
                session.subscribe(subscription.type, subscription.params)
            },
            reduce = { current, event ->
                (event as? JsonObject)?.obj("states")?.let { states ->
                    val purgeBefore = (clock.now() - hours.hours).toJavaInstant().epochSecond.toDouble()
                    current.orEmpty().merge(parseHistoryStates(states), purgeBefore)
                } ?: null.also { Timber.w("Ignoring an unexpected $description message") }
            },
        )
    }

    /**
     * [entityId]'s 5-minute statistics over the last day, loaded again every minute as the frontend does; `null`
     * when it has none (no metadata), so its states are shown instead. A failure keeps what was loaded before.
     */
    fun statistics(entityId: String): Flow<Loadable<EntityStatistics?>> = sessions.withServer { session ->
        flow {
            var shown: EntityStatistics? = null
            var loaded = false
            while (true) {
                when (val result = session.loadStatistics(entityId, clock.now().toJavaInstant())) {
                    is Fetched.Success -> {
                        shown = result.value
                        loaded = true
                        emit(Loadable.Ready(result.value))
                    }
                    is Fetched.Failure -> {
                        Timber.w("Couldn't load the statistics of $entityId: ${result.error}")
                        emit(
                            if (loaded) {
                                Loadable.Ready(
                                    shown,
                                    refreshError = result.error,
                                )
                            } else {
                                Loadable.Failed(result.error)
                            },
                        )
                    }
                }
                delay(STATISTICS_REFRESH)
            }
        }
    }

    private suspend fun ServerSession.loadStatistics(entityId: String, now: Instant): Fetched<EntityStatistics?> {
        val metadata = request(statisticsMetadataCommand(entityId)).expect<JsonArray>()
        val stats = request(historyStatisticsCommand(entityId, now)).expect<JsonObject>()
        return metadata.flatMap { metadataJson ->
            val found = parseStatisticsMetadata(metadataJson).firstOrNull { it.statisticId == entityId }
            stats.map { statsJson ->
                found?.let { EntityStatistics(it, parseStatistics(statsJson)[entityId].orEmpty()) }
            }
        }
    }

    private suspend fun ServerSession.request(command: WsCommand): Fetched<JsonElement?> =
        request(command.type, command.params)

    private companion object {
        val STATISTICS_REFRESH = 1.minutes
    }
}

/** An entity's statistics with their [metadata]. */
data class EntityStatistics(val metadata: StatisticsMetadata, val stats: List<StatisticValue>)
