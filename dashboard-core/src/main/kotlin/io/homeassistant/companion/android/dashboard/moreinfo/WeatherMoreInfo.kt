package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.derive.ForecastColumns
import io.homeassistant.companion.android.dashboard.derive.SecondaryWeather
import io.homeassistant.companion.android.dashboard.derive.defaultSecondary
import io.homeassistant.companion.android.dashboard.derive.entityIcon
import io.homeassistant.companion.android.dashboard.derive.forecastColumns
import io.homeassistant.companion.android.dashboard.derive.secondaryAttribute
import io.homeassistant.companion.android.dashboard.derive.supportsFeature
import io.homeassistant.companion.android.dashboard.derive.temperatureUnit
import io.homeassistant.companion.android.dashboard.display.formatEntityAttributeValue
import io.homeassistant.companion.android.dashboard.display.formatEntityState
import io.homeassistant.companion.android.dashboard.display.formatNumber
import io.homeassistant.companion.android.dashboard.display.relativeTime
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.feature.number
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.weather.ForecastEvent
import io.homeassistant.companion.android.dashboard.weather.WeatherIcon
import io.homeassistant.companion.android.dashboard.weather.forecastToShow
import io.homeassistant.companion.android.dashboard.weather.weatherStateIcon
import java.time.Instant
import kotlinx.serialization.json.JsonNull

/**
 * Port of `more-info-weather` (frontend@20260624.6 src/dialogs/more-info/controls/more-info-weather.ts): the
 * condition's picture (else the entity's icon), the state and when it changed, the temperature and today's high and
 * low (else the precipitation or humidity), the pressure, humidity, wind and visibility, then the forecast of the
 * types the entity offers (tabs when several), daily by default, and the data's attribution.
 */
data class WeatherMoreInfo(
    val condition: WeatherIcon?,
    val icon: String?,
    val state: String,
    val changed: String,
    val temperature: String?,
    val rows: List<WeatherRow>,
    val forecastTitle: String?,
    val tabs: List<Pair<String, String>>,
    val defaultType: String?,
    val attribution: String? = null,
)

/** A reading under the conditions: its [icon], [name] and [value]. */
data class WeatherRow(val icon: String, val name: String, val value: String)

/** The forecast part of a weather's details: the line under the temperature, and the forecast's days. */
data class WeatherForecastView(val secondary: SecondaryWeather?, val columns: ForecastColumns?)

internal fun HassSnapshot.weatherMoreInfo(state: EntityState, now: Instant): WeatherMoreInfo {
    val attributes = state.attributes
    val shown = { key: String -> attributes[key].let { it != null && it !is JsonNull } }
    val row = { icon: String, name: String, attribute: String, value: () -> String ->
        WeatherRow(icon, localize("ui.card.weather.attributes.$name"), value()).takeIf { shown(attribute) }
    }
    val types = FORECAST_TYPES.filter { (feature, _) -> state.supportsFeature(feature) }.map { it.second }
    return WeatherMoreInfo(
        condition = weatherStateIcon(state.state),
        icon = entityIcon(state.entityId),
        state = formatEntityState(state),
        changed = formats.relativeTime(minOf(Instant.ofEpochMilli((state.lastChanged * MILLIS).toLong()), now), now)
            .replaceFirstChar { it.uppercaseChar() },
        temperature = number(attributes["temperature"])?.let { "${formatNumber(it)} ${temperatureUnit(state)}" },
        rows = listOfNotNull(
            row("mdi:gauge", "air_pressure", "pressure") { formatEntityAttributeValue(state, "pressure") },
            row("mdi:water-percent", "humidity", "humidity") { formatEntityAttributeValue(state, "humidity") },
            row("mdi:weather-windy", "wind_speed", "wind_speed") {
                secondaryAttribute(state, "wind_speed", null).value
            },
            row("mdi:eye", "visibility", "visibility") { formatEntityAttributeValue(state, "visibility") },
        ),
        forecastTitle = "${localize("ui.card.weather.forecast")}:".takeIf { types.isNotEmpty() },
        tabs = types.map { it to localize("ui.card.weather.$it") }.takeIf { it.size > 1 }.orEmpty(),
        defaultType = types.firstOrNull(),
        attribution = attributes.string("attribution")?.ifEmpty { null },
    )
}

/** The forecast of [state] from the subscribed [event] at [now], `null` columns while it hasn't come. */
fun HassSnapshot.weatherForecastView(state: EntityState, event: ForecastEvent?, now: Instant): WeatherForecastView {
    val forecast = forecastToShow(state.attributes, event, type = null)
    return WeatherForecastView(
        secondary = defaultSecondary(state, forecast?.entries.orEmpty(), digits = null, now),
        columns = forecast?.let { forecastColumns(it, digits = null, dashForMissingLow = false) },
    )
}

private const val MILLIS = 1000.0

/** Each forecast type with its `WeatherEntityFeature`, in upstream's order (the first supported is the default). */
private val FORECAST_TYPES = listOf(1 to "daily", 2 to "twice_daily", 4 to "hourly")
