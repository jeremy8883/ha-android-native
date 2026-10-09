package net.jeremycasey.homeassistantnative.login

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.theme.HABorderWidth
import io.homeassistant.companion.android.common.compose.theme.HABrandColors
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.common.compose.theme.MaxButtonWidth
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import net.jeremycasey.homeassistantnative.R

// The companion app's server discovery screen (app/src/main/kotlin/io/homeassistant/companion/android/onboarding/
// serverdiscovery/ServerDiscoveryScreen.kt): searching, then the servers found, or typing the address.

/** Searching the network, with the servers found as they come, and a way to type the address instead. */
@Composable
internal fun ColumnScope.ServerDiscoveryContent(
    discovery: Discovery,
    connecting: Boolean,
    onConnect: (HomeAssistantInstance) -> Unit,
    onManualSetup: () -> Unit,
) {
    Text(
        text = stringResource(commonR.string.searching_home_network),
        style = HATextStyle.Headline,
        modifier = Modifier.padding(top = HADimens.SPACE6),
    )
    if (discovery.servers.isEmpty()) {
        ScanningForServer(failed = discovery.failed)
    } else {
        Spacer(modifier = Modifier.height(HADimens.SPACE8))
        discovery.servers.forEach { server -> ServerItem(server, enabled = !connecting) { onConnect(server) } }
        Box(modifier = Modifier.fillMaxWidth().padding(HADimens.SPACE8), contentAlignment = Alignment.Center) {
            HALoading()
        }
        Spacer(modifier = Modifier.weight(1f))
    }
    HAPlainButton(
        text = stringResource(commonR.string.manual_setup),
        onClick = onManualSetup,
        modifier = Modifier.fillMaxWidth().padding(bottom = HADimens.SPACE6),
    )
}

/** A server found, as a bordered row with the Home Assistant logo, its name and its address. */
@Composable
private fun ServerItem(server: HomeAssistantInstance, enabled: Boolean, onClick: () -> Unit) {
    val rowShape = RoundedCornerShape(size = HARadius.XL)
    Row(
        modifier = Modifier
            .widthIn(max = MaxButtonWidth)
            .fillMaxWidth()
            .padding(vertical = HADimens.SPACE2)
            .border(BorderStroke(HABorderWidth.S, LocalHAColorScheme.current.colorBorderNeutralQuiet), rowShape)
            .clip(rowShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = LocalHAColorScheme.current.colorFillPrimaryLoudHover),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            imageVector = ImageVector.vectorResource(R.drawable.ic_home_assistant_branding),
            contentDescription = null,
            modifier = Modifier.size(ICON_SIZE).padding(vertical = HADimens.SPACE2, horizontal = HADimens.SPACE4),
        )
        Column(modifier = Modifier.padding(vertical = HADimens.SPACE2).padding(end = HADimens.SPACE4)) {
            Text(text = server.name, style = HATextStyle.Body.copy(textAlign = TextAlign.Start))
            Text(text = server.url.toString(), style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.Start))
        }
    }
}

/** The searching animation; after a while without a server (or when searching failed), what to check. */
@Composable
private fun ColumnScope.ScanningForServer(failed: Boolean) {
    var waitedLong by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(NO_SERVER_HINT_DELAY)
        waitedLong = true
    }
    Spacer(modifier = Modifier.weight(POSITION))
    AnimatedIcon()
    Spacer(modifier = Modifier.weight(POSITION))
    val hintAlpha by animateFloatAsState(
        targetValue = if (failed || waitedLong) 1f else 0f,
        animationSpec = tween(durationMillis = HINT_FADE_MILLIS, easing = FastOutSlowInEasing),
    )
    Text(
        text = stringResource(commonR.string.server_discovery_no_server_info),
        style = HATextStyle.Body,
        modifier = Modifier
            .padding(vertical = HADimens.SPACE3, horizontal = HADimens.SPACE4)
            .alpha(hintAlpha)
            .widthIn(max = MaxButtonWidth),
    )
    Spacer(modifier = Modifier.weight(1f - 2f * POSITION))
}

/** Dots turning around the Home Assistant mark, which pulses. */
@Composable
private fun AnimatedIcon() {
    Box(modifier = Modifier.padding(HADimens.SPACE3)) {
        val rotation by rememberInfiniteTransition(label = "dots_rotation").animateFloat(
            initialValue = 0f,
            targetValue = FULL_TURN,
            animationSpec = infiniteRepeatable(tween(ROTATION_MILLIS, easing = LinearEasing), RepeatMode.Restart),
            label = "dots_rotation_value",
        )
        val pulse by rememberInfiniteTransition(label = "icon_pulse").animateFloat(
            initialValue = 1f,
            targetValue = PULSE_SCALE,
            animationSpec = infiniteRepeatable(tween(PULSE_MILLIS, easing = LinearEasing), RepeatMode.Reverse),
            label = "icon_pulse_value",
        )
        Image(
            imageVector = ImageVector.vectorResource(R.drawable.dots),
            contentDescription = null,
            modifier = Modifier.size(DOTS_SIZE.dp).align(Alignment.Center).rotate(rotation),
        )
        Box(
            modifier = Modifier
                .size(MARK_SIZE.dp)
                .scale(pulse)
                .align(Alignment.Center)
                .background(HABrandColors.Blue, CircleShape),
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(commonR.drawable.ic_stat_ic_notification_blue),
                contentDescription = null,
                modifier = Modifier.align(Alignment.Center).size(HASize.X5L).scale(pulse),
                tint = HABrandColors.Background,
            )
        }
    }
}

internal val ICON_SIZE = 64.dp
private const val POSITION = 0.2f
private val NO_SERVER_HINT_DELAY = 5.seconds
private const val HINT_FADE_MILLIS = 2000
private const val FULL_TURN = 360f
private const val ROTATION_MILLIS = 5000
private const val PULSE_SCALE = 1.15f
private const val PULSE_MILLIS = 800
private const val DOTS_SIZE = 220
private const val MARK_SIZE = 80
