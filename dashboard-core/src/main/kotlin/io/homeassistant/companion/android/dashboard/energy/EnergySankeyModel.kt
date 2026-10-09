package io.homeassistant.companion.android.dashboard.energy

import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.entity.entityContext
import kotlin.math.max

/**
 * The energy sankey: from the grid, solar and the battery to the home (and back to the grid and the battery), then
 * by floor and area to the devices, and what no device accounts for. Port of `HuiEnergySankeyCard.render`
 * (frontend@20260624.6 src/panels/lovelace/cards/energy/hui-energy-sankey-card.ts).
 */
fun HassSnapshot.energySankey(data: EnergyData, groupByFloor: Boolean, groupByArea: Boolean): SankeyData {
    val sums = data.summed()
    val consumption = sums.consumption().total
    val builder = SankeyBuilder(this)
    val home = SankeyNode(HOME, config.locationName.orEmpty(), max(0.0, consumption.usedTotal), PRIMARY, 1)
    builder.nodes += home
    builder.sources(data.prefs.energySources, sums, consumption)
    builder.devices(deviceNodes(data, data.prefs.deviceConsumption), home.value, groupByFloor, groupByArea)
    return SankeyData(builder.nodes, builder.links)
}

/**
 * The water sankey: from the water sources to the home, then by floor and area to the devices, and what no device
 * accounts for. Port of `HuiWaterSankeyCard.render` (frontend@20260624.6
 * src/panels/lovelace/cards/water/hui-water-sankey-card.ts).
 */
fun HassSnapshot.waterSankey(data: EnergyData, groupByFloor: Boolean, groupByArea: Boolean): SankeyData {
    fun growth(id: String) = statisticsSumGrowth(data.stats, listOf(id)) ?: 0.0
    val sources = data.prefs.energySources.filterIsInstance<EnergySource.Utility>().filter {
        it.type ==
            UtilityType.WATER
    }
    // Devices included in another are counted in it already
    val downstream = data.prefs.deviceConsumptionWater.filter {
        it.includedInStat == null
    }.sumOf { growth(it.statConsumption) }
    val supply = sources.sumOf { growth(it.statEnergyFrom) }
    val builder = SankeyBuilder(this)
    val home = SankeyNode(HOME, config.locationName.orEmpty(), max(0.0, max(downstream, supply)), PRIMARY, 1)
    builder.nodes += home
    sources.forEach { source ->
        val value = growth(source.statEnergyFrom)
        if (value < MIN_DEVICE) return@forEach
        val id = "source-${source.statEnergyFrom}"
        val label =
            source.name?.ifEmpty { null }
                ?: statisticLabel(source.statEnergyFrom, data.statsMetadata[source.statEnergyFrom])
        builder.nodes += SankeyNode(id, label, value, SankeyColor.Variable("energy-water-color"), 0)
        builder.links += SankeyLink(id, HOME, value)
    }
    builder.devices(deviceNodes(data, data.prefs.deviceConsumptionWater), home.value, groupByFloor, groupByArea)
    return SankeyData(builder.nodes, builder.links)
}

/** The [devices] with at least 0.01 (kWh or volume), with the device they're included in. */
private fun HassSnapshot.deviceNodes(data: EnergyData, devices: List<DeviceConsumption>): List<DeviceNode> =
    devices.mapIndexedNotNull { index, device ->
        val value = statisticsSumGrowth(data.stats, listOf(device.statConsumption)) ?: 0.0
        if (value < MIN_DEVICE) return@mapIndexedNotNull null
        val label =
            device.name?.ifEmpty { null }
                ?: statisticLabel(device.statConsumption, data.statsMetadata[device.statConsumption])
        DeviceNode(
            SankeyNode(
                id = device.statConsumption,
                label = label,
                value = value,
                color = SankeyColor.Palette(index),
                index = DEVICE_INDEX,
                entityId = device.statConsumption.takeUnless(::isExternalStatistic),
            ),
            device.includedInStat,
        )
    }

/** The nodes and flows of a sankey being built. */
internal class SankeyBuilder(private val hass: HassSnapshot) {
    val nodes = mutableListOf<SankeyNode>()
    val links = mutableListOf<SankeyLink>()

    /** The battery, grid and solar, with their flows to the home and between them. */
    fun sources(sources: List<EnergySource>, sums: EnergySums, consumption: Consumption) {
        fun total(flow: EnergyFlow) = sums.total[flow] ?: 0.0
        if (sources.any { it is EnergySource.Battery }) {
            nodes += node(BATTERY_NODE, "battery", total(EnergyFlow.FROM_BATTERY), "energy-battery-out-color", 0)
            links += SankeyLink(BATTERY_NODE, HOME, consumption.usedBattery)
            nodes += node(BATTERY_IN, "battery", total(EnergyFlow.TO_BATTERY), "energy-battery-in-color", 1)
            if (consumption.gridToBattery > 0) links += SankeyLink(GRID_NODE, BATTERY_IN, consumption.gridToBattery)
            if (consumption.solarToBattery > 0) links += SankeyLink(SOLAR_NODE, BATTERY_IN, consumption.solarToBattery)
        }
        val grids = sources.filterIsInstance<EnergySource.Grid>()
        if (grids.isNotEmpty()) {
            nodes += node(GRID_NODE, "grid", total(EnergyFlow.FROM_GRID), "energy-grid-consumption-color", 0)
            links += SankeyLink(GRID_NODE, HOME, consumption.usedGrid)
        }
        if (sources.any { it is EnergySource.Solar }) {
            nodes += node(SOLAR_NODE, "solar", total(EnergyFlow.SOLAR), "energy-solar-color", 0)
            links += SankeyLink(SOLAR_NODE, HOME, consumption.usedSolar)
        }
        if (!grids.firstOrNull()?.statEnergyTo.isNullOrEmpty()) {
            nodes += node(GRID_RETURN, "grid", total(EnergyFlow.TO_GRID), "energy-grid-return-color", 1)
            if (consumption.batteryToGrid > 0) links += SankeyLink(BATTERY_NODE, GRID_RETURN, consumption.batteryToGrid)
            if (consumption.solarToGrid > 0) links += SankeyLink(SOLAR_NODE, GRID_RETURN, consumption.solarToGrid)
        }
    }

    private fun node(id: String, label: String, value: Double, color: String, index: Int) = SankeyNode(
        id,
        hass.localize("ui.panel.lovelace.cards.energy.energy_distribution.$label"),
        value,
        SankeyColor.Variable(color),
        index,
    )

    /**
     * The devices: from the device they're included in or (grouped by floor and area) from the home, in columns by
     * inclusion, and what of the home's [homeValue] no device accounts for.
     */
    fun devices(devices: List<DeviceNode>, homeValue: Double, groupByFloor: Boolean, groupByArea: Boolean) {
        devices.forEach { device -> device.parent?.let { links += SankeyLink(it, device.node.id) } }
        val topLevel = devices.filter { it.parent == null }.map { it.node }
        flowToDevices(topLevel, groupByFloor, groupByArea)
        val sections =
            deviceSections(
                devices.mapNotNull { d ->
                    d.parent?.let { d.node.id to it }
                }.toMap(),
                devices.map { it.node },
            )
        sections.forEachIndexed { index, section -> section.forEach { nodes += it.copy(index = DEVICE_INDEX + index) } }
        val untracked = homeValue - topLevel.sumOf { it.value }
        if (untracked > 0) {
            nodes += SankeyNode(
                id = UNTRACKED_ID,
                label = hass.localize(
                    "ui.panel.lovelace.cards.energy.energy_devices_detail_graph.untracked_consumption",
                ),
                value = untracked,
                color = SankeyColor.Variable("state-unavailable-color"),
                index = DEVICE_INDEX - 1 + sections.size,
            )
            links += SankeyLink(HOME, UNTRACKED_ID, untracked)
        }
    }

    /**
     * The flows from the home to the devices without a parent: through their floor and area when grouping, straight
     * otherwise.
     */
    private fun flowToDevices(devices: List<SankeyNode>, groupByFloor: Boolean, groupByArea: Boolean) {
        if (!groupByArea && !groupByFloor) {
            devices.forEach { links += SankeyLink(HOME, it.id, it.value) }
            return
        }
        val (areas, floors) = hass.groupByFloorAndArea(devices)
        floors.keys.sortedWith(
            compareByDescending {
                hass.registries.floors[it]?.level?.toDouble()
                    ?: Double.NEGATIVE_INFINITY
            },
        )
            .forEach { floorId ->
                val floorNode = if (floorId == NO_FLOOR ||
                    !groupByFloor
                ) {
                    HOME
                } else {
                    floorNode(floorId, floors.getValue(floorId).value)
                }
                floors.getValue(floorId).areas.forEach { areaId ->
                    val area = areas.getValue(areaId)
                    val target = if (areaId == NO_AREA ||
                        !groupByArea
                    ) {
                        floorNode
                    } else {
                        areaNode(areaId, area.value, floorNode)
                    }
                    area.devices.forEach { links += SankeyLink(target, it.id, it.value) }
                }
            }
    }

    private fun floorNode(floorId: String, value: Double) = "floor_$floorId".also { id ->
        nodes += SankeyNode(id, hass.registries.floors[floorId]?.name.orEmpty(), value, PRIMARY, FLOOR_INDEX)
        links += SankeyLink(HOME, id)
    }

    private fun areaNode(areaId: String, value: Double, floorNode: String) = "area_$areaId".also { id ->
        nodes += SankeyNode(id, hass.registries.areas[areaId]?.name.orEmpty(), value, PRIMARY, AREA_INDEX)
        links += SankeyLink(floorNode, id, value)
    }
}

internal class DeviceNode(val node: SankeyNode, val parent: String?)

internal class AreaGroup(var value: Double = 0.0, val devices: MutableList<SankeyNode> = mutableListOf())

internal class FloorGroup(var value: Double = 0.0, val areas: MutableList<String> = mutableListOf())

/** The devices by area and the areas by floor, with their totals. Port of `_groupByFloorAndArea`. */
internal fun HassSnapshot.groupByFloorAndArea(
    devices: List<SankeyNode>,
): Pair<Map<String, AreaGroup>, Map<String, FloorGroup>> {
    val areas = linkedMapOf(NO_AREA to AreaGroup())
    val floors = linkedMapOf(NO_FLOOR to FloorGroup(areas = mutableListOf(NO_AREA)))
    devices.forEach { device ->
        val context = if (device.id in states) registries.entityContext(device.id) else null
        val area = context?.area
        if (area == null) {
            areas.getValue(NO_AREA).apply {
                value += device.value
                this.devices += device
            }
            return@forEach
        }
        areas.getOrPut(area.areaId) { AreaGroup() }.apply {
            value += device.value
            this.devices += device
        }
        val floor = context.floor
        if (floor != null) {
            floors.getOrPut(floor.floorId) { FloorGroup() }.apply {
                value += device.value
                if (area.areaId !in this.areas) this.areas += area.areaId
            }
        } else {
            floors.getValue(NO_FLOOR).apply {
                value += device.value
                if (area.areaId !in this.areas) this.areas.add(0, area.areaId)
            }
        }
    }
    return areas to floors
}

/**
 * The devices in columns: the top-level parents first, then their children, and so on. Port of
 * `_getDeviceSections`.
 */
internal fun deviceSections(parents: Map<String, String>, devices: List<SankeyNode>): List<List<SankeyNode>> {
    val parentIds = parents.values.toSet()
    val (top, rest) = devices.partition { it.id in parentIds && it.id !in parents }
    if (top.isEmpty()) return listOf(devices)
    val remaining = parents.filterValues { parent -> top.none { it.id == parent } }
    return listOf(top) + deviceSections(remaining, rest)
}

internal const val HOME = "home"
private const val BATTERY_NODE = "battery"
private const val BATTERY_IN = "battery_in"
private const val GRID_NODE = "grid"
private const val GRID_RETURN = "grid_return"
private const val SOLAR_NODE = "solar"
private const val NO_AREA = "no_area"
private const val NO_FLOOR = "no_floor"
private const val FLOOR_INDEX = 2
private const val AREA_INDEX = 3
private const val DEVICE_INDEX = 4
private const val MIN_DEVICE = 0.01
internal val PRIMARY = SankeyColor.Variable("primary-color")
