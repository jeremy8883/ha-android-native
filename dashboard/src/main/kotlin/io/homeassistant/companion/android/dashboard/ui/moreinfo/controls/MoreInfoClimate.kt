package io.homeassistant.companion.android.dashboard.ui.moreinfo.controls

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
import io.homeassistant.companion.android.dashboard.ui.components.ControlSelectMenus
import io.homeassistant.companion.android.dashboard.ui.components.IconToggle
import io.homeassistant.companion.android.dashboard.ui.components.IconToggleGroup
import io.homeassistant.companion.android.dashboard.ui.statecontrol.CircularStateControl
import io.homeassistant.companion.android.dashboard.ui.theme.Css
import io.homeassistant.companion.android.dashboard.ui.theme.cssFontPx

/**
 * The controls of a thermostat's details, port of `more-info-climate` (frontend@20260624.6
 * src/dialogs/more-info/controls/more-info-climate.ts): the current readings, the temperature dial (or the
 * humidity dial, when chosen) and the menus.
 *
 * Upstream: `more-info-climate` (src/dialogs/more-info/controls/more-info-climate.ts)
 */
@Composable
internal fun MoreInfoClimate(climate: ClimateMoreInfo, state: EntityState, onAction: (CardAction) -> Unit) {
    var showHumidity by rememberSaveable(state.entityId) { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Css.Space.S6),
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

/**
 * The current temperature and humidity, side by side: a small label over each value.
 *
 * Upstream: the `.current` of `more-info-climate`, `more-info-humidifier`, `more-info-water_heater`
 * (src/dialogs/more-info/controls/more-info-climate.ts; src/dialogs/more-info/controls/more-info-humidifier.ts;
 * src/dialogs/more-info/controls/more-info-water_heater.ts)
 */
@Composable
internal fun CurrentReadings(readings: List<Pair<String, String>>) {
    if (readings.isEmpty()) return
    val colors = LocalHAColorScheme.current
    Row(horizontalArrangement = Arrangement.spacedBy(Css.Space.S6)) {
        readings.forEach { (label, value) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // `.current .label`, `.current .value`
                Text(
                    label,
                    style = Css.Text.Body.copy(
                        lineHeight = Css.LineHeight.Condensed,
                        letterSpacing = LABEL_LETTER_SPACING.cssFontPx,
                    ),
                    color = colors.colorTextSecondary,
                )
                Text(
                    value,
                    style = Css.Text.Body.copy(
                        fontSize = Css.FontSize.Xl,
                        fontWeight = Css.FontWeight.Medium,
                        lineHeight = Css.LineHeight.Condensed,
                    ),
                    color = colors.colorTextPrimary,
                )
            }
        }
    }
}

private const val UNAVAILABLE = "unavailable"

/** `.current .label`'s `letter-spacing: 0.4px`. */
private const val LABEL_LETTER_SPACING = 0.4
