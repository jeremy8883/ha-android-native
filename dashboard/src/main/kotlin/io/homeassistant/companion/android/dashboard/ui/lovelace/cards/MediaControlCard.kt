package io.homeassistant.companion.android.dashboard.ui.lovelace.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.DisplayColor
import io.homeassistant.companion.android.dashboard.derive.MediaControl
import io.homeassistant.companion.android.dashboard.derive.MediaControlModel
import io.homeassistant.companion.android.dashboard.derive.mediaControlModel
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.moreinfo.MediaGrouping
import io.homeassistant.companion.android.dashboard.moreinfo.MediaPosition
import io.homeassistant.companion.android.dashboard.ui.components.DashboardCardSurface
import io.homeassistant.companion.android.dashboard.ui.components.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.components.mediaplayer.MediaBrowserDialog
import io.homeassistant.companion.android.dashboard.ui.components.mediaplayer.MediaGroupingDialog
import io.homeassistant.companion.android.dashboard.ui.lovelace.common.CardInteractions
import io.homeassistant.companion.android.dashboard.ui.lovelace.components.Marquee
import io.homeassistant.companion.android.dashboard.ui.theme.Css
import io.homeassistant.companion.android.dashboard.ui.theme.cssPx
import io.homeassistant.companion.android.dashboard.ui.theme.toColor
import java.time.Instant
import java.time.ZonedDateTime
import kotlinx.coroutines.delay

/**
 * A media control card: on the artwork's colours (the theme's primary colour without artwork), the player's icon
 * and name, what is playing, its controls with browse and join at the end, and the track's progress; the artwork
 * fills a square on the right, fading into the colour. Off, the background turns grey without the artwork.
 *
 * Upstream: `hui-media-control-card` (src/panels/lovelace/cards/hui-media-control-card.ts)
 */
@Composable
internal fun MediaControlCard(
    card: CardConfig,
    hass: State<HassSnapshot?>,
    now: State<ZonedDateTime?>,
    interactions: CardInteractions,
    modifier: Modifier = Modifier,
) {
    val media by remember(card) { derivedStateOf { hass.value?.mediaControlModel(card) } }
    val model = media ?: return UnsupportedCard(card.entity.orEmpty(), modifier)
    val artwork by rememberArtworkColors(model.picture)
    var heightPx by remember { mutableIntStateOf(0) }
    val height = with(LocalDensity.current) { heightPx.toDp() }
    val foreground = artwork?.foreground?.let(::Color) ?: DisplayColor.Theme(TEXT_PRIMARY).toColor() ?: Color.White
    DashboardCardSurface(modifier = modifier) {
        BoxWithConstraints(Modifier.fillMaxWidth().onSizeChanged { heightPx = it.height }) {
            val width = maxWidth
            MediaBackground(model, artwork, height, Modifier.matchParentSize())
            MediaPlayer(
                model = model,
                foreground = foreground,
                narrow = width < NARROW_WIDTH && width >= VERY_NARROW_WIDTH,
                hideProgress = width < NARROW_WIDTH,
                veryNarrow = width < VERY_NARROW_WIDTH,
                hass = hass,
                now = now,
                interactions = interactions,
            )
        }
    }
}

/**
 * The player's name, what is playing, the controls and the progress bar, in the artwork's text colour.
 *
 * Upstream: part of `hui-media-control-card` (`.player`) (src/panels/lovelace/cards/hui-media-control-card.ts)
 */
@Composable
private fun MediaPlayer(
    model: MediaControlModel,
    foreground: Color,
    narrow: Boolean,
    hideProgress: Boolean,
    veryNarrow: Boolean,
    hass: State<HassSnapshot?>,
    now: State<ZonedDateTime?>,
    interactions: CardInteractions,
) {
    val progress = model.progress?.takeIf { !hideProgress }
    val showControls = model.controls.isNotEmpty() && (!veryNarrow || model.off || model.idleOrOn)
    val content = !model.unavailable && (model.title != null || showControls)
    Box(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(
                    start = Css.Space.S4,
                    top = Css.Space.S4,
                    end = Css.Space.S4,
                    // `.no-progress.player:not(.no-controls)`
                    bottom = if (progress == null && showControls) 0.cssPx else Css.Space.S4,
                ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = Css.Space.S10)) {
                DashboardIcon(model.icon, foreground, Modifier.size(HASize.X2L))
                Text(
                    model.name,
                    style = TextStyle(fontSize = Css.FontSize.M),
                    color = foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = Css.Space.S2),
                )
            }
            if (content) {
                Column(Modifier.padding(top = Css.Space.S4)) {
                    MediaInfo(model, foreground)
                    if (showControls) {
                        MediaControls(model, foreground, narrow, progress == null, hass, interactions)
                    }
                    progress?.let { MediaProgress(it, model.seekable, foreground, now, interactions) }
                }
            }
        }
        IconButton(
            onClick = { interactions.onAction(CardAction.MoreInfo(model.entityId)) },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(Css.Space.S1)
                .semantics { contentDescription = model.moreInfoLabel },
        ) {
            DashboardIcon(MORE_INFO_ICON, foreground, Modifier.size(HASize.X2L))
        }
    }
}

/**
 * The title (larger, scrolling by when tapped and too long) and the description under it.
 *
 * Upstream: part of `hui-media-control-card` (`.media-info`) (src/panels/lovelace/cards/hui-media-control-card.ts)
 */
@Composable
private fun MediaInfo(model: MediaControlModel, foreground: Color) {
    model.title?.let {
        Marquee(it, TextStyle(fontSize = TITLE_SIZE), foreground, Modifier.padding(bottom = Css.Space.S1))
    }
    model.subtitle?.let {
        Text(
            it,
            style = TextStyle(fontSize = Css.FontSize.M),
            color = foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The transport buttons (play, pause and stop larger), then browse and join at the end.
 *
 * Upstream: part of `hui-media-control-card` (`.controls`) (src/panels/lovelace/cards/hui-media-control-card.ts)
 */
@Composable
private fun MediaControls(
    model: MediaControlModel,
    foreground: Color,
    narrow: Boolean,
    noProgress: Boolean,
    hass: State<HassSnapshot?>,
    interactions: CardInteractions,
) {
    val bottom = if (narrow || noProgress || model.picture == null) 0.cssPx else Css.Space.S2
    val vertical = if (model.picture == null) 0.cssPx else Css.Space.S2
    Row(
        Modifier
            .fillMaxWidth()
            .startMargin(-Css.Space.S3)
            .padding(top = vertical, bottom = bottom, end = if (model.picture == null) 0.dp else Css.Space.S2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            model.controls.forEach { control -> TransportButton(control, foreground, narrow, interactions) }
        }
        model.browseLabel?.let { BrowseButton(model.entityId, it, foreground, hass, interactions) }
        model.grouping?.let { JoinButton(it, foreground, interactions) }
    }
}

/**
 * One transport button: 44dp with a 30dp icon, the play, pause and stop ones 56dp with a 40dp icon (smaller when
 * narrow).
 *
 * Upstream: part of `hui-media-control-card` (`.controls ha-icon-button`)
 * (src/panels/lovelace/cards/hui-media-control-card.ts)
 */
@Composable
private fun TransportButton(control: MediaControl, foreground: Color, narrow: Boolean, interactions: CardInteractions) {
    val (button, icon) = when {
        control.main && narrow -> NARROW_MAIN_BUTTON to NARROW_MAIN_ICON
        control.main -> MAIN_BUTTON to MAIN_ICON
        narrow -> NARROW_BUTTON to NARROW_ICON
        else -> BUTTON to ICON
    }
    IconButton(
        onClick = { interactions.onAction(control.action) },
        modifier = Modifier.size(button).semantics { contentDescription = control.label },
    ) {
        DashboardIcon(control.icon, foreground, Modifier.size(icon))
    }
}

/**
 * Opens the media browser, which plays what is picked on the player.
 *
 * Upstream: part of `hui-media-control-card` (`_handleBrowseMedia`) (src/panels/lovelace/cards/hui-media-control-card.ts)
 */
@Composable
private fun BrowseButton(
    entityId: String,
    label: String,
    foreground: Color,
    hass: State<HassSnapshot?>,
    interactions: CardInteractions,
) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = Modifier.size(BUTTON).semantics { contentDescription = label }) {
        DashboardIcon(BROWSE_ICON, foreground, Modifier.size(HASize.X2L))
    }
    val snapshot = hass.value
    if (open && snapshot != null) {
        MediaBrowserDialog(entityId, label, snapshot, interactions.onAction) { open = false }
    }
}

/**
 * Opens the dialog joining other players to this one; a badge counts the grouped players when more than one.
 *
 * Upstream: part of `hui-media-control-card` (`_handleJoinMediaPlayers`, `.badge`)
 * (src/panels/lovelace/cards/hui-media-control-card.ts)
 */
@Composable
private fun JoinButton(grouping: MediaGrouping, foreground: Color, interactions: CardInteractions) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.size(BUTTON)) {
        IconButton(
            onClick = { open = true },
            modifier = Modifier.size(BUTTON).semantics { contentDescription = grouping.label },
        ) {
            DashboardIcon(JOIN_ICON, foreground, Modifier.size(HASize.X2L))
        }
        grouping.count?.let { count ->
            val accent = DisplayColor.Theme(ACCENT).toColor() ?: foreground
            val onAccent = DisplayColor.Theme(TEXT_ACCENT).toColor() ?: Color.White
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .heightIn(min = BADGE_HEIGHT)
                    .widthIn(min = BADGE_HEIGHT)
                    .clip(RoundedCornerShape(BADGE_RADIUS))
                    .background(accent)
                    .padding(horizontal = Css.Space.S1),
                contentAlignment = Alignment.Center,
            ) {
                Text("$count", style = TextStyle(fontSize = Css.FontSize.Xs), color = onAccent)
            }
        }
    }
    if (open) MediaGroupingDialog(grouping, interactions.onAction) { open = false }
}

/**
 * The track's progress, an 8dp bar without a handle in the text colour (the accent without artwork), counting up
 * each second while playing; a tap seeks there when the player can seek.
 *
 * Upstream: part of `hui-media-control-card` (`ha-slider`, `_updateProgressBar`, `_handleSeek`)
 * (src/panels/lovelace/cards/hui-media-control-card.ts)
 */
@Composable
private fun MediaProgress(
    position: MediaPosition,
    seekable: Boolean,
    foreground: Color,
    now: State<ZonedDateTime?>,
    interactions: CardInteractions,
) {
    val base = now.value?.toInstant() ?: Instant.EPOCH
    // The dashboard's clock ticks slowly, so the seconds since it last did are counted here
    var ticks by remember(base, position) { mutableIntStateOf(0) }
    if (position.ticking) {
        LaunchedEffect(base, position) {
            while (true) {
                delay(TICK_MS)
                ticks++
            }
        }
    }
    val fraction = (position.at(base.plusSeconds(ticks.toLong())) / position.duration).toFloat().coerceIn(0f, 1f)
    var widthPx by remember { mutableIntStateOf(0) }
    val shape = RoundedCornerShape(PROGRESS_HEIGHT / 2)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = PROGRESS_MARGIN)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(position, seekable) {
                if (seekable) {
                    detectTapGesturesSeek(widthPx = { widthPx }) { percent ->
                        interactions.onAction(position.seek.withValue(position.duration * percent / PERCENT))
                    }
                }
            }
            .height(PROGRESS_HEIGHT)
            .clip(shape)
            .background(PROGRESS_TRACK),
    ) {
        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(foreground))
    }
}

private suspend fun PointerInputScope.detectTapGesturesSeek(widthPx: () -> Int, onSeek: (Int) -> Unit) =
    detectTapGestures { offset ->
        val width = widthPx()
        // `ha-slider` steps by 1 %
        if (width > 0) onSeek(Math.round(offset.x / width * PERCENT).coerceIn(0, PERCENT.toInt()))
    }

/** A CSS margin on the start side, which may be negative to widen the element over its parent's padding. */
private fun Modifier.startMargin(margin: Dp): Modifier = layout { measurable, constraints ->
    val shift = margin.roundToPx()
    val width = (constraints.maxWidth - shift).coerceAtLeast(0)
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(shift, 0) }
}

private const val ACCENT = "accent"
private const val TEXT_PRIMARY = "text-primary-color"
private const val TEXT_ACCENT = "text-accent-color"
private const val MORE_INFO_ICON = "mdi:dots-vertical"
private const val BROWSE_ICON = "mdi:play-box-multiple"
private const val JOIN_ICON = "mdi:speaker-multiple"
private const val PERCENT = 100f
private const val TICK_MS = 1000L
private val NARROW_WIDTH = 350.dp
private val VERY_NARROW_WIDTH = 300.dp

/** `hui-marquee`'s `font-size: 1.2em` of the card's 14px. */
private val TITLE_SIZE = Css.FontSize.M * 1.2f
private val BUTTON = 44.dp
private val ICON = 30.dp
private val MAIN_BUTTON = 56.dp
private val MAIN_ICON = 40.dp
private val NARROW_BUTTON = 40.dp
private val NARROW_ICON = 28.dp
private val NARROW_MAIN_BUTTON = 50.dp
private val NARROW_MAIN_ICON = 36.dp
private val BADGE_HEIGHT = 16.dp
private val BADGE_RADIUS = 10.dp
private val PROGRESS_HEIGHT = 8.dp

/** Half of what the hidden 16dp handle adds around the 8dp track. */
private val PROGRESS_MARGIN = 4.dp

/** `--ha-slider-track-color: rgba(200, 200, 200, 0.5)`. */
private val PROGRESS_TRACK = Color(red = 200, green = 200, blue = 200, alpha = 128)
