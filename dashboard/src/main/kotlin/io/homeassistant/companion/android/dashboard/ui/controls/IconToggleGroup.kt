package io.homeassistant.companion.android.dashboard.ui.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardIcon

/**
 * Port of `ha-icon-button-group` with `ha-icon-button-toggle`s (frontend@20260624.6 src/components/): icons in a
 * pill, the selected one filled in. [onSelect] gets the index of the one tapped.
 */
@Composable
internal fun IconToggleGroup(toggles: List<IconToggle>, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalHAColorScheme.current
    Row(
        modifier = modifier
            .height(HADimens.SPACE12)
            .clip(RoundedCornerShape(GROUP_RADIUS))
            .background(GROUP_BACKGROUND),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        toggles.forEachIndexed { index, toggle ->
            Box(
                modifier = Modifier
                    .size(HADimens.SPACE12)
                    .selectable(selected = toggle.selected, enabled = toggle.enabled, role = Role.Tab) {
                        onSelect(index)
                    }
                    .semantics { contentDescription = toggle.label },
                contentAlignment = Alignment.Center,
            ) {
                val filled = toggle.selected && toggle.enabled
                Box(
                    modifier = Modifier
                        .size(HADimens.SPACE10)
                        .clip(RoundedCornerShape(HARadius.X2L))
                        .background(if (filled) colors.colorTextPrimary else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    DashboardIcon(
                        name = toggle.icon,
                        tint = when {
                            !toggle.enabled -> colors.colorTextDisabled
                            filled -> colors.colorSurfaceDefault
                            else -> colors.colorTextPrimary
                        },
                        modifier = Modifier.size(HASize.X2L),
                    )
                }
            }
        }
    }
}

private val GROUP_RADIUS = HADimens.SPACE7

/** `rgba(139, 145, 151, 0.1)`, the group's background in both themes. */
private val GROUP_BACKGROUND = Color(red = 139, green = 145, blue = 151, alpha = 26)
