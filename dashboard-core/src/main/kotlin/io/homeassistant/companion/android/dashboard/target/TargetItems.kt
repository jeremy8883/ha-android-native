package io.homeassistant.companion.android.dashboard.target

import io.homeassistant.companion.android.dashboard.derive.deviceName
import io.homeassistant.companion.android.dashboard.derive.entityName
import io.homeassistant.companion.android.dashboard.derive.stateName
import io.homeassistant.companion.android.dashboard.display.caseInsensitiveStringCompare
import io.homeassistant.companion.android.dashboard.entity.DeviceEntry
import io.homeassistant.companion.android.dashboard.entity.EntityEntry
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.entity.entityContext
import io.homeassistant.companion.android.dashboard.logbook.domainToName
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull

// What a target picker offers of each type. Ports of `getEntities` (frontend@20260624.6
// src/data/entity/entity_picker.ts), `getDevices` (src/data/device/device_picker.ts), `getAreasAndFloors`
// (src/data/area_floor_picker.ts) and `getLabels` (src/data/label/label_picker.ts), as `ha-target-picker` calls them
// with an entity filter.

/**
 * An item the picker offers: [primary] over [secondary], [domainName] at the end, its [icon], a label's [color],
 * whether an area is drawn under its floor ([underFloor]) and is its floor's [last]; [searchLabels] and
 * [sortingLabel] are what searching and ordering read.
 */
data class TargetPickerItem(
    val ref: TargetRef,
    val primary: String,
    val secondary: String? = null,
    val domainName: String? = null,
    val icon: TargetIcon? = null,
    val color: String? = null,
    val underFloor: Boolean = false,
    val last: Boolean = false,
    val searchLabels: Map<String, String?> = emptyMap(),
    val sortingLabel: String? = null,
)

/** What an item or chip shows before its name. */
sealed interface TargetIcon {
    /** An icon by name (`mdi:sofa`). */
    data class Named(val icon: String) : TargetIcon

    /** The entity's state icon, coloured by its state (`state-badge`). */
    data class Entity(val entityId: String) : TargetIcon

    /** The integration's logo from the brands API, for a device. */
    data class Brand(val domain: String) : TargetIcon
}

/**
 * What the picker's items are drawn from: [hass], the [entityFilter] the picker was given (`null` when every
 * entity is offered) and the integration of each config entry ([configEntryDomains], by entry id) for devices.
 */
class TargetPickerSource(
    val hass: HassSnapshot,
    val entityFilter: ((String) -> Boolean)?,
    val configEntryDomains: Map<String, String>,
) {
    /** Port of `getDeviceEntityDisplayLookup`: each device's registry entities, in registry order. */
    internal val deviceEntities: Map<String, List<EntityEntry>> =
        hass.registries.entities.values.mapNotNull { entity -> entity.deviceId?.let { it to entity } }
            .groupBy({ it.first }, { it.second })

    /** Whether [entityId] passes the filter: it has a state and the filter takes it. */
    internal fun passes(entityId: String): Boolean = entityId in hass.states && entityFilter?.invoke(entityId) != false

    /** The devices the filter keeps (with an entity that passes it), every device without a filter. */
    internal fun filteredDevices(): Collection<DeviceEntry> = if (entityFilter == null) {
        hass.registries.devices.values
    } else {
        hass.registries.devices.values.filter { device ->
            deviceEntities[device.id].orEmpty().any { passes(it.entityId) }
        }
    }

    /** The registry entities the filter keeps among [entities], `null` without a filter. */
    internal fun filteredEntities(entities: Collection<EntityEntry>): List<EntityEntry>? =
        if (entityFilter == null) null else entities.filter { passes(it.entityId) }
}

/** Port of `getEntities`: the entities that pass the filter, but [excluded], by name. */
fun TargetPickerSource.entityItems(excluded: List<String>): List<TargetPickerItem> =
    hass.states.keys.filter { it !in excluded && passes(it) }.map(::entityItem).sortedBySortingLabel()

private fun TargetPickerSource.entityItem(entityId: String): TargetPickerItem {
    val state = hass.states.getValue(entityId)
    val context = hass.registries.entityContext(entityId)
    val entityName = hass.entityName(state)?.ifEmpty { null }
    val deviceName = context.device?.deviceName()?.ifEmpty { null }
    val areaName = context.area?.name?.trim()?.ifEmpty { null }
    val domainName = hass.domainToName(entityId.substringBefore('.'))
    val primary = entityName ?: deviceName ?: entityId
    val secondary = listOfNotNull(areaName, deviceName.takeIf { entityName != null }).joinToString(" ▸ ")
    return TargetPickerItem(
        ref = TargetRef(TargetType.ENTITY, entityId),
        primary = primary,
        secondary = secondary.ifEmpty { null },
        domainName = domainName,
        icon = TargetIcon.Entity(entityId),
        searchLabels = mapOf(
            "entityName" to entityName,
            "friendlyName" to state.stateName().ifEmpty { null },
            "deviceName" to deviceName,
            "areaName" to areaName,
            "domainName" to domainName.ifEmpty { null },
            "entityId" to entityId,
        ),
        sortingLabel = listOf(primary, secondary).filter { it.isNotEmpty() }.joinToString("_"),
    )
}

/** Port of `getDevices`: the enabled devices with an entity that passes the filter, but [excluded], by name. */
fun TargetPickerSource.deviceItems(excluded: List<String>): List<TargetPickerItem> = filteredDevices()
    .filter { it.json["disabled_by"]?.stringOrNull == null && it.id !in excluded }
    .map(::deviceItem)
    .sortedBySortingLabel()

private fun TargetPickerSource.deviceItem(device: DeviceEntry): TargetPickerItem {
    val lookup = deviceEntities.takeIf { entityFilter != null }
    val deviceName = hass.deviceNameDisplay(device, lookup?.get(device.id))
    val via = device.json.string("via_device_id")?.let { hass.registries.devices[it] }
    val viaName = via?.let { hass.deviceNameDisplay(it, lookup?.get(it.id)) }
    val viaAreaName = via?.areaId?.let { hass.registries.areas[it] }?.name?.trim()?.ifEmpty { null }
    // Port of `computeDeviceAreaLabel`
    val areaName = device.areaId?.let { hass.registries.areas[it] }?.name?.trim()?.ifEmpty { null }
        ?: viaAreaName?.let { "$it ▸ $viaName" }
        ?: viaName?.ifEmpty { null }
    val domain = device.json.string("primary_config_entry")?.let { configEntryDomains[it] }
    val domainName = domain?.let { hass.domainToName(it) }
    val primary = deviceName.ifEmpty { hass.localize("ui.components.device-picker.unnamed_device") }
    return TargetPickerItem(
        ref = TargetRef(TargetType.DEVICE, device.id),
        primary = primary,
        secondary = areaName,
        domainName = domainName,
        icon = domain?.let { TargetIcon.Brand(it) },
        searchLabels = mapOf(
            "deviceName" to deviceName,
            "areaName" to areaName,
            "domain" to domain,
            "domainName" to domainName,
            "viaDeviceName" to viaName?.ifEmpty { null },
            "viaDeviceArea" to viaAreaName,
        ),
        sortingLabel = listOfNotNull(primary, areaName, domainName).filter { it.isNotEmpty() }.joinToString("_"),
    )
}

/**
 * Port of `computeDeviceNameDisplay`: the device's name, else the name of its first [entities] with a state, else
 * "Unnamed device".
 */
internal fun HassSnapshot.deviceNameDisplay(device: DeviceEntry, entities: List<EntityEntry>?): String =
    device.deviceName()?.ifEmpty { null }
        ?: entities?.firstNotNullOfOrNull { states[it.entityId]?.stateName() }
        ?: localize(
            "ui.panel.config.devices.unnamed_device",
            mapOf("type" to localize("ui.panel.config.devices.type.${device.json.string("entry_type") ?: "device"}")),
        )

/**
 * Port of `getLabels`: the labels, but [excluded], by name; with a filter, only those on a device, an entity, or the
 * area of one, that passes it.
 */
fun TargetPickerSource.labelItems(excluded: List<String>): List<TargetPickerItem> {
    val registries = hass.registries
    val used = if (entityFilter == null) {
        null
    } else {
        val devices = filteredDevices()
        val entities = filteredEntities(registries.entities.values.filter { it.labels.isNotEmpty() }).orEmpty()
        val areaLabels = (devices.mapNotNull { it.areaId } + entities.mapNotNull { it.areaId })
            .flatMap { registries.areas[it]?.labels().orEmpty() }
        (devices.flatMap { it.labels() } + entities.flatMap { it.labels } + areaLabels).toSet()
    }
    return registries.labels.values
        .filter { it.labelId !in excluded && (used == null || it.labelId in used) }
        .map { label ->
            TargetPickerItem(
                ref = TargetRef(TargetType.LABEL, label.labelId),
                primary = label.name,
                secondary = label.description?.ifEmpty { null },
                icon = TargetIcon.Named(label.icon ?: LABEL_ICON),
                color = label.color?.ifEmpty { null },
                searchLabels = mapOf("name" to label.name, "description" to label.description, "id" to label.labelId),
                sortingLabel = label.name,
            )
        }
        .sortedBySortingLabel()
}

private fun List<TargetPickerItem>.sortedBySortingLabel() =
    sortedWith { a, b -> caseInsensitiveStringCompare(a.sortingLabel.orEmpty(), b.sortingLabel.orEmpty()) }

/** `mdiTextureBox`, an area's icon when it has none. */
internal const val AREA_ICON = "mdi:texture-box"

/** `mdiLabel`, a label's icon when it has none. */
internal const val LABEL_ICON = "mdi:label"
