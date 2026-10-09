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
    return matchesState(state, filter) && matchesRegistry(registries.entityContext(entityId), filter)
}

private fun matchesState(state: EntityState, filter: EntityFilter): Boolean {
    val deviceClass = state.attributes.string("device_class")?.ifEmpty { null } ?: DEVICE_CLASS_NONE
    return filter.domains.allows(state.domain) &&
        filter.hiddenDomains?.contains(state.domain) != true &&
        filter.deviceClasses.allows(deviceClass)
}

private fun matchesRegistry(context: EntityContext, filter: EntityFilter): Boolean {
    val entity = context.entity
    val category = entity?.entityCategory?.ifEmpty { null } ?: ENTITY_CATEGORY_NONE
    return entity?.hidden != true &&
        filter.floors.allows(context.floor?.floorId) &&
        filter.areas.allows(context.area?.areaId) &&
        filter.devices.allows(context.device?.id) &&
        (filter.labels == null || entity?.labels?.any { it in filter.labels } == true) &&
        filter.entityCategories.allows(category) &&
        // Hiding platforms also hides entities without a registry entry
        (filter.hiddenPlatforms == null || (entity != null && entity.platform !in filter.hiddenPlatforms))
}

/** Whether these criteria allow [value]: no criteria allow anything. */
private fun <T> Set<T>?.allows(value: T): Boolean = this == null || value in this

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
