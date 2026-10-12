package io.homeassistant.companion.android.dashboard.ui.moreinfo.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.dashboard.ui.theme.Css

/**
 * The layout of a domain's details (`moreInfoControlStyle`): the [top] part (a thermostat's current readings),
 * [topGap] over the [controls] (24px apart), then 24px under them the [bottom] part (the menus). Given a minimum
 * height (the dialog's room, for full-height details), the bottom part sits at its bottom and the controls are
 * centred in the room between; taller content just grows, as the frontend's flex column does.
 *
 * Upstream: `moreInfoControlStyle` (src/dialogs/more-info/components/more-info-control-style.ts)
 */
@Composable
internal fun MoreInfoControlsLayout(
    modifier: Modifier = Modifier,
    top: (@Composable () -> Unit)? = null,
    topGap: Dp = 0.dp,
    bottom: (@Composable () -> Unit)? = null,
    controls: @Composable ColumnScope.() -> Unit,
) {
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            Box { top?.invoke() }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Css.Space.S6),
                content = controls,
            )
            Box { bottom?.invoke() }
        },
    ) { measurables, constraints ->
        val loose = Constraints(maxWidth = constraints.maxWidth)
        val (topPlaceable, controlsPlaceable, bottomPlaceable) = measurables.map { it.measure(loose) }
        val topSpace = if (top != null) topPlaceable.height + topGap.roundToPx() else 0
        val bottomSpace = if (bottom != null) bottomPlaceable.height + Css.Space.S6.roundToPx() else 0
        val height = maxOf(constraints.minHeight, topSpace + controlsPlaceable.height + bottomSpace)
        val width = constraints.maxWidth
        layout(width, height) {
            topPlaceable.place((width - topPlaceable.width) / 2, 0)
            val room = height - topSpace - bottomSpace
            controlsPlaceable.place(
                (width - controlsPlaceable.width) / 2,
                topSpace + (room - controlsPlaceable.height) / 2,
            )
            bottomPlaceable.place((width - bottomPlaceable.width) / 2, height - bottomPlaceable.height)
        }
    }
}
