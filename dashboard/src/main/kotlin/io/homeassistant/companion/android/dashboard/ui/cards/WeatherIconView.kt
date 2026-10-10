package io.homeassistant.companion.android.dashboard.ui.cards

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import io.homeassistant.companion.android.dashboard.weather.WeatherIcon
import io.homeassistant.companion.android.dashboard.weather.WeatherPaint

/** A weather condition, port of `getWeatherStateIcon`: its drawing scaled into the box, or its icon in [tint]. */
@Composable
internal fun WeatherIconView(icon: WeatherIcon, tint: Color, modifier: Modifier = Modifier) {
    when (icon) {
        is WeatherIcon.Icon -> DashboardIcon(icon.icon, tint, modifier)
        is WeatherIcon.Drawing -> {
            val parts = remember(icon) { icon.parts.map { it.paint to PathParser().parsePathString(it.path).toPath() } }
            Canvas(modifier) {
                scale(size.minDimension / VIEW_BOX, pivot = Offset.Zero) {
                    parts.forEach { (paint, path) -> drawPart(paint, path) }
                }
            }
        }
    }
}

private fun DrawScope.drawPart(paint: WeatherPaint, path: Path) {
    // `paint-order: stroke`: the outline goes under the fill
    paint.stroke?.let { drawPath(path, Color(it), style = Stroke(width = SNOW_STROKE)) }
    drawPath(path, Color(paint.fill))
}

/** The drawings' `viewBox="0 0 17 17"`. */
private const val VIEW_BOX = 17f
private const val SNOW_STROKE = 1f
