package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.model.array
import io.homeassistant.companion.android.dashboard.model.number
import io.homeassistant.companion.android.dashboard.model.stringOrNull

/**
 * Whether the entity's `supported_features` bitmask includes [feature].
 * Port of `supportsFeature` (frontend@20260624.6 src/common/entity/supports-feature.ts).
 */
fun EntityState.supportsFeature(feature: Int): Boolean =
    ((attributes.number("supported_features")?.toInt() ?: 0) and feature) != 0

/** Feature bits, from src/data/{cover,climate,fan,water_heater}.ts. */
object EntityFeature {
    const val COVER_OPEN = 1
    const val COVER_CLOSE = 2
    const val CLIMATE_TARGET_TEMPERATURE = 1
    const val CLIMATE_TARGET_TEMPERATURE_RANGE = 2
    const val FAN_SET_SPEED = 1
    const val WATER_HEATER_TARGET_TEMPERATURE = 1
}

private val MODES_SUPPORTING_BRIGHTNESS = setOf("hs", "xy", "rgb", "rgbw", "rgbww", "color_temp", "brightness", "white")

/** Port of `lightSupportsBrightness` (src/data/light.ts). */
fun EntityState.lightSupportsBrightness(): Boolean =
    attributes.array("supported_color_modes")?.any { it.stringOrNull in MODES_SUPPORTING_BRIGHTNESS } == true
