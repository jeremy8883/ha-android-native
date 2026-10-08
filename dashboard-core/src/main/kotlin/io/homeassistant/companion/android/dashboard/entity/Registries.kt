package io.homeassistant.companion.android.dashboard.entity

import io.homeassistant.companion.android.dashboard.model.array
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.number
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Entity registry entry as the frontend sees it (`hass.entities`), decoded from the compact
 * `config/entity_registry/list_for_display` format. Disabled entities are never listed.
 */
data class EntityEntry(
    val entityId: String,
    val deviceId: String?,
    val areaId: String?,
    val labels: List<String>,
    val translationKey: String?,
    val platform: String?,
    val entityCategory: String?,
    val hasEntityName: Boolean,
    val name: String?,
    val icon: String?,
    val hidden: Boolean,
    val displayPrecision: Int?,
)

/** Device registry entry. [json] keeps every field for future ports. */
data class DeviceEntry(
    val id: String,
    val name: String?,
    val nameByUser: String?,
    val areaId: String?,
    val json: JsonObject,
)

/** Area registry entry. */
data class AreaEntry(
    val areaId: String,
    val name: String,
    val floorId: String?,
    val icon: String?,
    val temperatureEntityId: String?,
    val humidityEntityId: String?,
    val json: JsonObject,
)

/** Floor registry entry. */
data class FloorEntry(val floorId: String, val name: String, val level: Int?, val icon: String?, val json: JsonObject)

/**
 * The registries a dashboard depends on, keyed by id. Maps keep the server's order, which strategies
 * rely on just like the frontend's insertion-ordered objects.
 */
data class Registries(
    val entities: Map<String, EntityEntry>,
    val devices: Map<String, DeviceEntry>,
    val areas: Map<String, AreaEntry>,
    val floors: Map<String, FloorEntry>,
) {
    companion object {
        val EMPTY = Registries(emptyMap(), emptyMap(), emptyMap(), emptyMap())
    }
}

/**
 * Decode a `config/entity_registry/list_for_display` result.
 * Port of the `subscribeEntityRegistryDisplay` callback in frontend@20260624.6 src/state/connection-mixin.ts.
 */
fun parseEntityRegistryDisplay(result: JsonObject): Map<String, EntityEntry> {
    // Index → category name, for example {"0": "config", "1": "diagnostic"}
    val categories = result.obj("entity_categories") ?: JsonObject(emptyMap())
    return result.objects("entities").mapNotNull { entry ->
        val entityId = entry.string("ei") ?: return@mapNotNull null
        entityId to EntityEntry(
            entityId = entityId,
            deviceId = entry.string("di"),
            areaId = entry.string("ai"),
            labels = entry.array("lb")?.mapNotNull { it.stringOrNull }.orEmpty(),
            translationKey = entry.string("tk"),
            platform = entry.string("pl"),
            entityCategory = entry["ec"]?.jsonPrimitive?.intOrNull?.let { categories.string(it.toString()) },
            hasEntityName = entry.boolean("hn") == true,
            name = entry.string("en"),
            icon = entry.string("ic"),
            hidden = entry.boolean("hb") == true,
            displayPrecision = entry.number("dp")?.toInt(),
        )
    }.toMap()
}

/** Decode a `config/device_registry/list` result. */
fun parseDeviceRegistry(result: JsonArray): Map<String, DeviceEntry> =
    result.filterIsInstance<JsonObject>().mapNotNull {
        val id = it.string("id") ?: return@mapNotNull null
        id to DeviceEntry(id, it.string("name"), it.string("name_by_user"), it.string("area_id"), it)
    }.toMap()

/** Decode a `config/area_registry/list` result. */
fun parseAreaRegistry(result: JsonArray): Map<String, AreaEntry> = result.filterIsInstance<JsonObject>().mapNotNull {
    val id = it.string("area_id") ?: return@mapNotNull null
    id to AreaEntry(
        areaId = id,
        name = it.string("name").orEmpty(),
        floorId = it.string("floor_id"),
        icon = it.string("icon"),
        temperatureEntityId = it.string("temperature_entity_id"),
        humidityEntityId = it.string("humidity_entity_id"),
        json = it,
    )
}.toMap()

/** Decode a `config/floor_registry/list` result. */
fun parseFloorRegistry(result: JsonArray): Map<String, FloorEntry> = result.filterIsInstance<JsonObject>().mapNotNull {
    val id = it.string("floor_id") ?: return@mapNotNull null
    id to FloorEntry(id, it.string("name").orEmpty(), it.number("level")?.toInt(), it.string("icon"), it)
}.toMap()
