package io.homeassistant.companion.android.dashboard.energy

import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.string
import kotlin.math.ceil
import kotlin.math.max

// The live sankeys of the energy dashboard's Now tab, from the current power and flow rates. Ports of `render` in
// hui-power-sankey-card.ts (src/panels/lovelace/cards/energy/) and hui-water-flow-sankey-card.ts
// (src/panels/lovelace/cards/water/), frontend@20260624.6.

/**
 * The power sankey: from the grid, solar and the battery to the home (and back to the grid and the battery) right
 * now, then by floor and area to the devices, and what no device accounts for. Devices using under a thousandth of
 * the home's power are grouped as "Other" by parent.
 */
fun HassSnapshot.powerSankey(prefs: EnergyPreferences, groupByFloor: Boolean, groupByArea: Boolean): SankeyData {
    val flows = powerFlows(prefs)
    val builder = SankeyBuilder(this)
    val home = SankeyNode(HOME, config.locationName.orEmpty(), flows.usedTotal, PRIMARY, 1)
    builder.nodes += home
    builder.powerSources(flows)
    val devices = rateDevices(prefs.deviceConsumption, flows.usedTotal * MIN_SHARE, HOME) { powerWatts(it) }
    val sections = builder.placeDevices(devices.nodes, HOME, groupByFloor, groupByArea)
    val untracked = home.value - devices.topLevel
    if (untracked > MIN_UNTRACKED) builder.untracked(untracked, HOME, sections)
    return SankeyData(builder.nodes, builder.links)
}

/**
 * The water flow sankey: from the water sources (or the only one, without a home node) to the devices right now, by
 * floor and area, and what no device accounts for. Without sources, the home takes the devices' total.
 */
fun HassSnapshot.waterFlowSankey(prefs: EnergyPreferences, groupByFloor: Boolean, groupByArea: Boolean): SankeyData {
    fun flow(id: String) = flowRateLitresPerMinute(id) ?: 0.0
    val sources = prefs.energySources.filterIsInstance<EnergySource.Utility>()
        .filter { it.type == UtilityType.WATER }
        .mapNotNull { it.statRate?.ifEmpty { null } }
    val total = if (sources.isEmpty()) {
        // Devices included in another are counted in it already
        prefs.deviceConsumptionWater.filter { it.includedInStat.isNullOrEmpty() }
            .sumOf { device -> device.statRate?.ifEmpty { null }?.let(::flow) ?: 0.0 }
    } else {
        sources.sumOf { max(flow(it), 0.0) }
    }
    val builder = SankeyBuilder(this)
    val waterLabel = localize("ui.panel.lovelace.cards.energy.energy_distribution.water")
    fun sourceNode(id: String, statRate: String, value: Double) =
        SankeyNode(id, entityLabel(statRate).ifEmpty { waterLabel }, value, WATER_COLOR, 0, entityId = statRate)
    val root = sources.singleOrNull()?.also { builder.nodes += sourceNode(it, it, max(0.0, flow(it))) } ?: run {
        sources.forEach { statRate ->
            val value = flow(statRate)
            if (value <= 0) return@forEach
            builder.nodes += sourceNode("water_source_$statRate", statRate, value)
            builder.links += SankeyLink("water_source_$statRate", HOME)
        }
        builder.nodes += SankeyNode(HOME, config.locationName.orEmpty(), max(0.0, total), PRIMARY, 1)
        HOME
    }
    val devices = rateDevices(prefs.deviceConsumptionWater, total * MIN_SHARE, root, ::flowRateLitresPerMinute)
    val sections = builder.placeDevices(devices.nodes, root, groupByFloor, groupByArea)
    val untracked = total - devices.topLevel
    if (untracked > MIN_UNTRACKED) builder.untracked(untracked, root, sections)
    return SankeyData(builder.nodes, builder.links)
}

/** The battery, grid and solar sources there is power from or to, with the flows between them. */
private fun SankeyBuilder.powerSources(flows: PowerFlows) {
    val consumption = flows.consumption
    if (flows.fromBattery > 0) {
        nodes += node(BATTERY_NODE, "battery", flows.fromBattery, "energy-battery-out-color", 0)
        links += SankeyLink(BATTERY_NODE, HOME)
    }
    if (flows.toBattery > 0) {
        nodes += node(BATTERY_IN_NODE, "battery", flows.toBattery, "energy-battery-in-color", 1)
        if (consumption.gridToBattery > 0) links += SankeyLink(GRID_NODE, BATTERY_IN_NODE)
        if (consumption.solarToBattery > 0) links += SankeyLink(SOLAR_NODE, BATTERY_IN_NODE)
    }
    if (flows.fromGrid > 0) {
        nodes += node(GRID_NODE, "grid", flows.fromGrid, "energy-grid-consumption-color", 0)
        links += SankeyLink(GRID_NODE, HOME)
    }
    if (flows.solar > 0) {
        nodes += node(SOLAR_NODE, "solar", flows.solar, "energy-solar-color", 0)
        links += SankeyLink(SOLAR_NODE, HOME)
    }
    if (flows.toGrid > 0) {
        nodes += node(GRID_RETURN_NODE, "grid", flows.toGrid, "energy-grid-return-color", 1)
        if (consumption.batteryToGrid > 0) links += SankeyLink(BATTERY_NODE, GRID_RETURN_NODE)
        if (consumption.solarToGrid > 0) links += SankeyLink(SOLAR_NODE, GRID_RETURN_NODE)
    }
}

/** The device nodes, and how much of the root's value the ones without a parent account for. */
private class RateDevices(val nodes: List<DeviceNode>, val topLevel: Double)

/** A device under the threshold, kept to be shown alone or grouped with its siblings. */
private class SmallDevice(
    val statRate: String,
    val name: String?,
    val value: Double,
    val parent: String?,
    val index: Int,
)

/**
 * The [devices] with a rate, under the closest ancestor that's shown; those under [threshold] are grouped as "Other"
 * by parent ([root] when they have none) unless alone.
 */
private fun HassSnapshot.rateDevices(
    devices: List<DeviceConsumption>,
    threshold: Double,
    root: String,
    rate: (String) -> Double?,
): RateDevices {
    val parents = RateParents(devices) { (rate(it) ?: 0.0) >= threshold }
    val nodes = mutableListOf<DeviceNode>()
    var topLevel = 0.0
    val small = linkedMapOf<String, MutableList<SmallDevice>>()
    fun add(device: SmallDevice) {
        nodes += DeviceNode(rateNode(device), device.parent)
        if (device.parent == null) topLevel += device.value
    }
    devices.forEachIndexed { index, device ->
        val statRate = device.statRate?.ifEmpty { null } ?: return@forEachIndexed
        val value = rate(statRate) ?: 0.0
        val entry = SmallDevice(statRate, device.name, value, parents.of(device), index)
        if (value < threshold) small.getOrPut(entry.parent ?: root) { mutableListOf() } += entry else add(entry)
    }
    small.forEach { (parent, group) ->
        val total = group.sumOf { it.value }
        when {
            total <= 0 -> Unit
            group.size == 1 -> add(group.single())
            else -> {
                val other = SankeyNode(
                    id = "other_$parent",
                    label = localize("ui.panel.lovelace.cards.energy.energy_devices_detail_graph.other"),
                    value = ceil(total),
                    color = UNAVAILABLE,
                    index = DEVICE_INDEX,
                )
                nodes += DeviceNode(other, parent.takeIf { it != root })
                if (parent == root) topLevel += total
            }
        }
    }
    return RateDevices(nodes, topLevel)
}

private fun HassSnapshot.rateNode(device: SmallDevice) = SankeyNode(
    id = device.statRate,
    label = device.name?.ifEmpty { null } ?: entityLabel(device.statRate),
    value = device.value,
    color = SankeyColor.Palette(device.index),
    index = DEVICE_INDEX,
    entityId = device.statRate,
)

/**
 * Which shown device each device is under: the closest ancestor (through `included_in_stat`) whose rate is [shown].
 * Port of `findEffectiveParent`.
 */
private class RateParents(devices: List<DeviceConsumption>, shown: (String) -> Boolean) {
    private val byConsumption = devices.associateBy { it.statConsumption }
    private val shownRates = devices.mapNotNull { it.statRate?.ifEmpty { null } }.filter(shown).toSet()

    fun of(device: DeviceConsumption): String? {
        val seen = mutableSetOf<String>()
        var current = device.includedInStat?.ifEmpty { null }
        var found: String? = null
        while (found == null && current != null && seen.add(current)) {
            val parent = byConsumption[current] ?: break
            found = parent.statRate?.takeIf { it in shownRates }
            current = parent.includedInStat?.ifEmpty { null }
        }
        return found
    }
}

/** The entity's friendly name, or its id. */
private fun HassSnapshot.entityLabel(entityId: String): String =
    states[entityId]?.attributes?.string("friendly_name")?.ifEmpty { null } ?: entityId

private const val MIN_SHARE = 0.001
private const val MIN_UNTRACKED = 1.0
private val WATER_COLOR = SankeyColor.Variable("energy-water-color")
