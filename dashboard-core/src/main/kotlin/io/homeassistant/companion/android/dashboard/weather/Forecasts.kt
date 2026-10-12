package io.homeassistant.companion.android.dashboard.weather

import io.homeassistant.companion.android.dashboard.derive.supportsFeature
import io.homeassistant.companion.android.dashboard.display.parseJsDate
import io.homeassistant.companion.android.dashboard.energy.WsCommand
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.EntityStates
import io.homeassistant.companion.android.dashboard.feature.forecastFeatureRequests
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import java.time.ZoneOffset
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Weather forecasts. Ports of `ForecastEvent`, `subscribeForecast` and `getForecast` (frontend@20260624.6
// src/data/weather.ts).

/** A forecast a card subscribes to: [entityId]'s of [type] (`daily`, `hourly` or `twice_daily`). */
data class ForecastKey(val entityId: String, val type: String)

/** The forecast the server sent, its entries oldest first (`null` when the entity has none). */
data class ForecastEvent(val type: String, val forecast: List<JsonObject>?)

/** A forecast subscription's outcome: what the server last sent, or why the subscription failed. */
sealed interface ForecastResult {
    /** The server sent [event]. */
    data class Received(val event: ForecastEvent) : ForecastResult

    /** The subscription failed: [error] is why (upstream's `err.message || err.code`). */
    data class Failed(val error: String) : ForecastResult
}

/** The forecast the server sent, `null` while it hasn't (or when the subscription failed). */
val ForecastResult.eventOrNull: ForecastEvent? get() = (this as? ForecastResult.Received)?.event

/** A forecast to show: its entries, and whether they are `daily`, `hourly` or `twice_daily`. */
data class Forecast(val entries: List<JsonObject>, val type: String)

/** The subscription to [key]'s forecast (`weather/subscribe_forecast`). */
fun subscribeForecastCommand(key: ForecastKey): WsCommand = WsCommand(
    "weather/subscribe_forecast",
    buildJsonObject {
        put("forecast_type", key.type)
        put("entity_id", key.entityId)
    },
)

/** Read a forecast subscription's event; `null` when it isn't one. */
fun parseForecastEvent(event: JsonObject): ForecastEvent? = event.string("type")?.let { type ->
    ForecastEvent(type, (event["forecast"] as? JsonArray)?.let { event.objects("forecast") })
}

/**
 * Port of `getForecast`: the subscribed [event] of the configured [type] when it has more than two entries; without
 * a type, the event, or else the entity's legacy `forecast` attribute (from [attributes]); `legacy` reads only that.
 */
fun forecastToShow(attributes: JsonObject, event: ForecastEvent?, type: String?): Forecast? {
    val subscribed = event?.forecast?.takeIf { it.size > MIN_ENTRIES }?.let { Forecast(it, event.type) }
    return when (type) {
        null -> subscribed ?: legacyForecast(attributes)
        LEGACY -> legacyForecast(attributes)
        else -> subscribed?.takeIf { it.type == type }
    }
}

/** Port of `getLegacyForecast`: hourly when entries are under 8 hours apart, twice daily under a day, else daily. */
private fun legacyForecast(attributes: JsonObject): Forecast? {
    val entries = (attributes["forecast"] as? JsonArray)?.let { attributes.objects("forecast") }
        ?.takeIf { it.size > MIN_ENTRIES } ?: return null
    val gap = entries.secondGap()
    return Forecast(
        entries,
        when {
            gap != null && gap < EIGHT_HOURS_MS -> "hourly"
            gap != null && gap < DAY_MS -> "twice_daily"
            else -> "daily"
        },
    )
}

/** The time from the second to the third entry, in milliseconds. */
private fun List<JsonObject>.secondGap(): Long? {
    val (second, third) = listOf(this[1], this[2]).map { entry ->
        entry.string("datetime")?.let { parseJsDate(it, ZoneOffset.UTC) }
    }
    return if (second != null && third != null) third.toEpochMilli() - second.toEpochMilli() else null
}

/** Whether [type] needs a forecast subscription, as `_needForecastSubscription` checks. */
fun needsForecastSubscription(type: String?): Boolean = type != null && type != LEGACY

private const val LEGACY = "legacy"
private const val MIN_ENTRIES = 2
private const val EIGHT_HOURS_MS = 28_800_000L
private const val DAY_MS = 86_400_000L

/**
 * The forecasts the shown [cards] subscribe to, when the server has weather (`isComponentLoaded(weather)`): weather
 * forecast cards with a `forecast_type` (other than `legacy`), the entities cards' weather rows, each its
 * entity's default type (`hui-weather-entity-row`), and the tiles' forecast features.
 */
fun forecastRequests(cards: List<CardConfig>, components: Set<String>, states: EntityStates): Set<ForecastKey> {
    if (WEATHER !in components) return emptySet()
    val forecastCards = cards.filter { it.type == WEATHER_FORECAST_CARD }.mapNotNull { card ->
        val type = card.json.string("forecast_type")?.takeIf(::needsForecastSubscription)
        val entityId = card.json.string("entity")
        if (type != null && entityId != null) ForecastKey(entityId, type) else null
    }
    val rows = cards.filter { it.type == ENTITIES_CARD }.flatMap { card ->
        (card.json["entities"] as? JsonArray).orEmpty().mapNotNull { entry ->
            val entityId = (entry as? JsonPrimitive)?.content ?: (entry as? JsonObject)?.string("entity")
            entityId?.takeIf { it.startsWith("$WEATHER.") }?.let(states::get)
                ?.let { state -> defaultForecastType(state)?.let { ForecastKey(state.entityId, it) } }
        }
    }
    return (forecastCards + rows + cards.flatMap { forecastFeatureRequests(it, states) }).toSet()
}

/** Port of `getDefaultForecastType`: daily, else twice daily, else hourly, as [state] supports them. */
fun defaultForecastType(state: EntityState): String? =
    FORECAST_FEATURES.firstOrNull { (feature, _) -> state.supportsFeature(feature) }?.second

private val FORECAST_FEATURES = listOf(1 to "daily", 2 to "twice_daily", 4 to "hourly")

private const val WEATHER = "weather"
private const val WEATHER_FORECAST_CARD = "weather-forecast"
private const val ENTITIES_CARD = "entities"
