package io.homeassistant.companion.android.dashboard.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.ui.theme.Css
import io.homeassistant.companion.android.dashboard.ui.theme.cssPx

/**
 * A switch at the frontend's size, in Material's switch colours: the track in the primary colour when on, outlined
 * when off, its thumb at the end it is switched to. Its touch area is at least `--ha-switch-min-touch-size` (40px)
 * high, as upstream's. Its size defaults to `ha-switch`'s own (`--ha-switch-width`, `--ha-switch-size`,
 * `--ha-switch-thumb-size`).
 *
 * Upstream: `ha-switch` (src/components/ha-switch.ts)
 */
@Composable
internal fun HaSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    width: Dp = DEFAULT_WIDTH,
    height: Dp = DEFAULT_HEIGHT,
    thumb: Dp = DEFAULT_THUMB,
) {
    val scheme = MaterialTheme.colorScheme
    val inset = (height - thumb) / 2
    val thumbOffset by animateDpAsState(if (checked) width - thumb - inset else inset, label = "thumb")
    val track = if (checked) LocalHAColorScheme.current.colorFillPrimaryLoudResting else scheme.surfaceContainerHighest
    Box(
        modifier = modifier
            .size(width, maxOf(height, MIN_TOUCH))
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .size(width, height)
                .background(track, CircleShape)
                .then(if (checked) Modifier else Modifier.border(Css.BorderWidth.Sm, scheme.outline, CircleShape)),
        )
        Box(
            Modifier
                .offset(x = thumbOffset)
                .size(thumb)
                .background(if (checked) scheme.onPrimary else scheme.outline, CircleShape),
        )
    }
}

private val DEFAULT_WIDTH = 48.cssPx
private val DEFAULT_HEIGHT = 24.cssPx
private val DEFAULT_THUMB = 18.cssPx

/** `--ha-switch-min-touch-size`. */
private val MIN_TOUCH = 40.cssPx

/** `--ha-switch-disabled-opacity`. */
private const val DISABLED_ALPHA = 0.3f
