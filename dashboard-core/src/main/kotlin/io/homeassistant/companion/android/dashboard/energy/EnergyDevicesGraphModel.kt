package io.homeassistant.companion.android.dashboard.energy

import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import java.math.BigDecimal
import kotlin.math.min

/**
 * The devices' consumption over the period, largest first: as bars, or as a donut with what no device accounts for
 * and the total in the middle. Port of `HuiEnergyDevicesGraphCard._getStatistics` (frontend@20260624.6
 * src/panels/lovelace/cards/energy/hui-energy-devices-graph-card.ts).
 *
 * @property slices the devices drawn: the [legend]'s but the hidden ones
 * @property total the donut's total, without the hidden devices, `null` for bars
 * @property legend the donut's legend: every device, hidden or not, at most `max_devices`
 */
data class EnergyDevicesGraphModel(
    val slices: List<DeviceSlice>,
    val compare: Boolean,
    val total: Double?,
    val legend: List<DeviceSlice> = slices,
)

/**
 * A device's consumption (without the devices included in it), and the compared period's.
 *
 * @property colorIndex the device's index in the graph palette, `null` for the untracked consumption
 */
data class DeviceSlice(
    val id: String,
    val name: String,
    val value: Double,
    val valueText: String,
    val compareValue: Double?,
    val colorIndex: Int?,
)

/** How the devices graph is drawn. */
enum class DevicesChartType(val value: String) {
    BAR("bar"),
    PIE("pie"),
}

/**
 * The devices of [data] drawn as [type], at most [DevicesGraphOptions.maxDevices], without compound devices when
 * [DevicesGraphOptions.hideCompound], without the [hidden] ones (`_hiddenStats`, by statistic id).
 */
fun HassSnapshot.energyDevicesGraph(
    data: EnergyData,
    type: DevicesChartType,
    options: DevicesGraphOptions = DevicesGraphOptions(),
    hidden: Set<String> = emptySet(),
): EnergyDevicesGraphModel {
    val devices = data.prefs.deviceConsumption
    val compound = devices.mapNotNull { it.includedInStat }.toSet()
    fun totals(stats: Statistics) = devices.associate {
        it.statConsumption to
            (statisticsSumGrowth(stats, listOf(it.statConsumption)) ?: 0.0)
    }
    val totals = totals(data.stats)
    val compareTotals = data.statsCompare?.let(::totals)
    fun withoutChildren(id: String, of: Map<String, Double>): Double {
        val value = of.getValue(id)
        val children = devices.filter { it.includedInStat == id }.sumOf { of.getValue(it.statConsumption) }
        return value - min(value, children)
    }
    val slices = devices.mapIndexedNotNull { index, device ->
        val id = device.statConsumption
        if (options.hideCompound && id in compound) return@mapIndexedNotNull null
        val value = if (options.hideCompound) totals.getValue(id) else withoutChildren(id, totals)
        DeviceSlice(
            id = id,
            name = deviceName(device, data, id in compound),
            value = value,
            valueText = kwh(value),
            compareValue = compareTotals?.let { withoutChildren(id, it) },
            colorIndex = index,
        )
    }.toMutableList()
    val total = if (type == DevicesChartType.PIE) {
        addUntracked(data, slices)
        slices.filter { it.id !in hidden }.sumOf { it.value }
    } else {
        null
    }
    slices.sortByDescending { it.value }
    val legend = options.maxDevices?.takeIf { it > 0 }?.let { slices.take(it) } ?: slices
    return EnergyDevicesGraphModel(
        slices = legend.filter { it.id !in hidden },
        compare = compareTotals != null,
        total = total,
        legend = legend,
    )
}

/** The devices graph card's options. */
data class DevicesGraphOptions(val maxDevices: Int? = null, val hideCompound: Boolean = false)

/** Adds what no device accounts for to the donut. */
private fun HassSnapshot.addUntracked(data: EnergyData, slices: MutableList<DeviceSlice>) {
    val sums = data.summed()
    val showUntracked = listOf(EnergyFlow.FROM_GRID, EnergyFlow.SOLAR, EnergyFlow.FROM_BATTERY).any {
        it in sums.byStart
    }
    val untracked = if (showUntracked) sums.consumption().total.usedTotal - slices.sumOf { it.value } else 0.0
    if (untracked > 0) {
        val compareUntracked = data.statsCompare?.let {
            data.summed(compare = true).consumption().total.usedTotal - slices.sumOf { s -> s.compareValue ?: 0.0 }
        }
        slices += DeviceSlice(
            id = UNTRACKED_ID,
            name = localize("$DEVICES.untracked_consumption"),
            value = untracked,
            valueText = kwh(untracked),
            compareValue = compareUntracked?.takeIf { it > 0 },
            colorIndex = null,
        )
    }
}

private fun HassSnapshot.deviceName(device: DeviceConsumption, data: EnergyData, compound: Boolean): String {
    val base =
        device.name?.ifEmpty { null }
            ?: statisticLabel(device.statConsumption, data.statsMetadata[device.statConsumption])
    return if (compound) "$base (${localize("$DEVICES.untracked")})" else base
}

private fun HassSnapshot.kwh(value: Double) = formats.number(BigDecimal.valueOf(value), 0, 2) + " kWh"

private const val DEVICES = "ui.panel.lovelace.cards.energy.energy_devices_graph"
