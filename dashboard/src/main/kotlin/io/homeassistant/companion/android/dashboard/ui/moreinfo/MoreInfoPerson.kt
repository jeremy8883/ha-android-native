package io.homeassistant.companion.android.dashboard.ui.moreinfo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.moreinfo.MapLocation
import io.homeassistant.companion.android.dashboard.moreinfo.MoreInfoExtra
import io.homeassistant.companion.android.dashboard.ui.cards.ServerImage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

/**
 * Port of `more-info-person`: the person on `ha-map` (300 high, zoomed to 14 on them, their accuracy circle around
 * them), then "Create zone from current location" for admins.
 */
@Composable
internal fun MoreInfoPerson(person: MoreInfoExtra.Person, onAction: (CardAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2)) {
        person.location?.let { location ->
            PersonMap(location, person, Modifier.fillMaxWidth().padding(vertical = HADimens.SPACE4).height(MAP_HEIGHT))
        }
        person.createZone?.let { label ->
            // UPSTREAM-DIFF: opens the zones settings in the frontend; upstream opens the zone editor there with the
            // person's coordinates filled in
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                HAPlainButton(label, { onAction(CardAction.Navigate(ZONES_PATH, replace = false)) })
            }
        }
    }
}

/**
 * The map's CARTO Voyager tiles (`createTileLayer`) around [location] at [ZOOM], the accuracy circle in the dark
 * primary colour, the marker in the middle, and the tiles' attribution.
 */
@Composable
private fun PersonMap(location: MapLocation, person: MoreInfoExtra.Person, modifier: Modifier) {
    val colors = LocalHAColorScheme.current
    BoxWithConstraints(modifier.clipToBounds().background(colors.colorSurfaceDefault)) {
        val density = LocalDensity.current
        val tile = TILE_SIZE
        // The location's place in the world at this zoom, in tiles
        val worldX = (location.longitude + HALF_TURN) / FULL_TURN * TILES
        val latitude = Math.toRadians(location.latitude)
        val worldY = (1 - ln(tan(latitude) + 1 / cos(latitude)) / PI) / 2 * TILES
        val centerX = maxWidth / 2
        val centerY = maxHeight / 2
        val firstX = floor(worldX - (centerX / tile)).toInt()
        val firstY = floor(worldY - (centerY / tile)).toInt()
        val columns = (maxWidth / tile).toInt() + 2
        val rows = (maxHeight / tile).toInt() + 2
        for (x in firstX until firstX + columns) {
            for (y in firstY until firstY + rows) {
                if (y < 0 || y >= TILES) continue
                val wrapped = Math.floorMod(x, TILES)
                AsyncImage(
                    model = "https://basemaps.cartocdn.com/rastertiles/voyager/$ZOOM/$wrapped/$y@2x.png",
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .size(tile)
                        .offset(centerX + tile * (x - worldX).toFloat(), centerY + tile * (y - worldY).toFloat()),
                )
            }
        }
        location.accuracy?.let { metres ->
            // Metres per tile pixel at this latitude and zoom
            val metresPerDp = EARTH_CIRCUMFERENCE * cos(latitude) / (TILES * tile.value)
            val radius = with(density) { (metres / metresPerDp).toFloat().dp.toPx() }
            val stroke = colors.colorFillPrimaryLoudResting
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(stroke.copy(alpha = CIRCLE_FILL), radius)
                drawCircle(stroke, radius, style = Stroke(width = 3.dp.toPx()))
            }
        }
        Marker(person, Modifier.align(Alignment.Center))
        Text(
            ATTRIBUTION,
            style = HATextStyle.BodyMedium.copy(fontSize = HATextStyle.BodyMedium.fontSize * ATTRIBUTION_SCALE),
            color = colors.colorTextSecondary,
            modifier = Modifier.align(Alignment.BottomEnd).background(Color.White.copy(alpha = ATTRIBUTION_BACKGROUND))
                .padding(horizontal = HADimens.SPACE1),
        )
    }
}

/** `ha-entity-marker`: a 48 circle with a primary border, the picture filling it, else the initials. */
@Composable
private fun Marker(person: MoreInfoExtra.Person, modifier: Modifier) {
    val colors = LocalHAColorScheme.current
    Box(
        modifier.size(MARKER_SIZE).clip(CircleShape).background(colors.colorSurfaceDefault)
            .border(1.dp, colors.colorFillPrimaryLoudResting, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val picture = person.picture
        if (picture != null) {
            ServerImage(picture, person.markerLabel, Modifier.fillMaxSize())
        } else {
            Text(person.markerLabel, style = HATextStyle.HeadlineMedium, color = colors.colorTextPrimary)
        }
    }
}

private const val ZONES_PATH = "/config/zone"
private const val ZOOM = 14
private val TILES = 2.0.pow(ZOOM).toInt()
private const val HALF_TURN = 180.0
private const val FULL_TURN = 360.0
private const val EARTH_CIRCUMFERENCE = 40_075_016.686
private const val CIRCLE_FILL = 0.2f
private const val ATTRIBUTION_SCALE = 0.75f
private const val ATTRIBUTION_BACKGROUND = 0.7f
private const val ATTRIBUTION = "© OpenStreetMap, © CARTO"
private val TILE_SIZE: Dp = 256.dp
private val MAP_HEIGHT = 300.dp
private val MARKER_SIZE = 48.dp
