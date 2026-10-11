package io.homeassistant.companion.android.dashboard.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.DomainRow
import io.homeassistant.companion.android.dashboard.derive.RowMedia
import io.homeassistant.companion.android.dashboard.derive.RowVolume
import io.homeassistant.companion.android.dashboard.ui.controls.haSliderColors

// The parts of the entities card's domain rows: the media under a player's name, a two-line state (climate,
// humidifier, event), a weather's temperature over today's high and low, and a player's volume line.

/** The row's name, with a second line under it (a media player's media). */
@Composable
internal fun RowName(name: String, secondary: String?, color: Color) {
    val colors = LocalHAColorScheme.current
    Column {
        Text(
            name,
            style = HATextStyle.Body.copy(textAlign = TextAlign.Start),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        secondary?.let {
            Text(
                it,
                style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.Start),
                color = colors.colorTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A state on two lines, right-aligned: climate and humidifier readings, an event's type, a weather's extremes. */
@Composable
internal fun DomainRowState(state: String?, domain: DomainRow, color: Color) {
    val colors = LocalHAColorScheme.current
    Column(horizontalAlignment = Alignment.End) {
        state?.let { Text(it, style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.End), color = color) }
        domain.stateSecondary?.let {
            Text(it, style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.End), color = colors.colorTextSecondary)
        }
        domain.weatherDetail?.let { detail ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE1),
            ) {
                detail.icon?.let { DashboardIcon(it, colors.colorTextSecondary, Modifier.size(HASize.XL)) }
                detail.label?.let { Text(it, style = HATextStyle.BodyMedium, color = colors.colorTextSecondary) }
                Text(detail.value, style = HATextStyle.BodyMedium, color = colors.colorTextSecondary)
            }
        }
    }
}

/** A playing media player's second line: mute, the volume slider (set when let go) or steps, then its buttons. */
@Composable
internal fun MediaVolumeLine(media: RowMedia, volume: RowVolume, onAction: (CardAction) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        volume.mute?.let { RowIconButtonView(it, onAction) }
        volume.level?.let { level ->
            Slider(
                value = dragging ?: level.toFloat(),
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { onAction(volume.setCall(Math.round(it).toDouble())) }
                    dragging = null
                },
                valueRange = 0f..PERCENT,
                colors = haSliderColors(),
                modifier = Modifier.weight(1f),
            )
        } ?: Row(Modifier.weight(1f)) { volume.steps.forEach { RowIconButtonView(it, onAction) } }
        media.bottom.forEach { RowIconButtonView(it, onAction) }
    }
}

private const val PERCENT = 100f
