package io.homeassistant.companion.android.dashboard.ui.cards

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.DisplayColor
import io.homeassistant.companion.android.dashboard.derive.LightCardModel
import io.homeassistant.companion.android.dashboard.derive.lightCardModel
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.ui.controls.RoundSlider
import io.homeassistant.companion.android.dashboard.ui.theme.toColor
import kotlinx.coroutines.delay

/**
 * A light card, port of `hui-light-card` (frontend@20260624.6 src/panels/lovelace/cards/hui-light-card.ts): a round
 * brightness slider around the light's large icon, which toggles it, its name under them (with the brightness while
 * it's dragged), and a button opening its details.
 */
@Composable
internal fun LightCard(
    card: CardConfig,
    hass: State<HassSnapshot?>,
    interactions: CardInteractions,
    modifier: Modifier = Modifier,
) {
    val light by remember(card) { derivedStateOf { hass.value?.lightCardModel(card) } }
    val model = when (val shown = light) {
        null -> return
        is LightCardModel.Warning -> return CardWarning(shown.text, modifier)
        is LightCardModel.Shown -> shown
    }
    val colors = LocalHAColorScheme.current
    // The brightness being dragged to, shown for half a second after it last changed
    var dragged by remember { mutableStateOf<Int?>(null) }
    var showBrightness by remember { mutableStateOf(false) }
    LaunchedEffect(dragged) {
        if (dragged == null) {
            delay(BRIGHTNESS_FADE_MS)
            showBrightness = false
        }
    }
    DashboardCardSurface(modifier = modifier) {
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                BrightnessControl(model, interactions, Modifier.padding(HADimens.SPACE4)) { value ->
                    dragged = value
                    if (value != null) showBrightness = true
                }
                LightInfo(model, dragged ?: model.brightness, showBrightness)
            }
            IconButton(
                onClick = { interactions.onAction(CardAction.MoreInfo(model.entityId)) },
                modifier = Modifier.align(Alignment.TopEnd).semantics { contentDescription = model.moreInfoLabel },
            ) {
                DashboardIcon(MORE_INFO_ICON, colors.colorTextSecondary, Modifier.size(HASize.X2L))
            }
        }
    }
}

/** The slider (hidden when the light has no brightness) with the light's icon in its middle. */
@Composable
private fun BrightnessControl(
    model: LightCardModel.Shown,
    interactions: CardInteractions,
    modifier: Modifier,
    onDragging: (Int?) -> Unit,
) {
    val colors = LocalHAColorScheme.current
    Box(
        modifier.widthIn(min = SLIDER_MIN_WIDTH, max = SLIDER_MAX_WIDTH).fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        RoundSlider(
            value = model.brightness,
            min = 1,
            max = PERCENT,
            bar = DisplayColor.Theme(PRIMARY).toColor() ?: colors.colorFillPrimaryLoudResting,
            track = DisplayColor.State(listOf(SLIDER_TRACK)).toColor() ?: colors.colorFillDisabledLoudResting,
            enabled = !model.disabled,
            onChanging = { onDragging(it) },
            onChanged = { value ->
                onDragging(null)
                interactions.onAction(model.brightnessCall.withValue(value.toDouble()))
            },
            modifier = Modifier.fillMaxWidth().aspectRatio(
                SLIDER_ASPECT_RATIO,
            ).alpha(if (model.supportsBrightness) 1f else 0f),
        )
        val icon = Modifier.fillMaxWidth(ICON_FRACTION).aspectRatio(1f).clip(CircleShape)
        DashboardIcon(
            model.icon,
            model.color.toColor() ?: colors.colorTextSecondary,
            if (model.disabled) icon else icon.elementGestures(model.actions, interactions),
        )
    }
}

/** The name, with the state above it while unavailable, or the brightness fading in while dragged. */
@Composable
private fun LightInfo(model: LightCardModel.Shown, brightness: Int, showBrightness: Boolean) {
    val colors = LocalHAColorScheme.current
    val brightnessAlpha by animateFloatAsState(if (showBrightness) 1f else 0f, tween(FADE_MS), label = "brightness")
    // Upstream pulls the info up into the slider's open bottom
    Column(
        Modifier.fillMaxWidth().pulledUp(INFO_OVERLAP).padding(HADimens.SPACE4),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val style = HATextStyle.Body.copy(fontSize = NAME_FONT_SIZE, lineHeight = NAME_LINE_HEIGHT)
        val stateText = model.stateText
        if (stateText != null) {
            Text(stateText, style = style, color = colors.colorTextPrimary)
        } else {
            Text(
                "$brightness %",
                style = style,
                color = colors.colorTextPrimary,
                modifier = Modifier.alpha(brightnessAlpha),
            )
        }
        Text(model.name, style = style, color = colors.colorTextPrimary, maxLines = 2)
    }
}

/** Drawn [by] higher, taking that much less room (a negative top margin). */
private fun Modifier.pulledUp(by: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val shift = by.roundToPx()
    layout(placeable.width, (placeable.height - shift).coerceAtLeast(0)) { placeable.place(0, -shift) }
}

private const val MORE_INFO_ICON = "mdi:dots-vertical"
private const val PRIMARY = "primary"
private const val SLIDER_TRACK = "slider-track-color"
private const val PERCENT = 100
private const val ICON_FRACTION = 0.6f

/** The arc's box: twice the radius wide, the radius plus the ends' depth (sin 45°) tall, with the handle's margin. */
private const val SLIDER_ASPECT_RATIO = 2f / 1.707f
private const val FADE_MS = 500
private const val BRIGHTNESS_FADE_MS = 500L
private val SLIDER_MIN_WIDTH = 100.dp
private val SLIDER_MAX_WIDTH = 200.dp
private val INFO_OVERLAP = 56.dp

/** `--name-font-size: 1.2rem`. */
private val NAME_FONT_SIZE = 19.2.sp
private val NAME_LINE_HEIGHT = 24.sp
