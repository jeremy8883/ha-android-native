package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.display.formatEntityAttributeValue
import io.homeassistant.companion.android.dashboard.display.formatEntityState
import io.homeassistant.companion.android.dashboard.display.formatNumber
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.feature.number
import io.homeassistant.companion.android.dashboard.model.jsTruthy
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonPrimitive

// The texts of the state cards that show the entity's state their own way: ha-climate-state, ha-humidifier-state,
// ha-water_heater-state and hass-media-player-model's titles (frontend@20260624.6 src/components/,
// src/util/hass-media-player-model.ts).

/** Port of `ha-climate-state`: the action and state, preset and target, then the current reading. */
internal fun HassSnapshot.climateRight(state: EntityState): StateCardRight {
    val preset = state.attributes.string("preset_mode")?.takeIf { it.isNotEmpty() && it != "none" }
        ?.let { " - ${formatEntityAttributeValue(state, "preset_mode")}" }.orEmpty()
    return withCurrent(
        state,
        "ui.card.climate.currently",
        label(state, "hvac_action") + preset,
        climateTarget(state),
        climateCurrent(state),
    )
}

/** `_computeTarget`: the temperature range or target, else the humidity range or target. */
private fun HassSnapshot.climateTarget(state: EntityState): String {
    val range = { low: String, high: String ->
        "${formatEntityAttributeValue(state, low)}-${formatEntityAttributeValue(state, high)}"
    }
    return when {
        present(
            state,
            "target_temp_low",
        ) &&
            present(state, "target_temp_high") -> range("target_temp_low", "target_temp_high")
        present(state, "temperature") -> formatEntityAttributeValue(state, "temperature")
        present(state, "target_humidity_low") && present(state, "target_humidity_high") ->
            range("target_humidity_low", "target_humidity_high")
        present(state, "humidity") -> formatEntityAttributeValue(state, "humidity")
        else -> ""
    }
}

/** `_computeCurrentStatus`: the current temperature and/or humidity. */
private fun HassSnapshot.climateCurrent(state: EntityState): String? {
    val readings = listOf("current_temperature", "current_humidity").filter { present(state, it) }
        .map { formatEntityAttributeValue(state, it) }
    return readings.takeIf { it.isNotEmpty() }?.joinToString("/ ")
}

/** Port of `ha-humidifier-state`: the action and state, mode and target humidity, then the current one. */
internal fun HassSnapshot.humidifierRight(state: EntityState): StateCardRight {
    val target = if (present(state, "humidity")) formatEntityAttributeValue(state, "humidity") else ""
    val current = if (present(
            state,
            "current_humidity",
        )
    ) {
        formatEntityAttributeValue(state, "current_humidity")
    } else {
        null
    }
    val mode = state.attributes.string("mode")?.takeIf { it.isNotEmpty() }
        ?.let { " - ${formatEntityAttributeValue(state, "mode")}" }.orEmpty()
    return withCurrent(state, "ui.card.humidifier.currently", label(state, "action") + mode, target, current)
}

internal fun HassSnapshot.withCurrent(
    state: EntityState,
    currentlyKey: String,
    label: String,
    target: String,
    current: String?,
): StateCardRight {
    if (state.state == STATE_UNAVAILABLE || state.state == STATE_UNKNOWN) {
        return textRight(localize("state.default.${state.state}"))
    }
    return StateCardRight(
        listOf(label, target).filter { it.isNotEmpty() }.joinToString(" "),
        current?.let { "${localize(currentlyKey)}: $it" },
    )
}

/** The state, after its action ("Cooling (Cool)") while not off. */
internal fun HassSnapshot.label(state: EntityState, actionAttribute: String): String {
    val stateString = formatEntityState(state)
    val action = state.attributes.string(actionAttribute)?.takeIf { it.isNotEmpty() }
    return if (action != null && state.state != "off") {
        "${formatEntityAttributeValue(state, actionAttribute)} ($stateString)"
    } else {
        stateString
    }
}

/** Port of `ha-water_heater-state`: the state and the target temperature (or range) in the system's unit. */
internal fun HassSnapshot.waterHeaterRight(state: EntityState): StateCardRight {
    val unit = config.temperatureUnit.orEmpty()
    val low = number(state.attributes["target_temp_low"])
    val high = number(state.attributes["target_temp_high"])
    val temperature = number(state.attributes["temperature"])
    val target = when {
        low != null && high != null -> "${formatNumber(low)} – ${formatNumber(high)} $unit"
        temperature != null -> "${formatNumber(temperature)} $unit"
        else -> ""
    }
    return textRight(listOf(formatEntityState(state), target).filter { it.isNotEmpty() }.joinToString(" "))
}

/** Port of `hass-media-player-model`'s titles: the media's title (else the state), and its artist, show or app. */
internal fun HassSnapshot.mediaRight(state: EntityState): StateCardRight {
    val attributes = state.attributes
    val secondary = when (attributes.string("media_content_type")) {
        "music" -> attributes.string("media_artist")
        "tvshow" -> {
            val season = attributes["media_season"]?.takeIf { jsTruthy(it) }?.let { (it as? JsonPrimitive)?.content }
            val episode = attributes["media_episode"]?.takeIf { jsTruthy(it) }?.let { (it as? JsonPrimitive)?.content }
            attributes.string("media_series_title").orEmpty() +
                season?.let { " S$it" + episode?.let { e -> "E$e" }.orEmpty() }.orEmpty()
        }
        else -> attributes.string("app_name")
    }
    return StateCardRight(
        cleanupMediaTitle(attributes.string("media_title")) ?: formatEntityState(state),
        secondary?.ifEmpty {
            null
        },
    )
}

private fun present(state: EntityState, attribute: String): Boolean =
    state.attributes[attribute].let { it != null && it !is kotlinx.serialization.json.JsonNull }
