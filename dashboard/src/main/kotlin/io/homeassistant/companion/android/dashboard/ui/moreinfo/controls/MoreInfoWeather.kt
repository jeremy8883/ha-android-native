package io.homeassistant.companion.android.dashboard.ui.moreinfo.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.data.ForecastRepository
import io.homeassistant.companion.android.dashboard.data.toForecastResult
import io.homeassistant.companion.android.dashboard.derive.SecondaryWeather
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.moreinfo.WeatherForecastView
import io.homeassistant.companion.android.dashboard.moreinfo.WeatherMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.weatherForecastView
import io.homeassistant.companion.android.dashboard.ui.components.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.lovelace.cards.ForecastRow
import io.homeassistant.companion.android.dashboard.ui.lovelace.cards.WeatherIconView
import io.homeassistant.companion.android.dashboard.ui.theme.Css
import io.homeassistant.companion.android.dashboard.weather.ForecastKey
import io.homeassistant.companion.android.dashboard.weather.ForecastResult
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Port of `more-info-weather`: the condition's picture (else the icon), the state and when it changed, the
 * temperature over today's high and low, the readings, then "Forecast:" with a tab per type the entity offers and
 * the chosen type's forecast (a spinner until it comes, why it failed when it did).
 *
 * Upstream: `more-info-weather` (src/dialogs/more-info/controls/more-info-weather.ts)
 */
@Composable
internal fun MoreInfoWeather(weather: WeatherMoreInfo, state: EntityState, hass: HassSnapshot, now: Instant) {
    val colors = LocalHAColorScheme.current
    var type by remember(state.entityId) { mutableStateOf(weather.defaultType) }
    val forecasts = hiltViewModel<MoreInfoForecastViewModel>(key = "forecast-${state.entityId}")
    LaunchedEffect(state.entityId, type) { type?.let { forecasts.show(ForecastKey(state.entityId, it)) } }
    val result by forecasts.result.collectAsStateWithLifecycle()
    val view = remember(state, result, now) { hass.weatherForecastView(state, result, now) }
    Column(verticalArrangement = Arrangement.spacedBy(Css.Space.S4)) {
        WeatherConditions(weather, view.secondary)
        weather.rows.forEach { row ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Css.Space.S4),
            ) {
                DashboardIcon(row.icon, colors.colorTextSecondary, Modifier.size(HASize.X2L))
                Text(
                    row.name,
                    style = Css.Text.Body.copy(textAlign = TextAlign.Start),
                    color = colors.colorTextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(row.value, style = Css.Text.Body, color = colors.colorTextPrimary)
            }
        }
        weather.forecastTitle?.let { title ->
            Text(
                title,
                style = Css.Text.Body.copy(fontWeight = FontWeight.Medium, textAlign = TextAlign.Start),
                color = colors.colorTextPrimary,
            )
            if (weather.tabs.isNotEmpty()) {
                PrimaryTabRow(selectedTabIndex = weather.tabs.indexOfFirst { it.first == type }.coerceAtLeast(0)) {
                    weather.tabs.forEach { (key, label) ->
                        Tab(selected = key == type, onClick = { type = key }, text = { Text(label) })
                    }
                }
            }
            WeatherForecast(view, Modifier.align(Alignment.CenterHorizontally))
        }
        weather.attribution?.let {
            Text(
                it,
                style = Css.Text.Body.copy(textAlign = TextAlign.Center),
                color = colors.colorTextSecondary,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

/**
 * The chosen type's forecast: its days, else why it failed, else a spinner until it comes.
 *
 * Upstream: part of `more-info-weather` (src/dialogs/more-info/controls/more-info-weather.ts)
 */
@Composable
private fun WeatherForecast(view: WeatherForecastView, centered: Modifier) {
    val columns = view.columns
    val failedToLoad = view.failedToLoad
    when {
        columns != null -> BoxWithConstraints(Modifier.fillMaxWidth()) { ForecastRow(columns, maxWidth, Modifier) }
        failedToLoad != null -> Text(
            failedToLoad,
            style = Css.Text.Body.copy(textAlign = TextAlign.Center),
            color = LocalHAColorScheme.current.colorTextSecondary,
            modifier = centered,
        )
        else -> CircularProgressIndicator(centered)
    }
}

/**
 * The condition's picture (else the icon), the state and when it changed, then the temperature over [secondary].
 *
 * Upstream: part of `more-info-weather` (src/dialogs/more-info/controls/more-info-weather.ts)
 */
@Composable
private fun WeatherConditions(weather: WeatherMoreInfo, secondary: SecondaryWeather?) {
    val colors = LocalHAColorScheme.current
    // `.state, .temp-attribute .temp`
    val stateStyle = Css.Text.Body.copy(fontSize = Css.FontSize.X3l, lineHeight = Css.LineHeight.Condensed)
    // `.attribute`
    val attributeStyle = Css.Text.Body.copy(lineHeight = ATTRIBUTE_LINE_HEIGHT.em)
    Row(
        horizontalArrangement = Arrangement.spacedBy(Css.Space.S4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        weather.condition?.let { WeatherIconView(it, colors.colorTextSecondary, Modifier.size(CONDITION_SIZE)) }
            ?: DashboardIcon(weather.icon, colors.colorTextSecondary, Modifier.size(CONDITION_SIZE))
        Column(Modifier.weight(1f)) {
            Text(
                weather.state,
                style = stateStyle,
                color = colors.colorTextPrimary,
            )
            Text(
                weather.changed,
                style = Css.Text.Body.copy(textAlign = TextAlign.Start),
                color = colors.colorTextSecondary,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            weather.temperature?.let {
                Text(it, style = stateStyle, color = colors.colorTextPrimary)
            }
            secondary?.let { shown ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Css.Space.S1),
                ) {
                    shown.icon?.let { DashboardIcon(it, colors.colorTextSecondary, Modifier.size(HASize.XL)) }
                    shown.label?.let {
                        Text(it, style = attributeStyle, color = colors.colorTextSecondary)
                    }
                    Text(shown.value, style = attributeStyle, color = colors.colorTextSecondary)
                }
            }
        }
    }
}

/** A weather's forecast of one type while its details show (`weather/subscribe_forecast`). */
@HiltViewModel
internal class MoreInfoForecastViewModel @Inject constructor(repository: ForecastRepository) : ViewModel() {
    private val key = MutableStateFlow<ForecastKey?>(null)

    /** The latest forecast, or why it can't be subscribed to (logged); `null` until the server answers. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val result: StateFlow<ForecastResult?> = key.filterNotNull().flatMapLatest { forecast ->
        repository.forecast(forecast).map { it.toForecastResult(forecast) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), null)

    /** Subscribe to [forecast]. */
    fun show(forecast: ForecastKey) {
        key.value = forecast
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}

private val CONDITION_SIZE = 64.dp

/** `.attribute`'s `line-height: 1`. */
private const val ATTRIBUTE_LINE_HEIGHT = 1f
