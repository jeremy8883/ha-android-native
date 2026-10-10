package io.homeassistant.companion.android.dashboard.ui.moreinfo

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.moreinfo.MediaPlayerMoreInfo
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.cards.ServerImage
import java.time.Instant

/**
 * The controls of a media player's details, port of `more-info-media_player` (frontend@20260624.6
 * src/dialogs/more-info/controls/more-info-media_player.ts): the artwork (larger while playing), the title and
 * artist, the position, the transport buttons, the volume, and the source, sound mode and power buttons.
 */
@Composable
internal fun MoreInfoMediaPlayer(info: MediaPlayerMoreInfo, now: Instant, onAction: (CardAction) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
    ) {
        val unavailable = info.unavailable
        if (unavailable != null) {
            EmptyCover(unavailable, icon = false)
            return@Column
        }
        Artwork(info)
        MediaTitles(info.title, info.artist)
        info.position?.let { MediaPositionBar(it, now, onAction) }
        info.main?.let { MediaMainButtons(it, onAction) }
        info.volume?.let { MediaVolumeRow(it, onAction) }
        MediaControlsRow(info, onAction)
    }
}

@Composable
private fun Artwork(info: MediaPlayerMoreInfo) {
    // Upstream shrinks the artwork while it isn't playing
    val width by animateDpAsState(if (info.playing) COVER_PLAYING else COVER_IDLE, label = "cover")
    val picture = info.picture
    if (picture == null) {
        EmptyCover(info.state, icon = true)
    } else {
        ServerImage(
            path = picture,
            contentDescription = info.title,
            modifier = Modifier
                .widthIn(max = width)
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(HARadius.X3L)),
        )
    }
}

/** Upstream's empty cover: a music note (or the state's text when unavailable) on a quiet square. */
@Composable
private fun EmptyCover(state: String, icon: Boolean) {
    val colors = LocalHAColorScheme.current
    Box(
        modifier = Modifier
            .widthIn(max = COVER_IDLE)
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(HARadius.X3L))
            .background(colors.colorFillDisabledQuietResting)
            .semantics { contentDescription = state },
        contentAlignment = Alignment.Center,
    ) {
        if (icon) {
            DashboardIcon("mdi:music-note", colors.colorTextSecondary, Modifier.size(EMPTY_ICON))
        } else {
            Text(state, style = HATextStyle.HeadlineMedium, color = colors.colorTextSecondary)
        }
    }
}

@Composable
private fun MediaTitles(title: String?, artist: String?) {
    val colors = LocalHAColorScheme.current
    if (title == null && artist == null) return
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        title?.let {
            Text(
                it,
                style = HATextStyle.HeadlineMedium,
                color = colors.colorTextPrimary,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.basicMarquee(),
            )
        }
        artist?.let {
            Text(
                it,
                style = HATextStyle.Body,
                color = colors.colorTextSecondary,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.basicMarquee(),
            )
        }
    }
}

private val COVER_PLAYING = 320.dp
private val COVER_IDLE = 240.dp
private val EMPTY_ICON = 96.dp
