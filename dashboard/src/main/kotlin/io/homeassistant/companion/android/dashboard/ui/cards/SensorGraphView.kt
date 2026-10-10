package io.homeassistant.companion.android.dashboard.ui.cards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.derive.DisplayColor
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.history.GraphCoordinates
import io.homeassistant.companion.android.dashboard.history.GraphHistory
import io.homeassistant.companion.android.dashboard.history.GraphPoint
import io.homeassistant.companion.android.dashboard.history.SensorGraph
import io.homeassistant.companion.android.dashboard.history.historyKey
import io.homeassistant.companion.android.dashboard.history.sensorGraphCoordinates
import io.homeassistant.companion.android.dashboard.ui.theme.toColor
import java.time.ZonedDateTime

/** What a graph shows: its line (flat while [loading]), why it has none, or that its history failed to load. */
private sealed interface GraphContent {
    data class Line(val coordinates: GraphCoordinates, val loading: Boolean) : GraphContent

    data class Message(val text: String) : GraphContent
}

/**
 * A sensor card's graph, port of `hui-graph-header-footer` and `hui-graph-base` (frontend@20260624.6
 * src/panels/lovelace/header-footer/, src/panels/lovelace/components/): the entity's recent states as a smooth line
 * in the accent colour over a faint fill, a fifth as tall as wide.
 */
@Composable
internal fun SensorGraphView(
    graph: SensorGraph,
    hass: State<HassSnapshot?>,
    now: State<ZonedDateTime?>,
    modifier: Modifier = Modifier,
) {
    var widthDp by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val content by remember(graph) {
        derivedStateOf { hass.value?.graphContent(graph, widthDp.toDouble(), now.value) }
    }
    val color = DisplayColor.Theme(ACCENT).toColor() ?: LocalHAColorScheme.current.colorFillPrimaryLoudResting
    Box(
        modifier.aspectRatio(ASPECT_RATIO).onSizeChanged { widthDp = with(density) { it.width.toDp().value } },
        contentAlignment = Alignment.Center,
    ) {
        when (val shown = content) {
            is GraphContent.Line -> Canvas(Modifier.matchParentSize().clipToBounds()) {
                drawGraph(shown.coordinates, if (shown.loading) null else color, color)
            }
            is GraphContent.Message -> Text(
                shown.text,
                style = HATextStyle.BodyMedium,
                color = LocalHAColorScheme.current.colorTextSecondary,
                modifier = Modifier.fillMaxWidth().padding(HADimens.SPACE2),
            )
            null -> Unit
        }
    }
}

private fun HassSnapshot.graphContent(graph: SensorGraph, width: Double, now: ZonedDateTime?): GraphContent? {
    if (width <= 0 || now == null) return null
    val history = graphHistories[graph.historyKey]
    val states = (history as? GraphHistory.Loaded)?.states
    val nowMillis = now.toInstant().toEpochMilli().toDouble()
    val coordinates = sensorGraphCoordinates(graph, states, this.states[graph.entityId], width, nowMillis)
    val error = localize("ui.components.history_charts.error")
    return when {
        history is GraphHistory.Failed -> GraphContent.Message(history.message?.let { "$error: $it" } ?: error)
        coordinates.points.isNotEmpty() || states == null -> GraphContent.Line(coordinates, loading = states == null)
        else -> GraphContent.Message(localize("ui.components.history_charts.no_history_found"))
    }
}

/**
 * The line (with [fill] under it down to the zero line, none while loading) in a box the coordinates' width wide,
 * scaled to the canvas; a flat line in the middle when there's nothing to draw yet.
 */
private fun DrawScope.drawGraph(coordinates: GraphCoordinates, fill: Color?, line: Color) {
    val points = coordinates.points
    val width = points.lastOrNull()?.x?.takeIf { it > 0 }
    if (width == null) {
        drawLine(
            line.copy(alpha = LINE_ALPHA),
            Offset(0f, size.height / 2),
            Offset(size.width, size.height / 2),
            STROKE.toPx(),
        )
        return
    }
    val scale = size.width / width.toFloat()
    val path = smoothPath(points, scale)
    if (fill != null) {
        val area = Path().apply {
            addPath(path)
            lineTo(size.width, coordinates.yAxisOrigin.toFloat() * scale)
            lineTo(0f, coordinates.yAxisOrigin.toFloat() * scale)
            close()
        }
        drawPath(area, fill.copy(alpha = FILL_ALPHA))
    }
    drawPath(
        path,
        line.copy(alpha = LINE_ALPHA),
        style = Stroke(width = STROKE.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

/** Port of `getPath`: quadratic curves through the midpoints, each bending towards a point. */
private fun smoothPath(points: List<GraphPoint>, scale: Float): Path = Path().apply {
    fun x(point: GraphPoint) = point.x.toFloat() * scale
    fun y(point: GraphPoint) = point.y.toFloat() * scale
    var last = points.first()
    moveTo(x(last), y(last))
    lineTo(x(last), y(last))
    points.forEachIndexed { index, next ->
        if (index > 0) {
            quadraticTo(x(last), y(last), (x(last) + x(next)) / 2, (y(last) + y(next)) / 2)
        }
        last = next
    }
    lineTo(x(last), y(last))
}

private const val ACCENT = "accent"
private const val ASPECT_RATIO = 5f
private const val LINE_ALPHA = 0.8f
private const val FILL_ALPHA = 0.1f
private val STROKE = 2.dp
