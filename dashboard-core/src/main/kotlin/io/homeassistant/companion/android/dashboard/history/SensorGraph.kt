package io.homeassistant.companion.android.dashboard.history

import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.model.jsNumber
import kotlin.math.ceil
import kotlin.math.floor

// The line graph under a sensor card. Ports of `hui-graph-header-footer`, `coordinates` and `getPath`'s input
// (frontend@20260624.6 src/panels/lovelace/header-footer/hui-graph-header-footer.ts,
// src/panels/lovelace/common/graph/coordinates.ts) and `downSampleLineData` (src/components/chart/down-sample.ts).

/**
 * A graph of [entityId]'s states over the last [hoursToShow], drawn as a smooth line (the mean of each period
 * unless [detail] is 2, which keeps each period's lowest and highest), between [minY] and [maxY] when set.
 */
data class SensorGraph(
    val entityId: String,
    val hoursToShow: Double,
    val detail: Int,
    val minY: Double?,
    val maxY: Double?,
)

/** The history a graph shows: its entity's states over its time window. */
data class GraphHistoryKey(val entityId: String, val hoursToShow: Double)

/** [SensorGraph]'s history key. */
val SensorGraph.historyKey: GraphHistoryKey get() = GraphHistoryKey(entityId, hoursToShow)

/** What a graph has of its history once its subscription answered: the states, or why they couldn't load. */
sealed interface GraphHistory {
    /** The entity's states, oldest first (empty when none were recorded). */
    data class Loaded(val states: List<HistoryState>) : GraphHistory

    /** The subscription failed, with the server's [message] (or error code) when it answered with one. */
    data class Failed(val message: String?) : GraphHistory
}

/**
 * Where a graph's line goes, in a box [width] wide and a fifth as tall: its [points] (x right, y down) and the
 * height of the zero line its fill goes down (or up) to.
 */
data class GraphCoordinates(val points: List<GraphPoint>, val yAxisOrigin: Double)

/** A point of a graph's line. */
data class GraphPoint(val x: Double, val y: Double)

/**
 * The coordinates of [graph] at [nowMillis] in a box [width] wide, from [history] (the stream's states of its
 * entity, `null` until it loaded; the [current] state alone while it has none). Until it loads, the current state
 * is drawn without the time window, as `_setLoadingCoordinates` does. States before the window (the stream drops
 * them as messages come) start left of the box.
 */
fun sensorGraphCoordinates(
    graph: SensorGraph,
    history: List<HistoryState>?,
    current: EntityState?,
    width: Double,
    nowMillis: Double,
): GraphCoordinates {
    val box = GraphBox(width, width / HEIGHT_RATIO)
    // `limitedHistoryFromStateObj`
    val currentOnly = current?.let { listOf(HistoryState(it.state, null, null, it.lastUpdated)) }
    val states = if (history == null) currentOnly else history.ifEmpty { currentOnly }
    // One point per hour, or per 5 pixels with detail 2
    val maxDetails = maxOf(
        MIN_DETAILS.toDouble(),
        if (graph.detail > 1) maxOf(width / POINT_SPACING, graph.hoursToShow) else graph.hoursToShow,
    )
    val window = Limits(nowMillis - graph.hoursToShow * MILLIS_PER_HOUR, nowMillis, graph.minY, graph.maxY)
    return when {
        states == null -> GraphCoordinates(emptyList(), 0.0)
        history == null -> coordinates(
            states,
            box,
            Sampling(MIN_DETAILS.toDouble(), useMean = false),
            window.copy(minX = null, maxX = null),
        )
        else -> coordinates(states, box, Sampling(maxDetails, useMean = graph.detail != 2), window)
    }
}

/** The box a graph draws in. */
private data class GraphBox(val width: Double, val height: Double)

/** How many points a graph keeps at most, and whether a period's mean stands for it (else its extremes). */
private data class Sampling(val maxDetails: Double, val useMean: Boolean)

/** The bounds of a graph; unset ones come from its data. */
private data class Limits(val minX: Double?, val maxX: Double?, val minY: Double?, val maxY: Double?)

/** The values a graph spans from its bottom to its top, and the height of its zero line. */
private data class YRange(val min: Double, val max: Double, val origin: Double)

private fun coordinates(
    history: List<HistoryState>,
    box: GraphBox,
    sampling: Sampling,
    limits: Limits,
): GraphCoordinates {
    val points = history.map { GraphPoint(it.lastUpdated * MILLIS, jsNumber(it.state)) }.filterNot { it.y.isNaN() }
    val sampled = downSample(points, sampling.maxDetails, limits.minX, limits.maxX, sampling.useMean)
    return calcPoints(sampled, box, limits)
}

/** Port of `calcPoints`: the points scaled into the box, with a margin of a tenth of the range above and below. */
private fun calcPoints(history: List<GraphPoint>, box: GraphBox, limits: Limits): GraphCoordinates {
    if (history.isEmpty()) return GraphCoordinates(emptyList(), box.height)
    val minX = limits.minX ?: history.first().x
    val maxX = limits.maxX ?: history.last().x
    val range = yRange(history, limits, box.height)
    val yDenom = (range.max - range.min).takeUnless { it == 0.0 } ?: 1.0
    val xDenom = (maxX - minX).takeUnless { it == 0.0 } ?: 1.0
    val points = history.map { (x, y) ->
        GraphPoint((x - minX) / xDenom * box.width, box.height - (y - range.min) / yDenom * box.height)
    }
    return GraphCoordinates(points + GraphPoint(box.width, points.last().y), range.origin)
}

/** The values [history] spans with its margins, from 0 unless some are negative, and where zero is. */
private fun yRange(history: List<GraphPoint>, limits: Limits, height: Double): YRange {
    var minY = limits.minY ?: history.first().y
    var maxY = limits.maxY ?: history.first().y
    history.forEach { (_, y) ->
        if (y < minY) {
            minY = y
        } else if (y > maxY) {
            maxY = y
        }
    }
    val rangeY = (maxY - minY).takeUnless { it == 0.0 } ?: (minY * MARGIN)
    maxY += rangeY * MARGIN
    minY -= rangeY * MARGIN
    return when {
        // All values are negative
        maxY < 0 -> YRange(minY, minOf(0.0, maxY), 0.0)
        // Some values are negative
        minY < 0 -> YRange(minY, maxY, maxY / ((maxY - minY).takeUnless { it == 0.0 } ?: 1.0) * height)
        else -> YRange(maxOf(0.0, minY), maxY, height)
    }
}

/**
 * Port of `downSampleLineData`: at most about [maxDetails] points, one per equal period from the start (their mean
 * when [useMean], else their lowest and highest in order).
 */
internal fun downSample(
    data: List<GraphPoint>,
    maxDetails: Double,
    minX: Double?,
    maxX: Double?,
    useMean: Boolean,
): List<GraphPoint> {
    if (data.size <= maxDetails) return data
    val min = minX ?: data.first().x
    val max = maxX ?: data.last().x
    val step = ceil((max - min) / floor(maxDetails))
    val frames = data.groupBy { floor((it.x - min) / step) }.values
    return if (useMean) {
        frames.map { frame -> GraphPoint(frame.sumOf { it.x } / frame.size, frame.sumOf { it.y } / frame.size) }
    } else {
        frames.flatMap { frame ->
            // The first of equal values wins, and the two keep their order
            val low = frame.reduce { a, b -> if (b.y < a.y) b else a }
            val high = frame.reduce { a, b -> if (b.y > a.y) b else a }
            when {
                low.x > high.x -> listOf(high, low)
                low.x < high.x -> listOf(low, high)
                else -> listOf(low)
            }
        }
    }
}

private const val MILLIS = 1000.0
private const val MILLIS_PER_HOUR = 3_600_000.0
private const val HEIGHT_RATIO = 5
private const val POINT_SPACING = 5
private const val MIN_DETAILS = 10
private const val MARGIN = 0.1
