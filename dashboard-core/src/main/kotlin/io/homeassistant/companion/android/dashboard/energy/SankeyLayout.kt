package io.homeassistant.companion.android.dashboard.energy

import kotlin.math.max

/**
 * Where a sankey chart's nodes and flows go, as ECharts' sankey layout places them with the frontend's options
 * (`layoutIterations: 0`, so columns are stacked from the start in the given order). Coordinates are along the flow
 * ([depth]: left to right, or top to bottom when vertical) and across it ([breadth]).
 *
 * Port of `computeNodeValues`, `computeNodeDepths`, `computeNodeBreadths` and `computeEdgeDepths` (echarts
 * src/chart/sankey/sankeyLayout.ts).
 */
data class SankeyLayout(val nodes: List<PlacedNode>, val links: List<PlacedLink>)

/** A node at [depth] (its edge before the flow) and from [breadth] for [size] across it. */
data class PlacedNode(val node: SankeyNode, val depth: Float, val breadth: Float, val size: Float)

/** A flow as wide as [size], leaving its source at [sourceBreadth] and reaching its target at [targetBreadth]. */
data class PlacedLink(
    val link: SankeyLink,
    val source: PlacedNode,
    val target: PlacedNode,
    val sourceBreadth: Float,
    val targetBreadth: Float,
    val size: Float,
)

/**
 * Lays [this] out in an area [length] long along the flow and [extent] across it, with nodes [nodeWidth] thick and
 * [nodeGap] apart.
 */
fun SankeyInput.layout(length: Float, extent: Float, nodeWidth: Float, nodeGap: Float): SankeyLayout {
    val values = nodeValues()
    val columns = columns.filter { it.isNotEmpty() }
    val depthStep = if (columns.size > 1) (length - nodeWidth) / (columns.size - 1) else 0f
    // One scale for every column: the fullest one fills the extent
    val scale = columns.minOf { column ->
        (extent - (column.size - 1) * nodeGap) /
            column.sumOf { values.getValue(it.id) }.toFloat().coerceAtLeast(Float.MIN_VALUE)
    }
    val stacking = Stacking(scale, nodeGap, extent)
    val placed = columns.flatMapIndexed { depth, column -> stacking.stack(column, depth * depthStep, values) }
        .associateBy { it.node.id }
    return SankeyLayout(placed.values.toList(), placeLinks(placed, scale))
}

/** Each node's value: its own, or more when more flows in or out of it. Port of `computeNodeValues`. */
private fun SankeyInput.nodeValues(): Map<String, Double> = columns.flatten().associate { node ->
    val out = links.filter { it.source == node.id }.sumOf { it.value ?: 0.0 }
    val into = links.filter { it.target == node.id }.sumOf { it.value ?: 0.0 }
    node.id to maxOf(out, into, node.value)
}

/** How columns are stacked: values to sizes by [scale], [gap] apart, within [extent]. */
private class Stacking(val scale: Float, val gap: Float, val extent: Float)

/** A column stacked from the start, pushed back if it overflows the extent. Port of `resolveCollisions`. */
private fun Stacking.stack(column: List<SankeyNode>, depth: Float, values: Map<String, Double>): List<PlacedNode> {
    var next = 0f
    val stacked = column.mapIndexed { index, node ->
        val breadth = max(index.toFloat(), next)
        val size = (values.getValue(node.id) * scale).toFloat()
        next = breadth + size + gap
        PlacedNode(node, depth, breadth, size)
    }
    val overflow = next - gap - extent
    if (overflow <= 0) return stacked
    // Move the last one back inside, and the ones before it as they then overlap
    var limit = extent
    return stacked.asReversed().map { placed ->
        val breadth = minOf(placed.breadth, limit - placed.size)
        limit = breadth - gap
        placed.copy(breadth = breadth)
    }.asReversed()
}

/**
 * The flows, stacked on each node in the order of the nodes at their other end. Port of `computeEdgeDepths`.
 */
private fun SankeyInput.placeLinks(nodes: Map<String, PlacedNode>, scale: Float): List<PlacedLink> {
    val indexed = links.withIndex().toList()
    val sourceAt = mutableMapOf<Int, Float>()
    val targetAt = mutableMapOf<Int, Float>()
    fun size(link: SankeyLink) = (link.value ?: 0.0).toFloat() * scale
    nodes.values.forEach { node ->
        var offset = 0f
        indexed.filter { it.value.source == node.node.id }.sortedBy { nodes[it.value.target]?.breadth ?: 0f }.forEach {
            sourceAt[it.index] = node.breadth + offset
            offset += size(it.value)
        }
        offset = 0f
        indexed.filter { it.value.target == node.node.id }.sortedBy { nodes[it.value.source]?.breadth ?: 0f }.forEach {
            targetAt[it.index] = node.breadth + offset
            offset += size(it.value)
        }
    }
    return indexed.mapNotNull { (index, link) ->
        val source = nodes[link.source] ?: return@mapNotNull null
        val target = nodes[link.target] ?: return@mapNotNull null
        PlacedLink(link, source, target, sourceAt.getValue(index), targetAt.getValue(index), size(link))
    }
}
