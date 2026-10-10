package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.attributeIcon
import io.homeassistant.companion.android.dashboard.derive.supportsFeature
import io.homeassistant.companion.android.dashboard.display.formatEntityAttributeValue
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.feature.attributeName
import io.homeassistant.companion.android.dashboard.feature.available
import io.homeassistant.companion.android.dashboard.feature.entityData
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// The menus of `more-info-fan` (frontend@20260624.6 src/dialogs/more-info/controls/more-info-fan.ts): the preset,
// the direction (with its attribute icons) and oscillation.

/** The preset menu, for a fan that supports presets and lists them. */
internal fun HassSnapshot.presetMenu(state: EntityState): SelectMenu? =
    if (state.supportsFeature(FEATURE_PRESET_MODE)) {
        attributeMenu(state, "preset_mode", "preset_modes", "set_preset_mode", "mdi:tune-variant")
    } else {
        null
    }

/** The direction menu, forward or reverse. */
internal fun HassSnapshot.directionMenu(state: EntityState): SelectMenu? {
    if (!state.supportsFeature(FEATURE_DIRECTION)) return null
    val current = state.attributes.string(DIRECTION)
    return SelectMenu(
        label = attributeName(state, DIRECTION),
        icon = current?.let { attributeIcon(state, DIRECTION, it) } ?: DIRECTION_ICON,
        value = current,
        enabled = state.available(),
        options = listOf("forward", "reverse").map { direction ->
            MenuOption(
                value = direction,
                label = formatEntityAttributeValue(state, DIRECTION, JsonPrimitive(direction)),
                icon = attributeIcon(state, DIRECTION, direction),
                action = call(state, "set_direction", DIRECTION to JsonPrimitive(direction)),
            )
        },
    )
}

/** The oscillation menu, on or off. */
internal fun HassSnapshot.oscillatingMenu(state: EntityState): SelectMenu? {
    if (!state.supportsFeature(FEATURE_OSCILLATE)) return null
    return SelectMenu(
        label = attributeName(state, OSCILLATING),
        icon = OSCILLATING_OFF_ICON,
        value = (state.attributes.boolean(OSCILLATING) == true).toString(),
        enabled = state.available(),
        options = listOf(true, false).map { on ->
            MenuOption(
                value = on.toString(),
                label = formatEntityAttributeValue(state, OSCILLATING, JsonPrimitive(on)),
                icon = if (on) OSCILLATING_ICON else OSCILLATING_OFF_ICON,
                action = call(state, "oscillate", OSCILLATING to JsonPrimitive(on)),
            )
        },
    )
}

private fun call(state: EntityState, service: String, value: Pair<String, JsonPrimitive>) =
    CardAction.CallService(FAN, service, JsonObject(entityData(state) + value), target = null)

private const val FAN = "fan"
private const val DIRECTION = "direction"
private const val OSCILLATING = "oscillating"
private const val DIRECTION_ICON = "mdi:rotate-right"
private const val OSCILLATING_ICON = "mdi:arrow-oscillating"
private const val OSCILLATING_OFF_ICON = "mdi:arrow-oscillating-off"
private const val FEATURE_OSCILLATE = 2
private const val FEATURE_DIRECTION = 4
private const val FEATURE_PRESET_MODE = 8
