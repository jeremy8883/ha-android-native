package io.homeassistant.companion.android.dashboard.ui.lovelace.cardfeatures

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.derive.DisplayColor
import io.homeassistant.companion.android.dashboard.feature.ForecastFeature
import io.homeassistant.companion.android.dashboard.feature.ForecastKind
import io.homeassistant.companion.android.dashboard.feature.GradientStop
import io.homeassistant.companion.android.dashboard.feature.PrecipitationMark
import io.homeassistant.companion.android.dashboard.feature.dailyEntries
import io.homeassistant.companion.android.dashboard.feature.dayLabelTimes
import io.homeassistant.companion.android.dashboard.feature.hourLabelTimes
import io.homeassistant.companion.android.dashboard.feature.hourSlotLabelTimes
import io.homeassistant.companion.android.dashboard.feature.precipitationMarks
import io.homeassistant.companion.android.dashboard.feature.temperatureBars
import io.homeassistant.companion.android.dashboard.feature.temperatureLine
import io.homeassistant.companion.android.dashboard.history.GraphBox
import io.homeassistant.companion.android.dashboard.history.GraphPoint
import io.homeassistant.companion.android.dashboard.ui.lovelace.components.smoothPath
import io.homeassistant.companion.android.dashboard.ui.theme.Css
import io.homeassistant.companion.android.dashboard.ui.theme.cssFontPx
import io.homeassistant.companion.android.dashboard.ui.theme.parseCssColor
import io.homeassistant.companion.android.dashboard.ui.theme.toColor
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale

/**
 * A forecast feature: a spinner until the forecast arrives, then its temperature bars or line, or its
 * precipitation bars, with the days or hours under them unless `show_labels` is off.
 *
 * Upstream: `hui-temperature-forecast-card-feature`, `hui-precipitation-forecast-card-feature`
 * (src/panels/lovelace/card-features/hui-temperature-forecast-card-feature.ts;
 * src/panels/lovelace/card-features/hui-precipitation-forecast-card-feature.ts)
 */
@Composable
internal fun FeatureForecast(feature: ForecastFeature, featureColor: Color) {
    val temperature = feature.kind is ForecastKind.Temperature
    // The temperature graph's bottom corners follow the card's, unless labels sit under it
    val shape = if (temperature && !feature.showLabels) {
        RoundedCornerShape(bottomStart = Css.Radius.Md, bottomEnd = Css.Radius.Md)
    } else {
        RoundedCornerShape(0.dp)
    }
    Box(Modifier.fillMaxWidth().height(FEATURE_HEIGHT).clip(shape), contentAlignment = Alignment.Center) {
        when {
            feature.entries == null -> CircularProgressIndicator(Modifier.size(SPINNER_SIZE), strokeWidth = 2.dp)
            !feature.hasContent() -> ForecastInfo(feature.noForecast)
            else -> Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth().padding(bottom = if (feature.showLabels) LABEL_GAP else 0.dp)) {
                    ForecastGraph(feature, featureColor)
                }
                if (feature.showLabels) ForecastLabels(feature)
            }
        }
    }
}

/** Whether the forecast has anything to draw; upstream says there's no forecast otherwise. */
private fun ForecastFeature.hasContent(): Boolean = when {
    kind is ForecastKind.Temperature && hourly -> temperatureLine(1.0, 1.0)?.coordinates?.points?.isNotEmpty() == true
    hourly -> precipitationMarks(1.0, 1.0).isNotEmpty()
    else -> dailyEntries().isNotEmpty()
}

/**
 * The bars or line, drawn to the canvas' size.
 *
 * Upstream: `_renderBars`, `hui-graph-base`, `_renderDailyBars`, `_renderHourlyBars`
 * (src/panels/lovelace/card-features/hui-temperature-forecast-card-feature.ts;
 * src/panels/lovelace/components/hui-graph-base.ts;
 * src/panels/lovelace/card-features/hui-precipitation-forecast-card-feature.ts)
 */
@Composable
private fun ForecastGraph(feature: ForecastFeature, featureColor: Color) {
    val custom = feature.color?.toColor()
    val rainy = DisplayColor.State(listOf(RAINY_COLOR)).toColor() ?: featureColor
    Canvas(Modifier.fillMaxSize()) {
        // The geometry is in CSS pixels, as upstream's
        val width = (size.width / density).toDouble()
        val height = (size.height / density).toDouble()
        scale(density, pivot = Offset.Zero) {
            drawForecast(feature, GraphBox(width, height), custom, rainy, featureColor)
        }
    }
}

/** The forecast in a [box] measured in CSS pixels. */
private fun DrawScope.drawForecast(
    feature: ForecastFeature,
    box: GraphBox,
    custom: Color?,
    rainy: Color,
    featureColor: Color,
) {
    val width = box.width
    val height = box.height
    when {
        feature.kind is ForecastKind.Precipitation -> drawMarks(
            feature.precipitationMarks(width, height),
            custom ?: rainy,
        )
        feature.hourly -> feature.temperatureLine(width, height)?.let { line ->
            val brush = custom?.let(::SolidColor) ?: gradient(line.stops, height.toFloat())
            drawLine(line.coordinates.points, line.coordinates.yAxisOrigin.toFloat(), brush)
        }
        else -> feature.temperatureBars(feature.dailyEntries(), width, height).forEach { bar ->
            val top = bar.y.toFloat()
            val brush = custom?.let(::SolidColor)
                ?: bar.stops?.let { gradient(it, bar.height.toFloat(), top) } ?: SolidColor(featureColor)
            drawRoundRect(
                brush,
                Offset(bar.x.toFloat(), top),
                Size(bar.width.toFloat(), bar.height.toFloat()),
                CornerRadius(bar.radius.toFloat()),
            )
        }
    }
}

/** A vertical gradient of [stops] over [height] from [top]. */
private fun gradient(stops: List<GradientStop>, height: Float, top: Float = 0f): Brush = Brush.verticalGradient(
    colorStops = stops.map { it.offset.toFloat() to (parseCssColor(it.color) ?: Color.Unspecified) }.toTypedArray(),
    startY = top,
    endY = top + height,
)

/** The hourly temperature line over a faint fill down to the zero line, as `hui-graph-base` draws it. */
private fun DrawScope.drawLine(points: List<GraphPoint>, origin: Float, brush: Brush) {
    if (points.isEmpty()) return
    val path = smoothPath(points, 1f)
    val area = Path().apply {
        addPath(path)
        lineTo(points.last().x.toFloat(), origin)
        lineTo(0f, origin)
        close()
    }
    drawPath(area, brush, alpha = FILL_ALPHA)
    drawPath(
        path,
        brush,
        alpha = LINE_ALPHA,
        style = Stroke(width = STROKE_WIDTH, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

/** The precipitation bars, rounded at the top, and dots, at the marks' opacity. */
private fun DrawScope.drawMarks(marks: List<PrecipitationMark>, color: Color) {
    marks.forEach { mark ->
        when (mark) {
            is PrecipitationMark.Dot -> drawCircle(
                color,
                mark.radius.toFloat(),
                Offset(mark.cx.toFloat(), mark.cy.toFloat()),
                alpha = MARK_ALPHA,
            )
            is PrecipitationMark.Bar -> {
                val r = minOf(mark.radius, mark.width / 2, mark.height).toFloat()
                val path = Path().apply {
                    val x = mark.x.toFloat()
                    val y = mark.y.toFloat()
                    val w = mark.width.toFloat()
                    val h = mark.height.toFloat()
                    addRoundRect(
                        RoundRect(
                            left = x,
                            top = y,
                            right = x + w,
                            bottom = y + h,
                            topLeftCornerRadius = CornerRadius(r),
                            topRightCornerRadius = CornerRadius(r),
                        ),
                    )
                }
                drawPath(path, color, alpha = MARK_ALPHA)
            }
        }
    }
}

/**
 * The days (a narrow weekday per day) or hours under the forecast, in 9px secondary text.
 *
 * Upstream: `renderDayLabels`, `renderHourLabels`, `renderHourSlotLabels`
 * (src/panels/lovelace/card-features/common/graph-labels.ts)
 */
@Composable
private fun ForecastLabels(feature: ForecastFeature) {
    val locale = Locale.getDefault()
    val zone = feature.zone
    val hour = remember(locale, feature.amPm, zone) {
        DateTimeFormatter.ofPattern(if (feature.amPm) "h a" else "H", locale).withZone(zone)
    }
    val style = TextStyle(fontSize = 9.cssFontPx, lineHeight = 9.cssFontPx)
    val color = LocalHAColorScheme.current.colorTextSecondary
    val modifier = Modifier.fillMaxWidth().height(LABEL_HEIGHT)
    fun day(millis: Double) = Instant.ofEpochMilli(millis.toLong()).atZone(zone).dayOfWeek
        .getDisplayName(DateTextStyle.NARROW, locale)
    fun hourText(millis: Double) = hour.format(Instant.ofEpochMilli(millis.toLong()))
    when {
        !feature.hourly -> Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            feature.dayLabelTimes(feature.dailyEntries()).forEach {
                Text(day(it), Modifier.weight(1f), color = color, style = style, textAlign = TextAlign.Center)
            }
        }
        feature.kind is ForecastKind.Temperature -> Row(
            modifier,
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            feature.hourLabelTimes().forEach { Text(hourText(it), color = color, style = style) }
        }
        else -> SlotLabels(
            feature.hoursToShow,
            feature.hourSlotLabelTimes().map { (i, t) ->
                i to hourText(t)
            },
            style,
            color,
            modifier,
        )
    }
}

/**
 * Labels centred on their slot of [slots] equal columns.
 *
 * Upstream: part of `renderHourSlotLabels` (src/panels/lovelace/card-features/common/graph-labels.ts)
 */
@Composable
private fun SlotLabels(
    slots: Int,
    labels: List<Pair<Int, String>>,
    style: TextStyle,
    color: Color,
    modifier: Modifier,
) {
    Layout(
        content = {
            labels.forEach { (_, text) -> Text(text, color = color, style = style, maxLines = 1, softWrap = false) }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val slot = constraints.maxWidth.toFloat() / slots
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { i, placeable ->
                // Centred in its slot, or from the slot's start when wider than it, as CSS lays out overflowing text
                val start = slot * labels[i].first
                val x = if (placeable.width > slot) start else start + (slot - placeable.width) / 2
                placeable.place(x.toInt(), (constraints.maxHeight - placeable.height) / 2)
            }
        }
    }
}

/**
 * The text shown instead of the forecast.
 *
 * Upstream: part of `hui-temperature-forecast-card-feature` (src/panels/lovelace/card-features/hui-temperature-forecast-card-feature.ts)
 */
@Composable
private fun ForecastInfo(text: String) {
    Text(
        text,
        style = Css.Text.Body.copy(fontSize = Css.FontSize.S),
        color = LocalHAColorScheme.current.colorTextSecondary,
    )
}

private const val RAINY_COLOR = "state-weather-rainy-color"
private const val MARK_ALPHA = 0.4f
private const val LINE_ALPHA = 0.8f
private const val FILL_ALPHA = 0.1f

/** `strokeWidth` (src/data/graph.ts), in the scaled CSS pixels. */
private const val STROKE_WIDTH = 2f
private val SPINNER_SIZE = 20.dp

/** `LABEL_HEIGHT` and `LABEL_GAP`, the bars' bottom padding with labels. */
private val LABEL_HEIGHT = 10.dp
private val LABEL_GAP = 2.dp
