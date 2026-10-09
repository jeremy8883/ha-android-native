package io.homeassistant.companion.android.dashboard.energy

import kotlin.math.min

// The data of the frontend's sankey chart, and what it lays out from it. Port of `Node`, `Link` and `_createData`
// (frontend@20260624.6 src/components/chart/ha-sankey-chart.ts).

/**
 * A node of a sankey chart: its [value], and the column it's in ([index], ordered but not necessarily contiguous).
 *
 * @property entityId the entity a tap on the node opens, if any
 */
data class SankeyNode(
    val id: String,
    val label: String,
    val value: Double,
    val color: SankeyColor,
    val index: Int,
    val entityId: String? = null,
)

/** A flow between two nodes; without a [value], what's left of the source's or the target's. */
data class SankeyLink(val source: String, val target: String, val value: Double? = null)

/** The nodes and flows of a sankey chart. */
data class SankeyData(val nodes: List<SankeyNode>, val links: List<SankeyLink>)

/** A node's colour: a theme variable, or the graph palette's colour at an index. */
sealed interface SankeyColor {
    data class Variable(val name: String) : SankeyColor

    data class Palette(val index: Int) : SankeyColor
}

/** What the chart lays out: the nodes with a value, by column in order, and the flows with their values. */
data class SankeyInput(val columns: List<List<SankeyNode>>, val links: List<SankeyLink>)

/**
 * The columns and flows of [this]: nodes without a value are left out, each column after the first is ordered like
 * its nodes' parents in the columns before (orphans last), and each flow's value is capped by what its nodes have
 * left. Port of `_createData` and `_processLinks`.
 */
fun SankeyData.layoutInput(): SankeyInput {
    val shown = nodes.filter { it.value > 0 }
    // Upstream sorts the column indexes as strings, as `Array.sort` does without a comparator
    val indexes = shown.map { it.index }.distinct().sortedBy { it.toString() }
    val columns = mutableListOf<List<SankeyNode>>()
    indexes.forEach { index ->
        val column = shown.filter { it.index == index }
        if (column.isEmpty()) return@forEach
        columns += if (columns.isEmpty()) column else column.sortedWith(parentOrder(columns))
    }
    return SankeyInput(columns, processLinks(shown))
}

/** Nodes in the order of their parents in [columns], orphans last. */
private fun SankeyData.parentOrder(columns: List<List<SankeyNode>>) = Comparator<SankeyNode> { a, b ->
    val first = parentIndex(a.id, columns)
    val second = parentIndex(b.id, columns)
    when {
        first == second -> 0
        first == NO_PARENT -> 1
        second == NO_PARENT -> -1
        else -> first.compareTo(second)
    }
}

/** The average place of [id]'s parents, counted from the last column back. Port of `_findParentIndex`. */
private fun SankeyData.parentIndex(id: String, columns: List<List<SankeyNode>>): Double {
    val parents = links.filter { it.target == id }.map { it.source }
    val places = parents.mapNotNull { parent ->
        var offset = 0
        columns.asReversed().firstNotNullOfOrNull { column ->
            column.indexOfFirst { it.id == parent }.takeIf { it != -1 }?.let { offset + it }.also {
                offset +=
                    column.size
            }
        }
    }
    return if (places.isEmpty()) NO_PARENT else places.average()
}

private fun SankeyData.processLinks(shown: List<SankeyNode>): List<SankeyLink> {
    val byId = shown.associateBy { it.id }
    val accountedIn = mutableMapOf<String, Double>()
    val accountedOut = mutableMapOf<String, Double>()
    return links.mapNotNull { link ->
        val source = byId[link.source] ?: return@mapNotNull null
        val target = byId[link.target] ?: return@mapNotNull null
        val sourceRemaining = source.value - (accountedOut[source.id] ?: 0.0)
        val targetRemaining = target.value - (accountedIn[target.id] ?: 0.0)
        val value = min(min(link.value ?: sourceRemaining, sourceRemaining), targetRemaining)
        accountedIn[target.id] = (accountedIn[target.id] ?: 0.0) + value
        accountedOut[source.id] = (accountedOut[source.id] ?: 0.0) + value
        link.copy(value = value).takeIf { value > 0 }
    }
}

private const val NO_PARENT = -1.0
