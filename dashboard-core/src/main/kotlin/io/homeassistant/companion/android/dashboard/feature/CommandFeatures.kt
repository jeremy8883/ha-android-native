package io.homeassistant.companion.android.dashboard.feature

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.STATE_UNAVAILABLE
import io.homeassistant.companion.android.dashboard.derive.isActive
import io.homeassistant.companion.android.dashboard.derive.stateColor
import io.homeassistant.companion.android.dashboard.derive.supportsFeature
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Ports of the card features that are a row of command buttons (frontend@20260624.6
// src/panels/lovelace/card-features/): counter actions, vacuum and lawn mower commands, update actions, cover tilt,
// valve open/close, the button and the lock's open door.

internal fun call(state: EntityState, service: String, extra: Map<String, JsonPrimitive> = emptyMap()) =
    CardAction.CallService(state.domain, service, JsonObject(entityData(state) + extra), target = null)

/** Port of `hui-counter-actions-card-feature`: decrement, reset and increment, or the configured ones. */
internal fun HassSnapshot.counterActions(state: EntityState, feature: JsonObject): TileFeature? {
    if (state.domain != "counter") return null
    val value = state.state.toDoubleOrNull()?.toLong()
    fun limit(key: String) = number(state.attributes[key])?.takeIf { it == Math.floor(it) }?.toLong()
    val buttons = (feature.configuredList("actions") ?: COUNTER_ACTIONS).filter { it in COUNTER_ACTIONS }.map {
        val (icon, atLimit) = when (it) {
            "increment" -> "mdi:plus" to (value != null && value == limit("maximum"))
            "decrement" -> "mdi:minus" to (value != null && value == limit("minimum"))
            else -> "mdi:restore" to false
        }
        TileFeature.Button(
            localize("ui.card.counter.actions.$it"),
            icon,
            !atLimit && state.available(),
            call(state, it),
        )
    }
    return TileFeature.Buttons(buttons)
}

/**
 * Port of `hui-update-actions-card-feature`: skip and install. Installing backs up first when the `backup` option
 * says `yes`, or asks when it says `ask`, for updates that can back up.
 */
internal fun HassSnapshot.updateActions(state: EntityState, feature: JsonObject): TileFeature? {
    if (state.domain != "update" || !state.supportsFeature(UPDATE_INSTALL)) return null
    val latest = state.attributes.string("latest_version")
    val skipped = latest != null && state.attributes.string("skipped_version") == latest
    val installing = state.attributes["in_progress"]?.isTruthy() == true
    val available = state.available()
    val supportsBackup = state.supportsFeature(UPDATE_BACKUP)
    val option = feature.string("backup")
    fun install(backup: Boolean) = call(state, "install", mapOf("backup" to JsonPrimitive(backup)))
    return TileFeature.Buttons(
        listOf(
            TileFeature.Button(
                localize("ui.dialogs.more_info_control.update.skip"),
                "mdi:cancel",
                available && !(skipped || !state.isActive() || installing),
                call(state, "skip"),
            ),
            TileFeature.Button(
                localize("ui.dialogs.more_info_control.update.install"),
                "mdi:cellphone-arrow-down",
                available && !((!state.isActive() && !skipped) || installing),
                install(supportsBackup && option == "yes"),
                question = if (supportsBackup && option == "ask") {
                    TileFeature.Question(
                        title = localize("ui.dialogs.update_backup.title"),
                        text = localize("ui.dialogs.update_backup.text"),
                        yesLabel = localize("ui.dialogs.update_backup.create"),
                        noLabel = localize("ui.common.no"),
                        yes = install(true),
                        no = install(false),
                    )
                } else {
                    null
                },
            ),
        ),
    )
}

/** JavaScript truthiness of an attribute (`!!value`). */
private fun JsonElement.isTruthy(): Boolean = when {
    this !is JsonPrimitive -> true
    isString -> content.isNotEmpty()
    else -> content != "false" && content != "0" && content != "null"
}

/** Port of `hui-cover-tilt-card-feature`: open, stop and close the tilt. */
internal fun HassSnapshot.coverTilt(state: EntityState): TileFeature? {
    if (state.domain != "cover" ||
        (!state.supportsFeature(COVER_OPEN_TILT) && !state.supportsFeature(COVER_CLOSE_TILT))
    ) {
        return null
    }
    val tilt = number(state.attributes["current_tilt_position"])
    val available = state.available()
    val assumed = state.assumedState()
    return TileFeature.Buttons(
        listOfNotNull(
            TileFeature.Button(
                localize("ui.card.cover.open_tilt_cover"),
                "mdi:arrow-top-right",
                available && (assumed || tilt != FULLY_OPEN),
                call(state, "open_cover_tilt"),
            ).takeIf { state.supportsFeature(COVER_OPEN_TILT) },
            TileFeature.Button(
                localize("ui.card.cover.stop_cover"),
                "mdi:stop",
                available,
                call(state, "stop_cover_tilt"),
            ).takeIf { state.supportsFeature(COVER_STOP_TILT) },
            TileFeature.Button(
                localize("ui.card.cover.close_tilt_cover"),
                "mdi:arrow-bottom-left",
                available && (assumed || tilt != FULLY_CLOSED),
                call(state, "close_cover_tilt"),
            ).takeIf { state.supportsFeature(COVER_CLOSE_TILT) },
        ),
    )
}

/**
 * Port of `hui-valve-open-close-card-feature`: a switch, or close, stop and open buttons when the state is only
 * assumed or unknown.
 */
internal fun HassSnapshot.valveOpenClose(state: EntityState): TileFeature? {
    if (state.domain != "valve" || (!state.supportsFeature(VALVE_OPEN) && !state.supportsFeature(VALVE_CLOSE))) {
        return null
    }
    return if (state.assumedState() || state.state == UNKNOWN) {
        valveButtons(state)
    } else {
        TileFeature.Switch(
            label = localize("ui.card.common.toggle"),
            checked = state.state in VALVE_OPEN_STATES,
            enabled = state.available(),
            onIcon = VALVE_OPEN_ICON,
            offIcon = VALVE_CLOSED_ICON,
            turnOn = call(state, "open_valve"),
            turnOff = call(state, "close_valve"),
        )
    }
}

/** Close, stop and open, for a valve whose state is only assumed or unknown; the one matching it is filled. */
private fun HassSnapshot.valveButtons(state: EntityState) = TileFeature.Buttons(
    listOfNotNull(
        TileFeature.Button(
            localize("ui.card.valve.close_valve"),
            VALVE_CLOSED_ICON,
            canCloseCover(state),
            call(state, "close_valve"),
            active = state.state == "closed",
            color = stateColor(state, "closed"),
        ).takeIf { state.supportsFeature(VALVE_CLOSE) },
        TileFeature.Button(
            localize("ui.card.valve.stop_valve"),
            "mdi:stop",
            state.available(),
            call(state, "stop_valve"),
        ).takeIf { state.supportsFeature(VALVE_STOP) },
        TileFeature.Button(
            localize("ui.card.valve.open_valve"),
            VALVE_OPEN_ICON,
            canOpenCover(state),
            call(state, "open_valve"),
            active = state.state in VALVE_OPEN_STATES,
            color = stateColor(state, "open"),
        ).takeIf { state.supportsFeature(VALVE_OPEN) },
    ),
)

/**
 * Port of `hui-button-card-feature`: presses a button, or runs a scene or script with the configured `data` as its
 * variables; a script whose required fields that data doesn't fill opens its details instead.
 */
internal fun HassSnapshot.buttonFeature(state: EntityState, feature: JsonObject): TileFeature? {
    if (state.domain !in BUTTON_DOMAINS) return null
    val data = feature.obj("data")
    val press = state.domain == "button" || state.domain == "input_button"
    val variables = data?.let { mapOf("variables" to it) }.orEmpty()
    val action = CardAction.CallService(
        state.domain,
        if (press) "press" else "turn_on",
        JsonObject(entityData(state) + variables),
        target = null,
    )
    return TileFeature.Buttons(
        listOf(
            TileFeature.Button(
                label = feature.string("action_name") ?: localize("ui.card.button.press"),
                icon = null,
                enabled = state.state != STATE_UNAVAILABLE,
                action = action,
                text = feature.string("action_name") ?: localize("ui.card.button.press"),
                moreInfo = state.entityId.takeIf { state.domain == "script" && scriptNeedsFields(state, data) },
            ),
        ),
    )
}

/** Ports of `hasRequiredScriptFields` and `requiredScriptFieldsFilled` (src/data/script.ts). */
private fun HassSnapshot.scriptNeedsFields(state: EntityState, data: JsonObject?): Boolean {
    val fields = services?.get("script")?.get(state.entityId.substringAfter('.'))?.obj("fields") ?: return false
    val required = fields.filterValues { (it as? JsonObject)?.boolean("required") == true }.keys
    return required.isNotEmpty() && (data == null || required.any { it !in data })
}

/** Port of `hui-lock-open-door-card-feature`, for locks that can open. */
internal fun HassSnapshot.lockOpenDoor(state: EntityState): TileFeature? {
    if (state.domain != "lock" || !state.supportsFeature(LOCK_OPEN)) return null
    return TileFeature.OpenDoor(
        label = localize("ui.card.lock.open_door"),
        confirmLabel = localize("ui.card.lock.open_door_confirm"),
        doneLabel = localize("ui.card.lock.open_door_done"),
        enabled = state.available() &&
            (state.assumedState() || (state.state != "open" && state.state !in LOCK_WAITING)),
        action = lockCall(state, "open"),
    )
}

private val COUNTER_ACTIONS = listOf("decrement", "reset", "increment")
private val LOCK_WAITING = setOf("opening", "unlocking", "locking")
private val VALVE_OPEN_STATES = setOf("open", "closing", "opening")
private val BUTTON_DOMAINS = setOf("button", "input_button", "scene", "script")
private const val UNKNOWN = "unknown"
private const val VALVE_OPEN_ICON = "mdi:valve-open"
private const val VALVE_CLOSED_ICON = "mdi:valve-closed"
private const val FULLY_OPEN = 100.0
private const val FULLY_CLOSED = 0.0

private const val UPDATE_INSTALL = 1
private const val UPDATE_BACKUP = 8
private const val COVER_OPEN_TILT = 16
private const val COVER_CLOSE_TILT = 32
private const val COVER_STOP_TILT = 64
private const val VALVE_OPEN = 1
private const val VALVE_CLOSE = 2
private const val VALVE_STOP = 8
private const val LOCK_OPEN = 1
