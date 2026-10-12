package io.homeassistant.companion.android.dashboard.feature

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.color.DEFAULT_MAX_KELVIN
import io.homeassistant.companion.android.dashboard.color.DEFAULT_MIN_KELVIN
import io.homeassistant.companion.android.dashboard.derive.DisplayColor
import io.homeassistant.companion.android.dashboard.derive.cssColor
import io.homeassistant.companion.android.dashboard.derive.isActive
import io.homeassistant.companion.android.dashboard.derive.stateColor
import io.homeassistant.companion.android.dashboard.derive.supportsFeature
import io.homeassistant.companion.android.dashboard.display.jsNumberString
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.history.isNumericFromAttributes
import io.homeassistant.companion.android.dashboard.model.array
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import io.homeassistant.companion.android.dashboard.moreinfo.ControlSlider
import io.homeassistant.companion.android.dashboard.moreinfo.SliderBackground
import io.homeassistant.companion.android.dashboard.moreinfo.SliderMode
import io.homeassistant.companion.android.dashboard.moreinfo.colorTemperatureGradient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Ports of the slider and number card features (frontend@20260624.6 src/panels/lovelace/card-features/): cover
// and valve position, cover tilt position, light colour temperature, target humidity, numeric input and the media
// player's volume. In `cardFeatureStyles` a slider's bar is the feature colour, over a track of it at a fifth.

/** The feature colour's track, at `--control-slider-background-opacity: 0.2`. */
private val FEATURE_TRACK = SliderBackground.Tint(null, TRACK_OPACITY)

/**
 * The colour of a position slider: the tile's configured colour, else the state's with the inactive colour set
 * to the open one, so a closed cover's slider doesn't look disabled.
 */
private fun positionColor(state: EntityState, configColor: String?): DisplayColor? {
    if (configColor != null) return cssColor(configColor)
    val color = stateColor(state)
    val open = stateColor(state, "open")
    return if (color is DisplayColor.State && open != null) {
        color.copy(overrides = mapOf("state-${state.domain}-inactive-color" to open))
    } else {
        color
    }
}

/** Ports of `hui-cover-position-card-feature` and `hui-valve-position-card-feature`. */
internal fun HassSnapshot.positionSlider(state: EntityState?, configColor: String?): TileFeature? {
    if (state == null || !state.supportsFeature(SET_POSITION)) return null
    val cover = state.domain == "cover"
    val percentage = if (state.isActive()) number(state.attributes[CURRENT_POSITION]) ?: 0.0 else 0.0
    val color = positionColor(state, configColor)
    return TileFeature.ValueSlider(
        ControlSlider(
            label = attributeName(state, CURRENT_POSITION),
            value = maxOf(Math.round(percentage).toDouble(), 0.0),
            min = 0.0,
            max = PERCENT,
            step = 1.0,
            unit = "%",
            mode = SliderMode.Start,
            inverted = true,
            showHandle = true,
            enabled = state.available(),
            color = color,
            background = SliderBackground.Tint(color, TRACK_OPACITY),
            service = ValueService(
                state.domain,
                if (cover) "set_cover_position" else "set_valve_position",
                entityData(state),
                "position",
            ),
        ),
    )
}

/** Port of `hui-cover-tilt-position-card-feature`: a cursor over stripes widening towards the end. */
internal fun HassSnapshot.tiltPositionSlider(state: EntityState, configColor: String?): TileFeature? {
    if (state.domain != "cover" || !state.supportsFeature(SET_TILT_POSITION)) return null
    val tilt = number(state.attributes[CURRENT_TILT_POSITION]) ?: 0.0
    val color = positionColor(state, configColor)
    return TileFeature.ValueSlider(
        ControlSlider(
            label = attributeName(state, CURRENT_TILT_POSITION),
            value = maxOf(Math.round(tilt).toDouble(), 0.0),
            min = 0.0,
            max = PERCENT,
            step = 1.0,
            unit = "%",
            mode = SliderMode.Cursor,
            inverted = true,
            showHandle = false,
            enabled = state.available(),
            color = color,
            background = SliderBackground.Stripes(color, TRACK_OPACITY),
            service = ValueService("cover", "set_cover_tilt_position", entityData(state), "tilt_position"),
        ),
    )
}

/** Port of `hui-light-color-temp-card-feature`: a cursor over the light's range of whites. */
internal fun HassSnapshot.lightColorTemp(state: EntityState): TileFeature? {
    val modes = state.attributes.array("supported_color_modes")?.mapNotNull { it.stringOrNull }.orEmpty()
    if (state.domain != "light" || COLOR_TEMP !in modes) return null
    val min = number(state.attributes["min_color_temp_kelvin"]) ?: DEFAULT_MIN_KELVIN
    val max = number(state.attributes["max_color_temp_kelvin"]) ?: DEFAULT_MAX_KELVIN
    return TileFeature.ValueSlider(
        ControlSlider(
            label = localize("ui.card.light.color_temperature"),
            value = number(state.attributes["color_temp_kelvin"]),
            min = min,
            max = max,
            step = 1.0,
            unit = "K",
            mode = SliderMode.Cursor,
            inverted = false,
            showHandle = state.isActive(),
            enabled = state.available(),
            color = null,
            background = SliderBackground.Gradient(colorTemperatureGradient(min, max)),
            service = ValueService("light", "turn_on", entityData(state), "color_temp_kelvin"),
        ),
    )
}

/** Port of `hui-target-humidity-card-feature`: the humidifier's target, from its minimum to its maximum. */
internal fun HassSnapshot.targetHumidity(state: EntityState): TileFeature? {
    if (state.domain != "humidifier") return null
    return TileFeature.ValueSlider(
        ControlSlider(
            label = attributeName(state, "humidity"),
            value = number(state.attributes["humidity"]),
            min = number(state.attributes["min_humidity"]) ?: 0.0,
            max = number(state.attributes["max_humidity"]) ?: PERCENT,
            step = 1.0,
            unit = "%",
            mode = SliderMode.Start,
            inverted = false,
            showHandle = false,
            enabled = state.available(),
            color = null,
            background = FEATURE_TRACK,
            service = ValueService("humidifier", "set_humidity", entityData(state), "humidity"),
        ),
    )
}

/** Port of `hui-numeric-input-card-feature`: a slider, or minus and plus buttons with the `buttons` style. */
internal fun numericInput(state: EntityState, feature: JsonObject): TileFeature? {
    if (state.domain != "input_number" && state.domain != "number") return null
    val value = state.state.toDoubleOrNull()?.takeIf { it.isFinite() }
    val min = number(state.attributes["min"])
    val max = number(state.attributes["max"])
    val step = number(state.attributes["step"])
    val unit = state.attributes.string("unit_of_measurement")
    val service = ValueService(state.domain, "set_value", entityData(state), "value")
    return if (feature.string("style") == "buttons") {
        val buttonStep = step ?: 1.0
        TileFeature.NumberButtons(
            listOf(
                TileFeature.NumberItem(
                    label = "",
                    value = value,
                    min = min,
                    max = max,
                    step = buttonStep,
                    fractionDigits = fractionDigitsOf(buttonStep),
                    unit = unit,
                    service = service.takeIf { state.available() },
                    color = null,
                ),
            ),
        )
    } else {
        TileFeature.ValueSlider(
            ControlSlider(
                label = "",
                value = value,
                min = min ?: 0.0,
                max = max ?: PERCENT,
                step = step ?: 1.0,
                unit = unit,
                mode = SliderMode.Start,
                inverted = false,
                showHandle = false,
                enabled = state.available(),
                color = null,
                background = FEATURE_TRACK,
                service = service,
            ),
        )
    }
}

/**
 * Ports of `hui-media-player-volume-slider-card-feature` and `-volume-buttons-`: the volume in percent, as a slider
 * or as minus and plus buttons stepping by the `step` option, then a mute button unless `show_mute_button` is off.
 */
internal fun HassSnapshot.mediaPlayerVolume(state: EntityState, feature: JsonObject, buttons: Boolean): TileFeature? {
    if (state.domain != MEDIA_PLAYER || !state.supportsFeature(MEDIA_VOLUME_SET)) return null
    val volume = number(state.attributes["volume_level"])?.let { Math.round(it * PERCENT).toDouble() }
    val service = ValueService(MEDIA_PLAYER, "volume_set", entityData(state), "volume_level", divisor = PERCENT)
    val mute = muteButton(state, feature)
    return if (buttons) {
        val step = number(feature["step"]) ?: DEFAULT_VOLUME_STEP
        TileFeature.NumberButtons(
            listOf(
                TileFeature.NumberItem(
                    label = "",
                    value = volume,
                    min = 0.0,
                    max = PERCENT,
                    step = step,
                    fractionDigits = fractionDigitsOf(step),
                    unit = "%",
                    service = service.takeIf { state.available() },
                    color = null,
                ),
            ),
            trailing = mute,
        )
    } else {
        TileFeature.ValueSlider(
            ControlSlider(
                label = "",
                value = volume,
                min = 0.0,
                max = PERCENT,
                step = 1.0,
                unit = "%",
                mode = SliderMode.Start,
                inverted = false,
                showHandle = state.isActive(),
                enabled = state.available(),
                color = null,
                background = FEATURE_TRACK,
                service = service,
            ),
            trailing = mute,
        )
    }
}

/** Port of `renderMuteButton`: for players that can mute, unless the feature hides it. */
private fun HassSnapshot.muteButton(state: EntityState, feature: JsonObject): TileFeature.Button? {
    if (feature.boolean("show_mute_button") == false || !state.supportsFeature(MEDIA_VOLUME_MUTE)) return null
    val muted = state.attributes.boolean("is_volume_muted") == true
    return TileFeature.Button(
        label = localize("ui.card.media_player.${if (muted) "media_volume_unmute" else "media_volume_mute"}"),
        icon = if (muted) "mdi:volume-off" else "mdi:volume-high",
        enabled = state.available(),
        action = CardAction.CallService(
            MEDIA_PLAYER,
            "volume_mute",
            JsonObject(entityData(state) + ("is_volume_muted" to JsonPrimitive(!muted))),
            target = null,
        ),
    )
}

/**
 * Port of `hui-bar-gauge-card-feature`: a numeric sensor's state between the `min` and `max` options (0 and 100 by
 * default); nothing when the state isn't a number or the range is empty.
 */
internal fun barGauge(state: EntityState, feature: JsonObject): TileFeature? {
    val min = number(feature["min"]) ?: 0.0
    val max = number(feature["max"]) ?: PERCENT
    val value = state.state.trim().toDoubleOrNull()
    val supported = state.domain == "sensor" && isNumericFromAttributes(state.attributes)
    return if (supported && value != null && min < max) {
        TileFeature.BarGauge(((value - min) / (max - min)).coerceIn(0.0, 1.0))
    } else {
        null
    }
}

/** The decimals of [step], as upstream counts them from its text. */
private fun fractionDigitsOf(step: Double): Int = jsNumberString(step).substringAfter('.', "").length

private const val TRACK_OPACITY = 0.2f
private const val CURRENT_POSITION = "current_position"
private const val CURRENT_TILT_POSITION = "current_tilt_position"
private const val COLOR_TEMP = "color_temp"
private const val SET_POSITION = 4
private const val SET_TILT_POSITION = 128
private const val MEDIA_VOLUME_SET = 4
private const val MEDIA_VOLUME_MUTE = 8
private const val DEFAULT_VOLUME_STEP = 5.0
