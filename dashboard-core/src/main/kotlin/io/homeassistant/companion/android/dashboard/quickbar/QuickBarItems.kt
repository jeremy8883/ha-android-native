package io.homeassistant.companion.android.dashboard.quickbar

import io.homeassistant.companion.android.dashboard.display.caseInsensitiveStringCompare
import io.homeassistant.companion.android.dashboard.model.array
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import io.homeassistant.companion.android.dashboard.search.FuseIndex
import io.homeassistant.companion.android.dashboard.search.FuseKey
import io.homeassistant.companion.android.dashboard.search.multiTermSortedSearch
import io.homeassistant.companion.android.dashboard.target.TargetIcon
import io.homeassistant.companion.android.dashboard.target.TargetPickerItem
import io.homeassistant.companion.android.dashboard.target.TargetPickerSource
import io.homeassistant.companion.android.dashboard.target.deviceItems
import io.homeassistant.companion.android.dashboard.target.entityItems

// The quick bar's list. Port of `_getItemsMemoized` and `_filterGroup` of `ha-quick-bar` (frontend@20260624.6
// src/dialogs/quick-bar/ha-quick-bar.ts), with `getEntities`, `getDevices` (as the target picker's, unfiltered) and
// `getAreas` (src/data/area/area_picker.ts).

/** A row of the quick bar's list. */
sealed interface QuickBarRow {
    /** A section's title, shown when every section is listed. */
    data class Title(val text: String) : QuickBarRow

    /** An item to choose. */
    data class Item(val item: QuickBarItem) : QuickBarRow

    /** That nothing matches the search (or nothing is offered, without one). */
    data class Empty(val text: String) : QuickBarRow
}

/**
 * The items the quick bar offers, built once, with their search indexes; [rows] lists them for a search and
 * section. Commands, devices and areas are for admins only.
 */
class QuickBarItems(private val source: QuickBarSource) {
    /** The sections the user may choose, in the order of their chips. */
    val sections: List<QuickBarSection> = if (source.isAdmin) {
        QuickBarSection.entries
    } else {
        listOf(QuickBarSection.NAVIGATE, QuickBarSection.ENTITY)
    }

    private val groups: Map<QuickBarSection, Group> by lazy {
        val picker = TargetPickerSource(
            hass = source.hass,
            entityFilter = null,
            configEntryDomains = source.configEntryDomains,
        )
        sections.associateWith { section ->
            when (section) {
                QuickBarSection.NAVIGATE -> Group(source.navigationItems(), NAVIGATE_KEYS)
                QuickBarSection.COMMAND -> Group(source.commandItems(), COMMAND_KEYS)
                // Searched in the server's order (upstream sorts them only when listing them all)
                QuickBarSection.ENTITY -> Group(
                    items = picker.entityItems(emptyList()).map(::entityItem),
                    keys = ENTITY_KEYS,
                    searchOrder = source.hass.states.keys.toList(),
                )
                QuickBarSection.DEVICE -> Group(
                    items = picker.deviceItems(emptyList()).map(::deviceItem),
                    keys = DEVICE_KEYS,
                    searchOrder = source.hass.registries.devices.keys.toList(),
                )
                QuickBarSection.AREA -> Group(source.areaItems(), AREA_KEYS)
            }
        }
    }

    /**
     * The rows for [search] in [section] (every section when `null`): each section's matching items under its title,
     * the best matches first, or why there are none.
     */
    fun rows(search: String, section: QuickBarSection?): List<QuickBarRow> {
        val rows = sections.filter { section == null || it == section }.flatMap { shown ->
            val found = groups.getValue(shown).search(search)
            if (found.isEmpty()) {
                emptyList()
            } else {
                listOfNotNull(QuickBarRow.Title(sectionTitle(shown)).takeIf { section == null }) +
                    found.map { QuickBarRow.Item(it) }
            }
        }
        if (rows.isNotEmpty()) return rows
        val empty = if (search.isNotBlank()) {
            source.hass.localize("$QUICK_BAR.nothing_found")
        } else {
            source.hass.localize(NO_ITEMS)
        }
        return listOf(QuickBarRow.Empty(empty))
    }

    /** A section's title and the label of its chip. */
    fun sectionTitle(section: QuickBarSection): String = source.hass.localize(
        when (section) {
            QuickBarSection.NAVIGATE -> "$QUICK_BAR.navigate_title"
            QuickBarSection.COMMAND -> "$QUICK_BAR.commands_title"
            QuickBarSection.ENTITY -> "$TARGET_TYPES.entities"
            QuickBarSection.DEVICE -> "$TARGET_TYPES.devices"
            QuickBarSection.AREA -> "$TARGET_TYPES.areas"
        },
    )

    private fun entityItem(item: TargetPickerItem): QuickBarItem = QuickBarItem(
        id = "entity$SEPARATOR${item.ref.id}",
        section = QuickBarSection.ENTITY,
        primary = item.primary,
        secondary = item.secondary,
        domainName = item.domainName,
        icon = QuickBarIcon.Entity(item.ref.id),
        action = QuickBarAction.MoreInfo(item.ref.id),
        searchLabels = item.searchLabels,
        sortingLabel = item.sortingLabel,
    )

    /** A device, drawn with its integration's icon (`ha-domain-icon`), else the devices icon. */
    private fun deviceItem(item: TargetPickerItem): QuickBarItem = QuickBarItem(
        id = "device$SEPARATOR${item.ref.id}",
        section = QuickBarSection.DEVICE,
        primary = item.primary,
        secondary = item.secondary,
        domainName = item.domainName,
        icon = (item.icon as? TargetIcon.Brand)?.let { source.domainIcon(it.domain) }
            ?: QuickBarIcon.Plain(DEVICES_ICON),
        action = QuickBarAction.Navigate("/config/devices/device/${item.ref.id}"),
        searchLabels = item.searchLabels,
        sortingLabel = item.sortingLabel,
    )

    /**
     * A section's [items] as listed without a search, and their search index, over the items in [searchOrder] (by
     * the id after the section's prefix) when given, as ties keep that order.
     */
    private class Group(val items: List<QuickBarItem>, keys: List<FuseKey>, searchOrder: List<String>? = null) {
        private val index = FuseIndex(
            searchOrder?.let { order ->
                val position = order.withIndex().associate { (index, id) -> id to index }
                items.sortedBy { position[it.id.substringAfter(SEPARATOR)] }
            } ?: items,
            keys,
        ) { it.searchLabels }

        fun search(search: String): List<QuickBarItem> =
            if (search.isBlank()) items else multiTermSortedSearch(index, search)
    }
}

/**
 * Port of `getAreas`, unfiltered: every area in registry order, named and with its floor, its icon or the area
 * icon.
 */
private fun QuickBarSource.areaItems(): List<QuickBarItem> = hass.registries.areas.values.map { area ->
    val name = area.name.trim()
    val floorName = area.floorId?.let { hass.registries.floors[it] }?.name?.trim()?.ifEmpty { null }
    val primary = name.ifEmpty { area.areaId }
    QuickBarItem(
        id = "area$SEPARATOR${area.areaId}",
        section = QuickBarSection.AREA,
        primary = primary,
        secondary = floorName,
        icon = QuickBarIcon.Plain(area.icon ?: AREA_ICON),
        action = QuickBarAction.Navigate("/config/areas/area/${area.areaId}"),
        searchLabels = mapOf(
            "primary" to primary,
            "secondary" to floorName,
            "areaId" to area.areaId,
            "aliases" to area.json.array("aliases")?.mapNotNull { it.stringOrNull }?.joinToString(" "),
        ),
    )
}

/** Ordered by [QuickBarItem.sortingLabel], as the frontend's collator orders them. */
internal fun List<QuickBarItem>.sortedBySortingLabel(): List<QuickBarItem> =
    sortedWith { a, b -> caseInsensitiveStringCompare(a.sortingLabel.orEmpty(), b.sortingLabel.orEmpty()) }

private const val SEPARATOR = "________"
private const val TARGET_TYPES = "ui.components.target-picker.type"
private const val NO_ITEMS = "ui.components.combo-box.no_items"
private const val DEVICES_ICON = "mdi:devices"
private const val AREA_ICON = "mdi:texture-box"

/** `navigateComboBoxKeys`. */
private val NAVIGATE_KEYS = listOf(FuseKey("primary", 10.0), FuseKey("path", 8.0), FuseKey("secondary", 6.0))

/** `commandComboBoxKeys`. */
private val COMMAND_KEYS = listOf(FuseKey("primary", 10.0), FuseKey("domain", 8.0), FuseKey("secondary", 6.0))

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

/** `areaComboBoxKeys`. */
private val AREA_KEYS = listOf(
    FuseKey("primary", 10.0),
    FuseKey("aliases", 8.0),
    FuseKey("secondary", 6.0),
    FuseKey("domain", 4.0),
    FuseKey("areaId", 2.0),
)
