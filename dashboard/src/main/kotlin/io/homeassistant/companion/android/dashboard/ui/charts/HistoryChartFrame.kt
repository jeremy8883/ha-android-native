package io.homeassistant.companion.android.dashboard.ui.charts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.homeassistant.companion.android.dashboard.display.DisplayFormats
import io.homeassistant.companion.android.dashboard.energy.niceTicks
import io.homeassistant.companion.android.dashboard.energy.valueLabel
import io.homeassistant.companion.android.dashboard.history.historyTimeLabel
import io.homeassistant.companion.android.dashboard.history.historyTimeTicks
import kotlin.math.abs

/** How a chart's axis labels are formatted ([formats]), measured and drawn. */
internal class ChartText(
    private val measurer: TextMeasurer,
    private val style: TextStyle,
    val formats: DisplayFormats,
    /** The screen's density, for sizes given in dp. */
    val density: Float = 1f,
) {
    /** [text] on one line in [color] (else the labels'), cut with an ellipsis to fit [width] px. */
    fun measureFitting(text: String, width: Int, color: Color? = null) = measurer.measure(
        text,
        color?.let { style.copy(color = it) } ?: style,
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        constraints = Constraints(maxWidth = width.coerceAtLeast(0)),
    )

    fun measure(text: String, bold: Boolean = false) =
        measurer.measure(text, if (bold) style.copy(fontWeight = FontWeight.Bold) else style)

    companion object {
        /** The axis labels' size, as the frontend's charts. */
        val FONT_SIZE = 12.sp
    }
}

/** What the legend says: its toggle's label and its overflow chip's ("More", "Less"). */
internal data class HistoryLegendTexts(val toggle: String, val more: String, val less: String)

/** The span of a chart's time axis, epoch ms. */
internal class TimeSpan(val start: Double, val end: Double)

/**
 * Where a history chart's plot is: between a value axis (its [ticks], scaled to the values) on the left and a time
 * axis from [time] start to end below.
 */
internal class HistoryFrame(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val time: TimeSpan,
    val ticks: List<Double>,
) {
    fun x(time: Double): Float {
        val span = (this.time.end - this.time.start).coerceAtLeast(1.0)
        return (left + (time - this.time.start) / span * (right - left)).toFloat()
    }

    fun y(value: Double): Float {
        val low = ticks.firstOrNull() ?: 0.0
        val high = ticks.lastOrNull()?.takeIf { it != low } ?: (low + 1)
        return (bottom - (value - low) / (high - low) * (bottom - top)).toFloat()
    }

    /** Where an area's base is: 0 when the axis shows it, else its bottom, like ECharts' `origin: auto`. */
    val baseline: Float get() = y(0.0.coerceIn(ticks.first(), ticks.last()))

    /** The time of [times] nearest to [px], within the plot. */
    fun timeAt(px: Float, times: List<Double>): Double? =
        if (px < left || px > right) null else times.minByOrNull { abs(x(it) - px) }
}

/**
 * The frame of a chart of [values] over [time], its value labels with [digits] fraction digits, its plot starting at
 * least [minLeft] px from the left (so charts drawn one above the other line up).
 */
internal fun DrawScope.historyFrame(
    values: List<Double>,
    time: TimeSpan,
    digits: Int,
    text: ChartText,
    minLeft: Float = 0f,
): HistoryFrame {
    val ticks = valueTicks(values)
    val labelHeight = text.measure("0").size.height
    return HistoryFrame(
        left = maxOf(valueAxisWidth(ticks, digits, text) + LABEL_GAP.toPx(), minLeft),
        // Room for the unit above the axis and half the top label
        top = labelHeight * 2f,
        right = size.width - EDGE.toPx(),
        bottom = size.height - labelHeight - LABEL_GAP.toPx(),
        time = time,
        ticks = ticks,
    )
}

/** The value axis's ticks for [values]. */
internal fun valueTicks(values: List<Double>): List<Double> =
    niceTicks(values.minOrNull() ?: 0.0, values.maxOrNull() ?: 0.0, scale = true)

/** The width of the value axis's widest label, px. */
internal fun valueAxisWidth(ticks: List<Double>, digits: Int, text: ChartText): Int =
    ticks.maxOf { text.measure(text.formats.valueLabel(it, digits)).size.width }

/** The value axis: [unit] above it, a grid line and a label at each tick. */
internal fun DrawScope.drawValueAxis(
    frame: HistoryFrame,
    unit: String?,
    digits: Int,
    text: ChartText,
    lineColor: Color,
) {
    val formats = text.formats
    unit?.trim()?.ifEmpty { null }?.let { drawText(text.measure(it), topLeft = Offset.Zero) }
    frame.ticks.forEach { tick ->
        val y = frame.y(tick)
        drawLine(lineColor, Offset(frame.left, y), Offset(frame.right, y), strokeWidth = 1.dp.toPx())
        val label = text.measure(formats.valueLabel(tick, digits))
        drawText(label, topLeft = Offset(frame.left - LABEL_GAP.toPx() - label.size.width, y - label.size.height / 2))
    }
}

/** The time axis's labels below the plot from [left] to [right] over [time], at hours (minutes over a short span). */
internal fun DrawScope.drawTimeAxis(left: Float, right: Float, top: Float, time: TimeSpan, text: ChartText) {
    val formats = text.formats
    val span = (time.end - time.start).coerceAtLeast(1.0)
    val maxLabels = ((right - left) / MIN_TICK_SPACING.toPx()).toInt().coerceAtLeast(1)
    val spanMillis = (time.end - time.start).toLong()
    formats.historyTimeTicks(time.start.toLong(), time.end.toLong(), maxLabels).forEach { tick ->
        val axisLabel = formats.historyTimeLabel(tick, spanMillis)
        val label = text.measure(axisLabel.text, bold = axisLabel.bold)
        val x = (left + (tick - time.start) / span * (right - left)).toFloat()
        drawText(label, topLeft = Offset((x - label.size.width / 2).coerceIn(0f, size.width - label.size.width), top))
    }
}

/** The gap between labels and the plot. */
internal val LABEL_GAP = 8.dp
private val EDGE = 1.dp
private val MIN_TICK_SPACING = 64.dp

/** The runs of [points] between gaps ([isGap]). */
internal fun <T> runsBetweenGaps(points: List<T>, isGap: (T) -> Boolean): List<List<T>> {
    val runs = mutableListOf<List<T>>()
    var start = -1
    points.forEachIndexed { index, point ->
        when {
            isGap(point) -> {
                if (start >= 0) runs += points.subList(start, index)
                start = -1
            }
            start < 0 -> start = index
        }
    }
    if (start >= 0) runs += points.subList(start, points.size)
    return runs
}
