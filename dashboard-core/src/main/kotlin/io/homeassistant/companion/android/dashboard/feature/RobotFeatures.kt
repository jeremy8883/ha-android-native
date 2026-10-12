package io.homeassistant.companion.android.dashboard.feature

import io.homeassistant.companion.android.dashboard.derive.supportsFeature
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import kotlinx.serialization.json.JsonObject

// Ports of `hui-vacuum-commands-card-feature` and `hui-lawn-mower-commands-card-feature` (frontend@20260624.6
// src/panels/lovelace/card-features/).

/** Port of `hui-vacuum-commands-card-feature`: start/pause, stop and return home, or the configured commands. */
internal fun HassSnapshot.vacuumCommands(state: EntityState, feature: JsonObject): TileFeature? {
    if (state.domain != "vacuum" || VACUUM_COMMAND_FLAGS.keys.none { vacuumSupports(state, it) }) return null
    val commands = feature.configuredList("commands") ?: VACUUM_DEFAULT_COMMANDS
    val buttons = commands.filter { it in VACUUM_COMMAND_FLAGS && vacuumSupports(state, it) }.map { command ->
        val spec = vacuumButton(state, command)
        TileFeature.Button(
            localize("ui.dialogs.more_info_control.vacuum.${spec.key}"),
            spec.icon,
            spec.enabled && state.available(),
            call(state, spec.service),
        )
    }
    return TileFeature.Buttons(buttons)
}

private fun vacuumSupports(state: EntityState, command: String) =
    VACUUM_COMMAND_FLAGS.getValue(command).any { state.supportsFeature(it) }

/** A command's translation key, icon, service, and whether it applies now (`VACUUM_COMMANDS_BUTTONS`). */
private data class CommandSpec(val key: String, val icon: String, val service: String, val enabled: Boolean = true)

private fun vacuumButton(state: EntityState, command: String): CommandSpec = when (command) {
    "start_pause" -> vacuumStartPause(state)
    "stop" -> CommandSpec("stop", "mdi:stop", "stop", state.state !in VACUUM_STOPPED)
    "clean_spot" -> CommandSpec("clean_spot", "mdi:target-variant", "clean_spot")
    "locate" -> CommandSpec("locate", "mdi:map-marker", "locate")
    else -> CommandSpec("return_home", "mdi:home-import-outline", "return_to_base", state.state != "returning")
}

private fun vacuumStartPause(state: EntityState): CommandSpec {
    val cleaning = state.state == "cleaning" || state.state == "on"
    return when {
        !state.supportsFeature(VACUUM_STATE) &&
            !state.supportsFeature(VACUUM_START) &&
            state.supportsFeature(VACUUM_PAUSE) -> CommandSpec("start_pause", "mdi:play-pause", "start_pause")
        cleaning && state.supportsFeature(VACUUM_PAUSE) -> CommandSpec("pause", "mdi:pause", "pause")
        else -> CommandSpec("start", "mdi:play", "start", !cleaning)
    }
}

/** Port of `hui-lawn-mower-commands-card-feature`: start/pause and dock, or the configured commands. */
internal fun HassSnapshot.lawnMowerCommands(state: EntityState, feature: JsonObject): TileFeature? {
    if (state.domain != "lawn_mower" || LAWN_MOWER_COMMAND_FLAGS.keys.none { mowerSupports(state, it) }) return null
    val commands = feature.configuredList("commands") ?: LAWN_MOWER_COMMAND_FLAGS.keys.toList()
    val buttons = commands.filter { it in LAWN_MOWER_COMMAND_FLAGS && mowerSupports(state, it) }.map { command ->
        val spec = if (command == "dock") {
            CommandSpec("dock", "mdi:home-import-outline", "dock", state.state != "docked")
        } else if (state.state == "mowing" && state.supportsFeature(MOWER_PAUSE)) {
            CommandSpec("pause", "mdi:pause", "pause")
        } else {
            CommandSpec("start", "mdi:play", "start_mowing")
        }
        TileFeature.Button(
            localize("ui.dialogs.more_info_control.lawn_mower.${spec.key}"),
            spec.icon,
            spec.enabled && state.available(),
            call(state, spec.service),
        )
    }
    return TileFeature.Buttons(buttons)
}

private fun mowerSupports(state: EntityState, command: String) =
    LAWN_MOWER_COMMAND_FLAGS.getValue(command).any { state.supportsFeature(it) }

private val VACUUM_DEFAULT_COMMANDS = listOf("start_pause", "stop", "return_home")
private val VACUUM_STOPPED = setOf("docked", "off", "idle")

// VacuumEntityFeature (src/data/vacuum.ts)
private const val VACUUM_PAUSE = 4
private const val VACUUM_STOP = 8
private const val VACUUM_RETURN_HOME = 16
private const val VACUUM_LOCATE = 512
private const val VACUUM_CLEAN_SPOT = 1024
private const val VACUUM_STATE = 4096
private const val VACUUM_START = 8192

/** `VACUUM_COMMANDS_FEATURES`, in `VACUUM_COMMANDS`' order. */
private val VACUUM_COMMAND_FLAGS = linkedMapOf(
    "start_pause" to listOf(VACUUM_PAUSE, VACUUM_START),
    "stop" to listOf(VACUUM_STOP),
    "clean_spot" to listOf(VACUUM_CLEAN_SPOT),
    "locate" to listOf(VACUUM_LOCATE),
    "return_home" to listOf(VACUUM_RETURN_HOME),
)

// LawnMowerEntityFeature (src/data/lawn_mower.ts)
private const val MOWER_START = 1
private const val MOWER_PAUSE = 2
private const val MOWER_DOCK = 4

/** `LAWN_MOWER_COMMANDS_FEATURES`. */
private val LAWN_MOWER_COMMAND_FLAGS = linkedMapOf(
    "start_pause" to listOf(MOWER_PAUSE, MOWER_START),
    "dock" to listOf(MOWER_DOCK),
)
