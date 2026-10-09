package io.homeassistant.companion.android.dashboard.energy

import io.homeassistant.companion.android.dashboard.derive.stateName
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import kotlin.math.max
import kotlin.math.min

/**
 * The energy usage graph: the energy the home used by period from solar, the battery and the grid above the axis,
 * and what charged the battery or went to the grid below it, with the compared period's bars beside them. Port of
 * `HuiEnergyUsageGraphCard._getStatistics` (frontend@20260624.6
 * src/panels/lovelace/cards/energy/hui-energy-usage-graph-card.ts).
 *
 * @property total the energy the home used in the period, when any
 */
data class EnergyUsageGraphModel(val chart: EnergyBarChart, val total: Double?)

/** The bars of `HuiEnergyUsageGraphCard._processDataSet`, for the period or (when [compare]) the compared one. */
fun HassSnapshot.energyUsageGraph(data: EnergyData): EnergyUsageGraphModel {
    val context = SeriesContext(data, data.prefs.usageStatistics(this), YRange())
    val sums = data.summed()
    val consumption = sums.consumption()
    val compareSeries = data.statsCompare?.let {
        val compareSums = data.summed(compare = true)
        usageSeries(context, it, compareSums, compareSums.consumption(), compare = true)
    }.orEmpty()
    // The compare series come first, so their bars are drawn first
    val series = (compareSeries + usageSeries(context, data.stats, sums, consumption, compare = false))
        .sortedBy { it.order }
    val (xMin, xMax) = barChartRange(data, formats.zone)
    return EnergyUsageGraphModel(
        chart = EnergyBarChart(
            series = series,
            xMin = xMin,
            xMax = xMax,
            period = suggestedPeriod(data.period, fine = false),
            yFractionDigits = context.range.fractionDigits(),
            unit = KWH,
            compare = data.comparePeriod != null,
            showCompareYear = data.comparePeriod?.let { it.start.year != data.period.start.year } == true,
        ),
        total = consumption.total.usedTotal.takeIf { it > 0 },
    )
}

/** The statistics of each kind with the name a named source gives them, in the order upstream lists them. */
private class UsageStatistics(val ids: Map<String, List<String>>, val labels: Map<String, String>)

private fun EnergyPreferences.usageStatistics(hass: HassSnapshot): UsageStatistics {
    val ids = linkedMapOf<String, MutableList<String>>()
    val labels = mutableMapOf<String, String>()
    fun add(kind: String, id: String) = ids.getOrPut(kind) { mutableListOf() }.add(id)
    fun named(key: String, name: String) = hass.localize("$CARDS$key", mapOf("name" to name))
    energySources.forEach { source ->
        when (source) {
            is EnergySource.Solar -> add(SOLAR, source.statEnergyFrom)
            is EnergySource.Battery -> {
                add(TO_BATTERY, source.statEnergyTo)
                add(FROM_BATTERY, source.statEnergyFrom)
                source.name?.let { labels[TO_BATTERY + source.statEnergyTo] = named(NAMED_BATTERY_CHARGED, it) }
            }
            is EnergySource.Grid -> addGrid(source, ::add, labels, ::named)
            is EnergySource.Utility -> Unit
        }
    }
    return UsageStatistics(ids, labels)
}

private fun addGrid(
    source: EnergySource.Grid,
    add: (String, String) -> Unit,
    labels: MutableMap<String, String>,
    named: (String, String) -> String,
) {
    val from = source.statEnergyFrom?.takeIf { it.isNotEmpty() }
    val to = source.statEnergyTo?.takeIf { it.isNotEmpty() }
    from?.let { id ->
        add(FROM_GRID, id)
        source.name?.let { labels[FROM_GRID + id] = if (to != null) named(NAMED_GRID_CONSUMED, it) else it }
    }
    to?.let { id ->
        add(TO_GRID, id)
        source.name?.let { labels[TO_GRID + id] = if (from != null) named(NAMED_GRID_EXPORTED, it) else it }
    }
}

/** The lowest and highest bar values, for the y axis's fraction digits. */
private class YRange {
    var min = Double.POSITIVE_INFINITY
    var max = Double.NEGATIVE_INFINITY

    fun track(value: Double) {
        min = min(min, value)
        max = max(max, value)
    }

    fun fractionDigits() = yAxisFractionDigits(min, max)
}

/** What every series of the graph shares. */
private class SeriesContext(val data: EnergyData, val ids: UsageStatistics, val range: YRange)

private fun HassSnapshot.usageSeries(
    context: SeriesContext,
    statistics: Statistics,
    sums: EnergySums,
    consumption: ConsumptionData,
    compare: Boolean,
): List<EnergyBarSeries> {
    val data = context.data
    val ids = context.ids
    val range = context.range
    val combined = combinedData(statistics, sums, consumption, ids.ids)
    val period = suggestedPeriod(data.period, fine = false)
    val keys = sums.timestamps
    val offset = periodMidpointOffset(period, keys)
    val transform = compareTransform(data, formats.zone)
    return combined.flatMap { (kind, sources) ->
        sources.map { (statId, values) ->
            val points = keys.map { key ->
                val value = values[key] ?: 0.0
                val y = if (value != 0.0 && kind in NEGATIVE) -value else value
                range.track(y)
                val x = if (compare) transform(key) + offset else key + offset
                EnergyBarPoint(x, y, key)
            }
            EnergyBarSeries(
                id = "${if (compare) COMPARE_PREFIX else ""}$statId-$kind",
                name = USED_LABELS[kind]?.let { localize("$USAGE_GRAPH$it") }
                    ?: ids.labels[kind + statId]
                    ?: statisticLabel(statId, data.statsMetadata[statId]),
                kind = kind,
                colorIndex = if (kind in USED) null else ids.ids[kind]?.indexOf(statId),
                compare = compare,
                order = STACK_ORDER[kind] ?: combined.size,
                points = points,
            )
        }
    }
}

/**
 * The values of each series by kind and statistic: the grid and battery meters', then the home's use of solar, the
 * battery and (where the grid also charged the battery) the grid.
 */
private fun combinedData(
    statistics: Statistics,
    sums: EnergySums,
    consumption: ConsumptionData,
    ids: Map<String, List<String>>,
): Map<String, Map<String, Map<Long, Double>>> {
    val combined = linkedMapOf<String, Map<String, Map<Long, Double>>>()
    ids.forEach { (kind, statIds) ->
        if (kind !in METERED) return@forEach
        combined[kind] = statIds.mapNotNull { id ->
            statistics[id]?.let { stats ->
                id to
                    linkedMapOf<Long, Double>().apply {
                        stats.forEach { s -> s.change?.let { putIfAbsent(s.start, it) } }
                    }
            }
        }.toMap(LinkedHashMap())
    }
    if (SOLAR in ids) combined[USED_SOLAR] = mapOf(USED_SOLAR to consumption.byStart.mapValues { it.value.usedSolar })
    if (FROM_BATTERY in
        ids
    ) {
        combined[USED_BATTERY] = mapOf(USED_BATTERY to consumption.byStart.mapValues { it.value.usedBattery })
    }
    val fromGrid = combined[FROM_GRID]
    if (fromGrid != null && EnergyFlow.TO_BATTERY in sums.byStart) {
        val (grid, usedGrid) = splitGridToBattery(fromGrid, consumption)
        combined[FROM_GRID] = grid
        combined[USED_GRID] = mapOf(USED_GRID to usedGrid)
    }
    return combined
}

/**
 * Where the grid also charged the battery, the grid's bar is only what the home used: kept on the grid source when
 * there is one in that period, else shown as "combined from grid" since the sources can't be told apart.
 */
private fun splitGridToBattery(
    fromGrid: Map<String, Map<Long, Double>>,
    consumption: ConsumptionData,
): Pair<Map<String, Map<Long, Double>>, Map<Long, Double>> {
    val grid = fromGrid.mapValues { LinkedHashMap(it.value) }.toMap(LinkedHashMap())
    val usedGrid = linkedMapOf<Long, Double>()
    consumption.byStart.forEach { (start, period) ->
        if (period.gridToBattery == 0.0) return@forEach
        val sources = grid.filterValues { (it[start] ?: 0.0) != 0.0 }.keys
        if (sources.size == 1) {
            grid.getValue(sources.single())[start] = period.usedGrid
        } else {
            grid.values.forEach { it.remove(start) }
            usedGrid[start] = period.usedGrid
        }
    }
    return grid to usedGrid
}

/** Port of `getStatisticLabel` (src/data/recorder.ts): the entity's name, else the statistic's. */
fun HassSnapshot.statisticLabel(statisticId: String, metadata: StatisticsMetadata?): String =
    states[statisticId]?.stateName() ?: metadata?.name?.ifEmpty { null } ?: statisticId

private const val KWH = "kWh"
private const val CARDS = "ui.panel.lovelace.cards.energy."
private const val USAGE_GRAPH = CARDS + "energy_usage_graph."
private const val NAMED_BATTERY_CHARGED = "energy_sources_table.named_battery_charged"
private const val NAMED_GRID_CONSUMED = "energy_usage_graph.named_grid_consumed"
private const val NAMED_GRID_EXPORTED = "energy_usage_graph.named_grid_exported"
private const val TO_GRID = "to_grid"
private const val FROM_GRID = "from_grid"
private const val TO_BATTERY = "to_battery"
private const val FROM_BATTERY = "from_battery"
private const val USED_GRID = "used_grid"
private const val USED_SOLAR = "used_solar"
private const val USED_BATTERY = "used_battery"
private val METERED = setOf(TO_GRID, FROM_GRID, TO_BATTERY)
private val NEGATIVE = setOf(TO_GRID, TO_BATTERY)
private val USED = setOf(USED_GRID, USED_SOLAR, USED_BATTERY)
private val USED_LABELS = mapOf(
    USED_GRID to "combined_from_grid",
    USED_SOLAR to "consumed_solar",
    USED_BATTERY to "consumed_battery",
)
private val STACK_ORDER = mapOf(TO_BATTERY to 1, TO_GRID to 2, USED_SOLAR to 3, USED_BATTERY to 4)
