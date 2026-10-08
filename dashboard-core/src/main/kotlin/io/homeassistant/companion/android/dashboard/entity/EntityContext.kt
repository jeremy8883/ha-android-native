package io.homeassistant.companion.android.dashboard.entity

/** Registry context of an entity. All `null` when the entity has no registry entry. */
data class EntityContext(
    val entity: EntityEntry?,
    val device: DeviceEntry?,
    val area: AreaEntry?,
    val floor: FloorEntry?,
)

/**
 * The registry entry, device, area and floor of [entityId]. The entity's own area wins over its device's area.
 * Port of `getEntityContext` / `getEntityEntryContext` (frontend@20260624.6 src/common/entity/context/get_entity_context.ts).
 */
fun Registries.entityContext(entityId: String): EntityContext {
    val entry = entities[entityId] ?: return EntityContext(null, null, null, null)
    val device = entry.deviceId?.let(devices::get)
    val area = (entry.areaId ?: device?.areaId)?.let(areas::get)
    val floor = area?.floorId?.let(floors::get)
    return EntityContext(entry, device, area, floor)
}
