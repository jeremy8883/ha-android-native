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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.display.DisplayFormats
import io.homeassistant.companion.android.dashboard.history.HistoryLineChart
import io.homeassistant.companion.android.dashboard.history.HistoryLineSeries
import io.homeassistant.companion.android.dashboard.history.SeriesColor
import io.homeassistant.companion.android.dashboard.ui.cards.energy.ChartTooltipCard
import io.homeassistant.companion.android.dashboard.ui.cards.energy.TooltipLine
import io.homeassistant.companion.android.dashboard.ui.cards.energy.graphColorVariable
import io.homeassistant.companion.android.dashboard.ui.theme.resolveVariable
import java.math.BigDecimal
import java.time.Instant

/**
 * A history line chart drawn like the frontend's (state-history-chart-line): stepped lines (each value holds until
 * the next), broken at gaps, and the climate's or humidifier's areas, over [time] in [unit]. A tap shows the values
 * at the nearest time.
 */
@Composable
internal fun HistoryLineChartView(
    chart: HistoryLineChart,
    unit: String,
    time: TimeSpan,
    formats: DisplayFormats,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val colors = LocalHAColorScheme.current
    val text =
        ChartText(measurer, TextStyle(color = colors.colorTextSecondary, fontSize = ChartText.FONT_SIZE), formats)
    val seriesColors = remember(chart, dark) { chart.series.map { seriesColor(it.color, dark) } }
    val values = remember(chart) { chart.series.flatMap { s -> s.points.mapNotNull { it.y } } }
    val times = remember(chart) { chart.series.flatMap { s -> s.points.map { it.x } }.distinct().sorted() }
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
            drawValueAxis(layout, unit, chart.yFractionDigits, text, colors.colorBorderNeutralQuiet)
            drawTimeAxis(layout.left, layout.right, layout.bottom + LABEL_GAP.toPx(), time, text)
            chart.series.forEachIndexed { index, series -> drawSeries(layout, series, seriesColors[index]) }
            selected?.let { at ->
                val x = layout.x(at)
                drawLine(colors.colorBorderNeutralQuiet, Offset(x, layout.top), Offset(x, layout.bottom), 1.dp.toPx())
            }
        }
        selected?.let { at ->
            val rows = chart.series.mapIndexedNotNull { index, series ->
                if (series.fill) return@mapIndexedNotNull null
                series.valueAt(at)?.let { value ->
                    val number = formats.number(
                        BigDecimal.valueOf(value),
                        0,
                        chart.yFractionDigits.coerceAtLeast(MIN_DIGITS),
                    )
                    TooltipLine(seriesColors[index], "${series.name}: $number ${unit.trim()}".trimEnd())
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

/** A series' stepped line, or its area down to the axis's base when it's filled. */
private fun DrawScope.drawSeries(frame: HistoryFrame, series: HistoryLineSeries, color: Color) {
    runsBetweenGaps(series.points) { it.y == null }.forEach { run ->
        val path = Path()
        run.forEachIndexed { index, point ->
            val x = frame.x(point.x)
            val y = frame.y(point.y ?: return@forEachIndexed)
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                // Each value holds until the next point
                path.lineTo(x, frame.y(run[index - 1].y ?: return@forEachIndexed))
                path.lineTo(x, y)
            }
        }
        if (series.fill) {
            val first = frame.x(run.first().x)
            val last = frame.x(run.last().x)
            path.lineTo(last, frame.baseline)
            path.lineTo(first, frame.baseline)
            path.close()
            drawPath(path, color.copy(alpha = AREA_ALPHA))
        } else {
            drawPath(path, color, style = Stroke(LINE_WIDTH.toPx()))
        }
    }
}

/** The value in effect at [time]: the last point's at or before it. */
private fun HistoryLineSeries.valueAt(time: Double): Double? = points.lastOrNull { it.x <= time }?.y

private fun seriesColor(color: SeriesColor, dark: Boolean): Color = when (color) {
    is SeriesColor.Palette -> resolveVariable(graphColorVariable(color.index), dark)
    is SeriesColor.Variable -> resolveVariable(color.name, dark)
} ?: Color.Gray

private val CHART_HEIGHT = 200.dp
private val LINE_WIDTH = 1.5.dp

/** The areas' fill, `7F` of the frontend's colour. */
private const val AREA_ALPHA = 0x7F / 255f
private const val MIN_DIGITS = 1
