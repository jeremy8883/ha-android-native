package io.homeassistant.companion.android.dashboard.energy

import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import kotlin.math.max

/**
 * The devices' consumption by period: a bar per device (without the devices included in it), then what no device
 * accounts for. Port of `generateEnergyDevicesDetailGraphData` (frontend@20260624.6
 * src/panels/lovelace/cards/energy/energy-devices-detail-graph-data.ts).
 *
 * Device series have the kind [DEVICE_KIND] and the graph palette's index as colour; untracked consumption has the
 * kind [UNTRACKED_KIND].
 */
fun HassSnapshot.energyDevicesDetailGraph(data: EnergyData, maxDevices: Int?): EnergyBarChart {
    val devices = data.prefs.deviceConsumption
    val children = devices.mapNotNull { device -> device.includedInStat?.let { it to device.statConsumption } }
        .groupBy({ it.first }, { it.second })
    val growth = devices.associate {
        it.statConsumption to
            (statisticsSumGrowth(data.stats, listOf(it.statConsumption)) ?: 0.0)
    }
    val sorted = devices.map { it.statConsumption }.sortedByDescending { id ->
        children[id].orEmpty().fold(growth.getValue(id)) { acc, child -> acc - growth.getValue(child) }
    }
    val context = DetailContext(this, data, children, sorted, maxDevices)
    val sums = data.summed()
    val showUntracked = listOf(EnergyFlow.FROM_GRID, EnergyFlow.SOLAR, EnergyFlow.FROM_BATTERY).any {
        it in sums.byStart
    }
    val compare = data.statsCompare?.let { stats ->
        val series = context.deviceSeries(stats, compare = true)
        series +
            if (showUntracked) {
                context.untracked(
                    series,
                    data.summed(compare = true).consumption(),
                    compare = true,
                )
            } else {
                emptyList()
            }
    }.orEmpty()
    val main = context.deviceSeries(data.stats, compare = false)
    val untracked = if (showUntracked) context.untracked(main, sums.consumption(), compare = false) else emptyList()
    val all = compare + main + untracked
    val values = all.flatMap { s -> s.points.map { it.y } }
    val (xMin, xMax) = barChartRange(data, formats.zone)
    return EnergyBarChart(
        series = all,
        xMin = xMin,
        xMax = xMax,
        period = suggestedPeriod(data.period, fine = false),
        yFractionDigits = yAxisFractionDigits(
            values.minOrNull() ?: Double.POSITIVE_INFINITY,
            values.maxOrNull() ?: Double.NEGATIVE_INFINITY,
        ),
        unit = KWH,
        compare = data.comparePeriod != null,
        showCompareYear = data.comparePeriod?.let { it.start.year != data.period.start.year } == true,
    )
}

/** The kind of a device's series, coloured by the graph palette at its colour index. */
const val DEVICE_KIND = "device"

/** The kind of the untracked consumption's series. */
const val UNTRACKED_KIND = "untracked"

/** The id of the untracked consumption series, and of the over-reported one. */
const val UNTRACKED_ID = "untracked"
const val OVER_REPORTED_ID = "untracked-negative"

private class DetailContext(
    val hass: HassSnapshot,
    val data: EnergyData,
    val children: Map<String, List<String>>,
    val sorted: List<String>,
    val maxDevices: Int?,
) {
    private val period = suggestedPeriod(data.period, fine = false)
    private val transform = compareTransform(data, hass.formats.zone)

    /** A series per device, without its children's consumption, in the order of their consumption. */
    fun deviceSeries(statistics: Statistics, compare: Boolean): List<EnergyBarSeries> {
        val series = data.prefs.deviceConsumption.mapIndexedNotNull { index, device ->
            val order = sorted.indexOf(device.statConsumption)
            if (maxDevices != null && maxDevices > 0 && order >= maxDevices) return@mapIndexedNotNull null
            EnergyBarSeries(
                id = (if (compare) COMPARE_PREFIX else "") + "${device.statConsumption}-$order",
                name = name(device),
                kind = DEVICE_KIND,
                colorIndex = index,
                compare = compare,
                order = order,
                points = points(statistics, device.statConsumption, compare),
            )
        }
        return sorted.mapNotNull { id ->
            series.firstOrNull {
                it.id.removePrefix(COMPARE_PREFIX).substringBeforeLast('-') ==
                    id
            }
        }
    }

    private fun points(statistics: Statistics, statId: String, compare: Boolean): List<EnergyBarPoint> {
        val childChanges = children[statId].orEmpty().map { child ->
            statistics[child].orEmpty().reversed().associate { it.start to (it.change ?: 0.0) }
        }
        val center = period == StatisticPeriod.HOUR || period == StatisticPeriod.FIVE_MINUTES
        val move: (Long) -> Long = if (compare) transform else { time -> time }
        var previous: Long? = null
        return statistics[statId].orEmpty().mapNotNull { point ->
            val change = point.change?.takeIf { it != 0.0 } ?: return@mapNotNull null
            if (previous == point.start) return@mapNotNull null
            previous = point.start
            val y = change - childChanges.sumOf { it[point.start] ?: 0.0 }
            val x = if (center) (move(point.start) + move(point.end)) / 2 else move(point.start)
            EnergyBarPoint(x, y, point.start)
        }
    }

    private fun name(device: DeviceConsumption): String {
        val base =
            device.name?.ifEmpty { null }
                ?: hass.statisticLabel(device.statConsumption, data.statsMetadata[device.statConsumption])
        return if (device.statConsumption in children) "$base (${hass.localize("$DETAIL.untracked")})" else base
    }

    /**
     * What no device accounts for, and (when any) the over-reported part where the devices add up to more than the
     * home used. Port of `processUntracked`.
     */
    fun untracked(
        devices: List<EnergyBarSeries>,
        consumption: ConsumptionData,
        compare: Boolean,
    ): List<EnergyBarSeries> {
        val tracked = mutableMapOf<Long, Double>()
        devices.forEach { s -> s.points.forEach { tracked[it.start] = (tracked[it.start] ?: 0.0) + it.y } }
        val times = consumption.byStart.keys.sorted()
        val offset = periodMidpointOffset(period, times)
        val positive = mutableListOf<EnergyBarPoint>()
        val negative = mutableListOf<EnergyBarPoint>()
        times.forEach { t ->
            val raw = consumption.byStart.getValue(t).usedTotal - (tracked[t] ?: 0.0)
            val x = (if (compare) transform(t) else t) + offset
            positive += EnergyBarPoint(x, max(0.0, raw), t)
            if (raw < 0) negative += EnergyBarPoint(x, raw, t)
        }
        fun series(id: String, key: String, points: List<EnergyBarPoint>) = EnergyBarSeries(
            id = (if (compare) COMPARE_PREFIX else "") + id,
            name = hass.localize("$DETAIL.$key"),
            kind = UNTRACKED_KIND,
            colorIndex = null,
            compare = compare,
            order = Int.MAX_VALUE,
            points = points,
        )
        return listOfNotNull(
            series(UNTRACKED_ID, "untracked_consumption", positive),
            series(OVER_REPORTED_ID, "over_reported_consumption", negative).takeIf { negative.isNotEmpty() },
        )
    }
}

private const val DETAIL = "ui.panel.lovelace.cards.energy.energy_devices_detail_graph"
private const val KWH = "kWh"
