package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.entity.DeviceEntry
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.entity.entityContext
import io.homeassistant.companion.android.dashboard.model.jsTruthy
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The entity's `friendly_name`, or its object id with underscores as spaces when the attribute is absent.
 * Port of `computeStateName` (frontend@20260624.6 src/common/entity/compute_state_name.ts).
 */
fun EntityState.stateName(): String = when (val friendlyName = attributes["friendly_name"]) {
    null -> entityId.substringAfter('.').replace('_', ' ')
    JsonNull -> ""
    is JsonPrimitive -> friendlyName.content
    else -> friendlyName.toString()
}

/** Port of `computeDeviceName` (src/common/entity/compute_device_name.ts). */
fun DeviceEntry.deviceName(): String? = (nameByUser?.ifEmpty { null } ?: name)?.trim()

private val PREFIX_SUFFIXES = listOf(" ", ": ", " - ")

/**
 * [entityName] without a leading [prefix] (case-insensitive, followed by a separator), with its first word
 * capitalised unless it already has upper case; `null` when the prefix is not there or nothing would remain.
 * Port of `stripPrefixFromEntityName` (src/common/entity/strip_prefix_from_entity_name.ts).
 */
fun stripPrefixFromEntityName(entityName: String, prefix: String): String? {
    val lowerName = entityName.lowercase()
    for (suffix in PREFIX_SUFFIXES) {
        val prefixWithSuffix = prefix.lowercase() + suffix
        if (!lowerName.startsWith(prefixWithSuffix)) continue
        val newName = entityName.substring(prefixWithSuffix.length)
        if (newName.isEmpty()) continue
        // Upstream checks `newName.substr(0, newName.indexOf(" "))`, which is "" when there is no space
        val firstWord = newName.indexOf(' ').let { if (it < 0) "" else newName.substring(0, it) }
        return if (firstWord.lowercase() != firstWord) newName else newName[0].uppercaseChar() + newName.substring(1)
    }
    return null
}

/**
 * The display name of [state] for a card's `name` option: a plain string, one item or a list of items
 * (`{type: entity | device | area | floor}` or `{type: text, text}`), joined by [separator]. Without a
 * name, the friendly name. An entity whose name is its device's name shows the device name for `entity`.
 *
 * Port of `computeEntityNameDisplay` (frontend@20260624.6 src/common/entity/compute_entity_name_display.ts).
 */
fun HassSnapshot.entityNameDisplay(state: EntityState, name: JsonElement?, separator: String = " "): String {
    if (name is JsonPrimitive && name.isString) return name.content
    if (name == null || !jsTruthy(name)) return state.stateName()

    var items = (name as? JsonArray)?.toList() ?: listOf(name)
    val types = items.map { (it as? JsonObject)?.string("type") }
    if (types.all {
            it == NAME_TEXT
        }
    ) {
        return items.joinToString(separator) { (it as JsonObject).string(NAME_TEXT).orEmpty() }
    }

    if (entityName(state) == null && NAME_DEVICE !in types) {
        items = items.map { item -> if ((item as? JsonObject)?.string("type") == NAME_ENTITY) DEVICE_ITEM else item }
    }
    val names = entityNameList(state, items)
    if (names.size == 1) return names[0].orEmpty()
    return names.filterNot { it.isNullOrEmpty() }.joinToString(separator)
}

/** Port of `computeEntityNameList`: the name of each item, `null` when it has none. */
private fun HassSnapshot.entityNameList(state: EntityState, items: List<JsonElement>): List<String?> {
    val context = registries.entityContext(state.entityId)
    return items.map { item ->
        val obj = item as? JsonObject
        when (obj?.string("type")) {
            NAME_ENTITY -> entityName(state)
            NAME_DEVICE -> context.device?.deviceName()
            NAME_AREA -> context.area?.name?.trim()
            NAME_FLOOR -> context.floor?.name?.trim()
            NAME_TEXT -> obj.string(NAME_TEXT)
            else -> ""
        }
    }
}

/**
 * The entity's own name without its device name, `null` when it is just the device's name.
 * Port of `computeEntityName` and `computeEntityEntryName` (src/common/entity/compute_entity_name.ts).
 */
fun HassSnapshot.entityName(state: EntityState): String? {
    val entry = registries.entities[state.entityId] ?: return state.stateName()
    val name = entry.name?.ifEmpty { null }
    val device = entry.deviceId?.let { registries.devices[it] } ?: return name
    val deviceName = device.deviceName()
    return when {
        deviceName == name -> null
        deviceName != null && name != null -> stripPrefixFromEntityName(name, deviceName) ?: name
        else -> name
    }
}

private const val NAME_ENTITY = "entity"
private const val NAME_DEVICE = "device"
private const val NAME_AREA = "area"
private const val NAME_FLOOR = "floor"
private const val NAME_TEXT = "text"
private val DEVICE_ITEM = JsonObject(mapOf("type" to JsonPrimitive(NAME_DEVICE)))
