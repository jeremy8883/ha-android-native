package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.action.ElementActions
import io.homeassistant.companion.android.dashboard.action.elementActions
import io.homeassistant.companion.android.dashboard.display.formatEntityState
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.feature.number
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.weather.Forecast
import io.homeassistant.companion.android.dashboard.weather.ForecastKey
import io.homeassistant.companion.android.dashboard.weather.WeatherIcon
import io.homeassistant.companion.android.dashboard.weather.forecastToShow
import io.homeassistant.companion.android.dashboard.weather.needsForecastSubscription
import io.homeassistant.companion.android.dashboard.weather.weatherStateIcon
import java.time.Instant
import kotlinx.serialization.json.JsonObject

/**
 * A weather forecast card: the current weather (its drawn condition, state, name, temperature and one more
 * measure) and the forecast's next entries. Port of `hui-weather-forecast-card` (frontend@20260624.6
 * src/panels/lovelace/cards/hui-weather-forecast-card.ts) at its regular (not short) height.
 */
sealed interface WeatherForecastCardModel {
    /** A warning in its place: the entity doesn't exist. */
    data class Warning(val text: String) : WeatherForecastCardModel

    /** The entity is unavailable: [text] says so in the card. */
    data class Unavailable(val text: String, val actions: ElementActions) : WeatherForecastCardModel

    /** The weather, [current] unless hidden, and the [forecast] when shown and known. */
    data class Shown(val current: CurrentWeather?, val forecast: ForecastColumns?, val actions: ElementActions) :
        WeatherForecastCardModel
}

/**
 * The current weather: the [condition]'s drawing (else the entity's [stateIcon]), its [state] and [name], the
 * [temperature] with its [temperatureUnit], and the [secondary] measure under it.
 */
data class CurrentWeather(
    val condition: WeatherIcon?,
    val stateIcon: String?,
    val state: String,
    val name: String,
    val temperature: String?,
    val temperatureUnit: String,
    val secondary: SecondaryWeather?,
)

/** The measure under the temperature: its [icon] (else its [label]) and [value]. */
data class SecondaryWeather(val icon: String?, val label: String?, val value: String)

/** The forecast's entries in groups, one per day with its weekday above for hourly and twice daily forecasts. */
data class ForecastColumns(val groups: List<List<ForecastItem>>, val dayHeaders: Boolean)

/**
 * A forecast entry: its time [label] (a [dayNight] label for twice daily ones), its day's [dayHeader] on the day's
 * first entry, its [condition], and its temperature and low ("—" when unknown, [low] `null` when not shown).
 */
data class ForecastItem(
    val label: String,
    val dayNight: Boolean,
    val dayHeader: String?,
    val condition: WeatherIcon?,
    val temperature: String,
    val low: String?,
)

/** The weather forecast card of [card] at [now] (for today's high and low). */
fun HassSnapshot.weatherForecastCardModel(card: CardConfig, now: Instant): WeatherForecastCardModel {
    val json = card.json
    val entityId = json.string("entity").orEmpty()
    val state = states[entityId]
    val actions = elementActions(json, tapWhenUnset = true)
    val forecastType = json.string("forecast_type")
    return when {
        state == null -> WeatherForecastCardModel.Warning(localize("ui.card.common.entity_not_found"))
        state.state == STATE_UNAVAILABLE -> WeatherForecastCardModel.Unavailable(
            localize(
                "ui.panel.lovelace.warning.entity_unavailable",
                mapOf("entity" to "${entityNameDisplay(state, json["name"])} ($entityId)"),
            ),
            actions,
        )
        else -> {
            val event = forecastType?.takeIf(::needsForecastSubscription)?.let { forecasts[ForecastKey(entityId, it)] }
            val shown = forecastToShow(state.attributes, event, forecastType)
                ?.takeIf { json.boolean("show_forecast") != false && it.entries.isNotEmpty() }
                ?.let { it.copy(entries = it.entries.take(number(json["forecast_slots"])?.toInt() ?: DEFAULT_SLOTS)) }
            val digits = if (json.boolean("round_temperature") == true) 0 else null
            WeatherForecastCardModel.Shown(
                current = currentWeather(state, json, shown, digits, now)
                    .takeIf { shown == null || json.boolean("show_current") != false },
                forecast = shown?.let { forecastColumns(it, digits) },
                actions = actions,
            )
        }
    }
}

private fun HassSnapshot.currentWeather(
    state: EntityState,
    json: JsonObject,
    forecast: Forecast?,
    digits: Int?,
    now: Instant,
): CurrentWeather {
    val temperature = number(state.attributes["temperature"])
    return CurrentWeather(
        condition = weatherStateIcon(state.state),
        stateIcon = entityIcon(state.entityId),
        state = formatEntityState(state),
        name = entityNameDisplay(state, json["name"]),
        temperature = temperature?.let { formatTemperature(it, digits) },
        temperatureUnit = temperatureUnit(state),
        secondary = json.string("secondary_info_attribute")?.let { secondaryAttribute(state, it, digits) }
            ?: defaultSecondary(state, forecast?.entries.orEmpty(), digits, now),
    )
}

private const val DEFAULT_SLOTS = 5
