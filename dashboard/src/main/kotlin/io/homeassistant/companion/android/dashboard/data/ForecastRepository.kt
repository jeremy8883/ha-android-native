package io.homeassistant.companion.android.dashboard.data

import io.homeassistant.companion.android.dashboard.weather.ForecastEvent
import io.homeassistant.companion.android.dashboard.weather.ForecastKey
import io.homeassistant.companion.android.dashboard.weather.ForecastResult
import io.homeassistant.companion.android.dashboard.weather.parseForecastEvent
import io.homeassistant.companion.android.dashboard.weather.subscribeForecastCommand
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import timber.log.Timber

/** Weather forecasts as the weather forecast card subscribes to them. Kept in memory only, while shown. */
class ForecastRepository @Inject constructor(private val sessions: ServerSessions) {

    /** [key]'s forecast, the latest the server sent (`weather/subscribe_forecast`), subscribed again after a reconnection. */
    fun forecast(key: ForecastKey): Flow<Loadable<ForecastEvent>> = sessions.withServer { session ->
        val description = "forecast of ${key.entityId}"
        KeptData<ForecastEvent>(description, NotKept(), session.connection).subscribed<JsonElement>(
            subscribe = {
                val command = subscribeForecastCommand(key)
                session.subscribe(command.type, command.params)
            },
            reduce = { current, event ->
                (event as? JsonObject)?.let(::parseForecastEvent)
                    ?: current.also { Timber.w("Ignoring an unexpected $description message") }
            },
        )
    }
}

/**
 * This subscription's outcome for the cards: what the server sent, or why it failed (logged, with [key]); `null`
 * while nothing came yet.
 */
internal fun Loadable<ForecastEvent>.toForecastResult(key: ForecastKey): ForecastResult? = when (this) {
    Loadable.Loading -> null
    is Loadable.Ready -> ForecastResult.Received(value)
    is Loadable.Failed -> {
        Timber.w("Couldn't subscribe to the $key forecast: $error")
        ForecastResult.Failed(error.forecastReason())
    }
}

/** Why a forecast failed, as upstream's `err.message || err.code`, or its words for a lost connection. */
private fun LoadError.forecastReason(): String = when (this) {
    is LoadError.Server -> message?.ifEmpty { null } ?: code?.ifEmpty { null } ?: UNKNOWN_ERROR
    LoadError.NoResponse, LoadError.NoServer -> CONNECTION_LOST
    is LoadError.UnexpectedResponse -> UNKNOWN_ERROR
}

// Upstream's words when an error has no message (connection-mixin.ts), in English upstream too
private const val UNKNOWN_ERROR = "unknown error"
private const val CONNECTION_LOST = "connection lost"
