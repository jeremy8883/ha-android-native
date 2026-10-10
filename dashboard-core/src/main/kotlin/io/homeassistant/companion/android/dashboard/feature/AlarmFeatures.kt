package io.homeassistant.companion.android.dashboard.feature

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.action.CodeRequest
import io.homeassistant.companion.android.dashboard.derive.stateColor
import io.homeassistant.companion.android.dashboard.derive.supportsFeature
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal fun HassSnapshot.alarmModes(state: EntityState, feature: JsonObject): TileFeature? = when {
    state.domain != "alarm_control_panel" -> null
    state.state in ALARM_DISARM_ONLY_STATES -> TileFeature.Buttons(
        listOf(
            TileFeature.Button(
                localize("ui.card.alarm_control_panel.disarm"),
                "mdi:shield-off",
                true,
                alarmCall(state, DISARMED),
            ),
        ),
    )
    else -> alarmModeSelect(state, feature)
}

private fun HassSnapshot.alarmModeSelect(state: EntityState, feature: JsonObject): TileFeature {
    val supported = ALARM_MODES.keys.filter { mode ->
        val flag = ALARM_MODES.getValue(mode).second
        flag == null || state.supportsFeature(flag)
    }.reversed()
    val selectedModes = (feature["modes"] as? JsonArray)?.mapNotNull { it.stringOrNull }
    val modes = selectedModes?.filter { it in supported } ?: supported
    return TileFeature.Select(
        label = localize("ui.card.alarm_control_panel.modes_label"),
        options = modes.map { mode ->
            TileFeature.Option(
                mode,
                localize("ui.card.alarm_control_panel.modes.$mode"),
                ALARM_MODE_ICONS.getValue(mode),
                alarmCall(state, mode),
            )
        },
        selected = supported.firstOrNull { it == state.state },
        enabled = state.available(),
        color = stateColor(state),
    )
}

/** The service setting [mode], asking for the code when the panel needs one for it. */
internal fun HassSnapshot.alarmCall(state: EntityState, mode: String): CardAction.CallService {
    val codeFormat = state.attributes.string("code_format")?.ifEmpty { null }
    val codeArmRequired = (state.attributes["code_arm_required"] as? JsonPrimitive)?.content == "true"
    val disarm = mode == DISARMED
    val needsCode = codeFormat != null && (disarm || codeArmRequired)
    val title = localize("ui.card.alarm_control_panel.${if (disarm) "disarm" else "arm"}")
    return CardAction.CallService(
        "alarm_control_panel",
        ALARM_MODES.getValue(mode).first,
        entityData(state),
        target = null,
        code = if (needsCode) CodeRequest(state.entityId, "alarm_control_panel", codeFormat, title) else null,
    )
}

private const val DISARMED = "disarmed"
private val ALARM_DISARM_ONLY_STATES = setOf("triggered", "arming", "pending")

/** Port of `ALARM_MODES` (src/data/alarm_control_panel.ts): mode to service and supported-features flag. */
internal val ALARM_MODES: Map<String, Pair<String, Int?>> = linkedMapOf(
    "armed_home" to ("alarm_arm_home" to ALARM_ARM_HOME),
    "armed_away" to ("alarm_arm_away" to ALARM_ARM_AWAY),
    "armed_night" to ("alarm_arm_night" to ALARM_ARM_NIGHT),
    "armed_vacation" to ("alarm_arm_vacation" to ALARM_ARM_VACATION),
    "armed_custom_bypass" to ("alarm_arm_custom_bypass" to ALARM_ARM_CUSTOM_BYPASS),
    DISARMED to ("alarm_disarm" to null),
)
internal val ALARM_MODE_ICONS = mapOf(
    "armed_home" to "mdi:home",
    "armed_away" to "mdi:lock",
    "armed_night" to "mdi:moon-waning-crescent",
    "armed_vacation" to "mdi:airplane",
    "armed_custom_bypass" to "mdi:shield",
    DISARMED to "mdi:shield-off",
)

// AlarmControlPanelEntityFeature (src/data/alarm_control_panel.ts)
private const val ALARM_ARM_HOME = 1
private const val ALARM_ARM_AWAY = 2
private const val ALARM_ARM_NIGHT = 4
private const val ALARM_ARM_CUSTOM_BYPASS = 16
private const val ALARM_ARM_VACATION = 32
