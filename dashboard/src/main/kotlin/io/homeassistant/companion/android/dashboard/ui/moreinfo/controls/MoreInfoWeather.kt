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
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.data.ForecastRepository
import io.homeassistant.companion.android.dashboard.data.Loadable
import io.homeassistant.companion.android.dashboard.data.valueOrNull
import io.homeassistant.companion.android.dashboard.derive.SecondaryWeather
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.moreinfo.WeatherMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.weatherForecastView
import io.homeassistant.companion.android.dashboard.ui.components.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.lovelace.cards.ForecastRow
import io.homeassistant.companion.android.dashboard.ui.lovelace.cards.WeatherIconView
import io.homeassistant.companion.android.dashboard.ui.theme.FrontendTextStyle
import io.homeassistant.companion.android.dashboard.weather.ForecastEvent
import io.homeassistant.companion.android.dashboard.weather.ForecastKey
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
import timber.log.Timber

/**
 * Port of `more-info-weather`: the condition's picture (else the icon), the state and when it changed, the
 * temperature over today's high and low, the readings, then "Forecast:" with a tab per type the entity offers and
 * the chosen type's forecast (a spinner until it comes).
 *
 * Upstream: `more-info-weather` (src/dialogs/more-info/controls/more-info-weather.ts)
 */
@Composable
internal fun MoreInfoWeather(weather: WeatherMoreInfo, state: EntityState, hass: HassSnapshot, now: Instant) {
    val colors = LocalHAColorScheme.current
    var type by remember(state.entityId) { mutableStateOf(weather.defaultType) }
    val forecasts = hiltViewModel<MoreInfoForecastViewModel>(key = "forecast-${state.entityId}")
    LaunchedEffect(state.entityId, type) { type?.let { forecasts.show(ForecastKey(state.entityId, it)) } }
    val event by forecasts.event.collectAsStateWithLifecycle()
    val view = remember(state, event, now) { hass.weatherForecastView(state, event, now) }
    Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4)) {
        WeatherConditions(weather, view.secondary)
        weather.rows.forEach { row ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
            ) {
                DashboardIcon(row.icon, colors.colorTextSecondary, Modifier.size(HASize.X2L))
                Text(
                    row.name,
                    style = FrontendTextStyle.Body.copy(textAlign = TextAlign.Start),
                    color = colors.colorTextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(row.value, style = FrontendTextStyle.Body, color = colors.colorTextPrimary)
            }
        }
        weather.forecastTitle?.let { title ->
            Text(
                title,
                style = FrontendTextStyle.Body.copy(fontWeight = FontWeight.Medium, textAlign = TextAlign.Start),
                color = colors.colorTextPrimary,
            )
            if (weather.tabs.isNotEmpty()) {
                PrimaryTabRow(selectedTabIndex = weather.tabs.indexOfFirst { it.first == type }.coerceAtLeast(0)) {
                    weather.tabs.forEach { (key, label) ->
                        Tab(selected = key == type, onClick = { type = key }, text = { Text(label) })
                    }
                }
            }
            val columns = view.columns
            if (columns == null) {
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            } else {
                BoxWithConstraints(Modifier.fillMaxWidth()) { ForecastRow(columns, maxWidth, Modifier) }
            }
        }
        weather.attribution?.let {
            Text(
                it,
                style = HATextStyle.BodyMedium,
                color = colors.colorTextSecondary,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
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
    Row(
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        weather.condition?.let { WeatherIconView(it, colors.colorTextSecondary, Modifier.size(CONDITION_SIZE)) }
            ?: DashboardIcon(weather.icon, colors.colorTextSecondary, Modifier.size(CONDITION_SIZE))
        Column(Modifier.weight(1f)) {
            Text(
                weather.state,
                style = HATextStyle.Headline.copy(textAlign = TextAlign.Start, fontSize = STATE_SIZE),
                color = colors.colorTextPrimary,
            )
            Text(
                weather.changed,
                style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.Start),
                color = colors.colorTextSecondary,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            weather.temperature?.let {
                Text(it, style = HATextStyle.Headline.copy(fontSize = STATE_SIZE), color = colors.colorTextPrimary)
            }
            secondary?.let { shown ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE1),
                ) {
                    shown.icon?.let { DashboardIcon(it, colors.colorTextSecondary, Modifier.size(HASize.XL)) }
                    shown.label?.let {
                        Text(it, style = HATextStyle.BodyMedium, color = colors.colorTextSecondary)
                    }
                    Text(shown.value, style = HATextStyle.BodyMedium, color = colors.colorTextSecondary)
                }
            }
        }
    }
}

/** A weather's forecast of one type while its details show (`weather/subscribe_forecast`). */
@HiltViewModel
internal class MoreInfoForecastViewModel @Inject constructor(repository: ForecastRepository) : ViewModel() {
    private val key = MutableStateFlow<ForecastKey?>(null)

    /** The latest forecast, `null` until it comes (or while it can't be subscribed to, which is logged). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val event: StateFlow<ForecastEvent?> = key.filterNotNull().flatMapLatest(repository::forecast).map { loadable ->
        if (loadable is Loadable.Failed) Timber.w("Couldn't subscribe to the forecast: ${loadable.error}")
        loadable.valueOrNull
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
private val STATE_SIZE = 24.sp
