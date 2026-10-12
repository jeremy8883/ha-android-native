package io.homeassistant.companion.android.dashboard.ui.lovelace.cards

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.derive.CurrentWeather
import io.homeassistant.companion.android.dashboard.derive.ForecastColumns
import io.homeassistant.companion.android.dashboard.derive.ForecastItem
import io.homeassistant.companion.android.dashboard.derive.WeatherForecastCardModel
import io.homeassistant.companion.android.dashboard.derive.weatherForecastCardModel
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.ui.components.DashboardCardSurface
import io.homeassistant.companion.android.dashboard.ui.components.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.lovelace.common.CardInteractions
import io.homeassistant.companion.android.dashboard.ui.lovelace.common.elementGestures
import io.homeassistant.companion.android.dashboard.ui.lovelace.components.CardWarning
import io.homeassistant.companion.android.dashboard.ui.theme.Css
import io.homeassistant.companion.android.dashboard.ui.theme.cssPx
import java.time.Instant
import java.time.ZonedDateTime

/**
 * A weather forecast card, port of `hui-weather-forecast-card` (frontend@20260624.6
 * src/panels/lovelace/cards/hui-weather-forecast-card.ts): the current weather with its drawn condition, then the
 * forecast's entries side by side, scrolling sideways when they don't fit.
 *
 * Upstream: `hui-weather-forecast-card` (src/panels/lovelace/cards/hui-weather-forecast-card.ts)
 */
@Composable
internal fun WeatherForecastCard(
    card: CardConfig,
    hass: State<HassSnapshot?>,
    now: State<ZonedDateTime?>,
    interactions: CardInteractions,
    modifier: Modifier = Modifier,
) {
    val weather by remember(card) {
        derivedStateOf { hass.value?.weatherForecastCardModel(card, now.value?.toInstant() ?: Instant.EPOCH) }
    }
    when (val model = weather) {
        null -> Unit
        is WeatherForecastCardModel.Warning -> CardWarning(model.text, modifier)
        is WeatherForecastCardModel.Unavailable -> DashboardCardSurface(
            modifier.elementGestures(model.actions, interactions),
        ) {
            Box(
                Modifier.fillMaxWidth().heightIn(min = UNAVAILABLE_HEIGHT).padding(UNAVAILABLE_PADDING),
                contentAlignment = Alignment.Center,
            ) {
                // `.unavailable`: `--ha-font-size-l`
                Text(
                    model.text,
                    style = Css.Text.Body.copy(textAlign = TextAlign.Center, fontSize = Css.FontSize.L),
                    color = LocalHAColorScheme.current.colorTextPrimary,
                )
            }
        }
        is WeatherForecastCardModel.Shown -> DashboardCardSurface(
            modifier.elementGestures(model.actions, interactions),
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val width = maxWidth
                val size = WeatherSize.of(width)
                Column(Modifier.fillMaxWidth().padding(vertical = Css.Space.S4)) {
                    model.current?.let { CurrentWeatherRow(it, size) }
                    model.forecast?.let { forecast ->
                        val top = if (model.current != null) Css.Space.S4 else 0.dp
                        ForecastRow(forecast, width, Modifier.padding(top = top))
                    }
                }
            }
        }
    }
}

/** Upstream's width classes: smaller text and icon when narrow, no name or measure when very narrow. */
private enum class WeatherSize(val icon: Dp, val big: TextUnit, val unit: TextUnit, val showDetails: Boolean) {
    REGULAR(64.cssPx, Css.FontSize.X3l, Css.FontSize.X2l, true),
    NARROW(52.cssPx, Css.FontSize.Xl, Css.FontSize.L, true),
    VERY_NARROW(52.cssPx, Css.FontSize.Xl, Css.FontSize.L, false),
    ;

    companion object {
        fun of(width: Dp): WeatherSize = when {
            width < VERY_NARROW_WIDTH -> VERY_NARROW
            width < NARROW_WIDTH -> NARROW
            else -> REGULAR
        }
    }
}

/**
 * The current weather: its condition drawn, its state and name, then the temperature and a chosen attribute.
 *
 * Upstream: part of `hui-weather-forecast-card` (src/panels/lovelace/cards/hui-weather-forecast-card.ts)
 */
@Composable
private fun CurrentWeatherRow(current: CurrentWeather, size: WeatherSize) {
    val colors = LocalHAColorScheme.current
    Row(Modifier.fillMaxWidth().padding(horizontal = Css.Space.S4), verticalAlignment = Alignment.CenterVertically) {
        val iconModifier = Modifier.padding(end = Css.Space.S4).size(size.icon)
        current.condition?.let { WeatherIconView(it, colors.colorTextSecondary, iconModifier) }
            ?: DashboardIcon(current.stateIcon, colors.colorTextSecondary, iconModifier)
        Column(Modifier.weight(1f).padding(end = Css.Space.S3)) {
            Text(
                current.state,
                style = Css.Text.Body.copy(
                    fontSize = size.big,
                    lineHeight = size.big * LINE_HEIGHT,
                    textAlign = TextAlign.Start,
                ),
                color = colors.colorTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (size.showDetails) {
                Text(
                    current.name,
                    style = Css.Text.Body.copy(textAlign = TextAlign.Start, lineHeight = Css.LineHeight.Condensed),
                    color = colors.colorTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    current.temperature ?: " ",
                    style = Css.Text.Body.copy(fontSize = size.big, lineHeight = size.big * LINE_HEIGHT),
                    color = colors.colorTextPrimary,
                )
                Text(
                    " ${current.temperatureUnit}",
                    style = Css.Text.Body.copy(fontSize = size.unit, lineHeight = size.unit * LINE_HEIGHT),
                    color = colors.colorTextPrimary,
                )
            }
            current.secondary?.takeIf { size.showDetails }?.let { secondary ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Css.Space.S1),
                ) {
                    secondary.icon?.let { DashboardIcon(it, colors.colorTextSecondary, Modifier.size(ATTRIBUTE_ICON)) }
                    Text(
                        listOfNotNull(secondary.label, secondary.value).joinToString(" "),
                        style = Css.Text.Body.copy(lineHeight = Css.LineHeight.Condensed),
                        color = colors.colorTextSecondary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * The entries spread across the card, scrolling sideways when they don't fit.
 *
 * Upstream: part of `hui-weather-forecast-card` (src/panels/lovelace/cards/hui-weather-forecast-card.ts)
 */
@Composable
internal fun ForecastRow(forecast: ForecastColumns, width: Dp, modifier: Modifier) {
    Row(
        modifier
            .horizontalScroll(rememberScrollState())
            .widthIn(min = width)
            .padding(horizontal = Css.Space.S4),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        forecast.groups.forEach { group ->
            Row {
                group.forEach { item -> ForecastEntry(item, forecast.dayHeaders) }
            }
        }
    }
}

/**
 * One forecast entry: its day or hour, its condition, and its high and low.
 *
 * Upstream: part of `hui-weather-forecast-card` (src/panels/lovelace/cards/hui-weather-forecast-card.ts)
 */
@Composable
private fun ForecastEntry(item: ForecastItem, dayHeaders: Boolean) {
    val colors = LocalHAColorScheme.current
    Column(
        Modifier.widthIn(min = ENTRY_MIN_WIDTH).padding(horizontal = Css.Space.S2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Css.Space.S1),
    ) {
        val small = Css.Text.Body.copy(fontSize = SMALL_FONT, lineHeight = SMALL_FONT)
        if (dayHeaders) {
            // The slot keeps the entries aligned when only the day's first has a header
            Text(
                item.dayHeader.orEmpty(),
                style = small.copy(fontWeight = FontWeight.Bold),
                color = colors.colorTextSecondary,
                maxLines = 1,
                modifier = Modifier.heightIn(min = HEADER_SLOT),
            )
        }
        Text(item.label, style = small, color = colors.colorTextSecondary, maxLines = 1)
        item.condition?.let {
            WeatherIconView(
                it,
                colors.colorTextSecondary,
                Modifier.padding(vertical = ICON_PADDING).size(FORECAST_ICON),
            )
        }
        Text(
            item.temperature,
            // `.forecast .temp`: `--ha-font-size-l` at `line-height: 1`
            style = Css.Text.Body.copy(fontSize = Css.FontSize.L, lineHeight = 1.em),
            color = colors.colorTextPrimary,
            maxLines = 1,
        )
        item.low?.let { Text(it, style = Css.Text.Body, color = colors.colorTextSecondary, maxLines = 1) }
    }
}

private const val LINE_HEIGHT = 1.2f
private val NARROW_WIDTH = 375.dp
private val VERY_NARROW_WIDTH = 300.dp
private val ATTRIBUTE_ICON = 20.cssPx
private val ENTRY_MIN_WIDTH = 48.cssPx
private val FORECAST_ICON = 40.cssPx
private val ICON_PADDING = 6.cssPx
private val HEADER_SLOT = 16.cssPx
private val SMALL_FONT = Css.FontSize.S
private val UNAVAILABLE_HEIGHT = 100.cssPx
private val UNAVAILABLE_PADDING = 20.dp
