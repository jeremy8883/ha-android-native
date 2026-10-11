package io.homeassistant.companion.android.dashboard.panels

import io.homeassistant.companion.android.dashboard.derive.deviceName
import io.homeassistant.companion.android.dashboard.derive.entityName
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.entity.entityContext
import java.net.URLDecoder
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

// What the History and Logbook panels read from their address, and the entities they let pick. Ports of
// `decodeHistoryLogbookQueryParams` and `historyLogbookTargetFromQueryParams` (frontend@20260624.6
// src/common/url/history-logbook-query-params.ts), with the target resolved to entities as `resolveEntityIDs` does.

/** The panel's address: the entities to show (every entity when `null`) and the period's ends when given. */
data class HistoryLogbookQuery(val entityIds: List<String>?, val start: Instant?, val end: Instant?)

/**
 * Reads [query] ("entity_id=light.a,light.b&start_date=…"): the entities, devices and areas it targets as their
 * entities that pass [include], and the period's ends.
 */
fun HassSnapshot.historyLogbookQuery(query: String?, include: (String) -> Boolean): HistoryLogbookQuery {
    val params = query.orEmpty().split('&').filter { '=' in it }.associate { pair ->
        pair.substringBefore('=') to URLDecoder.decode(pair.substringAfter('='), Charsets.UTF_8)
    }
    fun list(key: String) = params[key]?.split(',')?.filter { it.isNotEmpty() }.orEmpty()
    val devices = list(DEVICE_ID).toSet()
    val areas = list(AREA_ID).toSet()
    val targeted = list(ENTITY_ID) + registries.entities.values
        .filter { entry ->
            entry.deviceId in devices || registries.entityContext(entry.entityId).area?.areaId in areas
        }
        .map { it.entityId }
    val hasTarget = listOf(ENTITY_ID, DEVICE_ID, AREA_ID).any { it in params }
    return HistoryLogbookQuery(
        entityIds = targeted.distinct().filter(include).takeIf { hasTarget },
        start = params[START_DATE]?.let(::parseDate),
        end = params[END_DATE]?.let(::parseDate),
    )
}

/**
 * An entity a picker offers: its [name], with where it is ([context], "Kitchen ▸ Motion sensor") and its
 * integration's name ([domainName], "Light").
 */
data class PickerEntity(val entityId: String, val name: String, val context: String?, val domainName: String? = null)

/**
 * The entities that pass [include], named as `entityDisplay` names them, by name. Port of the entities
 * `ha-target-picker` offers (areas, devices and labels left out).
 */
fun HassSnapshot.pickerEntities(include: (String) -> Boolean): List<PickerEntity> = states.keys.filter(include)
    .map { entityId ->
        val domain = entityId.substringBefore('.')
        pickerEntity(entityId).copy(domainName = localize("component.$domain.title").ifEmpty { domain })
    }
    .sortedBy { it.name.lowercase() }

/** [entityId] named as `entityDisplay` names it; [entityId] alone when it doesn't exist. */
fun HassSnapshot.pickerEntity(entityId: String): PickerEntity {
    val state = states[entityId] ?: return PickerEntity(entityId, entityId, null)
    val entityName = entityName(state)?.ifEmpty { null }
    val context = registries.entityContext(entityId)
    val deviceName = context.device?.deviceName()?.ifEmpty { null }
    val where = listOfNotNull(context.area?.name?.trim()?.ifEmpty { null }, deviceName.takeIf { entityName != null })
    return PickerEntity(
        entityId,
        entityName ?: deviceName ?: entityId,
        where.takeIf {
            it.isNotEmpty()
        }?.joinToString(" ▸ "),
    )
}

/** The entities whose name, place or id has every word of [query], ignoring case. */
fun List<PickerEntity>.matching(query: String): List<PickerEntity> {
    val words = query.lowercase().split(' ').filter { it.isNotEmpty() }
    return filter { entity ->
        val text = listOfNotNull(entity.name, entity.context, entity.entityId).joinToString(" ").lowercase()
        words.all { it in text }
    }
}

private fun parseDate(text: String): Instant? = try {
    OffsetDateTime.parse(text).toInstant()
} catch (_: DateTimeParseException) {
    null
}

private const val ENTITY_ID = "entity_id"
private const val DEVICE_ID = "device_id"
private const val AREA_ID = "area_id"
private const val START_DATE = "start_date"
private const val END_DATE = "end_date"
