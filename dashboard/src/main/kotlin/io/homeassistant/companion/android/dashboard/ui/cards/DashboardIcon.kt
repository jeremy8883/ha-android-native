package io.homeassistant.companion.android.dashboard.ui.cards

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.rememberImageVector

/**
 * Displays an MDI icon name supplied by Home Assistant, ignoring unknown icon names safely. Like the frontend's
 * `ha-icon`, its own icons (logos such as `mdi:music-assistant`) come before MDI's.
 */
@Composable
internal fun DashboardIcon(name: String?, tint: Color, modifier: Modifier = Modifier) {
    val iconName = remember(name) { name?.removePrefix(MDI_PREFIX)?.takeIf(String::isNotBlank) } ?: return
    val custom = remember(iconName) { FRONTEND_CUSTOM_ICONS[iconName]?.let(::customIconVector) }
    val vector = custom ?: remember(iconName) { Mdi.fromMdiName(iconName) }?.rememberImageVector() ?: return
    Image(
        imageVector = vector,
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier,
    )
}

private fun customIconVector(path: String): ImageVector = ImageVector.Builder(
    defaultWidth = ICON_SIZE.dp,
    defaultHeight = ICON_SIZE.dp,
    viewportWidth = ICON_SIZE,
    viewportHeight = ICON_SIZE,
).addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black)).build()

private const val MDI_PREFIX = "mdi:"

/** MDI's viewport, which the frontend's own icons use too. */
private const val ICON_SIZE = 24f
