package io.homeassistant.companion.android.dashboard.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.display.DisplayFormats
import io.homeassistant.companion.android.dashboard.history.TimelineBand
import io.homeassistant.companion.android.dashboard.history.TimelineChart
import io.homeassistant.companion.android.dashboard.history.TimelineTooltip
import io.homeassistant.companion.android.dashboard.history.timelineTooltip
import io.homeassistant.companion.android.dashboard.ui.cards.energy.ChartTooltipCard
import io.homeassistant.companion.android.dashboard.ui.cards.energy.TooltipLine

/**
 * The timelines of entities' states, drawn like the frontend's (state-history-chart-timeline): a row per entity,
 * a band per state in its colour, labelled when it fits, over [time]. A tap on a band shows its tooltip: its entity
 * (with [showNames]), state, start, end and duration (led by [durationLabel]).
 * With [showNames], each row's entity is named on its left, in a column at least [minLeft] wide. Pinching zooms
 * ([zoom], shared by the History panel's charts), and with a [zoomResetLabel] the chart has its own reset button.
 *
 * Upstream: `state-history-chart-timeline` (src/components/chart/state-history-chart-timeline.ts)
 */
@Composable
internal fun TimelineChartView(
    timelines: List<TimelineChart>,
    time: TimeSpan,
    formats: DisplayFormats,
    dark: Boolean,
    durationLabel: String,
    modifier: Modifier = Modifier,
    showNames: Boolean = false,
    minLeft: Dp = 0.dp,
    zoom: ChartZoomState = remember { ChartZoomState() },
    zoomResetLabel: String? = null,
) {
    val measurer = rememberTextMeasurer()
    val colors = LocalHAColorScheme.current
    val text =
        ChartText(
            measurer,
            TextStyle(color = colors.colorTextPrimary, fontSize = ChartText.FONT_SIZE),
            formats,
            LocalDensity.current.density,
        )
    val nameColor = colors.colorTextPrimary
    val bandColors = remember(timelines, dark) {
        timelines.map { timeline -> timeline.bands.map { TimelineColors.resolve(it.color, dark) } }
    }
    // Zooming hides the tooltip, as the frontend does on touch
    var selected by remember(timelines, zoom.zoom) { mutableStateOf<Pair<Int, Int>?>(null) }
    val height = ROW_HEIGHT * timelines.size + AXIS_HEIGHT
    val axis = time.zoomed(zoom.zoom)
    // Where the plot is (left, width) and a row's height, px, as last drawn
    val layout = remember { FloatArray(LAYOUT_SIZE) }
    Box(modifier.fillMaxWidth().height(height)) {
        Canvas(
            Modifier.fillMaxWidth().height(height)
                .chartZoomGestures(zoom) { layout[0]..layout[1] }
                .chartTaps(zoom, timelines) { offset ->
                    val left = layout[0]
                    val hit =
                        bandAt(offset.copy(x = offset.x - left), timelines, axis.shown, layout[1] - left, layout[2])
                    selected = if (hit == selected) null else hit
                },
        ) {
            val left = plotLeft(timelines, showNames, minLeft.toPx(), text)
            layout[0] = left
            layout[1] = size.width
            layout[2] = ROW_HEIGHT.toPx()
            val shown = axis.shown
            timelines.forEachIndexed { row, timeline ->
                if (showNames) drawName(timeline.name, row, left, text, nameColor)
                val span = (shown.end - shown.start).coerceAtLeast(1.0)
                // Bands start no further left than the plot, nor end past it
                val x = { at: Double ->
                    (left + (at - shown.start) / span * (size.width - left)).toFloat().coerceIn(left, size.width)
                }
                timeline.bands.forEachIndexed { index, band -> drawBand(band, bandColors[row][index], row, x, text) }
            }
            val axisTop = ROW_HEIGHT.toPx() * timelines.size + LABEL_GAP.toPx()
            drawTimeAxis(TimeAxisPlace(left, size.width, axisTop), axis, text, colors.colorBorderNeutralQuiet)
        }
        selected?.let { (row, index) ->
            BandTooltip(timelines, row to index, bandColors) {
                formats.timelineTooltip(it.first, it.second, showNames, durationLabel)
            }
        }
        zoomResetLabel?.let { ChartZoomReset(zoom, it, Modifier.align(Alignment.TopEnd).padding(RESET_PADDING)) }
    }
}

/**
 * The tooltip of [timelines]' band at [at] (row, index), as [tooltip] words it: the entity, then the state by its
 * colour, its start, end and duration on their own lines.
 *
 * Upstream: part of `state-history-chart-timeline` (src/components/chart/state-history-chart-timeline.ts)
 */
@Composable
private fun BandTooltip(
    timelines: List<TimelineChart>,
    at: Pair<Int, Int>,
    bandColors: List<List<Color>>,
    tooltip: (Pair<TimelineChart, TimelineBand>) -> TimelineTooltip,
) {
    val (row, index) = at
    val timeline = timelines.getOrNull(row) ?: return
    val band = timeline.bands.getOrNull(index) ?: return
    val words = tooltip(timeline to band)
    // Over whatever is below: the tooltip is taller than a row or two of timelines
    Popup(alignment = Alignment.TopStart) { TooltipContent(words, bandColors[row][index]) }
}

/**
 * The tooltip's lines: the entity, then the state by its [color], its start, end and duration.
 *
 * Upstream: part of `state-history-chart-timeline` (src/components/chart/state-history-chart-timeline.ts)
 */
@Composable
private fun TooltipContent(words: TimelineTooltip, color: Color) {
    ChartTooltipCard(
        title = words.title,
        rows = listOf(
            TooltipLine(color, words.state),
            TooltipLine(null, words.start),
            TooltipLine(null, words.end),
            TooltipLine(null, words.duration),
        ),
        total = null,
    )
}

/** Where the bands start: after the names' column when they're shown, at least [minLeft]. */
private fun plotLeft(timelines: List<TimelineChart>, showNames: Boolean, minLeft: Float, text: ChartText): Float =
    if (showNames) maxOf(text.timelineNamesWidth(timelines.map { it.name }), minLeft) else minLeft

/**
 * The width of the names' column of timelines named [names]: the widest name and its margin, at most
 * [MAX_NAMES_WIDTH] (the frontend's `maxInternalLabelWidth` on a phone), px.
 */
internal fun ChartText.timelineNamesWidth(names: List<String>): Float {
    val widest = names.maxOfOrNull { measure(it).size.width } ?: 0
    return minOf(widest + NAME_MARGIN.value * density, MAX_NAMES_WIDTH.value * density)
}

/** [name] right-aligned before the plot at [left], cut with an ellipsis to fit. */
private fun DrawScope.drawName(name: String, row: Int, left: Float, text: ChartText, color: Color) {
    val width = (left - NAME_MARGIN.toPx()).toInt().coerceAtLeast(0)
    val label = text.measureFitting(name, width, color)
    val top = ROW_HEIGHT.toPx() * row + (ROW_HEIGHT.toPx() - label.size.height) / 2
    drawText(label, topLeft = Offset(left - NAME_MARGIN.toPx() - label.size.width, top))
}

/** [band] in [row], [x] placing its times, in [color] with its label when it fits. */
private fun DrawScope.drawBand(band: TimelineBand, color: Color, row: Int, x: (Double) -> Float, text: ChartText) {
    val left = x(band.start)
    val right = x(band.end)
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

private val ROW_HEIGHT: Dp = 30.dp
private val NAME_MARGIN: Dp = 5.dp
private val MAX_NAMES_WIDTH: Dp = 105.dp
private val BAR_HEIGHT: Dp = 20.dp
private val AXIS_HEIGHT: Dp = 30.dp
private val LABEL_PADDING: Dp = 4.dp
private const val HALF = 0.5f
private const val LAYOUT_SIZE = 3

/** A label is shown when the band fits at least this share of it (the rest is cut off). */
private const val LABEL_MIN_SHARE = 3f
