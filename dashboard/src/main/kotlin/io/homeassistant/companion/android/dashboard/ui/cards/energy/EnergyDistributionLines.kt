package io.homeassistant.companion.android.dashboard.ui.cards.energy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.dashboard.energy.DistributionFlow
import io.homeassistant.companion.android.dashboard.energy.EnergyDistributionModel
import io.homeassistant.companion.android.dashboard.energy.HomeRing
import kotlin.math.max

// The drawings of the energy distribution card: the lines between the circles, with dots moving along them, and the
// home's ring. Geometry of the SVGs of `hui-energy-distribution-card` (frontend@20260624.6), whose lines use a
// 100×100 view box scaled to cover their area (`xMidYMid slice`) with strokes that don't scale.

/** The lines between the grid, solar, battery and home, with a dot along each flow. */
@Composable
internal fun DistributionLines(model: EnergyDistributionModel, colors: DistributionColors, modifier: Modifier) {
    val time = rememberAnimationTime()
    Canvas(modifier) {
        val box = ViewBox(size)
        clipRect {
            val lines = lines(model, colors, box)
            lines.forEach { drawPath(it.path, it.color, style = Stroke(width = 1.dp.toPx())) }
            val dotRadius = box.scale + DOT_STROKE.toPx()
            lines.forEach { line ->
                dots(line, model, colors).forEach { (flow, color) ->
                    val seconds = model.flows.getValue(flow)
                    val progress = progress(time.longValue, seconds)
                    // Grid to battery runs the battery–grid line backwards
                    drawDot(
                        line.path,
                        if (flow ==
                            DistributionFlow.GRID_TO_BATTERY
                        ) {
                            1 - progress
                        } else {
                            progress
                        },
                        dotRadius,
                        color,
                    )
                }
            }
        }
    }
}

/** The lines the sources configured have, in the frontend's order. */
private fun lines(model: EnergyDistributionModel, colors: DistributionColors, box: ViewBox): List<Line> {
    val hasBattery = model.battery != null
    val hasSolar = model.solar != null
    val hasGrid = model.grid != null
    return buildList {
        if (model.grid?.returned != null && hasSolar) {
            add(
                Line(
                    DistributionFlow.SOLAR_TO_GRID,
                    box.path(if (hasBattery) RETURN_WITH_BATTERY else RETURN),
                    colors.gridOut,
                ),
            )
        }
        if (hasSolar) {
            add(
                Line(
                    DistributionFlow.SOLAR_TO_HOME,
                    box.path(if (hasBattery) SOLAR_WITH_BATTERY else SOLAR),
                    colors.solar,
                ),
            )
        }
        if (hasBattery) {
            add(Line(DistributionFlow.BATTERY_TO_HOME, box.path(BATTERY_HOUSE), colors.batteryOut))
            if (hasGrid) add(batteryGridLine(box, model, colors))
        }
        if (hasBattery &&
            hasSolar
        ) {
            add(Line(DistributionFlow.SOLAR_TO_BATTERY, box.path(BATTERY_SOLAR), colors.batteryIn))
        }
        if (hasGrid) {
            val grid = when {
                hasBattery -> GRID_WITH_BATTERY
                hasSolar -> GRID_WITH_SOLAR
                else -> GRID
            }
            add(Line(DistributionFlow.GRID_TO_HOME, box.path(grid), colors.gridIn))
        }
    }
}

/** The battery–grid line carries both directions; its colour is the export's when the battery went to the grid. */
private fun batteryGridLine(box: ViewBox, model: EnergyDistributionModel, colors: DistributionColors): Line {
    val color = when {
        DistributionFlow.BATTERY_TO_GRID in model.flows -> colors.gridOut
        DistributionFlow.GRID_TO_BATTERY in model.flows -> colors.gridIn
        else -> colors.line
    }
    return Line(DistributionFlow.BATTERY_TO_GRID, box.path(BATTERY_GRID), color)
}

/** The dots a line carries, with their colours: the battery–grid line has one each way. */
private fun dots(
    line: Line,
    model: EnergyDistributionModel,
    colors: DistributionColors,
): List<Pair<DistributionFlow, Color>> = if (line.flow == DistributionFlow.BATTERY_TO_GRID) {
    listOf(DistributionFlow.GRID_TO_BATTERY to colors.gridIn, DistributionFlow.BATTERY_TO_GRID to colors.gridOut)
} else {
    listOf(line.flow to line.color)
}.filter { (flow, _) -> flow in model.flows }

private class Line(val flow: DistributionFlow, val path: Path, val color: Color)

/** A short vertical line under (or, [upwards], above) gas, water and low-carbon, with a dot when [animated]. */
@Composable
internal fun FlowLine(color: Color, animated: Boolean, upwards: Boolean = false) {
    val time = rememberAnimationTime(animated)
    Canvas(Modifier.size(CIRCLE, FLOW_LINE_HEIGHT)) {
        val x = size.width / 2
        val path = Path().apply {
            moveTo(x, if (upwards) size.height else 0f)
            lineTo(x, if (upwards) 0f else size.height)
        }
        drawPath(path, color, style = Stroke(width = 1.dp.toPx()))
        if (animated) drawDot(path, progress(time.longValue, UTILITY_SECONDS), 1.dp.toPx() + DOT_STROKE.toPx(), color)
    }
}

/** The home's ring: the shares of its consumption. Like an SVG circle, it starts at three o'clock, clockwise. */
@Composable
internal fun HomeRing(ring: HomeRing, colors: DistributionColors, modifier: Modifier) {
    Canvas(modifier) {
        val stroke = RING_STROKE.toPx()
        val inset = (size.width - RING_RADIUS_RATIO * size.width) / 2
        val arcSize = Size(size.width - 2 * inset, size.height - 2 * inset)
        fun arc(from: Double, length: Double?, color: Color) {
            if (length == null || length.isNaN() || length <= 0) return
            drawArc(
                color,
                (from * FULL_TURN).toFloat(),
                (length * FULL_TURN).toFloat(),
                false,
                Offset(inset, inset),
                arcSize,
                style = Stroke(stroke),
            )
        }
        val solar = ring.solar?.takeUnless { it.isNaN() } ?: 0.0
        val battery = ring.battery?.takeUnless { it.isNaN() } ?: 0.0
        arc(1 - solar, ring.solar, colors.solar)
        arc(1 - solar - battery, ring.battery, colors.batteryOut)
        ring.lowCarbon?.let { arc(1 - solar - battery - it, it, colors.lowCarbon) }
        arc(0.0, ring.grid, colors.gridIn)
    }
}

/** The milliseconds since the dots started moving, updated every frame while [running]. */
@Composable
private fun rememberAnimationTime(running: Boolean = true): MutableLongState {
    val time = remember { mutableLongStateOf(0L) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val start = withFrameMillis { it }
        while (true) withFrameMillis { time.longValue = it - start }
    }
    return time
}

/** Where a dot taking [seconds] per trip is after [millis], from 0 to 1. */
private fun progress(millis: Long, seconds: Double): Float {
    val trip = seconds * MILLIS
    return if (trip <= 0) 0f else ((millis % trip) / trip).toFloat()
}

private fun DrawScope.drawDot(path: Path, progress: Float, radius: Float, color: Color) {
    val measure = PathMeasure().apply { setPath(path, false) }
    drawCircle(color, radius, measure.getPosition(measure.length * progress))
}

/** The lines' view box, scaled to cover the area and centred. */
private class ViewBox(size: Size) {
    val scale = max(size.width, size.height) / VIEW_BOX
    private val matrix = Matrix().apply {
        translate((size.width - VIEW_BOX * scale) / 2, (size.height - VIEW_BOX * scale) / 2)
        scale(scale, scale)
    }

    /** The SVG path [data] in this view box; the path, not the canvas, is scaled, so strokes keep their width. */
    fun path(data: String): Path = PathParser().parsePathString(data).toPath().apply { transform(matrix) }
}

// The frontend's paths, with and without the battery's row below
private const val RETURN = "M47,0 v15 c0,40 -10,35 -30,35 h-20"
private const val RETURN_WITH_BATTERY = "M45,0 v15 c0,35 -10,30 -30,30 h-20"
private const val SOLAR = "M53,0 v15 c0,40 10,35 30,35 h20"
private const val SOLAR_WITH_BATTERY = "M55,0 v15 c0,35 10,30 30,30 h20"
private const val BATTERY_HOUSE = "M55,100 v-15 c0,-35 10,-30 30,-30 h20"
private const val BATTERY_GRID = "M45,100 v-15 c0,-35 -10,-30 -30,-30 h-20"
private const val BATTERY_SOLAR = "M50,0 V100"
private const val GRID = "M0,53 H100"
private const val GRID_WITH_SOLAR = "M0,56 H100"
private const val GRID_WITH_BATTERY = "M0,50 H100"

private const val VIEW_BOX = 100f
private const val FULL_TURN = 360
private const val MILLIS = 1000
private const val UTILITY_SECONDS = 2.0

/** The circle's radius (38) over its box (80). */
private const val RING_RADIUS_RATIO = 76f / 80f
private val RING_STROKE = 4.dp

/** Half the dots' stroke, which doesn't scale with the view box. */
private val DOT_STROKE = 2.dp
private val FLOW_LINE_HEIGHT = 30.dp
