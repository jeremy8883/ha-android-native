package io.homeassistant.companion.android.dashboard.ui.lovelace.cardfeatures

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.feature.TrendGraphFeature
import io.homeassistant.companion.android.dashboard.history.GraphBox
import io.homeassistant.companion.android.dashboard.history.GraphHistory
import io.homeassistant.companion.android.dashboard.history.sensorGraphCoordinates
import io.homeassistant.companion.android.dashboard.ui.lovelace.components.drawGraph
import io.homeassistant.companion.android.dashboard.ui.theme.Css

/**
 * The trend graph: the sensor's line in the feature colour over a faint fill, the feature's height, its bottom
 * corners rounded; flat while loading, or the error or "No state history found." instead.
 *
 * Upstream: `hui-trend-graph-card-feature`, `hui-graph-base`
 * (src/panels/lovelace/card-features/hui-trend-graph-card-feature.ts;
 * src/panels/lovelace/components/hui-graph-base.ts)
 */
@Composable
internal fun FeatureTrendGraph(feature: TrendGraphFeature, color: Color) {
    val history = feature.history
    if (history is GraphHistory.Failed) {
        TrendMessage(history.message.orEmpty())
        return
    }
    val states = (history as? GraphHistory.Loaded)?.states
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(
            FEATURE_HEIGHT,
        ).clip(RoundedCornerShape(bottomStart = GRAPH_RADIUS, bottomEnd = GRAPH_RADIUS)),
    ) {
        val now = feature.nowMillis
        val coordinates = now?.let {
            sensorGraphCoordinates(
                feature.graph,
                states,
                feature.current,
                GraphBox(maxWidth.value.toDouble(), maxHeight.value.toDouble()),
                it,
            )
        }
        if (states != null && coordinates != null && coordinates.points.isEmpty()) {
            TrendMessage(NO_HISTORY)
            return@BoxWithConstraints
        }
        Canvas(Modifier.fillMaxSize()) {
            if (coordinates != null) drawGraph(coordinates, if (states == null) null else color, color)
        }
    }
}

/**
 * The text the trend graph shows instead of its line.
 *
 * Upstream: part of `hui-trend-graph-card-feature` (src/panels/lovelace/card-features/hui-trend-graph-card-feature.ts)
 */
@Composable
private fun TrendMessage(text: String) {
    Box(Modifier.fillMaxWidth().height(FEATURE_HEIGHT), contentAlignment = Alignment.Center) {
        Text(text, style = Css.Text.Body, color = LocalHAColorScheme.current.colorTextPrimary)
    }
}

/** Upstream's text, which it doesn't translate. */
private const val NO_HISTORY = "No state history found."

/** `hui-graph-base`'s bottom corners in the feature (`border-bottom-*-radius: 8px`). */
private val GRAPH_RADIUS = Css.Radius.Md
