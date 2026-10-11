package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.display.formatEntityAttributeValue
import io.homeassistant.companion.android.dashboard.display.formatEntityState
import io.homeassistant.companion.android.dashboard.display.timestampDisplay
import io.homeassistant.companion.android.dashboard.display.updateStateDisplay
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.feature.entityData
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.weather.ForecastKey
import io.homeassistant.companion.android.dashboard.weather.WeatherIcon
import io.homeassistant.companion.android.dashboard.weather.defaultForecastType
import io.homeassistant.companion.android.dashboard.weather.forecastToShow
import io.homeassistant.companion.android.dashboard.weather.weatherStateIcon
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Ports of the entities card's domain rows that aren't a plain switch or input (frontend@20260624.6
// src/panels/lovelace/entity-rows/): group, valve, cover, climate (and water heater), humidifier, event, update
// and weather. The media player's is in EntityRowMedia.kt.

/**
 * What a domain's row shows on its right: a [control], else its [state] with a second line under it
 * ([stateSecondary]); a weather row draws its [condition] instead of the badge, and today's high and low (or
 * precipitation) under its temperature ([weatherDetail]); a media player's row its media under the name
 * ([nameSecondary]).
 */
data class DomainRow(
    val control: RowControl? = null,
    val state: String? = null,
    val stateSecondary: String? = null,
    val nameSecondary: String? = null,
    val condition: WeatherIcon? = null,
    val weatherDetail: SecondaryWeather? = null,
)

/** The row of [state]'s domain when it has its own, `null` for the others. */
internal fun HassSnapshot.domainRow(state: EntityState, config: JsonObject, now: Instant): DomainRow? =
    when (state.domain) {
        // A group without members that toggle, and a valve opening or closing, show their state instead
        "group" -> toggleOrState(state, groupCanToggle(state))
        "valve" -> toggleOrState(state, state.state in VALVE_TOGGLE_STATES)
        "cover" -> DomainRow(control = coverControls(state))
        "climate", "water_heater" -> climateRight(state).let {
            DomainRow(state = it.state, stateSecondary = it.secondary)
        }
        "humidifier" -> humidifierRight(state).let { DomainRow(state = it.state, stateSecondary = it.secondary) }
        "event" -> eventRow(state, config, now)
        "update" -> DomainRow(state = updateStateDisplay(state))
        "media_player" -> mediaRow(state)
        "weather" -> weatherRow(state, now)
        else -> null
    }

/** The entity's switch when [toggles], else its state. */
private fun HassSnapshot.toggleOrState(state: EntityState, toggles: Boolean): DomainRow =
    if (toggles) DomainRow(control = entityToggle(state)) else DomainRow(state = formatEntityState(state))

/** `_computeCanToggle`: a member (or a nested group's) whose domain toggles (`DOMAINS_TOGGLE`). */
private fun HassSnapshot.groupCanToggle(group: EntityState, depth: Int = 0): Boolean = depth < MAX_GROUP_DEPTH &&
    (group.attributes["entity_id"] as? JsonArray).orEmpty().any { member ->
        val id = (member as? JsonPrimitive)?.content ?: return@any false
        val domain = id.substringBefore('.')
        if (domain == "group") states[id]?.let { groupCanToggle(it, depth + 1) } == true else domain in DOMAINS_TOGGLE
    }

/** `hui-event-entity-row`: when it last fired (a timestamp in the row's format) over the event's type. */
private fun HassSnapshot.eventRow(state: EntityState, config: JsonObject, now: Instant): DomainRow {
    if (state.state == STATE_UNAVAILABLE ||
        state.state == STATE_UNKNOWN
    ) {
        return DomainRow(state = formatEntityState(state))
    }
    return DomainRow(
        state = timestampDisplay(state.state, now, config.string("time_format") ?: "relative"),
        stateSecondary = formatEntityAttributeValue(state, "event_type"),
    )
}

/**
 * `hui-weather-entity-row`: the condition's picture, the temperature (else the state), and under it today's high
 * and low from the default forecast type's subscription, else the precipitation or humidity.
 */
private fun HassSnapshot.weatherRow(state: EntityState, now: Instant): DomainRow {
    val noValue = state.state == STATE_UNAVAILABLE || state.state == STATE_UNKNOWN
    val temperature = state.attributes["temperature"].takeUnless { it == null || it is JsonNull }
    val event = defaultForecastType(state)?.let { forecasts[ForecastKey(state.entityId, it)] }
    val forecast = forecastToShow(state.attributes, event, type = null)
    return DomainRow(
        state = if (noValue ||
            temperature == null
        ) {
            formatEntityState(state)
        } else {
            formatEntityAttributeValue(state, "temperature")
        },
        condition = weatherStateIcon(state.state),
        weatherDetail = defaultSecondary(state, forecast?.entries.orEmpty(), digits = null, now = now),
    )
}

internal fun mediaCall(state: EntityState, service: String, extra: Map<String, JsonPrimitive> = emptyMap()) =
    CardAction.CallService("media_player", service, JsonObject(entityData(state) + extra), target = null)

private const val MAX_GROUP_DEPTH = 5

/** Port of `DOMAINS_TOGGLE`. */
internal val DOMAINS_TOGGLE =
    setOf("fan", "input_boolean", "light", "switch", "group", "automation", "humidifier", "valve")

private val VALVE_TOGGLE_STATES = setOf("open", "closed", STATE_UNAVAILABLE, STATE_UNKNOWN)
