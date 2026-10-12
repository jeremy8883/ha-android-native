package io.homeassistant.companion.android.dashboard.feature

import io.homeassistant.companion.android.dashboard.derive.DisplayColor
import io.homeassistant.companion.android.dashboard.derive.cssColor
import io.homeassistant.companion.android.dashboard.derive.supportsFeature
import io.homeassistant.companion.android.dashboard.display.parseJsDate
import io.homeassistant.companion.android.dashboard.display.weatherUnit
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.EntityStates
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.weather.ForecastKey
import io.homeassistant.companion.android.dashboard.weather.ForecastResult
import io.homeassistant.companion.android.dashboard.weather.eventOrNull
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.serialization.json.JsonObject

// Ports of `hui-temperature-forecast-card-feature` and `hui-precipitation-forecast-card-feature`
// (frontend@20260624.6 src/panels/lovelace/card-features/, with common/forecast.ts and common/graph-labels.ts).
// The drawing is in ForecastGeometry.kt.

/** Which forecast feature: temperatures, or precipitation as an amount or a [probability]. */
sealed interface ForecastKind {
    /** `temperature-forecast`. */
    data object Temperature : ForecastKind

    /** `precipitation-forecast`. */
    data class Precipitation(val probability: Boolean) : ForecastKind
}

/**
 * A forecast feature of a weather entity.
 *
 * @property resolution the forecast subscribed to: `daily`, `twice_daily` or `hourly`
 * @property entries the forecast's entries, `null` until it arrives (a spinner shows) or when it failed
 * @property failedToLoad why the forecast couldn't be loaded ("Failed to load forecast: …"), shown in its place;
 * `null` unless the subscription failed
 * @property color the configured colour, replacing the temperature gradient or the rainy colour
 * @property currentTemperature the entity's temperature, which starts an hourly temperature line
 * @property fahrenheit whether temperatures are in °F (the gradient works in °C)
 * @property imperial whether precipitation is in inches
 * @property nowMillis the time the hourly forecast starts at, `null` when unknown
 * @property amPm whether the hour labels show AM and PM (`useAmPm`)
 * @property zone the time zone of the labels
 */
data class ForecastFeature(
    val kind: ForecastKind,
    val resolution: String,
    val entries: List<JsonObject>?,
    val daysToShow: Int,
    val hoursToShow: Int,
    val showLabels: Boolean,
    val color: DisplayColor?,
    val currentTemperature: Double?,
    val fahrenheit: Boolean,
    val imperial: Boolean,
    val noForecast: String,
    val failedToLoad: String?,
    val nowMillis: Double?,
    val amPm: Boolean,
    val zone: ZoneId,
) : TileFeature {
    /** Whether the forecast is hourly (a line or slots), rather than one bar per day or half day. */
    val hourly: Boolean get() = resolution == HOURLY

    /** Entries per day: two for a twice-daily forecast. */
    val entriesPerDay: Int get() = if (resolution == TWICE_DAILY) 2 else 1
}

/** The forecast type a feature subscribes to: the configured one when supported, else the default. */
private fun forecastResolution(state: EntityState, configured: String?): String? {
    val supported = FORECAST_TYPES.filter { (flag, _) -> state.supportsFeature(flag) }.map { it.second }
    return configured?.takeIf { it in supported } ?: supported.firstOrNull()
}

/** Port of `supportsForecast`: weather entities with any forecast. */
private fun EntityState.supportsForecast(): Boolean =
    domain == WEATHER && FORECAST_TYPES.any { (flag, _) -> supportsFeature(flag) }

/** The temperature or precipitation forecast feature of [state], for weather entities with a forecast. */
internal fun HassSnapshot.forecastFeature(
    state: EntityState,
    feature: JsonObject,
    kind: ForecastKind,
    nowMillis: Double?,
): TileFeature? {
    val resolution = forecastResolution(state, feature.string("forecast_type"))
    if (!state.supportsForecast() || resolution == null) return null
    val result = forecasts[ForecastKey(state.entityId, resolution)]
    val name = if (kind is ForecastKind.Temperature) "temperature-forecast" else "precipitation-forecast"
    return ForecastFeature(
        kind = kind,
        resolution = resolution,
        // An event without a forecast is an empty one, as upstream's `forecastEvent.forecast ?? []`
        entries = result?.eventOrNull?.let { it.forecast.orEmpty() },
        daysToShow = number(feature["days_to_show"])?.toInt() ?: DEFAULT_DAYS_TO_SHOW,
        hoursToShow = number(feature["hours_to_show"])?.toInt() ?: DEFAULT_HOURS_TO_SHOW,
        showLabels = feature.boolean("show_labels") != false,
        color = feature.string("color")?.ifEmpty { null }?.let(::cssColor),
        currentTemperature = number(state.attributes["temperature"]),
        fahrenheit = weatherUnit(state, "temperature") == UNIT_F,
        imperial = weatherUnit(state, "precipitation") == UNIT_IN,
        noForecast = localize("ui.panel.lovelace.editor.features.types.$name.no_forecast"),
        failedToLoad = (result as? ForecastResult.Failed)?.let {
            localize("ui.panel.lovelace.editor.features.types.$name.failed_to_load", mapOf("error" to it.error))
        },
        nowMillis = nowMillis,
        amPm = formats.amPm,
        zone = formats.zone,
    )
}

/** The forecasts the forecast features of a tile [card] subscribe to. */
fun forecastFeatureRequests(card: CardConfig, states: EntityStates): List<ForecastKey> {
    val state = card.entity?.takeIf { card.type == "tile" }?.let(states::get) ?: return emptyList()
    return card.json.objects("features").filter { it.string("type") in FORECAST_FEATURE_TYPES }.mapNotNull {
        forecastResolution(state, it.string("forecast_type"))?.takeIf { state.supportsForecast() }
            ?.let { type -> ForecastKey(state.entityId, type) }
    }
}

/** An entry's time in milliseconds, `null` when it has none. */
internal fun JsonObject.timeMillis(): Double? =
    string("datetime")?.let { parseJsDate(it, ZoneOffset.UTC) }?.toEpochMilli()?.toDouble()

internal val FORECAST_FEATURE_TYPES = setOf("temperature-forecast", "precipitation-forecast")

/** `WeatherEntityFeature`'s forecast flags, in `getSupportedForecastTypes`' order. */
private val FORECAST_TYPES = listOf(1 to "daily", 2 to TWICE_DAILY, 4 to HOURLY)

private const val WEATHER = "weather"
private const val HOURLY = "hourly"
private const val TWICE_DAILY = "twice_daily"
private const val UNIT_F = "°F"
private const val UNIT_IN = "in"
private const val DEFAULT_DAYS_TO_SHOW = 7
private const val DEFAULT_HOURS_TO_SHOW = 24
