package io.homeassistant.companion.android.dashboard.ui.cards.energy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import io.homeassistant.companion.android.dashboard.energy.EnergyBarChart
import io.homeassistant.companion.android.dashboard.energy.EnergyBarSeries
import io.homeassistant.companion.android.dashboard.energy.EnergyTooltip
import io.homeassistant.companion.android.dashboard.energy.niceTicks
import io.homeassistant.companion.android.dashboard.energy.timeTickLabel
import io.homeassistant.companion.android.dashboard.energy.timeTicks
import io.homeassistant.companion.android.dashboard.energy.valueLabel
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * An energy bar chart drawn like the frontend's ECharts bars: stacked series above and below zero, the compared
 * period's stack beside the shown one's, a value axis with round ticks and a time axis. A tap on a period shows its
 * [tooltip]; [hidden] series (by id, without the compare prefix) are left out.
 */
@Composable
internal fun EnergyBarChartView(
    chart: EnergyBarChart,
    formats: DisplayFormats,
    hidden: Set<String>,
    tooltip: (List<EnergyBarSeries>, Long) -> EnergyTooltip?,
    formatTotal: (Double) -> String,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val dark = isSystemInDarkTheme()
    val colors = LocalHAColorScheme.current
    val axisText = AxisText(measurer, TextStyle(color = colors.colorTextSecondary, fontSize = AXIS_FONT))
    val shown = remember(chart, hidden) { chart.series.filter { it.id.removePrefix(COMPARE) !in hidden } }
    // Drawing decides the geometry; taps read it
    val geometry = remember { arrayOfNulls<ChartGeometry>(1) }
    var selected by remember(chart) { mutableStateOf<Long?>(null) }
    Box(modifier.fillMaxWidth().height(CHART_HEIGHT)) {
        Canvas(
            Modifier.fillMaxWidth().height(CHART_HEIGHT).pointerInput(chart) {
                detectTapGestures { offset ->
                    val start = geometry[0]?.periodAt(offset.x)
                    selected = if (start == selected) null else start
                }
            },
        ) {
            val layout = ChartGeometry.of(this, chart, shown, formats, axisText)
            geometry[0] = layout
            drawAxes(layout, formats, axisText, colors.colorBorderNeutralQuiet)
            selected?.let { start ->
                layout.highlight(start)?.let { (offset, size) ->
                    drawRect(colors.colorBorderNeutralQuiet.copy(alpha = HIGHLIGHT), offset, size)
                }
            }
            drawBars(layout, shown, dark)
        }
        selected?.let { start -> tooltip(shown, start) }?.let { EnergyTooltipCard(it, dark, formatTotal) }
    }
}

/** How the axes' labels are measured and drawn. */
private class AxisText(val measurer: TextMeasurer, val style: TextStyle) {
    fun measure(text: String) = measurer.measure(text, style)
}

/** The area the bars are drawn in, between the axes. */
private class PlotArea(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** The bars' size and places: [stacks] by whether they're the compared period's, [starts] by x. */
private class BarPlaces(val stackWidth: Float, val stacks: List<Boolean>, val starts: List<Pair<Long, Long>>)

/** Where everything goes in the chart, for drawing and for finding the tapped period. */
private class ChartGeometry(area: PlotArea, val chart: EnergyBarChart, val ticks: List<Double>, places: BarPlaces) {
    val left = area.left
    val top = area.top
    val right = area.right
    val bottom = area.bottom
    val stackWidth = places.stackWidth
    private val stacks = places.stacks
    private val starts = places.starts
    private val minY = ticks.first()
    private val maxY = ticks.last()

    fun x(time: Long): Float {
        val span = (chart.xMax - chart.xMin).toFloat().coerceAtLeast(1f)
        val padding = slot / 2
        return left + padding + (time - chart.xMin) / span * (right - left - 2 * padding)
    }

    fun y(value: Double): Float = (bottom - (value - minY) / (maxY - minY) * (bottom - top)).toFloat()

    /** The width of a period's place on the axis. */
    val slot: Float get() = stackWidth * max(stacks.size, 1) / BAR_SHARE

    /** The left of [compare]'s stack in the period centred on [center]. */
    fun stackLeft(center: Float, compare: Boolean): Float {
        val total = stackWidth * stacks.size + STACK_GAP * stackWidth * (stacks.size - 1)
        val index = stacks.indexOf(compare).coerceAtLeast(0)
        return center - total / 2 + index * stackWidth * (1 + STACK_GAP)
    }

    /** The period start whose bars are nearest to [px], within the plot. */
    fun periodAt(px: Float): Long? {
        if (px < left || px > right) return null
        return starts.minByOrNull { (x, _) -> abs(x(x) - px) }?.second
    }

    /** The band behind the bars of the period starting at [start]. */
    fun highlight(start: Long): Pair<Offset, Size>? {
        val x = starts.firstOrNull { it.second == start }?.first ?: return null
        val center = x(x)
        return Offset(center - slot / 2, top) to Size(slot, bottom - top)
    }

    companion object {
        fun of(
            scope: DrawScope,
            chart: EnergyBarChart,
            series: List<EnergyBarSeries>,
            formats: DisplayFormats,
            text: AxisText,
        ): ChartGeometry = with(scope) {
            val (low, high) = stackedExtent(series)
            val ticks = niceTicks(low, high)
            val labelWidth = ticks.maxOf {
                text.measure(formats.valueLabel(it, chart.yFractionDigits)).size.width
            }
            val labelHeight = text.measure("0").size.height
            val left = labelWidth + LABEL_GAP.toPx()
            // Room for the unit above the axis and half the top label
            val top = TOP.toPx() + labelHeight * 2
            val bottom = size.height - labelHeight - LABEL_GAP.toPx()
            // The shown period's bars and their starts, at the main series' places
            val starts = series.filterNot { it.compare }.flatMap { s -> s.points.map { it.x to it.start } }
                .distinctBy { it.first }.sortedBy { it.first }
            val periods = max(starts.size, 1)
            val stacks = listOfNotNull(true.takeIf { chart.compare }, false)
            val stackWidth = min(
                (size.width - left) / periods * BAR_SHARE / stacks.size,
                MAX_BAR_WIDTH.toPx(),
            )
            ChartGeometry(PlotArea(left, top, size.width, bottom), chart, ticks, BarPlaces(stackWidth, stacks, starts))
        }

        /** The lowest and highest sums of the stacks. */
        private fun stackedExtent(series: List<EnergyBarSeries>): Pair<Double, Double> {
            val sums = mutableMapOf<Pair<Boolean, Long>, Pair<Double, Double>>()
            series.forEach { s ->
                s.points.forEach { p ->
                    val key = s.compare to p.x
                    val (neg, pos) = sums[key] ?: (0.0 to 0.0)
                    sums[key] = if (p.y < 0) neg + p.y to pos else neg to pos + p.y
                }
            }
            return (sums.values.minOfOrNull { it.first } ?: 0.0) to (sums.values.maxOfOrNull { it.second } ?: 0.0)
        }
    }
}

private fun DrawScope.drawAxes(layout: ChartGeometry, formats: DisplayFormats, text: AxisText, lineColor: Color) {
    val chart = layout.chart
    // The unit above the value axis, then a line and a label at each tick
    drawText(text.measure(chart.unit), topLeft = Offset.Zero)
    layout.ticks.forEach { tick ->
        val y = layout.y(tick)
        drawLine(lineColor, Offset(layout.left, y), Offset(layout.right, y), strokeWidth = 1.dp.toPx())
        val label = text.measure(formats.valueLabel(tick, chart.yFractionDigits))
        drawText(label, topLeft = Offset(layout.left - LABEL_GAP.toPx() - label.size.width, y - label.size.height / 2))
    }
    val maxLabels = ((layout.right - layout.left) / MIN_TICK_SPACING.toPx()).toInt().coerceAtLeast(1)
    formats.timeTicks(chart, maxLabels).forEach { tick ->
        val label = text.measure(formats.timeTickLabel(tick, chart.period))
        val x = (layout.x(tick) - label.size.width / 2).coerceIn(0f, size.width - label.size.width)
        drawText(label, topLeft = Offset(x, layout.bottom + LABEL_GAP.toPx()))
    }
}

/** The bars, stacked from zero up and down; only each stack's outermost bars get round ends. */
private fun DrawScope.drawBars(layout: ChartGeometry, series: List<EnergyBarSeries>, dark: Boolean) {
    val radius = CAP_RADIUS.toPx()
    val stroke = BORDER.toPx()
    val tops = mutableMapOf<Pair<Boolean, Long>, Double>()
    val bottoms = mutableMapOf<Pair<Boolean, Long>, Double>()
    // Which series is outermost in each stack, to round its end
    val outermost = mutableMapOf<Triple<Boolean, Long, Boolean>, String>()
    series.forEach { s ->
        s.points.filter { it.y != 0.0 }.forEach { outermost[Triple(s.compare, it.x, it.y > 0)] = s.id }
    }
    series.forEach { s ->
        val variable = ENERGY_KIND_COLORS[s.kind] ?: return@forEach
        val fill = energyColor(variable, dark, s.colorIndex, background = true, compare = s.compare)
        val border = energyColor(variable, dark, s.colorIndex, background = false, compare = s.compare)
        s.points.forEach { p ->
            if (p.y == 0.0) return@forEach
            val key = s.compare to p.x
            val from = if (p.y > 0) tops[key] ?: 0.0 else bottoms[key] ?: 0.0
            val to = from + p.y
            if (p.y > 0) tops[key] = to else bottoms[key] = to
            val left = layout.stackLeft(layout.x(p.x), s.compare)
            val y1 = min(layout.y(from), layout.y(to))
            val y2 = max(layout.y(from), layout.y(to))
            val round = outermost[Triple(s.compare, p.x, p.y > 0)] == s.id
            val path = barPath(Rect(left, y1, left + layout.stackWidth, y2), if (round) radius else 0f, up = p.y > 0)
            drawPath(path, fill)
            drawPath(path, border, style = Stroke(stroke))
        }
    }
}

/** A bar whose end away from zero is rounded by [radius]. */
private fun barPath(bar: Rect, radius: Float, up: Boolean): Path {
    val r = CornerRadius(min(radius, min(bar.width / 2, bar.height)))
    val none = CornerRadius.Zero
    return Path().apply {
        addRoundRect(
            RoundRect(
                left = bar.left,
                top = bar.top,
                right = bar.right,
                bottom = bar.bottom,
                topLeftCornerRadius = if (up) r else none,
                topRightCornerRadius = if (up) r else none,
                bottomLeftCornerRadius = if (up) none else r,
                bottomRightCornerRadius = if (up) none else r,
            ),
        )
    }
}

private const val COMPARE = "compare-"
private val CHART_HEIGHT = 250.dp
private val AXIS_FONT = 12.sp
private val LABEL_GAP = 8.dp
private val TOP = 4.dp
private val MAX_BAR_WIDTH = 50.dp
private val CAP_RADIUS = 4.dp
private val BORDER = 1.dp
private val MIN_TICK_SPACING = 56.dp

/** The share of a period's place its bars take. */
private const val BAR_SHARE = 0.6f

/** The gap between the compared and the shown stacks, as a share of a stack. */
private const val STACK_GAP = 0.3f
private const val HIGHLIGHT = 0.5f
