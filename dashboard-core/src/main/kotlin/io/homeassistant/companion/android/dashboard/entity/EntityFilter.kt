package io.homeassistant.companion.android.dashboard.entity

import io.homeassistant.companion.android.dashboard.model.string

/** Entity category value meaning "no category". */
const val ENTITY_CATEGORY_NONE = "none"

/** Device class value matching entities without one. */
const val DEVICE_CLASS_NONE = "none"

/**
 * Criteria an entity must all match. `null` criteria are ignored; for [devices], [areas] and [floors]
 * a `null` element matches entities without one.
 * Port of `EntityFilter` (frontend@20260624.6 src/common/entity/entity_filter.ts).
 */
data class EntityFilter(
    val domains: Set<String>? = null,
    val deviceClasses: Set<String>? = null,
    val devices: Set<String?>? = null,
    val areas: Set<String?>? = null,
    val floors: Set<String?>? = null,
    val labels: Set<String>? = null,
    val entityCategories: Set<String>? = null,
    val hiddenPlatforms: Set<String>? = null,
    val hiddenDomains: Set<String>? = null,
)

/**
 * Whether [entityId] matches [filter]. Entities without a state never match, and neither do entities
 * hidden in the registry. Port of `generateEntityFilter`.
 */
fun HassSnapshot.matches(entityId: String, filter: EntityFilter): Boolean {
    val state = states[entityId] ?: return false
    val domain = entityId.substringBefore('.')
    if (filter.domains != null && domain !in filter.domains) return false
    if (filter.hiddenDomains != null && domain in filter.hiddenDomains) return false
    if (filter.deviceClasses != null &&
        (state.attributes.string("device_class")?.ifEmpty { null } ?: DEVICE_CLASS_NONE) !in filter.deviceClasses
    ) {
        return false
    }

    val (entity, device, area, floor) = registries.entityContext(entityId)
    if (entity?.hidden == true) return false
    if (filter.floors != null && floor?.floorId !in filter.floors) return false
    if (filter.areas != null && area?.areaId !in filter.areas) return false
    if (filter.devices != null && device?.id !in filter.devices) return false
    if (filter.labels != null && (entity == null || entity.labels.none { it in filter.labels })) return false
    if (filter.entityCategories != null &&
        (entity?.entityCategory?.ifEmpty { null } ?: ENTITY_CATEGORY_NONE) !in filter.entityCategories
    ) {
        return false
    }
    if (filter.hiddenPlatforms != null) {
        if (entity == null) return false
        if (entity.platform != null && entity.platform in filter.hiddenPlatforms) return false
    }
    return true
}

/** The entities of [entityIds] matching [filter], in order. */
fun HassSnapshot.filterEntities(entityIds: List<String>, filter: EntityFilter): List<String> =
    entityIds.filter { matches(it, filter) }

/**
 * The entities matching any of [filters], ordered by filter first, then by [entityIds] order, without duplicates.
 * Port of `findEntities`.
 */
fun HassSnapshot.findEntities(entityIds: List<String>, filters: List<EntityFilter>): List<String> {
    val results = LinkedHashSet<String>()
    for (filter in filters) {
        entityIds.filterTo(results) { matches(it, filter) }
    }
    return results.toList()
}
