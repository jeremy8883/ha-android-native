package io.homeassistant.companion.android.dashboard.strategy

import io.homeassistant.companion.android.dashboard.derive.EntityFeature
import io.homeassistant.companion.android.dashboard.derive.lightSupportsBrightness
import io.homeassistant.companion.android.dashboard.derive.stateName
import io.homeassistant.companion.android.dashboard.derive.stripPrefixFromEntityName
import io.homeassistant.companion.android.dashboard.derive.supportsFeature
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The card for [entityId] in an area: a picture card for cameras, otherwise a tile whose name has the area
 * name ([prefix]) stripped and, with [includeFeature], the first supported control feature.
 * Port of `computeAreaTileCardConfig` (frontend@20260624.6
 * src/panels/lovelace/strategies/areas/helpers/areas-strategy-helper.ts).
 */
fun HassSnapshot.areaTileCard(entityId: String, prefix: String, includeFeature: Boolean): JsonObject {
    val state = states[entityId]
    if (entityId.substringBefore('.') == "camera") {
        return buildJsonObject {
            put("type", "picture-entity")
            put("entity", entityId)
            put("show_state", false)
            put("show_name", false)
            putJsonObject("grid_options") {
                put("columns", CAMERA_COLUMNS)
                put("rows", 2)
            }
        }
    }
    val feature = if (includeFeature) state?.tileFeature() else null
    // Upstream calls computeStateName on the state, which must exist for entities found by filters
    val name = state?.stateName()?.let { stripPrefixFromEntityName(it, prefix.lowercase()) }
    return buildJsonObject {
        put("type", "tile")
        put("entity", entityId)
        name?.let { put("name", it) }
        feature?.let { putJsonArray("features") { addJsonObject { put("type", it) } } }
    }
}

/**
 * The first control feature a tile supports, in upstream priority order.
 * Ports of the `supports*CardFeature` predicates in src/panels/lovelace/card-features/.
 */
private fun EntityState.tileFeature(): String? = when {
    domain == "light" && lightSupportsBrightness() -> "light-brightness"
    domain == "cover" && (supportsFeature(EntityFeature.COVER_OPEN) || supportsFeature(EntityFeature.COVER_CLOSE)) ->
        "cover-open-close"
    supportsTargetTemperature() -> "target-temperature"
    domain == "fan" && supportsFeature(EntityFeature.FAN_SET_SPEED) -> "fan-speed"
    domain == "alarm_control_panel" -> "alarm-modes"
    domain == "lock" -> "lock-commands"
    else -> null
}

private fun EntityState.supportsTargetTemperature(): Boolean = (
    domain == "climate" &&
        (
            supportsFeature(EntityFeature.CLIMATE_TARGET_TEMPERATURE) ||
                supportsFeature(EntityFeature.CLIMATE_TARGET_TEMPERATURE_RANGE)
            )
    ) ||
    (domain == "water_heater" && supportsFeature(EntityFeature.WATER_HEATER_TARGET_TEMPERATURE))

/** Cameras take half a section's width. */
private const val CAMERA_COLUMNS = 6
