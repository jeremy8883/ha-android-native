package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.feature.ALARM_MODES
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * An alarm panel card: the panel's name with its state in a coloured chip (which opens its details), a button per
 * mode to arm (or one to disarm), and a code field with a keypad when the panel needs a code it has no default for.
 * Port of `hui-alarm-panel-card` (frontend@20260624.6 src/panels/lovelace/cards/hui-alarm-panel-card.ts).
 */
sealed interface AlarmPanelCardModel {
    /** A warning in its place: the panel doesn't exist, or the card's entity isn't one. */
    data class Warning(val text: String) : AlarmPanelCardModel

    /**
     * The panel.
     *
     * @property pulsing whether the state chip blinks (triggered, arming, pending)
     * @property code how the code is entered, `null` when none is asked for
     */
    data class Shown(
        val entityId: String,
        val name: String,
        val stateLabel: String,
        val stateIcon: String?,
        val stateColor: DisplayColor?,
        val pulsing: Boolean,
        val actions: List<AlarmAction>,
        val code: AlarmCodeEntry?,
    ) : AlarmPanelCardModel
}

/** A button arming the panel in a mode (or disarming it, in the danger colour); [call] takes the code entered. */
data class AlarmAction(val label: String, val disarm: Boolean, val service: String) {
    /** The call this button makes with [code] (none when empty), for [entityId]. */
    fun call(entityId: String, code: String): CardAction.CallService = CardAction.CallService(
        domain = ALARM_DOMAIN,
        service = service,
        data = buildJsonObject {
            put("entity_id", entityId)
            if (code.isNotEmpty()) put("code", code)
        },
        target = null,
    )
}

/** The code field, with a keypad under it when the code is a number; [clearLabel] names the keypad's clear key. */
data class AlarmCodeEntry(val label: String, val keypad: Boolean, val clearLabel: String)

/**
 * The alarm panel card of [card]. The code is asked for unless the panel's registry entry is known to store a
 * default code ([HassSnapshot.alarmDefaultCodes]), as upstream until it read the entry.
 */
fun HassSnapshot.alarmPanelCardModel(card: CardConfig): AlarmPanelCardModel {
    val json = card.json
    val entityId = json.string("entity")
    val state = entityId?.let(states::get)
    return when {
        entityId?.substringBefore('.') != ALARM_DOMAIN ->
            AlarmPanelCardModel.Warning(localize("ui.errors.config.configuration_error"))
        state == null -> AlarmPanelCardModel.Warning(localize("ui.card.common.entity_not_found"))
        else -> {
            val codeFormat = state.attributes.string("code_format")?.ifEmpty { null }
            AlarmPanelCardModel.Shown(
                entityId = entityId,
                name = entityNameDisplay(state, json["name"]),
                stateLabel = alarmStateLabel(state.state),
                stateIcon = entityIcon(entityId),
                stateColor = stateColor(state),
                pulsing = state.state in PULSING_STATES,
                actions = alarmActions(state, json["states"] as? JsonArray),
                code = codeFormat?.takeIf { alarmDefaultCodes[entityId] != true }?.let {
                    AlarmCodeEntry(
                        localize("ui.card.alarm_control_panel.code"),
                        it == NUMBER_FORMAT,
                        localize("ui.common.clear"),
                    )
                },
            )
        }
    }
}

/** The configured modes (or home and away, those the panel supports) while disarmed, else disarm alone. */
private fun HassSnapshot.alarmActions(state: EntityState, configured: JsonArray?): List<AlarmAction> {
    val modes = if (state.state == DISARMED) {
        configured?.mapNotNull { it.stringOrNull }
            ?: DEFAULT_STATES.filter { mode ->
                ALARM_MODES[MODE_STATES[mode]]?.second?.let(state::supportsFeature) == true
            }
    } else {
        listOf(DISARM)
    }
    return modes.map { mode ->
        AlarmAction(localize("ui.card.alarm_control_panel.$mode"), disarm = mode == DISARM, service = "alarm_$mode")
    }
}

/** Port of `_stateDisplay`. */
private fun HassSnapshot.alarmStateLabel(state: String): String = if (state == STATE_UNAVAILABLE) {
    localize("state.default.unavailable")
} else {
    localize("component.alarm_control_panel.entity_component._.state.$state").ifEmpty { state }
}

/** The alarm panel cards among [cards], whose panels' registry entries tell whether they have a default code. */
fun alarmPanelEntities(cards: List<CardConfig>): Set<String> = cards
    .filter { it.type == ALARM_PANEL_CARD }
    .mapNotNull { card -> card.json.string("entity")?.takeIf { it.substringBefore('.') == ALARM_DOMAIN } }
    .toSet()

private const val ALARM_PANEL_CARD = "alarm-panel"
private const val ALARM_DOMAIN = "alarm_control_panel"
private const val NUMBER_FORMAT = "number"
private const val DISARMED = "disarmed"
private const val DISARM = "disarm"
private val PULSING_STATES = setOf("triggered", "arming", "pending")

/** Port of `DEFAULT_STATES`. */
private val DEFAULT_STATES = listOf("arm_home", "arm_away")

/** Port of `ALARM_MODE_STATE_MAP`. */
private val MODE_STATES = mapOf(
    "arm_home" to "armed_home",
    "arm_away" to "armed_away",
    "arm_night" to "armed_night",
    "arm_vacation" to "armed_vacation",
    "arm_custom_bypass" to "armed_custom_bypass",
)
