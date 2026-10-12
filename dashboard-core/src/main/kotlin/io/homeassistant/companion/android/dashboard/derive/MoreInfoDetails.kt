package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.display.attributeValueDisplay
import io.homeassistant.companion.android.dashboard.display.formatEntityState
import io.homeassistant.companion.android.dashboard.energy.isoString
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.feature.attributeName
import io.homeassistant.companion.android.dashboard.model.jsKeys
import io.homeassistant.companion.android.dashboard.model.yamlDump
import java.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Port of `ha-more-info-details` (frontend@20260624.6 src/dialogs/more-info/ha-more-info-details.ts): the attributes
// of the details view, and its YAML view. The feature names are `getFeatures`' enums
// (src/common/entity/get_domain_features.ts, from src/data/*.ts).

/**
 * The details view's attributes of [state] as (name, value): those `ha-attributes` shows, then the built-in ones
 * (friendly name, icon...); the supported features by name ("None" without any) for domains with named features.
 */
internal fun HassSnapshot.detailAttributes(state: EntityState): List<Pair<String, String>> {
    val shown = displayAttributes(state)
    val builtIn = state.attributes.jsKeys().filter { it !in shown }
    val features = DOMAIN_FEATURES[state.domain]?.takeIf { SUPPORTED_FEATURES in state.attributes }
    return (shown + builtIn).map { attribute ->
        val value = if (attribute == SUPPORTED_FEATURES && features != null) {
            featureNames(state, features)
        } else {
            attributeValueDisplay(state, attribute)
        }
        attributeName(state, attribute) to value
    }
}

/** Port of `_renderFeatures`: the supported features' names ("Set temperature"), else "None". */
private fun HassSnapshot.featureNames(state: EntityState, features: List<Pair<String, Int>>): String =
    features.filter { (_, bit) -> state.supportsFeature(bit) }
        .joinToString(", ") { (name, _) -> name.replace('_', NBSP).lowercase().replaceFirstChar(Char::uppercaseChar) }
        .ifEmpty { localize("ui.common.none") }

/**
 * The YAML view of [entityId]'s details, `null` for an unknown entity: its state (translated, raw, when it changed
 * and updated) and its attributes, as `ha-yaml-editor` dumps them.
 */
fun HassSnapshot.moreInfoDetailsYaml(entityId: String): String? {
    val state = states[entityId] ?: return null
    val yamlData = JsonObject(
        mapOf(
            "state" to JsonObject(
                mapOf(
                    "translated" to JsonPrimitive(formatEntityState(state)),
                    "raw" to JsonPrimitive(state.state),
                    "last_changed" to JsonPrimitive(jsDate(state.lastChanged)),
                    "last_updated" to JsonPrimitive(jsDate(state.lastUpdated)),
                ),
            ),
            "attributes" to state.attributes,
        ),
    )
    return yamlDump(yamlData)
}

/** The `toISOString()` of the date `subscribe_entities` makes of [seconds] (`new Date(seconds * 1000)`). */
private fun jsDate(seconds: Double): String = isoString(Instant.ofEpochMilli((seconds * MILLIS).toLong()))

private const val SUPPORTED_FEATURES = "supported_features"
private const val NBSP = ' '
private const val MILLIS = 1000.0

/** Port of `DOMAIN_ENUMS`: each domain's feature names and bits, in their declared order. */
private val DOMAIN_FEATURES: Map<String, List<Pair<String, Int>>> = mapOf(
    "ai_task" to listOf(
        "GENERATE_DATA" to 1,
        "SUPPORT_ATTACHMENTS" to 2,
        "GENERATE_IMAGE" to 4,
    ),
    "alarm_control_panel" to listOf(
        "ARM_HOME" to 1,
        "ARM_AWAY" to 2,
        "ARM_NIGHT" to 4,
        "TRIGGER" to 8,
        "ARM_CUSTOM_BYPASS" to 16,
        "ARM_VACATION" to 32,
    ),
    "assist_satellite" to listOf(
        "ANNOUNCE" to 1,
    ),
    "calendar" to listOf(
        "CREATE_EVENT" to 1,
        "DELETE_EVENT" to 2,
        "UPDATE_EVENT" to 4,
    ),
    "camera" to listOf(
        "ON_OFF" to 1,
        "STREAM" to 2,
    ),
    "climate" to listOf(
        "TARGET_TEMPERATURE" to 1,
        "TARGET_TEMPERATURE_RANGE" to 2,
        "TARGET_HUMIDITY" to 4,
        "FAN_MODE" to 8,
        "PRESET_MODE" to 16,
        "SWING_MODE" to 32,
        "AUX_HEAT" to 64,
        "TURN_OFF" to 128,
        "TURN_ON" to 256,
        "SWING_HORIZONTAL_MODE" to 512,
    ),
    "conversation" to listOf(
        "CONTROL" to 1,
    ),
    "cover" to listOf(
        "OPEN" to 1,
        "CLOSE" to 2,
        "SET_POSITION" to 4,
        "STOP" to 8,
        "OPEN_TILT" to 16,
        "CLOSE_TILT" to 32,
        "STOP_TILT" to 64,
        "SET_TILT_POSITION" to 128,
    ),
    "fan" to listOf(
        "SET_SPEED" to 1,
        "OSCILLATE" to 2,
        "DIRECTION" to 4,
        "PRESET_MODE" to 8,
        "TURN_OFF" to 16,
        "TURN_ON" to 32,
    ),
    "humidifier" to listOf(
        "MODES" to 1,
    ),
    "lawn_mower" to listOf(
        "START_MOWING" to 1,
        "PAUSE" to 2,
        "DOCK" to 4,
    ),
    "light" to listOf(
        "EFFECT" to 4,
        "FLASH" to 8,
        "TRANSITION" to 32,
    ),
    "lock" to listOf(
        "OPEN" to 1,
    ),
    "media_player" to listOf(
        "PAUSE" to 1,
        "SEEK" to 2,
        "VOLUME_SET" to 4,
        "VOLUME_MUTE" to 8,
        "PREVIOUS_TRACK" to 16,
        "NEXT_TRACK" to 32,
        "TURN_ON" to 128,
        "TURN_OFF" to 256,
        "PLAY_MEDIA" to 512,
        "VOLUME_STEP" to 1024,
        "SELECT_SOURCE" to 2048,
        "STOP" to 4096,
        "CLEAR_PLAYLIST" to 8192,
        "PLAY" to 16384,
        "SHUFFLE_SET" to 32768,
        "SELECT_SOUND_MODE" to 65536,
        "BROWSE_MEDIA" to 131072,
        "REPEAT_SET" to 262144,
        "GROUPING" to 524288,
    ),
    "notify" to listOf(
        "TITLE" to 1,
    ),
    "remote" to listOf(
        "LEARN_COMMAND" to 1,
        "DELETE_COMMAND" to 2,
        "ACTIVITY" to 4,
    ),
    "siren" to listOf(
        "TURN_ON" to 1,
        "TURN_OFF" to 2,
        "TONES" to 4,
        "VOLUME_SET" to 8,
        "DURATION" to 16,
    ),
    "todo" to listOf(
        "CREATE_TODO_ITEM" to 1,
        "DELETE_TODO_ITEM" to 2,
        "UPDATE_TODO_ITEM" to 4,
        "MOVE_TODO_ITEM" to 8,
        "SET_DUE_DATE_ON_ITEM" to 16,
        "SET_DUE_DATETIME_ON_ITEM" to 32,
        "SET_DESCRIPTION_ON_ITEM" to 64,
    ),
    "update" to listOf(
        "INSTALL" to 1,
        "SPECIFIC_VERSION" to 2,
        "PROGRESS" to 4,
        "BACKUP" to 8,
        "RELEASE_NOTES" to 16,
    ),
    "vacuum" to listOf(
        "TURN_ON" to 1,
        "TURN_OFF" to 2,
        "PAUSE" to 4,
        "STOP" to 8,
        "RETURN_HOME" to 16,
        "FAN_SPEED" to 32,
        "BATTERY" to 64,
        "STATUS" to 128,
        "SEND_COMMAND" to 256,
        "LOCATE" to 512,
        "CLEAN_SPOT" to 1024,
        "MAP" to 2048,
        "STATE" to 4096,
        "START" to 8192,
        "CLEAN_AREA" to 16384,
    ),
    "valve" to listOf(
        "OPEN" to 1,
        "CLOSE" to 2,
        "SET_POSITION" to 4,
        "STOP" to 8,
    ),
    "water_heater" to listOf(
        "TARGET_TEMPERATURE" to 1,
        "OPERATION_MODE" to 2,
        "AWAY_MODE" to 4,
    ),
    "weather" to listOf(
        "FORECAST_DAILY" to 1,
        "FORECAST_HOURLY" to 2,
        "FORECAST_TWICE_DAILY" to 4,
    ),
)
