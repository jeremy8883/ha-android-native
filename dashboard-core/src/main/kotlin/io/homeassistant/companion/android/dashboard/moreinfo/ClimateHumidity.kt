package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.domainStateColor
import io.homeassistant.companion.android.dashboard.derive.isActive
import io.homeassistant.companion.android.dashboard.display.formatEntityState
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.feature.entityData
import kotlinx.serialization.json.JsonObject

// Port of `ha-state-control-climate-humidity` (frontend@20260624.6 src/state-control/climate/).

/** A thermostat's target humidity, `null` when it has none. */
fun climateHumidityTarget(state: EntityState): Double? = state.attributes.numberOrNull("humidity")

/** The call that sets the target humidity to [humidity]. */
fun climateHumidityCall(state: EntityState, humidity: Double): CardAction.CallService = CardAction.CallService(
    CLIMATE,
    "set_humidity",
    JsonObject(entityData(state) + ("humidity" to jsonNumber(humidity))),
    target = null,
)

/** The humidity dial of [state], a thermostat with a target humidity. */
internal fun HassSnapshot.climateHumidity(state: EntityState): CircularControl {
    val target = climateHumidityTarget(state)
    val unavailable = state.state == UNAVAILABLE
    val settable = target != null && !unavailable
    val active = state.isActive()
    return CircularControl(
        slider = CircularSlider(
            mode = CircularMode.Start,
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
            color = domainStateColor("humidifier", state, if (active) "on" else "off").takeIf { settable },
            lowColor = null,
            highColor = null,
            actionColor = null,
        ),
        label = when {
            unavailable -> formatEntityState(state, UNAVAILABLE)
            // 0 counts as no target, as upstream's falsy test
            target == null || target == 0.0 -> formatEntityState(state)
            else -> localize("ui.card.climate.humidity_target")
        },
        labelDisabled = unavailable,
        primary = target?.takeIf { settable }?.let { CircularPrimary.Target(BigNumber(it, "%", 0)) }
            ?: CircularPrimary.None,
        buttons = settable,
        lowButtonColor = null,
        highButtonColor = null,
    )
}

private const val UNAVAILABLE = "unavailable"
private const val PERCENT = 100.0
