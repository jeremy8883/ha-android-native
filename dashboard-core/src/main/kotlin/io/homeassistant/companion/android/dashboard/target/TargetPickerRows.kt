package io.homeassistant.companion.android.dashboard.target

import io.homeassistant.companion.android.dashboard.search.FuseIndex
import io.homeassistant.companion.android.dashboard.search.FuseKey
import io.homeassistant.companion.android.dashboard.search.multiTermSearch
import io.homeassistant.companion.android.dashboard.search.multiTermSortedSearch

// The list the target picker shows. Port of `_getItemsMemoized` and `_filterGroup` of `ha-target-picker`
// (frontend@20260624.6 src/components/ha-target-picker.ts), with the search keys of the four pickers.

/** A row of the picker's list. */
sealed interface TargetPickerRow {
    /** A section's title, shown when every section is listed. */
    data class Title(val text: String) : TargetPickerRow

    /** An item to pick. */
    data class Item(val item: TargetPickerItem) : TargetPickerRow

    /** That nothing matches [search] (or nothing is offered, without one). */
    data class Empty(val text: String) : TargetPickerRow
}

/** The sections the picker's filter chips choose, in their order; areas include floors. */
val TARGET_SECTIONS = listOf(TargetType.ENTITY, TargetType.DEVICE, TargetType.AREA, TargetType.LABEL)

/**
 * The items the picker offers for [target] (those already picked left out), built once, with their search
 * indexes; [rows] lists them for a search and section.
 */
class TargetPickerItems(private val source: TargetPickerSource, target: Target) {
    private val groups: Map<TargetType, Group> = mapOf(
        TargetType.ENTITY to Group(source.entityItems(target[TargetType.ENTITY]), ENTITY_KEYS, sorted = true),
        TargetType.DEVICE to Group(source.deviceItems(target[TargetType.DEVICE]), DEVICE_KEYS, sorted = true),
        TargetType.AREA to Group(
            source.areaFloorItems(target[TargetType.AREA], target[TargetType.FLOOR]),
            AREA_KEYS,
            sorted = false,
        ),
        TargetType.LABEL to Group(source.labelItems(target[TargetType.LABEL]), LABEL_KEYS, sorted = true),
    )

    /**
     * The rows for [search] in [section] (every section when `null`): each section's matching items under its
     * title, the best matches first (areas keep their order), or why there are none.
     */
    fun rows(search: String, section: TargetType?): List<TargetPickerRow> {
        val rows = TARGET_SECTIONS.filter { section == null || it == section }.flatMap { type ->
            val found = groups.getValue(type).search(search).let { if (type == TargetType.AREA) it.markLast() else it }
            if (found.isEmpty()) {
                emptyList()
            } else {
                listOfNotNull(
                    TargetPickerRow.Title(source.hass.localize("$TARGET.type.${SECTION_TITLES.getValue(type)}"))
                        .takeIf { section == null },
                ) + found.map { TargetPickerRow.Item(it) }
            }
        }
        if (rows.isNotEmpty()) return rows
        val empty = if (search.isNotBlank()) {
            source.hass.localize("$TARGET.no_target_found", mapOf("term" to "‘$search’"))
        } else {
            source.hass.localize("$TARGET.no_targets")
        }
        return listOf(TargetPickerRow.Empty(empty))
    }

    /** A section's items and their search index. */
    private class Group(val items: List<TargetPickerItem>, keys: List<FuseKey>, val sorted: Boolean) {
        private val index = FuseIndex(items, keys) { it.searchLabels }

        fun search(search: String): List<TargetPickerItem> = when {
            search.isBlank() -> items
            sorted -> multiTermSortedSearch(index, search)
            else -> multiTermSearch(index, search)
        }
    }
}

/** The areas each the last under their floor when the next row is a floor (or there is none). */
private fun List<TargetPickerItem>.markLast(): List<TargetPickerItem> = mapIndexed { index, item ->
    val next = getOrNull(index + 1)
    item.copy(last = next == null || (item.ref.type == TargetType.AREA && next.ref.type == TargetType.FLOOR))
}

private const val TARGET = "ui.components.target-picker"
private val SECTION_TITLES = mapOf(
    TargetType.ENTITY to "entities",
    TargetType.DEVICE to "devices",
    TargetType.AREA to "areas",
    TargetType.LABEL to "labels",
)

/** `entityComboBoxKeys`. */
private val ENTITY_KEYS = listOf(
    FuseKey("entityName", 10.0),
    FuseKey("friendlyName", 8.0),
    FuseKey("deviceName", 7.0),
    FuseKey("areaName", 6.0),
    FuseKey("domainName", 6.0),
    FuseKey("entityId", 3.0),
)

/** `deviceComboBoxKeys`. */
private val DEVICE_KEYS = listOf(
    FuseKey("deviceName", 10.0),
    FuseKey("areaName", 8.0),
    FuseKey("domainName", 4.0),
    FuseKey("domain", 4.0),
    FuseKey("viaDeviceName", 3.0),
    FuseKey("viaDeviceArea", 3.0),
)

/** `areaFloorComboBoxKeys`. */
private val AREA_KEYS = listOf(
    FuseKey("name", 10.0),
    FuseKey("aliases", 8.0),
    FuseKey("floorName", 6.0),
    FuseKey("relatedAreas", 4.0),
    FuseKey("id", 3.0),
)

/** `labelComboBoxKeys`. */
private val LABEL_KEYS = listOf(FuseKey("name", 10.0), FuseKey("description", 5.0), FuseKey("id", 4.0))
