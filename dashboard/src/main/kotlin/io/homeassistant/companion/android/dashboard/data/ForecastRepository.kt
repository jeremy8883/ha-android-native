package io.homeassistant.companion.android.dashboard.data

import io.homeassistant.companion.android.dashboard.weather.ForecastEvent
import io.homeassistant.companion.android.dashboard.weather.ForecastKey
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
