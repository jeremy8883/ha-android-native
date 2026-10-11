package io.homeassistant.companion.android.dashboard.target

import io.homeassistant.companion.android.dashboard.entity.AreaEntry
import io.homeassistant.companion.android.dashboard.entity.DeviceEntry
import io.homeassistant.companion.android.dashboard.entity.EntityEntry
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.array
import io.homeassistant.companion.android.dashboard.model.stringOrNull

// What a target's floors, areas, devices and labels stand for. Ports of `_handleExpand` (frontend@20260624.6
// src/components/ha-target-picker.ts) with `areaMeetsFilter`, `deviceMeetsFilter` and `entityRegMeetsFilter`
// (src/data/target.ts), and of `resolveEntityIDs` with the `expand*Target` helpers (src/data/selector.ts).

/**
 * Port of `_handleExpand`: [target] with [ref] (a floor, area, device or label) replaced by what it holds that
 * passes the picker's filter: a floor's areas; an area's devices and the entities placed in it; a device's
 * entities; a label's areas, devices and entities. Those already in the target aren't added twice.
 */
fun TargetPickerSource.expand(target: Target, ref: TargetRef): Target {
    val parts = parts(ref) ?: return target
    val areas = parts.areas.filter { it.areaId !in target[TargetType.AREA] && areaMeets(it) }.map { it.areaId }
    val devices = parts.devices.filter { it.id !in target[TargetType.DEVICE] && deviceMeets(it) }.map { it.id }
    val entities = parts.entities.filter {
        it.entityId !in target[TargetType.ENTITY] && entityMeets(it, parts.secondary)
    }
        .map { it.entityId }
    return target.plus(TargetType.ENTITY, entities).plus(TargetType.DEVICE, devices).plus(TargetType.AREA, areas) - ref
}

/** What an item is made of, before the filter; [secondary] when config and diagnostic entities count. */
private class Parts(
    val areas: List<AreaEntry> = emptyList(),
    val devices: List<DeviceEntry> = emptyList(),
    val entities: List<EntityEntry> = emptyList(),
    val secondary: Boolean = false,
)

private fun TargetPickerSource.parts(ref: TargetRef): Parts? {
    val registries = hass.registries
    return when (ref.type) {
        TargetType.FLOOR -> Parts(areas = registries.areas.values.filter { it.floorId == ref.id })
        TargetType.AREA -> Parts(
            devices = registries.devices.values.filter { it.areaId == ref.id },
            entities = registries.entities.values.filter { it.areaId == ref.id },
        )
        TargetType.DEVICE -> Parts(entities = registries.entities.values.filter { it.deviceId == ref.id })
        TargetType.LABEL -> Parts(
            areas = registries.areas.values.filter { ref.id in it.labels() },
            devices = registries.devices.values.filter { ref.id in it.labels() },
            entities = registries.entities.values.filter { ref.id in it.labels },
            secondary = true,
        )
        TargetType.ENTITY -> null
    }
}

/** Port of `areaMeetsFilter`: a device in it, or an entity placed in it, meets the filter. */
private fun TargetPickerSource.areaMeets(area: AreaEntry): Boolean =
    hass.registries.devices.values.any { it.areaId == area.areaId && deviceMeets(it) } ||
        hass.registries.entities.values.any { it.areaId == area.areaId && entityMeets(it, secondary = false) }

/** Port of `deviceMeetsFilter`: one of its entities meets the filter. */
private fun TargetPickerSource.deviceMeets(device: DeviceEntry): Boolean =
    hass.registries.entities.values.any { it.deviceId == device.id && entityMeets(it, secondary = false) }

/** Port of `entityRegMeetsFilter`: not hidden, not a config or diagnostic entity unless [secondary], and filtered. */
private fun TargetPickerSource.entityMeets(entity: EntityEntry, secondary: Boolean): Boolean = when {
    entity.hidden || (entity.entityCategory != null && !secondary) -> false
    entityFilter != null -> passes(entity.entityId)
    else -> true
}

/**
 * Port of `resolveEntityIDs` with no selector: the entities [target] stands for, its own first, then those of its
 * labels, floors, areas and devices (each with a state; areas only with something in them).
 */
fun HassSnapshot.resolveEntityIds(target: Target): List<String> {
    val resolved = Resolved(target)
    target[TargetType.LABEL].forEach { resolveLabel(it, resolved) }
    target[TargetType.FLOOR].forEach { floorId ->
        registries.areas.values.filter { it.floorId == floorId && areaHasTargets(it.areaId) }
            .forEach { resolved.areas += it.areaId }
    }
    resolved.areas.toList().forEach { areaId ->
        registries.devices.values.filter { it.areaId == areaId }.forEach { resolved.devices += it.id }
        registries.entities.values.filter { it.areaId == areaId && it.entityId in states }
            .forEach { resolved.entities += it.entityId }
    }
    resolved.devices.toList().forEach { deviceId ->
        registries.entities.values.filter { it.deviceId == deviceId && it.entityId in states }
            .forEach { resolved.entities += it.entityId }
    }
    return resolved.entities.toList()
}

/** The entities, devices and areas a target is found to stand for, in the order found. */
private class Resolved(target: Target) {
    val entities = LinkedHashSet(target[TargetType.ENTITY])
    val devices = LinkedHashSet(target[TargetType.DEVICE])
    val areas = LinkedHashSet(target[TargetType.AREA])
}

/** Port of `expandLabelTarget` with no selector: the label's areas (with something in them), devices and entities. */
private fun HassSnapshot.resolveLabel(labelId: String, resolved: Resolved) {
    registries.areas.values.filter { labelId in it.labels() && areaHasTargets(it.areaId) }.forEach {
        resolved.areas +=
            it.areaId
    }
    registries.devices.values.filter { labelId in it.labels() }.forEach { resolved.devices += it.id }
    registries.entities.values.filter { labelId in it.labels && it.entityId in states }.forEach {
        resolved.entities +=
            it.entityId
    }
}

/** Port of `areaMeetsTargetSelector` with no selector: a device is in it, or an entity with a state is. */
private fun HassSnapshot.areaHasTargets(areaId: String): Boolean =
    registries.devices.values.any { it.areaId == areaId } ||
        registries.entities.values.any { it.areaId == areaId && it.entityId in states }

internal fun AreaEntry.labels(): List<String> = json.array("labels")?.mapNotNull { it.stringOrNull }.orEmpty()

internal fun DeviceEntry.labels(): List<String> = json.array("labels")?.mapNotNull { it.stringOrNull }.orEmpty()
