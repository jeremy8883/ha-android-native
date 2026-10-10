package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.DisplayColor
import io.homeassistant.companion.android.dashboard.derive.isActive
import io.homeassistant.companion.android.dashboard.derive.stateColor
import io.homeassistant.companion.android.dashboard.derive.supportsFeature
import io.homeassistant.companion.android.dashboard.display.formatEntityAttributeValue
import io.homeassistant.companion.android.dashboard.display.formatEntityState
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.feature.attributeName
import io.homeassistant.companion.android.dashboard.feature.entityData
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

// Port of `more-info-humidifier` (frontend@20260624.6 src/dialogs/more-info/controls/more-info-humidifier.ts) with
// `ha-state-control-humidifier-humidity` (src/state-control/humidifier/).

/**
 * What a humidifier's details show: the current humidity, the humidity dial, and the state and mode menus.
 *
 * @property current the current humidity, as label and value
 */
data class HumidifierMoreInfo(
    val current: List<Pair<String, String>>,
    val humidity: CircularControl,
    val menus: List<SelectMenu>,
)

/** The details of a humidifier, or `null` for another entity. */
fun HassSnapshot.humidifierMoreInfo(state: EntityState): HumidifierMoreInfo? {
    if (state.domain != HUMIDIFIER) return null
    val current = state.attributes[CURRENT_HUMIDITY]?.takeIf { it !is JsonNull }?.let {
        listOf(attributeName(state, CURRENT_HUMIDITY) to formatEntityAttributeValue(state, CURRENT_HUMIDITY))
    }.orEmpty()
    val modeLabel = localize("ui.card.humidifier.mode")
    // Upstream labels it as the card does, and lists no modes when the entity names none
    val mode = if (state.supportsFeature(FEATURE_MODES)) {
        attributeMenu(state, "mode", "available_modes", "set_mode", MODE_ICON)?.copy(label = modeLabel)
            ?: SelectMenu(
                modeLabel,
                MODE_ICON,
                state.attributes.string("mode"),
                state.state != UNAVAILABLE,
                emptyList(),
            )
    } else {
        null
    }
    return HumidifierMoreInfo(
        current = current,
        humidity = humidifierHumidity(state),
        menus = listOfNotNull(stateMenu(state), mode),
    )
}

/** A humidifier's target humidity, `null` when it has none. */
fun humidifierTarget(state: EntityState): Double? = state.attributes.numberOrNull("humidity")

/** The call that sets the target humidity to [humidity]. */
fun humidifierHumidityCall(state: EntityState, humidity: Double): CardAction.CallService = CardAction.CallService(
    HUMIDIFIER,
    "set_humidity",
    JsonObject(entityData(state) + ("humidity" to jsonNumber(humidity))),
    target = null,
)

private fun HassSnapshot.humidifierHumidity(state: EntityState): CircularControl {
    val target = state.attributes.numberOrNull("humidity")
    val unavailable = state.state == UNAVAILABLE
    val settable = target != null && !unavailable
    val active = state.isActive()
    val dehumidifier = state.attributes.string("device_class") == "dehumidifier"
    return CircularControl(
        slider = CircularSlider(
            mode = if (dehumidifier && settable) CircularMode.End else CircularMode.Start,
            dual = false,
            value = target.takeIf { settable },
            low = null,
            high = null,
            current = state.attributes.numberOrNull(CURRENT_HUMIDITY),
            min = state.attributes.numberOrNull("min_humidity") ?: 0.0,
            max = state.attributes.numberOrNull("max_humidity") ?: PERCENT,
            step = state.attributes.numberOrNull("target_humidity_step") ?: 1.0,
            inactive = settable && !active,
            readonly = false,
            disabled = !settable,
            color = stateColor(state),
            lowColor = null,
            highColor = null,
            actionColor = humidifierActionColor(state, active),
        ),
        label = humidifierLabel(state, target != null),
        labelDisabled = unavailable,
        primary = when {
            target != null -> CircularPrimary.Target(BigNumber(target, "%", 0))
            !unavailable -> CircularPrimary.Text(formatEntityState(state))
            else -> CircularPrimary.None
        },
        buttons = settable,
        lowButtonColor = null,
        highButtonColor = null,
    )
}

/** Port of `_renderLabel`: unavailable (greyed), else the action, else the state when a target shows. */
private fun HassSnapshot.humidifierLabel(state: EntityState, showsTarget: Boolean): String? {
    val action = state.attributes.string("action")
    return when {
        state.state == UNAVAILABLE -> formatEntityState(state, UNAVAILABLE)
        action != null && action != OFF -> formatEntityAttributeValue(state, "action")
        showsTarget -> formatEntityState(state)
        else -> null
    }
}

/** The glow of what the humidifier is doing, while it humidifies or dries. */
private fun humidifierActionColor(state: EntityState, active: Boolean): DisplayColor? {
    val action = state.attributes.string("action")?.takeIf { it != "idle" && it != OFF && active }
    return action?.let { stateColor(state, ACTION_TO_STATE[it]) }
}

/** The state menu: off and on, by the menu's own icon. */
private fun HassSnapshot.stateMenu(state: EntityState): SelectMenu = SelectMenu(
    label = localize("ui.card.humidifier.state"),
    icon = "mdi:power",
    value = state.state,
    enabled = state.state != UNAVAILABLE,
    options = listOf(OFF, "on").map { value ->
        MenuOption(
            value = value,
            label = formatEntityState(state, value),
            icon = null,
            action = CardAction.CallService(
                HUMIDIFIER,
                if (value ==
                    "on"
                ) {
                    "turn_on"
                } else {
                    "turn_off"
                },
                entityData(state),
                null,
            ),
        )
    },
    optionIcons = false,
)

private const val HUMIDIFIER = "humidifier"
private const val OFF = "off"
private const val UNAVAILABLE = "unavailable"
private const val PERCENT = 100.0
private const val FEATURE_MODES = 1
private const val MODE_ICON = "mdi:tune-variant"

/** `HUMIDIFIER_ACTION_MODE`. */
private val ACTION_TO_STATE = mapOf("drying" to "on", "humidifying" to "on", "idle" to OFF, OFF to OFF)
