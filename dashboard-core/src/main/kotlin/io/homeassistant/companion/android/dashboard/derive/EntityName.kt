package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.entity.DeviceEntry
import io.homeassistant.companion.android.dashboard.entity.EntityState
import kotlinx.serialization.json.JsonNull
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
