package io.homeassistant.companion.android.dashboard.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * A column for a scrolling list ([modifier] scrolls it) that, when its content doesn't fit in [viewportHeight] (px),
 * spaces its children a little differently (from 2dp closer to 4dp further apart) so that the child at the bottom
 * edge is cut through its middle, showing there is more to scroll to. Content that fits is laid out as is.
 *
 * Measured like any column: the spacing is worked out at layout, from the children's heights, only when they or the
 * viewport change.
 *
 * Upstream: none (native: a hint that the drawer scrolls)
 */
@Composable
internal fun PeekColumn(
    viewportHeight: Int,
    modifier: Modifier = Modifier,
    topPadding: Dp = 0.dp,
    bottomPadding: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    Layout(content, modifier) { measurables, constraints ->
        val placeables = measurables.map {
            it.measure(constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity))
        }
        val top = topPadding.roundToPx()
        val extra = peekExtraGap(
            heights = placeables.map { it.height },
            viewportHeight = viewportHeight,
            padding = top..bottomPadding.roundToPx(),
            rules = PeekRules(
                extraRange = (-PEEK_LESS).roundToPx()..PEEK_MORE.roundToPx(),
                minPeekHeight = MIN_PEEK_HEIGHT.roundToPx(),
            ),
        )
        val height = top + placeables.sumOf { it.height } + extra * (placeables.size - 1).coerceAtLeast(0) +
            bottomPadding.roundToPx()
        layout(constraints.maxWidth, height) {
            var y = top
            placeables.forEach {
                it.place(0, y)
                y += it.height + extra
            }
        }
    }
}

/**
 * How children may be moved for [peekExtraGap]: the space added between them within [extraRange] (px, negative to
 * take some away); children shorter than [minPeekHeight] (dividers, spacers) don't count as cut.
 */
internal data class PeekRules(val extraRange: IntRange, val minPeekHeight: Int)

/**
 * The space to add between children of [heights] (px) so that the child crossing the bottom of [viewportHeight] shows
 * about half of itself, within [rules]: 0 when everything fits. [padding] is the space above (`first`) and below
 * (`last`) the children. The smallest change that cuts a child between 30% and 70% wins, else the one closest to half.
 */
internal fun peekExtraGap(heights: List<Int>, viewportHeight: Int, padding: IntRange, rules: PeekRules): Int {
    if (padding.first + heights.sum() + padding.last <= viewportHeight) return 0
    val candidates = rules.extraRange.map { extra ->
        extra to cutFraction(heights, viewportHeight, padding.first, extra, rules.minPeekHeight)
    }
    return candidates.filter { (_, cut) -> cut in GOOD_CUT }.minByOrNull { (extra, _) -> abs(extra) }?.first
        ?: candidates.minBy { (extra, cut) -> abs(cut - HALF) * rules.extraRange.count() + abs(extra) }.first
}

/** How much of the child crossing the bottom shows with [extra] between children; 0 when none worth cutting does. */
private fun cutFraction(heights: List<Int>, viewportHeight: Int, top: Int, extra: Int, minPeekHeight: Int): Float {
    var y = top
    heights.forEach { height ->
        if (y + height > viewportHeight) {
            return if (height < minPeekHeight || y >= viewportHeight) 0f else (viewportHeight - y).toFloat() / height
        }
        y += height + extra
    }
    return 0f
}

private const val HALF = 0.5f
private val GOOD_CUT = 0.3f..0.7f

/** How much closer, and further apart, children may be moved. */
private val PEEK_LESS = 2.dp
private val PEEK_MORE = 4.dp

/** Children shorter than this aren't worth cutting through. */
private val MIN_PEEK_HEIGHT = 16.dp
