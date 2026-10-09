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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.display.DisplayFormats
import io.homeassistant.companion.android.dashboard.history.TimelineBand
import io.homeassistant.companion.android.dashboard.history.TimelineChart
import io.homeassistant.companion.android.dashboard.ui.cards.energy.ChartTooltipCard
import io.homeassistant.companion.android.dashboard.ui.cards.energy.TooltipLine
import java.time.Instant

/**
 * The timelines of entities' states, drawn like the frontend's (state-history-chart-timeline): a row per entity,
 * a band per state in its colour, labelled when it fits, over [time]. A tap on a band shows its state and times.
 */
@Composable
internal fun TimelineChartView(
    timelines: List<TimelineChart>,
    time: TimeSpan,
    formats: DisplayFormats,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val colors = LocalHAColorScheme.current
    val text =
        ChartText(measurer, TextStyle(color = colors.colorTextSecondary, fontSize = ChartText.FONT_SIZE), formats)
    val bandColors = remember(timelines, dark) {
        timelines.map { timeline -> timeline.bands.map { TimelineColors.resolve(it.color, dark) } }
    }
    var selected by remember(timelines) { mutableStateOf<Pair<Int, Int>?>(null) }
    val height = ROW_HEIGHT * timelines.size + AXIS_HEIGHT
    Box(modifier.fillMaxWidth().height(height)) {
        Canvas(
            Modifier.fillMaxWidth().height(height).pointerInput(timelines) {
                detectTapGestures { offset ->
                    val hit = bandAt(offset, timelines, time, size.width.toFloat(), ROW_HEIGHT.toPx())
                    selected = if (hit == selected) null else hit
                }
            },
        ) {
            timelines.forEachIndexed { row, timeline ->
                timeline.bands.forEachIndexed { index, band ->
                    drawBand(band, bandColors[row][index], row, time, text)
                }
            }
            drawTimeAxis(0f, size.width, ROW_HEIGHT.toPx() * timelines.size + LABEL_GAP.toPx(), time, text)
        }
        selected?.let { (row, index) ->
            val band = timelines.getOrNull(row)?.bands?.getOrNull(index) ?: return@let
            ChartTooltipCard(
                title = band.label,
                rows = listOf(
                    TooltipLine(
                        bandColors[row][index],
                        "${formats.dateTime(band.start.toInstant())} – ${formats.dateTime(band.end.toInstant())}",
                    ),
                ),
                total = null,
            )
        }
    }
}

private fun DrawScope.drawBand(band: TimelineBand, color: Color, row: Int, time: TimeSpan, text: ChartText) {
    val span = (time.end - time.start).coerceAtLeast(1.0)
    val left = ((band.start - time.start) / span * size.width).toFloat().coerceAtLeast(0f)
    val right = ((band.end - time.start) / span * size.width).toFloat().coerceAtMost(size.width)
    if (right <= left) return
    val top = ROW_HEIGHT.toPx() * row + (ROW_HEIGHT - BAR_HEIGHT).toPx() / 2
    drawRect(color, Offset(left, top), Size(right - left, BAR_HEIGHT.toPx()))
    // The label inside the band, in black or white for contrast, when there's room for some of it
    val label = text.measure(band.label)
    val padding = LABEL_PADDING.toPx()
    if (right - left > padding * 2 + label.size.width / LABEL_MIN_SHARE) {
        clipRect(left + padding, top, right - padding, top + BAR_HEIGHT.toPx()) {
            drawText(
                label,
                color = if (color.luminance() > HALF) Color.Black else Color.White,
                topLeft = Offset(left + padding, top + (BAR_HEIGHT.toPx() - label.size.height) / 2),
            )
        }
    }
}

/** The (row, band) at [offset], if any. */
private fun bandAt(
    offset: Offset,
    timelines: List<TimelineChart>,
    time: TimeSpan,
    width: Float,
    rowHeight: Float,
): Pair<Int, Int>? {
    val row = (offset.y / rowHeight).toInt().takeIf { it in timelines.indices } ?: return null
    val at = time.start + offset.x / width * (time.end - time.start)
    val index = timelines[row].bands.indexOfFirst { at >= it.start && at < it.end }
    return if (index < 0) null else row to index
}

private fun Double.toInstant() = Instant.ofEpochMilli(toLong())

private val ROW_HEIGHT: Dp = 30.dp
private val BAR_HEIGHT: Dp = 20.dp
private val AXIS_HEIGHT: Dp = 30.dp
private val LABEL_PADDING: Dp = 4.dp
private const val HALF = 0.5f

/** A label is shown when the band fits at least this share of it (the rest is cut off). */
private const val LABEL_MIN_SHARE = 3f
