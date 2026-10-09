package io.homeassistant.companion.android.dashboard.ui.cards.energy

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardIcon

// The circles of the energy distribution card and what they hold (`.circle` and `.label` of
// hui-energy-distribution-card).

/** Moved up by [shift], taking that much less height (a negative top and bottom margin). */
internal fun Modifier.pullUp(shift: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val pixels = shift.roundToPx()
    layout(placeable.width, (placeable.height - pixels).coerceAtLeast(0)) { placeable.place(0, -pixels) }
}

/** A circle with its content; [border] `null` draws none (the home with its ring). */
@Composable
internal fun Node(border: Color?, content: @Composable () -> Unit) {
    val modifier = Modifier.size(CIRCLE)
    Column(
        modifier = if (border != null) modifier.border(BORDER, border, CircleShape) else modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { content() }
}

@Composable
internal fun NodeIcon(icon: String, tint: Color = LocalHAColorScheme.current.colorTextPrimary) {
    DashboardIcon(icon, tint, Modifier.padding(bottom = ICON_GAP).size(ICON))
}

@Composable
internal fun NodeText(text: String, color: Color = LocalHAColorScheme.current.colorTextPrimary) {
    Text(text, color = color, fontSize = FONT_S, lineHeight = LINE, textAlign = TextAlign.Center, maxLines = 1)
}

/** An amount with its direction (a small arrow), in its flow's colour. */
@Composable
internal fun Amount(arrow: String?, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        arrow?.let { DashboardIcon(it, color, Modifier.size(SMALL_ICON)) }
        NodeText(text, color)
    }
}

@Composable
internal fun NodeLabel(text: String) {
    Text(
        text,
        color = LocalHAColorScheme.current.colorTextSecondary,
        fontSize = FONT_S,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.height(LABEL_HEIGHT).widthIn(max = CIRCLE),
    )
}

/** The circles' size. */
internal val CIRCLE = 80.dp
private val BORDER = 2.dp
private val ICON = 24.dp
private val ICON_GAP = 2.dp
private val SMALL_ICON = 12.dp
private val FONT_S = 12.sp
private val LINE = 12.sp
private val LABEL_HEIGHT = 20.dp
