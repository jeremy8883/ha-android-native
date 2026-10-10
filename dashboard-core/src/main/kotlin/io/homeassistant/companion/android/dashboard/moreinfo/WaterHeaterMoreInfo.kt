package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.attributeIcon
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
import kotlinx.serialization.json.JsonPrimitive

// Port of `more-info-water_heater` (frontend@20260624.6 src/dialogs/more-info/controls/more-info-water_heater.ts)
// with `ha-state-control-water_heater-temperature` (src/state-control/water_heater/).

/**
 * What a water heater's details show: the current temperature, the temperature dial, and the operation and away
 * mode menus.
 *
 * @property current the current temperature, as label and value
 */
data class WaterHeaterMoreInfo(
    val current: List<Pair<String, String>>,
    val temperature: CircularControl,
    val menus: List<SelectMenu>,
)

/** The details of a water heater, or `null` for another entity. */
fun HassSnapshot.waterHeaterMoreInfo(state: EntityState): WaterHeaterMoreInfo? {
    if (state.domain != WATER_HEATER) return null
    val current = state.attributes[CURRENT_TEMPERATURE]?.takeIf { it !is JsonNull }?.let {
        listOf(attributeName(state, CURRENT_TEMPERATURE) to formatEntityAttributeValue(state, CURRENT_TEMPERATURE))
    }.orEmpty()
    return WaterHeaterMoreInfo(
        current = current,
        temperature = waterHeaterTemperature(state),
        menus = listOfNotNull(operationMenu(state), awayMenu(state)),
    )
}

/** A water heater's target temperature, `null` when it has none. */
fun waterHeaterTarget(state: EntityState): Double? = state.attributes.numberOrNull("temperature")

/** The call that sets the target temperature to [temperature]. */
fun waterHeaterTemperatureCall(state: EntityState, temperature: Double): CardAction.CallService =
    CardAction.CallService(
        WATER_HEATER,
        "set_temperature",
        JsonObject(entityData(state) + ("temperature" to jsonNumber(temperature))),
        target = null,
    )

private fun HassSnapshot.waterHeaterTemperature(state: EntityState): CircularControl {
    val target = state.attributes.numberOrNull("temperature")
    val unavailable = state.state == UNAVAILABLE
    val supportsTarget = state.supportsFeature(FEATURE_TARGET_TEMPERATURE)
    val settable = supportsTarget && target != null && !unavailable
    val step = climateTemperatureStep(state)
    val active = state.isActive()
    return CircularControl(
        slider = CircularSlider(
            mode = if (settable) CircularMode.Start else CircularMode.Full,
            dual = false,
            value = target.takeIf { settable },
            low = null,
            high = null,
            current = state.attributes.numberOrNull(CURRENT_TEMPERATURE),
            min = state.attributes.numberOrNull("min_temp") ?: 0.0,
            max = state.attributes.numberOrNull("max_temp") ?: PERCENT,
            step = step,
            inactive = settable && !active,
            readonly = !settable,
            disabled = !settable && !active,
            color = stateColor(state),
            lowColor = null,
            highColor = null,
            actionColor = null,
        ),
        label = waterHeaterLabel(state, supportsTarget, target),
        labelDisabled = unavailable,
        primary = target?.takeIf { settable }
            ?.let { CircularPrimary.Target(BigNumber(it, config.temperatureUnit.orEmpty(), fractionDigits(step))) }
            ?: CircularPrimary.None,
        buttons = settable,
        lowButtonColor = null,
        highButtonColor = null,
    )
}

/** Port of `_renderLabel`: unavailable, else the state without a target (or with 0, upstream's falsy test). */
private fun HassSnapshot.waterHeaterLabel(state: EntityState, supportsTarget: Boolean, target: Double?): String = when {
    state.state == UNAVAILABLE -> formatEntityState(state, UNAVAILABLE)
    !supportsTarget || target == null || target == 0.0 -> formatEntityState(state)
    else -> localize("ui.card.water_heater.target")
}

/** The operation mode menu, in upstream's order, with each mode's icon. */
private fun HassSnapshot.operationMenu(state: EntityState): SelectMenu? {
    val modes = state.attributes.stringList("operation_list")
        .takeIf { state.supportsFeature(FEATURE_OPERATION_MODE) && OPERATION_LIST in state.attributes }
        ?: return null
    return SelectMenu(
        label = localize("ui.card.water_heater.mode"),
        icon = "mdi:water-boiler",
        value = state.state,
        enabled = state.state != UNAVAILABLE,
        options = modes.sortedBy { OPERATION_MODES.indexOf(it) }.map { mode ->
            MenuOption(
                value = mode,
                label = formatEntityState(state, mode),
                icon = attributeIcon(state, "operation_mode", mode),
                action = call(state, "set_operation_mode", "operation_mode" to JsonPrimitive(mode)),
            )
        },
    )
}

/** The away mode menu: on and off, set as a boolean. */
private fun HassSnapshot.awayMenu(state: EntityState): SelectMenu? {
    if (!state.supportsFeature(FEATURE_AWAY_MODE)) return null
    return SelectMenu(
        label = attributeName(state, AWAY_MODE),
        icon = "mdi:account",
        value = state.attributes.string(AWAY_MODE),
        enabled = state.state != UNAVAILABLE,
        options = listOf("on", "off").map { value ->
            MenuOption(
                value = value,
                label = formatEntityAttributeValue(state, AWAY_MODE, JsonPrimitive(value)),
                icon = if (value == "on") "mdi:account-arrow-right" else "mdi:account",
                action = call(state, "set_away_mode", AWAY_MODE to JsonPrimitive(value == "on")),
            )
        },
    )
}

private fun call(state: EntityState, service: String, data: Pair<String, JsonPrimitive>) =
    CardAction.CallService(WATER_HEATER, service, JsonObject(mapOf(data) + entityData(state)), target = null)

private const val WATER_HEATER = "water_heater"
private const val UNAVAILABLE = "unavailable"
private const val AWAY_MODE = "away_mode"
private const val OPERATION_LIST = "operation_list"
private const val PERCENT = 100.0
private const val FEATURE_TARGET_TEMPERATURE = 1
private const val FEATURE_OPERATION_MODE = 2
private const val FEATURE_AWAY_MODE = 4

/** `OPERATION_MODES`, in upstream's order. */
private val OPERATION_MODES = listOf("electric", "gas", "heat_pump", "eco", "performance", "high_demand", "off")
