package io.homeassistant.companion.android.dashboard.ui.cards

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.rememberImageVector

/** Displays an MDI icon name supplied by Home Assistant, ignoring unknown icon names safely. */
@Composable
internal fun DashboardIcon(name: String?, tint: Color, modifier: Modifier = Modifier) {
    val icon = remember(name) {
        name
            ?.removePrefix(MDI_PREFIX)
            ?.takeIf(String::isNotBlank)
            ?.let(Mdi::fromMdiName)
    } ?: return
    Image(
        imageVector = icon.rememberImageVector(),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier,
    )
}

private const val MDI_PREFIX = "mdi:"
