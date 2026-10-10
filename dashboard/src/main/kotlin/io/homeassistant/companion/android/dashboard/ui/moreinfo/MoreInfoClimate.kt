package io.homeassistant.companion.android.dashboard.ui.moreinfo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.moreinfo.CircularTarget
import io.homeassistant.companion.android.dashboard.moreinfo.CircularTargets
import io.homeassistant.companion.android.dashboard.moreinfo.ClimateMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.climateHumidityCall
import io.homeassistant.companion.android.dashboard.moreinfo.climateHumidityTarget
import io.homeassistant.companion.android.dashboard.moreinfo.climateTargets
import io.homeassistant.companion.android.dashboard.moreinfo.climateTemperatureCall
import io.homeassistant.companion.android.dashboard.ui.controls.CircularStateControl
import io.homeassistant.companion.android.dashboard.ui.controls.ControlSelectMenus
import io.homeassistant.companion.android.dashboard.ui.controls.IconToggle
import io.homeassistant.companion.android.dashboard.ui.controls.IconToggleGroup

/**
 * The controls of a thermostat's details, port of `more-info-climate` (frontend@20260624.6
 * src/dialogs/more-info/controls/more-info-climate.ts): the current readings, the temperature dial (or the
 * humidity dial, when chosen) and the menus.
 */
@Composable
internal fun MoreInfoClimate(climate: ClimateMoreInfo, state: EntityState, onAction: (CardAction) -> Unit) {
    var showHumidity by rememberSaveable(state.entityId) { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE6),
    ) {
        CurrentReadings(climate.current)
        val humidity = climate.humidity
        if (showHumidity && humidity != null) {
            CircularStateControl(
                control = humidity,
                targets = CircularTargets(climateHumidityTarget(state), null, null),
                onSet = { targets, _ -> targets.value?.let { onAction(climateHumidityCall(state, it)) } },
                bottomUnit = true,
            )
        } else {
            CircularStateControl(
                control = climate.temperature,
                targets = climateTargets(state),
                onSet = { targets, target ->
                    onAction(climateTemperatureCall(state, targets, range = target != CircularTarget.Value))
                },
            )
        }
        if (humidity != null) {
            val enabled = state.state != UNAVAILABLE
            IconToggleGroup(
                toggles = listOf(
                    IconToggle("mdi:thermometer", climate.temperatureLabel, !showHumidity, enabled),
                    IconToggle("mdi:water-percent", climate.humidityLabel, showHumidity, enabled),
                ),
                onSelect = { showHumidity = it == 1 },
            )
        }
        ControlSelectMenus(climate.menus, onAction)
    }
}

/** The current temperature and humidity, side by side: a small label over each value. */
@Composable
internal fun CurrentReadings(readings: List<Pair<String, String>>) {
    if (readings.isEmpty()) return
    val colors = LocalHAColorScheme.current
    Row(horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE6)) {
        readings.forEach { (label, value) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, style = HATextStyle.BodyMedium, color = colors.colorTextSecondary)
                Text(
                    value,
                    style = HATextStyle.Body.copy(fontWeight = FontWeight.Medium),
                    color = colors.colorTextPrimary,
                )
            }
        }
    }
}

private const val UNAVAILABLE = "unavailable"
