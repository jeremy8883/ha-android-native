package io.homeassistant.companion.android.dashboard.ui.cards.energy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.energy.PlacedLink
import io.homeassistant.companion.android.dashboard.energy.PlacedNode
import io.homeassistant.companion.android.dashboard.energy.SankeyColor
import io.homeassistant.companion.android.dashboard.energy.SankeyData
import io.homeassistant.companion.android.dashboard.energy.SankeyInput
import io.homeassistant.companion.android.dashboard.energy.SankeyLayout
import io.homeassistant.companion.android.dashboard.energy.layout
import io.homeassistant.companion.android.dashboard.energy.layoutInput
import io.homeassistant.companion.android.dashboard.ui.theme.resolveVariable
import kotlin.math.min

/**
 * A sankey chart like the frontend's `ha-sankey-chart`: columns of nodes left to right (top to bottom when
 * [vertical]) with flows between them in gradients of their colours. A tap on a node with an entity opens it
 * ([onOpen]); other taps show the node's or flow's value.
 */
@Composable
internal fun SankeyChart(
    data: SankeyData,
    vertical: Boolean,
    formatValue: (Double) -> String,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val input = remember(data) { data.layoutInput() }
    val measurer = rememberTextMeasurer()
    val dark = isSystemInDarkTheme()
    val textColor = LocalHAColorScheme.current.colorTextPrimary
    // Drawing decides the layout; taps read it
    val drawn = remember { arrayOfNulls<Drawn>(1) }
    var tapped by remember(data) { mutableStateOf<String?>(null) }
    val height = if (vertical) VERTICAL_HEIGHT else HORIZONTAL_HEIGHT
    Box(modifier.fillMaxWidth().height(height)) {
        Canvas(
            Modifier.fillMaxWidth().height(height).pointerInput(data, vertical) {
                detectTapGestures { offset ->
                    val (layout, origin, frame) = drawn[0] ?: return@detectTapGestures
                    val hit = hitTest(layout, origin, offset, frame, formatValue)
                    hit?.second?.let(onOpen)
                    tapped = hit?.takeIf { it.second == null }?.first
                }
            },
        ) {
            drawn[0] = drawSankey(input, vertical, dark, measurer, textColor)
        }
        tapped?.let { SankeyTooltip(it) }
    }
}

/** What was drawn, for taps: the layout, where it starts, and how. */
private data class Drawn(val layout: SankeyLayout, val origin: Offset, val frame: SankeyFrame)

private fun DrawScope.drawSankey(
    input: SankeyInput,
    vertical: Boolean,
    dark: Boolean,
    measurer: TextMeasurer,
    textColor: Color,
): Drawn {
    val columns = input.columns.size.coerceAtLeast(1)
    val frame = SankeyFrame(
        vertical = vertical,
        nodeWidth = NODE_WIDTH.toPx(),
        labelSpace = (size.width / columns - FRONTEND_NODE_SIZE.toPx() - LABEL_DISTANCE.toPx()).coerceAtLeast(0f),
    )
    // The chart's margins (`top`/`bottom`/`left`/`right` of the series)
    val area = if (vertical) {
        Rect(MARGIN.toPx(), 0f, size.width - MARGIN.toPx(), size.height - VERTICAL_BOTTOM.toPx())
    } else {
        Rect(0f, MARGIN.toPx(), size.width - frame.labelSpace - LABEL_DISTANCE.toPx(), size.height - MARGIN.toPx())
    }
    val layout = if (vertical) {
        input.layout(area.height, area.width, frame.nodeWidth, NODE_GAP.toPx())
    } else {
        input.layout(area.width, area.height, frame.nodeWidth, NODE_GAP.toPx())
    }
    val nodeColors = layout.nodes.associate { it.node.id to it.node.color.resolve(dark, textColor) }
    translate(area.left, area.top) {
        layout.links.forEach { drawLink(it, vertical, frame.nodeWidth, nodeColors) }
        layout.nodes.forEach { node ->
            drawRect(
                nodeColors.getValue(node.node.id),
                node.topLeft(vertical),
                node.rectSize(vertical, frame.nodeWidth),
            )
            drawLabel(node, frame, measurer, textColor)
        }
    }
    return Drawn(layout, area.topLeft, frame)
}

/** The tapped node's or flow's value, like the chart's tooltip. */
@Composable
private fun SankeyTooltip(text: String) {
    val colors = LocalHAColorScheme.current
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.colorSurfaceDefault),
        elevation = CardDefaults.cardElevation(defaultElevation = TOOLTIP_ELEVATION),
        shape = RoundedCornerShape(HARadius.M),
        modifier = Modifier.padding(HADimens.SPACE2),
    ) {
        Text(
            text,
            style = HATextStyle.Body,
            color = colors.colorTextPrimary,
            textAlign = TextAlign.Start,
            modifier = Modifier.padding(HADimens.SPACE2),
        )
    }
}

/** How the chart is drawn: its direction, its nodes' thickness and the room for labels after them. */
private class SankeyFrame(val vertical: Boolean, val nodeWidth: Float, val labelSpace: Float)

private fun SankeyColor.resolve(dark: Boolean, fallback: Color): Color = when (this) {
    is SankeyColor.Variable -> resolveVariable(name, dark)
    is SankeyColor.Palette -> resolveVariable(graphColorVariable(index), dark)
} ?: fallback

private fun PlacedNode.topLeft(vertical: Boolean) = if (vertical) Offset(breadth, depth) else Offset(depth, breadth)

private fun PlacedNode.rectSize(vertical: Boolean, nodeWidth: Float) =
    if (vertical) Size(size, nodeWidth) else Size(nodeWidth, size)

/** A flow: a band curving from its source's end to its target, in a gradient of their colours. */
private fun DrawScope.drawLink(link: PlacedLink, vertical: Boolean, nodeWidth: Float, colors: Map<String, Color>) {
    val start = link.source.depth + nodeWidth
    val end = link.target.depth
    val middle = start + (end - start) * CURVENESS
    fun point(depth: Float, breadth: Float) = if (vertical) Offset(breadth, depth) else Offset(depth, breadth)
    val path = Path().apply {
        point(start, link.sourceBreadth).let { moveTo(it.x, it.y) }
        val c1 = point(middle, link.sourceBreadth)
        val c2 = point(middle, link.targetBreadth)
        val to = point(end, link.targetBreadth)
        cubicTo(c1.x, c1.y, c2.x, c2.y, to.x, to.y)
        point(end, link.targetBreadth + link.size).let { lineTo(it.x, it.y) }
        val c3 = point(middle, link.targetBreadth + link.size)
        val c4 = point(middle, link.sourceBreadth + link.size)
        val back = point(start, link.sourceBreadth + link.size)
        cubicTo(c3.x, c3.y, c4.x, c4.y, back.x, back.y)
        close()
    }
    val from = colors.getValue(link.source.node.id)
    val to = colors.getValue(link.target.node.id)
    val brush = Brush.linearGradient(listOf(from, to), point(start, 0f), point(end, 0f))
    drawPath(path, brush, alpha = LINK_ALPHA)
}

/**
 * A node's label: after it, as large as fits its height (up to 12sp); or below it when vertical, as large as lets
 * its longest word fit the node's width. Port of the chart's `labelLayout`.
 */
private fun DrawScope.drawLabel(node: PlacedNode, frame: SankeyFrame, measurer: TextMeasurer, color: Color) {
    val vertical = frame.vertical
    val nodeWidth = frame.nodeWidth
    val labelSpace = frame.labelSpace
    val reference = TextStyle(color = color, fontSize = FONT_SIZE)
    if (vertical) {
        val longest = node.node.label.split(' ').maxByOrNull { it.length }.orEmpty()
        val wordWidth = measurer.measure(longest, reference).size.width.coerceAtLeast(1)
        val available = node.size + LABEL_SLACK_VERTICAL.toPx()
        val fontSize = min(FONT_SIZE.value, available / wordWidth * FONT_SIZE.value)
        if (fontSize <= 1) return
        val text = measurer.measure(
            node.node.label,
            reference.copy(fontSize = fontSize.sp, textAlign = TextAlign.Center),
            // A little slack, so the longest word the size was fitted to isn't broken by rounding
            constraints = Constraints(maxWidth = (available * WRAP_SLACK).toInt().coerceAtLeast(1)),
        )
        drawText(
            text,
            topLeft = Offset(
                node.breadth + node.size / 2 - text.size.width / 2,
                node.depth + nodeWidth + LABEL_DISTANCE.toPx(),
            ),
        )
    } else {
        val lineHeight = measurer.measure(node.node.label, reference).size.height.coerceAtLeast(1)
        val available = node.size + LABEL_SLACK.toPx()
        val fontSize = min(FONT_SIZE.value, available / lineHeight * FONT_SIZE.value)
        if (fontSize <= 1) return
        val text = measurer.measure(
            node.node.label,
            reference.copy(fontSize = fontSize.sp, lineHeight = fontSize.sp),
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            constraints = Constraints(maxWidth = labelSpace.toInt().coerceAtLeast(1)),
        )
        drawText(
            text,
            topLeft = Offset(
                node.depth + nodeWidth + LABEL_DISTANCE.toPx(),
                node.breadth + node.size / 2 - text.size.height / 2,
            ),
        )
    }
}

/** What's under [offset]: a node (its label and value, and entity), or a flow (from → to and value). */
private fun hitTest(
    layout: SankeyLayout,
    origin: Offset,
    offset: Offset,
    frame: SankeyFrame,
    formatValue: (Double) -> String,
): Pair<String, String?>? {
    val vertical = frame.vertical
    val nodeWidth = frame.nodeWidth
    val local = offset - origin
    val depth = if (vertical) local.y else local.x
    val breadth = if (vertical) local.x else local.y
    layout.nodes.firstOrNull {
        depth in it.depth..(it.depth + nodeWidth) &&
            breadth in it.breadth..(it.breadth + it.size)
    }?.let {
        return "${it.node.label}\n${formatValue(it.node.value)}" to it.node.entityId
    }
    return layout.links.firstOrNull { link ->
        val start = link.source.depth + nodeWidth
        val end = link.target.depth
        if (depth !in start..end) return@firstOrNull false
        // The band's breadth there, following its curve closely enough for a tap
        val t = (depth - start) / (end - start).coerceAtLeast(1f)
        val eased = t * t * (3 - 2 * t)
        val top = link.sourceBreadth + (link.targetBreadth - link.sourceBreadth) * eased
        breadth in top..(top + link.size)
    }?.let { "${it.source.node.label} → ${it.target.node.label}\n${formatValue(it.link.value ?: 0.0)}" to null }
}

private val HORIZONTAL_HEIGHT = 340.dp
private val VERTICAL_HEIGHT = 440.dp
private val NODE_WIDTH = 15.dp
private val NODE_GAP = 6.dp
private val MARGIN = 5.dp
private val VERTICAL_BOTTOM = 25.dp
private val LABEL_DISTANCE = 5.dp
private val LABEL_SLACK = 8.dp
private val LABEL_SLACK_VERTICAL = 6.dp
private val FONT_SIZE = 12.sp
private val TOOLTIP_ELEVATION = 4.dp

/** The size the frontend leaves for a node when sharing the width between columns and labels. */
private val FRONTEND_NODE_SIZE = 30.dp
private const val CURVENESS = 0.5f
private const val WRAP_SLACK = 1.05f
private const val LINK_ALPHA = 0.4f
