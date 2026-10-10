package io.homeassistant.companion.android.dashboard.ui.moreinfo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.MediaControl
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.moreinfo.MediaMainControls
import io.homeassistant.companion.android.dashboard.moreinfo.MediaPlayerMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.MediaPosition
import io.homeassistant.companion.android.dashboard.moreinfo.MediaVolume
import io.homeassistant.companion.android.dashboard.moreinfo.SelectMenu
import io.homeassistant.companion.android.dashboard.moreinfo.formatMediaTime
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.controls.SelectMenuOptions
import io.homeassistant.companion.android.dashboard.ui.controls.haSliderColors
import java.time.Instant
import kotlinx.coroutines.delay

/** The position slider with the elapsed and total times; it counts up each second while the track plays. */
@Composable
internal fun MediaPositionBar(position: MediaPosition, now: Instant, onAction: (CardAction) -> Unit) {
    // The dashboard's clock ticks slowly, so the seconds since it last did are counted here
    var ticks by remember(now, position) { mutableIntStateOf(0) }
    if (position.ticking) {
        LaunchedEffect(now, position) {
            while (true) {
                delay(TICK_MS)
                ticks++
            }
        }
    }
    val at = now.plusSeconds(ticks.toLong())
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging ?: position.sliderAt(at).toFloat()
    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = shown,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onAction(position.seek.withValue(Math.round(it).toDouble())) }
                dragging = null
            },
            valueRange = 0f..position.duration.toFloat(),
            enabled = position.enabled,
            colors = haSliderColors(),
            modifier = Modifier.semantics { contentDescription = position.label },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            // As upstream, the elapsed time isn't held at the end
            TimeText(formatMediaTime(dragging?.toDouble() ?: position.at(at)))
            TimeText(formatMediaTime(position.duration))
        }
    }
}

@Composable
private fun TimeText(text: String) {
    Text(text, style = HATextStyle.BodyMedium, color = LocalHAColorScheme.current.colorTextSecondary)
}

/** Repeat and previous, the filled play button(s), next and shuffle; a missing side button keeps its space. */
@Composable
internal fun MediaMainButtons(controls: MediaMainControls, onAction: (CardAction) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        controls.left.forEach { SideButton(it, onAction) }
        controls.center.forEach { control ->
            FilledIconButton(
                onClick = { onAction(control.action) },
                modifier = Modifier.size(CENTER_SIZE),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = LocalHAColorScheme.current.colorFillPrimaryLoudResting,
                ),
            ) {
                ControlIcon(control, LocalHAColorScheme.current.colorOnPrimaryLoud, Modifier.size(HASize.X3L))
            }
        }
        controls.right.forEach { SideButton(it, onAction) }
    }
}

@Composable
private fun SideButton(control: MediaControl?, onAction: (CardAction) -> Unit) {
    if (control == null) {
        Box(Modifier.size(HADimens.SPACE12))
    } else {
        IconButton(onClick = { onAction(control.action) }) {
            ControlIcon(control, LocalHAColorScheme.current.colorTextPrimary, Modifier.size(HASize.X2L))
        }
    }
}

@Composable
private fun ControlIcon(control: MediaControl, tint: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    DashboardIcon(control.icon, tint, modifier.semantics { contentDescription = control.label })
}

/** The mute button, the step buttons or the volume slider (sent when released). */
@Composable
internal fun MediaVolumeRow(volume: MediaVolume, onAction: (CardAction) -> Unit) {
    val colors = LocalHAColorScheme.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        volume.mute?.let { SideButton(it, onAction) }
        volume.down?.let { SideButton(it, onAction) }
        volume.up?.let { SideButton(it, onAction) }
        if (volume.slider != null) {
            if (volume.mute == null) {
                DashboardIcon("mdi:volume-high", colors.colorTextPrimary, Modifier.size(HASize.X2L))
            }
            var dragging by remember { mutableStateOf<Float?>(null) }
            Slider(
                value = dragging ?: (volume.level ?: 0.0).toFloat(),
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { level ->
                        volume.setTo(Math.round(level / VOLUME_STEP) * VOLUME_STEP)?.let(onAction)
                    }
                    dragging = null
                },
                valueRange = 0f..PERCENT,
                colors = haSliderColors(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Browse, grouping, source, sound mode, turn on and turn off, as icon buttons; the menus open from theirs. */
@Composable
internal fun MediaControlsRow(
    info: MediaPlayerMoreInfo,
    entityId: String,
    hass: HassSnapshot,
    onAction: (CardAction) -> Unit,
) {
    val buttons =
        listOfNotNull(info.source, info.soundMode).isNotEmpty() ||
            info.turnOn != null ||
            info.turnOff != null ||
            info.grouping != null ||
            info.browseLabel != null
    if (!buttons) return
    Row(horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2, Alignment.CenterHorizontally)) {
        info.browseLabel?.let { BrowseButton(it, entityId, hass, onAction) }
        info.grouping?.let { MediaGroupingButton(it, onAction) }
        info.source?.let { MenuButton(it, onAction) }
        info.soundMode?.let { MenuButton(it, onAction) }
        info.turnOn?.let { SideButton(it, onAction) }
        info.turnOff?.let { SideButton(it, onAction) }
    }
}

/** The media browser's button, opening it. */
@Composable
private fun BrowseButton(label: String, entityId: String, hass: HassSnapshot, onAction: (CardAction) -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        DashboardIcon(
            "mdi:play-box-multiple",
            LocalHAColorScheme.current.colorTextPrimary,
            Modifier.size(HASize.X2L).semantics { contentDescription = label },
        )
    }
    if (open) MediaBrowserDialog(entityId, label, hass, onAction) { open = false }
}

@Composable
private fun MenuButton(menu: SelectMenu, onAction: (CardAction) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            DashboardIcon(
                menu.icon,
                LocalHAColorScheme.current.colorTextPrimary,
                Modifier.size(HASize.X2L).semantics { contentDescription = menu.label },
            )
        }
        SelectMenuOptions(menu, expanded, onDismiss = { expanded = false }, onAction = onAction)
    }
}

private const val TICK_MS = 1000L
private const val PERCENT = 100f
private const val VOLUME_STEP = 2.0
private val CENTER_SIZE = 56.dp
