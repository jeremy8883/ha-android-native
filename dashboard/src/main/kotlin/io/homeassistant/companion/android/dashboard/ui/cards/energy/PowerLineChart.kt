package io.homeassistant.companion.android.dashboard.ui.cards.energy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.display.DisplayFormats
import io.homeassistant.companion.android.dashboard.energy.PowerPoint
import io.homeassistant.companion.android.dashboard.energy.PowerSeries
import io.homeassistant.companion.android.dashboard.energy.PowerSourcesGraphModel
import io.homeassistant.companion.android.dashboard.energy.PowerStack
import io.homeassistant.companion.android.dashboard.energy.PowerTooltip
import io.homeassistant.companion.android.dashboard.energy.niceTicks
import io.homeassistant.companion.android.dashboard.energy.timeTickLabel
import io.homeassistant.companion.android.dashboard.energy.timeTicks
import io.homeassistant.companion.android.dashboard.energy.valueLabel
import io.homeassistant.companion.android.dashboard.ui.charts.appendSmooth
import io.homeassistant.companion.android.dashboard.ui.charts.smoothPath
import io.homeassistant.companion.android.dashboard.ui.theme.resolveVariable
import kotlin.math.abs

/**
 * The power sources graph drawn like the frontend's ECharts lines: smoothed areas stacked above and below 0, the use
 * as a dashed line on top, a value axis with round ticks and a time axis. A tap shows the values at the nearest time
 * ([tooltip]); [hidden] series (by source id) are left out of the stacks.
 */
@Composable
internal fun PowerLineChart(
    graph: PowerSourcesGraphModel,
    formats: DisplayFormats,
    hidden: Set<String>,
    tooltip: (Long) -> PowerTooltip?,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val colors = LocalHAColorScheme.current
    val text = LineAxisText(measurer, TextStyle(color = colors.colorTextSecondary, fontSize = AXIS_FONT))
    val lines = remember(graph, hidden, dark) { stackedLines(graph, hidden, dark) }
    val times = remember(graph) { graph.series.firstOrNull()?.points.orEmpty().map { it.x } }
    // Drawing decides the geometry; taps read it
    val frame = remember { arrayOfNulls<LineFrame>(1) }
    var selected by remember(graph) { mutableStateOf<Long?>(null) }
    Box(modifier.fillMaxWidth().height(CHART_HEIGHT)) {
        Canvas(
            Modifier.fillMaxWidth().height(CHART_HEIGHT).pointerInput(graph) {
                detectTapGestures { offset ->
                    val time = frame[0]?.timeAt(offset.x, times)
                    selected = if (time == selected) null else time
                }
            },
        ) {
            val layout = LineFrame.of(this, graph, lines, formats, text)
            frame[0] = layout
            drawLineAxes(layout, formats, text, colors.colorBorderNeutralQuiet)
            lines.sortedBy { it.z }.forEach { drawStackedLine(layout, it) }
            selected?.let { time ->
                val x = layout.x(time)
                drawLine(colors.colorBorderNeutralQuiet, Offset(x, layout.top), Offset(x, layout.bottom), 1.dp.toPx())
            }
        }
        selected?.let(tooltip)?.let { shown ->
            val fallback = colors.colorTextSecondary
            ChartTooltipCard(
                title = shown.title,
                rows = shown.rows.map {
                    TooltipLine(lineColor(it.series, dark) ?: fallback, "${it.series.name}: ${it.value}")
                },
                total = null,
            )
        }
    }
}

/** A series as drawn: its top ([values]) and what it's stacked on ([base]), at the graph's times. */
private class StackedLine(
    val series: PowerSeries,
    val values: List<PowerPoint>,
    val base: List<PowerPoint>?,
    val color: Color,
    val z: Int,
)

/**
 * The shown series with their values stacked on the ones before in their stack, and the use line, in ECharts'
 * drawing order (`z`): areas above 0 drawn grid, battery then solar, those below over them, the use on top.
 */
private fun stackedLines(graph: PowerSourcesGraphModel, hidden: Set<String>, dark: Boolean): List<StackedLine> {
    val tops = mutableMapOf<PowerStack, List<PowerPoint>>()
    var positive = 0
    var negative = 0
    return graph.series.filterNot { it.id.removeSuffix(NEGATIVE) in hidden }.mapNotNull { series ->
        val color = lineColor(series, dark) ?: return@mapNotNull null
        val stack = series.stack ?: return@mapNotNull StackedLine(series, series.points, null, color, USAGE_Z)
        val base = tops[stack] ?: series.points.map { PowerPoint(it.x, 0.0) }
        val values = series.points.zip(base) { point, below -> PowerPoint(point.x, point.y + below.y) }
        tops[stack] = values
        val z = if (stack == PowerStack.POSITIVE) POSITIVE_Z - positive++ else NEGATIVE_Z - negative++
        StackedLine(series, values, base, color, z)
    }
}

private fun lineColor(series: PowerSeries, dark: Boolean): Color? = resolveVariable(series.color, dark)

/** How the axes' labels are measured. */
private class LineAxisText(val measurer: TextMeasurer, val style: TextStyle) {
    fun measure(text: String) = measurer.measure(text, style)
}

/** Where everything goes in the chart: the plot between the axes and the value [ticks]. */
private class LineFrame(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val graph: PowerSourcesGraphModel,
    val ticks: List<Double>,
) {
    fun x(time: Long): Float {
        val span = (graph.xMax - graph.xMin).toFloat().coerceAtLeast(1f)
        return left + (time - graph.xMin) / span * (right - left)
    }

    fun y(value: Double): Float =
        (bottom - (value - ticks.first()) / (ticks.last() - ticks.first()) * (bottom - top)).toFloat()

    /** The time of [times] nearest to [px], within the plot. */
    fun timeAt(px: Float, times: List<Long>): Long? =
        if (px < left || px > right) null else times.minByOrNull { abs(x(it) - px) }

    companion object {
        fun of(
            scope: DrawScope,
            graph: PowerSourcesGraphModel,
            lines: List<StackedLine>,
            formats: DisplayFormats,
            text: LineAxisText,
        ): LineFrame = with(scope) {
            val values = lines.flatMap { line -> line.values.map { it.y } }
            val ticks = niceTicks(values.minOrNull() ?: 0.0, values.maxOrNull() ?: 0.0)
            val labelWidth = ticks.maxOf { text.measure(formats.valueLabel(it, graph.yFractionDigits)).size.width }
            val labelHeight = text.measure("0").size.height
            // Room for the unit above the axis and half the top label
            LineFrame(
                left = labelWidth + LABEL_GAP.toPx(),
                top = TOP.toPx() + labelHeight * 2,
                right = size.width - EDGE.toPx(),
                bottom = size.height - labelHeight - LABEL_GAP.toPx(),
                graph = graph,
                ticks = ticks,
            )
        }
    }
}

private fun DrawScope.drawLineAxes(layout: LineFrame, formats: DisplayFormats, text: LineAxisText, lineColor: Color) {
    val graph = layout.graph
    drawText(text.measure(KW), topLeft = Offset.Zero)
    layout.ticks.forEach { tick ->
        val y = layout.y(tick)
        drawLine(lineColor, Offset(layout.left, y), Offset(layout.right, y), strokeWidth = 1.dp.toPx())
        val label = text.measure(formats.valueLabel(tick, graph.yFractionDigits))
        drawText(label, topLeft = Offset(layout.left - LABEL_GAP.toPx() - label.size.width, y - label.size.height / 2))
    }
    val maxLabels = ((layout.right - layout.left) / MIN_TICK_SPACING.toPx()).toInt().coerceAtLeast(1)
    formats.timeTicks(graph.xMin, graph.xMax, graph.period, maxLabels).forEach { tick ->
        val label = text.measure(formats.timeTickLabel(tick, graph.period))
        val x = (layout.x(tick) - label.size.width / 2).coerceIn(0f, size.width - label.size.width)
        drawText(label, topLeft = Offset(x, layout.bottom + LABEL_GAP.toPx()))
    }
}

/**
 * A series: its area between its line and what it's stacked on, faded away from 0, then its line; the use as a
 * dashed line without an area.
 */
private fun DrawScope.drawStackedLine(layout: LineFrame, line: StackedLine) {
    val top = line.values.map { Offset(layout.x(it.x), layout.y(it.y)) }
    if (top.isEmpty()) return
    val base = line.base
    if (base == null) {
        val dash = PathEffect.dashPathEffect(floatArrayOf(DASH.toPx(), GAP.toPx()))
        drawPath(smoothPath(top), line.color, style = Stroke(USAGE_WIDTH.toPx(), pathEffect = dash))
        return
    }
    val bottom = base.map { Offset(layout.x(it.x), layout.y(it.y)) }
    val area = smoothPath(top).apply {
        appendSmooth(this, bottom.asReversed(), moveFirst = false)
        close()
    }
    val bounds = area.getBounds()
    // ECharts' gradient spans the area: strongest away from 0
    val strong = line.color.copy(alpha = AREA_STRONG)
    val faint = line.color.copy(alpha = AREA_FAINT)
    val stops = if (line.series.stack == PowerStack.POSITIVE) listOf(strong, faint) else listOf(faint, strong)
    drawPath(area, Brush.verticalGradient(stops, startY = bounds.top, endY = bounds.bottom))
    drawPath(smoothPath(top), line.color, style = Stroke(LINE_WIDTH.toPx()))
}

private const val NEGATIVE = "-negative"
private const val KW = "kW"
private const val AREA_STRONG = 0.75f
private const val AREA_FAINT = 0.25f
private const val POSITIVE_Z = 3
private const val NEGATIVE_Z = 4
private const val USAGE_Z = 5
private val CHART_HEIGHT = 250.dp
private val AXIS_FONT = 12.sp
private val LABEL_GAP = 8.dp
private val TOP = 4.dp
private val EDGE = 1.dp
private val LINE_WIDTH = 1.dp
private val USAGE_WIDTH = 1.5.dp
private val DASH = 7.dp
private val GAP = 2.dp
private val MIN_TICK_SPACING = 56.dp
