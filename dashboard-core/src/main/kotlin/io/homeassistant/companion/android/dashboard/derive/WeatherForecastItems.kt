package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.display.DatePart
import io.homeassistant.companion.android.dashboard.display.parseJsDate
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.feature.number
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.weather.Forecast
import io.homeassistant.companion.android.dashboard.weather.weatherStateIcon
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

// The weather forecast card's forecast entries. Port of its `_groupForecastByDay` and `_renderForecastItem`
// (frontend@20260624.6 src/panels/lovelace/cards/hui-weather-forecast-card.ts).

/** The forecast's entries with a temperature, grouped by day (with the weekday on the first) when not daily. */
internal fun HassSnapshot.forecastColumns(forecast: Forecast, digits: Int?): ForecastColumns {
    val dayHeaders = forecast.type == HOURLY || forecast.type == TWICE_DAILY
    val dated = forecast.entries.map { it to it.string("datetime")?.let { text -> parseJsDate(text, formats.zone) } }
    val groups = if (dayHeaders) {
        dated.groupBy { (_, at) ->
            at?.let { LocalDate.ofInstant(it, formats.zone) }
        }.values
    } else {
        listOf(dated)
    }
    return ForecastColumns(
        groups = groups.map { group ->
            group.filter { (entry, _) -> entry["temperature"].isSet() || entry["templow"].isSet() }
                .mapIndexed { index, (entry, at) ->
                    forecastItem(
                        entry,
                        at,
                        forecast.type,
                        digits,
                        dayHeaders && index == 0,
                    )
                }
        }.filter { it.isNotEmpty() },
        dayHeaders = dayHeaders,
    )
}

/** Port of `_renderForecastItem`: [entry] at [at], with its day's weekday above it when [withHeader]. */
private fun HassSnapshot.forecastItem(
    entry: JsonObject,
    at: Instant?,
    type: String,
    digits: Int?,
    withHeader: Boolean,
): ForecastItem {
    val hourly = type == HOURLY
    val daytime = entry.boolean("is_daytime") != false
    return ForecastItem(
        label = when {
            type == TWICE_DAILY -> localize(if (daytime) "ui.card.weather.day" else "ui.card.weather.night")
            hourly -> at?.let(formats::time).orEmpty()
            else -> at?.let(::weekday).orEmpty()
        },
        dayNight = type == TWICE_DAILY,
        dayHeader = at?.takeIf { withHeader }?.let(::weekday),
        condition = entry.string("condition")?.let {
            weatherStateIcon(it, night = entry.boolean("is_daytime") == false)
        },
        temperature = number(entry["temperature"])?.let { "${formatTemperature(it, digits)}°" } ?: NO_VALUE,
        low = number(entry["templow"])?.let { "${formatTemperature(it, digits)}°" } ?: NO_VALUE.takeUnless { hourly },
    )
}

private fun JsonElement?.isSet() = this != null && this !is JsonNull

private fun HassSnapshot.weekday(at: Instant): String =
    formats.datePart(LocalDate.ofInstant(at, formats.zone), DatePart.WEEKDAY_SHORT)

/** `formatNumber` with `maximumFractionDigits` of [digits], which unset is `Intl`'s 3. */
internal fun HassSnapshot.formatTemperature(value: Double, digits: Int?): String =
    formats.number(BigDecimal.valueOf(value), 0, digits ?: INTL_MAX_FRACTION_DIGITS)

private const val HOURLY = "hourly"
private const val TWICE_DAILY = "twice_daily"
private const val NO_VALUE = "—"
private const val INTL_MAX_FRACTION_DIGITS = 3
