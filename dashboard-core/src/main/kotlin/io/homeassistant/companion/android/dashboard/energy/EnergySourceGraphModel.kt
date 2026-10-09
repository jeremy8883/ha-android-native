package io.homeassistant.companion.android.dashboard.energy

import io.homeassistant.companion.android.dashboard.entity.HassSnapshot

/**
 * The bar graphs of one kind of source: gas or water consumption, or solar production, a series per source with
 * the compared period's beside it. Ports of `generateEnergyGasGraphData` (frontend@20260624.6
 * src/panels/lovelace/cards/energy/energy-gas-graph-data.ts), `HuiEnergyWaterGraphCard._getStatistics`
 * (hui-energy-water-graph-card.ts) and `generateEnergySolarGraphData` (energy-solar-graph-data.ts), with the
 * solar forecast.
 *
 * @property total the period's total, shown next to the title
 */
data class EnergySourceGraphModel(val chart: EnergyBarChart, val total: Double)

/** The kinds of sources with a bar graph of their own, by the series kind that colours them. */
enum class SourceGraphKind(val kind: String) {
    GAS("gas"),
    WATER("water"),
    SOLAR("solar"),
}

/** The graph of the sources of [kind] in [data]. */
fun HassSnapshot.energySourceGraph(data: EnergyData, kind: SourceGraphKind): EnergySourceGraphModel {
    val sources = data.prefs.energySources.filter { it.graphKind == kind }.mapNotNull { source ->
        source.statEnergyFrom?.let { GraphSource(it, source.name) }
    }
    val period = suggestedPeriod(data.period, fine = false)
    val transform = compareTransform(data, formats.zone)
    val range = mutableListOf<Double>()
    fun series(statistics: Statistics, compare: Boolean) = sources.mapIndexed { index, source ->
        val points = sourcePoints(statistics[source.statId].orEmpty(), period, transform.takeIf { compare })
        range += points.map { it.y }
        EnergyBarSeries(
            id = (if (compare) COMPARE_PREFIX else "") + source.statId,
            name = seriesName(kind, source, data),
            kind = kind.kind,
            colorIndex = index,
            compare = compare,
            order = 0,
            points = points,
        )
    }
    val compareSeries = data.statsCompare?.let { series(it, compare = true) }.orEmpty()
    val mainSeries = series(data.stats, compare = false)
    val lines = if (kind == SourceGraphKind.SOLAR) solarForecastLines(data, period) else emptyList()
    range += lines.flatMap { line -> line.points.map { it.y } }
    val (xMin, xMax) = barChartRange(data, formats.zone)
    return EnergySourceGraphModel(
        chart = EnergyBarChart(
            series = compareSeries + mainSeries,
            xMin = xMin,
            xMax = xMax,
            period = period,
            yFractionDigits = yAxisFractionDigits(
                range.minOrNull() ?: Double.POSITIVE_INFINITY,
                range.maxOrNull() ?: Double.NEGATIVE_INFINITY,
            ),
            unit = when (kind) {
                SourceGraphKind.GAS -> data.gasUnit
                SourceGraphKind.WATER -> data.waterUnit
                SourceGraphKind.SOLAR -> KWH
            },
            compare = data.comparePeriod != null,
            showCompareYear = data.comparePeriod?.let { it.start.year != data.period.start.year } == true,
            lines = lines,
        ),
        total = sources.sumOf { source -> data.stats[source.statId].orEmpty().sumOf { it.change ?: 0.0 } },
    )
}

private class GraphSource(val statId: String, val name: String?)

private val EnergySource.graphKind: SourceGraphKind?
    get() = when (this) {
        is EnergySource.Solar -> SourceGraphKind.SOLAR
        is EnergySource.Utility -> if (type == UtilityType.GAS) SourceGraphKind.GAS else SourceGraphKind.WATER
        else -> null
    }

/**
 * A bar per period that changed: in the middle of sub-daily periods, at the start of longer ones, moved onto the
 * shown period when [transform]ing the compared one. Port of `computeStatMidpoint` as these graphs inline it.
 */
private fun sourcePoints(
    stats: List<StatisticValue>,
    period: StatisticPeriod,
    transform: ((Long) -> Long)?,
): List<EnergyBarPoint> {
    val center = period == StatisticPeriod.HOUR || period == StatisticPeriod.FIVE_MINUTES
    val move = transform ?: { it }
    var previous: Long? = null
    return stats.mapNotNull { point ->
        val change = point.change?.takeIf { it != 0.0 } ?: return@mapNotNull null
        if (previous == point.start) return@mapNotNull null
        previous = point.start
        val x = if (center) (move(point.start) + move(point.end)) / 2 else move(point.start)
        EnergyBarPoint(x, change, point.start)
    }
}

private fun HassSnapshot.seriesName(kind: SourceGraphKind, source: GraphSource, data: EnergyData): String {
    val name = source.name?.ifEmpty { null } ?: statisticLabel(source.statId, data.statsMetadata[source.statId])
    return if (kind == SourceGraphKind.SOLAR) {
        localize("ui.panel.lovelace.cards.energy.energy_solar_graph.production", mapOf("name" to name))
    } else {
        name
    }
}

private const val KWH = "kWh"
