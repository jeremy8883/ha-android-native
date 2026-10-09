package io.homeassistant.companion.android.dashboard.ui.charts

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.display.DisplayFormats
import io.homeassistant.companion.android.dashboard.history.StatisticPoint
import io.homeassistant.companion.android.dashboard.history.StatisticSeries
import io.homeassistant.companion.android.dashboard.history.StatisticsChart
import io.homeassistant.companion.android.dashboard.ui.cards.energy.ChartTooltipCard
import io.homeassistant.companion.android.dashboard.ui.cards.energy.TooltipLine
import io.homeassistant.companion.android.dashboard.ui.cards.energy.graphColorVariable
import io.homeassistant.companion.android.dashboard.ui.theme.resolveVariable
import java.math.BigDecimal
import java.time.Instant

/**
 * A sensor's statistics drawn like the frontend's statistics chart: the band between the minimum and maximum
 * shaded, and the mean as a smoothed line, in the graph palette's first colour. A tap shows the values of the
 * nearest period.
 */
@Composable
internal fun StatisticsChartView(
    chart: StatisticsChart,
    formats: DisplayFormats,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val colors = LocalHAColorScheme.current
    val text =
        ChartText(measurer, TextStyle(color = colors.colorTextSecondary, fontSize = ChartText.FONT_SIZE), formats)
    val color = remember(dark) { resolveVariable(graphColorVariable(0), dark) ?: Color.Gray }
    val values = remember(chart) { chart.series.flatMap { s -> s.points.mapNotNull { it.value } } }
    val times = remember(chart) { chart.series.flatMap { s -> s.points.map { it.x } }.distinct().sorted() }
    val time = remember(times) { TimeSpan(times.firstOrNull() ?: 0.0, times.lastOrNull() ?: 0.0) }
    val frame = remember { arrayOfNulls<HistoryFrame>(1) }
    var selected by remember(chart) { mutableStateOf<Double?>(null) }
    Box(modifier.fillMaxWidth().height(CHART_HEIGHT)) {
        Canvas(
            Modifier.fillMaxWidth().height(CHART_HEIGHT).pointerInput(chart) {
                detectTapGestures { offset ->
                    val at = frame[0]?.timeAt(offset.x, times)
                    selected = if (at == selected) null else at
                }
            },
        ) {
            val layout = historyFrame(values, time, chart.yFractionDigits, text)
            frame[0] = layout
            drawValueAxis(layout, chart.unit, chart.yFractionDigits, text, colors.colorBorderNeutralQuiet)
            drawTimeAxis(layout.left, layout.right, layout.bottom + LABEL_GAP.toPx(), time, text)
            drawBand(layout, chart.series, color)
            chart.series.filterNot { it.hidden }.forEach { series -> drawLine(layout, series.points, color) }
            selected?.let { at ->
                val x = layout.x(at)
                drawLine(colors.colorBorderNeutralQuiet, Offset(x, layout.top), Offset(x, layout.bottom), 1.dp.toPx())
            }
        }
        selected?.let { at ->
            val rows = chart.series.mapNotNull { series ->
                series.points.lastOrNull { it.x <= at }?.value?.let { value ->
                    val number = formats.number(BigDecimal.valueOf(value), 0, chart.yFractionDigits.coerceAtLeast(1))
                    TooltipLine(color, "${series.name}: $number ${chart.unit.orEmpty()}".trimEnd())
                }
            }
            if (rows.isNotEmpty()) {
                ChartTooltipCard(
                    formats.dateTime(Instant.ofEpochMilli(at.toLong())),
                    rows,
                    total = null,
                )
            }
        }
    }
}

/** The band between its bottom and top series, smoothed like the lines, in runs between gaps. */
private fun DrawScope.drawBand(frame: HistoryFrame, series: List<StatisticSeries>, color: Color) {
    val top = series.firstOrNull { it.bandTop } ?: return
    val bottom = series.firstOrNull { it.band && !it.bandTop } ?: return
    runs(top.points).zip(runs(bottom.points)).forEach { (upper, lower) ->
        val path = smoothPath(frame.offsets(upper))
        appendSmooth(path, frame.offsets(lower).asReversed(), moveFirst = false)
        path.close()
        drawPath(path, color.copy(alpha = BAND_ALPHA))
    }
}

private fun DrawScope.drawLine(frame: HistoryFrame, points: List<StatisticPoint>, color: Color) {
    runs(points).forEach { run -> drawPath(smoothPath(frame.offsets(run)), color, style = Stroke(LINE_WIDTH.toPx())) }
}

/** Where [points] are in the plot. */
private fun HistoryFrame.offsets(points: List<StatisticPoint>) =
    points.mapNotNull { point -> point.value?.let { Offset(x(point.x), y(it)) } }

private fun runs(points: List<StatisticPoint>) = runsBetweenGaps(points) { it.value == null }

private val CHART_HEIGHT = 200.dp
private val LINE_WIDTH = 1.5.dp

/** The band's fill, `3F` of the frontend's colour. */
private const val BAND_ALPHA = 0x3F / 255f
