package io.homeassistant.companion.android.dashboard.target

import io.homeassistant.companion.android.dashboard.derive.stateName
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.strategy.home.defaultIcon

// The picked items as chips. Port of `_renderValueChips` (frontend@20260624.6 src/components/ha-target-picker.ts)
// and of `ha-target-picker-value-chip`'s `_itemData` (src/components/target-picker/).

/** A picked item's chip: its [name] and [icon], a label's [color], and whether it can be [expandable] into its parts. */
data class TargetChip(
    val ref: TargetRef,
    val name: String,
    val icon: TargetIcon,
    val color: String? = null,
    val expandable: Boolean = ref.type != TargetType.ENTITY,
)

/** The chips of [target]: its floors, then areas, devices, entities and labels. */
fun TargetPickerSource.chips(target: Target): List<TargetChip> = CHIP_ORDER.flatMap { type ->
    target[type].map { id -> chip(TargetRef(type, id)) }
}

private fun TargetPickerSource.chip(ref: TargetRef): TargetChip = when (ref.type) {
    TargetType.FLOOR -> floorChip(ref)
    TargetType.AREA -> areaChip(ref)
    TargetType.DEVICE -> deviceChip(ref)
    TargetType.ENTITY -> TargetChip(
        ref,
        hass.states[ref.id]?.stateName()?.ifEmpty {
            null
        } ?: ref.id,
        TargetIcon.Entity(ref.id),
    )
    TargetType.LABEL -> labelChip(ref)
}

private fun TargetPickerSource.floorChip(ref: TargetRef): TargetChip {
    val floor = hass.registries.floors[ref.id]
    return TargetChip(
        ref,
        floor?.name?.ifEmpty { null } ?: ref.id,
        TargetIcon.Named(
            floor?.icon ?: floor?.defaultIcon() ?: HOME_ICON,
        ),
    )
}

private fun TargetPickerSource.areaChip(ref: TargetRef): TargetChip {
    val area = hass.registries.areas[ref.id]
    return TargetChip(ref, area?.name?.ifEmpty { null } ?: ref.id, TargetIcon.Named(area?.icon ?: AREA_ICON))
}

private fun TargetPickerSource.deviceChip(ref: TargetRef): TargetChip {
    val device = hass.registries.devices[ref.id]
    val domain = device?.json?.string("primary_config_entry")?.let { configEntryDomains[it] }
    return TargetChip(
        ref,
        device?.let { hass.deviceNameDisplay(it, entities = null) } ?: ref.id,
        domain?.let { TargetIcon.Brand(it) } ?: TargetIcon.Named(DEVICES_ICON),
    )
}

private fun TargetPickerSource.labelChip(ref: TargetRef): TargetChip {
    val label = hass.registries.labels[ref.id]
    return TargetChip(
        ref,
        label?.name?.ifEmpty {
            null
        } ?: ref.id,
        TargetIcon.Named(label?.icon ?: LABEL_ICON),
        label?.color,
    )
}

private val CHIP_ORDER =
    listOf(TargetType.FLOOR, TargetType.AREA, TargetType.DEVICE, TargetType.ENTITY, TargetType.LABEL)
private const val HOME_ICON = "mdi:home"
private const val DEVICES_ICON = "mdi:devices"
