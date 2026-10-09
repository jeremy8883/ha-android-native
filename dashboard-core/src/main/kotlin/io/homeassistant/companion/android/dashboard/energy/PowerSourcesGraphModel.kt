package io.homeassistant.companion.android.dashboard.energy

import io.homeassistant.companion.android.dashboard.display.DisplayFormats
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import java.math.BigDecimal
import java.time.Instant
import kotlin.math.max
import kotlin.math.min

/**
 * The power sources graph: the power from solar, the battery and the grid as stacked areas above 0, what went into
 * the battery and the grid below, and the home's use as a line, over the period in kW. Port of
 * `generatePowerSourcesGraphData` (frontend@20260624.6
 * src/panels/lovelace/cards/energy/power-sources-graph-data.ts).
 *
 * @property series the stacked areas, positive ones first, then the use line ([PowerSeries.stack] `null`)
 * @property xMin the start of the period, in epoch milliseconds
 * @property xMax the end of the period
 * @property period the statistics' granularity, which the time axis is labelled by
 * @property today whether it's today's graph, which ends with the current states
 */
data class PowerSourcesGraphModel(
    val series: List<PowerSeries>,
    val yFractionDigits: Int,
    val xMin: Long,
    val xMax: Long,
    val period: StatisticPeriod,
    val today: Boolean,
)

/**
 * A line of the power sources graph, with a point at every time of the graph.
 *
 * @property color the theme variable it's drawn in
 * @property stack the stack its area is in, `null` for a line without an area
 */
data class PowerSeries(
    val id: String,
    val name: String,
    val color: String,
    val stack: PowerStack?,
    val points: List<PowerPoint>,
)

/** A value [y] in kW at [x], epoch milliseconds. */
data class PowerPoint(val x: Long, val y: Double)

/** The stack of the areas above 0 (power in) or below (power out). */
enum class PowerStack { POSITIVE, NEGATIVE }

/** The power sources graph of [data] at [now]; today's graph ends with the current states. */
fun HassSnapshot.powerSourcesGraph(data: EnergyData, now: Instant): PowerSourcesGraphModel {
    val zone = formats.zone
    val today = now.atZone(zone).toLocalDate()
    val current = now.takeIf { data.period.start == today && data.period.end == today }
    val split = POWER_SOURCES.mapNotNull { source -> sourcePoints(data, source, current)?.let { source to it } }.toMap()
    val range = split.values.flatMap { (positive, negative) -> (positive + negative).map { it.y } }
    fun series(source: PowerSource, stack: PowerStack, points: List<PowerPoint>) = PowerSeries(
        id = if (stack == PowerStack.POSITIVE) source.key else source.key + NEGATIVE_SUFFIX,
        name = localize("ui.panel.lovelace.cards.energy.power_graph.${source.key}"),
        color = source.color,
        stack = stack,
        points = points,
    )
    val positive = POWER_SOURCES.mapNotNull { source ->
        split[source]?.let { series(source, PowerStack.POSITIVE, it.first) }
    }
    val negative = NEGATIVE_ORDER.mapNotNull { source ->
        split[source]?.let { series(source, PowerStack.NEGATIVE, it.second) }
    }
    val stacked = fillGaps(positive + negative)
    return PowerSourcesGraphModel(
        series = stacked + usage(stacked),
        yFractionDigits = yAxisFractionDigits(
            range.minOrNull() ?: Double.POSITIVE_INFINITY,
            range.maxOrNull() ?: Double.NEGATIVE_INFINITY,
        ),
        xMin = data.period.startInstant(zone).toEpochMilli(),
        xMax = data.period.endInstant(zone).toEpochMilli(),
        period = suggestedPeriod(data.period, fine = true),
        today = current != null,
    )
}

/**
 * What the tooltip at [x] shows: its time and the value of each series but [hidden] ones (by id, a source hiding
 * its area below 0 too) that isn't 0. `null` when all are. Port of `formatTooltip` for lines.
 */
fun DisplayFormats.powerTooltip(graph: PowerSourcesGraphModel, hidden: Set<String>, x: Long): PowerTooltip? {
    val rows = graph.series.filterNot { it.id.removeSuffix(NEGATIVE_SUFFIX) in hidden }.mapNotNull { series ->
        val y = series.points.firstOrNull { it.x == x }?.y ?: return@mapNotNull null
        val text = number(BigDecimal.valueOf(y), 0, if (y < SMALL_VALUE) SMALL_DIGITS else DEFAULT_DIGITS)
        if (text == "0") null else PowerTooltipRow(series, "$text $KW")
    }
    return rows.takeIf { it.isNotEmpty() }?.let { PowerTooltip(time(Instant.ofEpochMilli(x)), it) }
}

/** The tooltip of a time of the power sources graph. */
data class PowerTooltip(val title: String, val rows: List<PowerTooltipRow>)

/** A series' value in a [PowerTooltip]. */
data class PowerTooltipRow(val series: PowerSeries, val value: String)

/**
 * The points of [source] above and below 0, with the current states at [now] when given; `null` when no source
 * of its kind has a power statistic.
 */
private fun HassSnapshot.sourcePoints(
    data: EnergyData,
    source: PowerSource,
    now: Instant?,
): Pair<List<PowerPoint>, List<PowerPoint>>? {
    val ids = data.prefs.energySources.filter { source.includes(it) }.mapNotNull { it.statRate?.ifEmpty { null } }
    if (ids.isEmpty()) return null
    return splitBySign(
        ids.map { id ->
            val current = now?.let { at ->
                powerWatts(id)?.let { StatisticValue(at.toEpochMilli(), at.toEpochMilli(), mean = it / WATTS_PER_KW) }
            }
            data.stats[id].orEmpty() + listOfNotNull(current)
        },
    )
}

/** The home's use: what all the areas add up to at each time, never below 0. */
private fun HassSnapshot.usage(stacked: List<PowerSeries>) = PowerSeries(
    id = USAGE,
    name = localize("ui.panel.lovelace.cards.energy.power_graph.usage"),
    color = "primary-text-color",
    stack = null,
    points = stacked.firstOrNull()?.points.orEmpty().mapIndexed { index, point ->
        PowerPoint(point.x, max(0.0, stacked.fold(0.0) { sum, series -> sum + series.points[index].y }))
    },
)

/**
 * The means of [statistics] summed at the middle of each period, above and below 0. Port of `processData`.
 */
private fun splitBySign(statistics: List<List<StatisticValue>>): Pair<List<PowerPoint>, List<PowerPoint>> {
    val sums = linkedMapOf<Long, Double>()
    statistics.forEach { values ->
        values.forEach { value ->
            val mean = value.mean ?: return@forEach
            val x = (value.start + value.end) / 2
            sums[x] = (sums[x] ?: 0.0) + mean
        }
    }
    return sums.map { (x, y) -> PowerPoint(x, max(0.0, y)) } to sums.map { (x, y) -> PowerPoint(x, min(0.0, y)) }
}

/** Every series with a point at each time any has one, 0 where it had none. Port of `fillLineGaps`. */
private fun fillGaps(series: List<PowerSeries>): List<PowerSeries> {
    val times = series.flatMap { s -> s.points.map { it.x } }.toSortedSet()
    return series.map { s ->
        val byTime = s.points.associateBy { it.x }
        s.copy(points = times.map { byTime[it] ?: PowerPoint(it, 0.0) })
    }
}

/** A source of power: its key (id and translation), colour and which configured sources it sums. */
private class PowerSource(val key: String, val color: String, val includes: (EnergySource) -> Boolean)

private val SOLAR_POWER = PowerSource("solar", "energy-solar-color") { it is EnergySource.Solar }
private val BATTERY_POWER = PowerSource("battery", "energy-battery-out-color") { it is EnergySource.Battery }
private val GRID_POWER = PowerSource("grid", "energy-grid-consumption-color") { it is EnergySource.Grid }

// Areas above 0 in this order, those below in the other (solar is never below)
private val POWER_SOURCES = listOf(SOLAR_POWER, BATTERY_POWER, GRID_POWER)
private val NEGATIVE_ORDER = listOf(BATTERY_POWER, GRID_POWER)

private const val USAGE = "usage"
private const val NEGATIVE_SUFFIX = "-negative"
private const val KW = "kW"
private const val SMALL_VALUE = 0.1
private const val SMALL_DIGITS = 3
private const val DEFAULT_DIGITS = 2
private const val WATTS_PER_KW = 1000.0
