package net.jeremycasey.homeassistantnative.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.generated.Check
import io.github.timoptr.mdiicons.rememberImageVector
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import net.jeremycasey.homeassistantnative.R

/**
 * The server's user, as their profile picture or initials, with a check badge when it's the current server. Copied
 * from the companion app's server chooser (app/src/main/kotlin/io/homeassistant/companion/android/settings/server/
 * ServerChooser.kt, `ServerUserAvatar` and `ActiveBadge`).
 */
@Composable
internal fun ServerAvatar(server: ServerItem, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(AVATAR_SIZE)) {
        val avatarModifier = Modifier.fillMaxSize().clip(CircleShape)
        val avatar = server.avatar
        if (avatar != null) {
            Image(
                bitmap = avatar.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = avatarModifier,
            )
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = avatarModifier.background(LocalHAColorScheme.current.colorFillPrimaryNormalResting),
            ) {
                Text(
                    text = server.initials,
                    style = HATextStyle.BodyMedium.copy(color = LocalHAColorScheme.current.colorOnPrimaryNormal),
                )
            }
        }
        if (server.active) ActiveBadge(Modifier.align(Alignment.BottomEnd))
    }
}

/** A check badge on the bottom-end of the avatar, ringed in the surface color to stand off its edge. */
@Composable
private fun ActiveBadge(modifier: Modifier = Modifier) {
    val colors = LocalHAColorScheme.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(AVATAR_BADGE_SIZE)
            .clip(CircleShape)
            .background(colors.colorSurfaceDefault)
            .padding(BADGE_RING.dp)
            .clip(CircleShape)
            .background(colors.colorFillPrimaryLoudResting),
    ) {
        Icon(
            imageVector = Mdi.Check.rememberImageVector(),
            contentDescription = stringResource(R.string.settings_active),
            tint = colors.colorOnPrimaryLoud,
            modifier = Modifier.size(AVATAR_BADGE_ICON_SIZE),
        )
    }
}

internal val AVATAR_SIZE = HADimens.SPACE10
private val AVATAR_BADGE_SIZE = HADimens.SPACE5
private val AVATAR_BADGE_ICON_SIZE = HADimens.SPACE3
private const val BADGE_RING = 2
