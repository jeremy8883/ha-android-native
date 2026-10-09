package io.homeassistant.companion.android.dashboard.strategy.home

import io.homeassistant.companion.android.dashboard.derive.deviceName
import io.homeassistant.companion.android.dashboard.entity.AreaEntry
import io.homeassistant.companion.android.dashboard.entity.ENTITY_CATEGORY_NONE
import io.homeassistant.companion.android.dashboard.entity.EntityFilter
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.entity.entityContext
import io.homeassistant.companion.android.dashboard.entity.filterEntities
import io.homeassistant.companion.android.dashboard.entity.findEntities
import io.homeassistant.companion.android.dashboard.strategy.areaTileCard
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The view of one area on the home dashboard: summary sections (lights, climate, security, media), scenes,
 * the remaining entities grouped by device, then automations; or an empty state when there is nothing.
 *
 * Port of `HomeAreaViewStrategy.generate` (frontend@20260624.6
 * src/panels/lovelace/strategies/home/home-area-view-strategy.ts). The strategy config is
 * `{type: "home-area", area, home_panel?}`.
 *
 * @throws IllegalArgumentException when the area is missing or unknown, as upstream
 */
fun HassSnapshot.homeAreaView(areaId: String?, homePanel: Boolean): JsonObject {
    requireNotNull(areaId) { "Area not provided" }
    val area = requireNotNull(registries.areas[areaId]) { "Unknown area" }
    val tile = { entityId: String -> areaTileCard(entityId, area.name, includeFeature = true) }
    val areaEntities = filterEntities(states.keys.toList(), EntityFilter(areas = setOf(areaId)))
    val bySummary = HomeSummary.entries.associateWith { findEntities(areaEntities, it.filters) }
    val sections = summarySections(bySummary, areaId, tile) + entitySections(areaEntities, bySummary, tile)
    if (sections.isEmpty()) return emptyAreaView(area.icon, homePanel)
    // Take the full width with a single section to avoid a narrow header on desktop
    val shown = sections.singleOrNull()?.let { listOf(JsonObject(it + ("column_span" to JsonPrimitive(2)))) }
        ?: sections
    return buildJsonObject {
        put("type", "sections")
        putJsonObject("header") { put("badges_position", "bottom") }
        // Between 2 and 3 columns; the max defines the width of the header
        put("max_columns", shown.size.coerceIn(2, MAX_COLUMNS))
        put("sections", JsonArray(shown))
        put("badges", areaBadges(area))
    }
}

/** The area's temperature and humidity, as badges. */
private fun areaBadges(area: AreaEntry) = buildJsonArray {
    area.temperatureEntityId?.let {
        addJsonObject {
            put("entity", it)
            put("type", "entity")
            put("color", "red")
        }
    }
    area.humidityEntityId?.let {
        addJsonObject {
            put("entity", it)
            put("type", "entity")
            put("color", "indigo")
        }
    }
}

/** A section for each summary (lights, climate, security, media) the area has entities for. */
private fun HassSnapshot.summarySections(
    bySummary: Map<HomeSummary, List<String>>,
    areaId: String,
    tile: (String) -> JsonObject,
): List<JsonObject> = listOfNotNull(
    bySummary.getValue(HomeSummary.LIGHT).takeIf { it.isNotEmpty() }?.let { lights ->
        gridSection(listOf(lightsHeading(lights, areaId)) + lights.map(tile))
    },
    bySummary.getValue(HomeSummary.CLIMATE).takeIf { it.isNotEmpty() }?.let { climate ->
        gridSection(listOf(summaryHeading(HomeSummary.CLIMATE, panelPath = "climate")) + climate.map(tile))
    },
    bySummary.getValue(HomeSummary.SECURITY).takeIf { it.isNotEmpty() }?.let { security ->
        gridSection(listOf(summaryHeading(HomeSummary.SECURITY, panelPath = "security")) + security.map(tile))
    },
    bySummary.getValue(HomeSummary.MEDIA_PLAYERS).takeIf { it.isNotEmpty() }?.let { media ->
        val heading = heading(
            HomeSummary.MEDIA_PLAYERS.label(localize),
            HomeSummary.MEDIA_PLAYERS.icon,
            navigate("media-players?historyBack=1"),
        )
        gridSection(listOf(heading) + media.map(tile))
    },
)

/** Scenes, the entities of no summary by device, then automations. */
private fun HassSnapshot.entitySections(
    areaEntities: List<String>,
    bySummary: Map<HomeSummary, List<String>>,
    tile: (String) -> JsonObject,
): List<JsonObject> {
    val summaryEntities = bySummary.filterKeys { it != HomeSummary.MAINTENANCE }.values.flatten().toSet()
    val scenes = filterEntities(
        areaEntities,
        EntityFilter(domains = setOf("scene"), entityCategories = setOf(ENTITY_CATEGORY_NONE)),
    )
    val automations = filterEntities(
        areaEntities,
        EntityFilter(domains = setOf("automation"), entityCategories = setOf(ENTITY_CATEGORY_NONE)),
    )
    val otherEntities = areaEntities.filter { it !in summaryEntities && it !in scenes && it !in automations }
    val deviceSections = deviceSections(otherEntities, tile)
    val adminLink = { path: String -> navigate(path).takeIf { user?.isAdmin == true } }
    return listOfNotNull(
        scenes.takeIf { it.isNotEmpty() }?.let {
            val heading = heading(
                localize("ui.panel.lovelace.strategy.home.scenes"),
                "mdi:palette",
                adminLink("/config/scene/dashboard"),
            )
            gridSection(listOf(heading) + it.map(tile))
        },
        DEVICES_DIVIDER.takeIf { deviceSections.isNotEmpty() },
    ) + deviceSections + listOfNotNull(
        // Automations come last
        automations.takeIf { it.isNotEmpty() }?.let {
            val heading = heading(
                localize("ui.panel.lovelace.strategy.home.automations"),
                "mdi:robot",
                adminLink("/config/automation/dashboard"),
            )
            gridSection(listOf(heading) + it.map(tile))
        },
    )
}

/** A full-width empty subtitle heading before the device sections. */
private val DEVICES_DIVIDER = buildJsonObject {
    put("type", "grid")
    put("column_span", MAX_COLUMNS)
    putJsonArray("cards") {
        addJsonObject {
            put("type", "heading")
            put("heading_style", "subtitle")
            put("heading", "")
        }
    }
}

/** One section per device (plus one for entities without a device), with primary entities as tiles. */
private fun HassSnapshot.deviceSections(otherEntities: List<String>, tile: (String) -> JsonObject): List<JsonObject> {
    val byDevice = linkedMapOf<String, MutableList<String>>()
    val unassigned = mutableListOf<String>()
    for (entityId in otherEntities) {
        if (entityId !in states) continue
        val device = registries.entityContext(entityId).device
        if (device == null) unassigned += entityId else byDevice.getOrPut(device.id) { mutableListOf() } += entityId
    }
    if (unassigned.isNotEmpty()) byDevice[UNASSIGNED_DEVICE] = unassigned

    val batteryFilter = EntityFilter(domains = setOf("sensor"), deviceClasses = setOf("battery"))
    val primaryFilter = EntityFilter(entityCategories = setOf(ENTITY_CATEGORY_NONE))

    return byDevice.mapNotNull { (deviceId, entityIds) ->
        val battery = filterEntities(entityIds, batteryFilter)
        val primary = entityIds.filter { it !in battery }.let { filterEntities(it, primaryFilter) }
        if (primary.isEmpty()) return@mapNotNull null

        val device = registries.devices[deviceId]
        val name = if (device != null) {
            device.deviceName()?.ifEmpty { null } ?: localize("ui.panel.lovelace.strategy.home.unnamed_device")
        } else {
            localize("ui.panel.lovelace.strategy.home.others")
        }
        val heading = buildJsonObject {
            put("type", "heading")
            put("heading", name)
            if (user?.isAdmin == true && device != null) putTapAction(navigate("/config/devices/device/${device.id}"))
            putJsonArray("badges") {
                battery.take(1).forEach { entityId ->
                    addJsonObject {
                        put("entity", entityId)
                        put("type", "entity")
                        putJsonObject("tap_action") { put("action", "more-info") }
                    }
                }
            }
        }
        val tiles = primary.map { entityId ->
            JsonObject(tile(entityId) + ("name" to buildJsonObject { put("type", "entity") }))
        }
        gridSection(listOf(heading) + tiles)
    }
}

/** The lights heading, with buttons to turn all of the area's lights on or off. */
private fun HassSnapshot.lightsHeading(lights: List<String>, areaId: String): JsonObject {
    val anyOn = buildJsonObject {
        put("condition", "or")
        putJsonArray("conditions") {
            lights.forEach { entityId ->
                addJsonObject {
                    put("condition", "state")
                    put("entity", entityId)
                    put("state", "on")
                }
            }
        }
    }
    fun powerButton(textKey: String, action: String, visibility: JsonObject, color: String?) = buildJsonObject {
        put("type", "button")
        put("icon", "mdi:power")
        color?.let { put("color", it) }
        put("text", localize(textKey))
        putJsonObject("tap_action") {
            put("action", "perform-action")
            put("perform_action", action)
            putJsonObject("target") { put("area_id", areaId) }
        }
        putJsonArray("visibility") { add(visibility) }
    }
    val noneOn = buildJsonObject {
        put("condition", "not")
        putJsonArray("conditions") { add(anyOn) }
    }
    return buildJsonObject {
        put("type", "heading")
        put("heading", HomeSummary.LIGHT.label(localize))
        put("icon", HomeSummary.LIGHT.icon)
        if ("light" in panels) putTapAction(navigate("/light?historyBack=1"))
        putJsonArray("badges") {
            add(powerButton("ui.panel.lovelace.strategy.light.off", "light.turn_on", noneOn, color = null))
            add(powerButton("ui.panel.lovelace.strategy.light.on", "light.turn_off", anyOn, color = "orange"))
        }
    }
}

/** A summary heading linking to its panel when that panel exists. */
private fun HassSnapshot.summaryHeading(summary: HomeSummary, panelPath: String): JsonObject =
    heading(summary.label(localize), summary.icon, navigate("/$panelPath?historyBack=1").takeIf { panelPath in panels })

private fun HassSnapshot.emptyAreaView(areaIcon: String?, homePanel: Boolean): JsonObject = buildJsonObject {
    put("type", "panel")
    putJsonArray("cards") {
        addJsonObject {
            put("type", "empty-state")
            put("icon", areaIcon?.ifEmpty { null } ?: "mdi:shape-square-rounded-plus")
            put("icon_color", "primary")
            put("content_only", true)
            put("title", localize("ui.panel.lovelace.strategy.home-area.no_devices_title"))
            put("content", localize("ui.panel.lovelace.strategy.home-area.no_devices_content"))
            if (homePanel && user?.isAdmin == true) {
                putJsonArray("buttons") {
                    addJsonObject {
                        put("icon", "mdi:plus")
                        put("text", localize("ui.panel.lovelace.strategy.home-area.no_devices_add_device"))
                        put("appearance", "plain")
                        put("variant", "brand")
                        putJsonObject("tap_action") {
                            put("action", "fire-dom-event")
                            putJsonObject("home_panel") { put("type", "add_integration") }
                        }
                    }
                    addJsonObject {
                        put("icon", "mdi:home-plus")
                        put("text", localize("ui.panel.lovelace.strategy.home-area.no_devices_assign_device"))
                        put("appearance", "plain")
                        put("variant", "brand")
                        putTapAction(navigate("/home/other-devices"))
                    }
                }
            }
        }
    }
}

private const val UNASSIGNED_DEVICE = "unassigned"

/** The most columns an area view has (and the span of a full-width section). */
private const val MAX_COLUMNS = 3
