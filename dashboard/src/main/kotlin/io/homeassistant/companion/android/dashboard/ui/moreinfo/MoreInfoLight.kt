package io.homeassistant.companion.android.dashboard.ui.moreinfo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.composable.HADropdownMenu
import io.homeassistant.companion.android.common.compose.theme.HABorderWidth
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HAFontSize
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.moreinfo.LightButton
import io.homeassistant.companion.android.dashboard.moreinfo.LightChannel
import io.homeassistant.companion.android.dashboard.moreinfo.LightChannelSlider
import io.homeassistant.companion.android.dashboard.moreinfo.LightMainControl
import io.homeassistant.companion.android.dashboard.moreinfo.LightMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.SelectMenu
import io.homeassistant.companion.android.dashboard.moreinfo.lightColorBrightnessCall
import io.homeassistant.companion.android.dashboard.moreinfo.lightColorPicker
import io.homeassistant.companion.android.dashboard.moreinfo.lightHueCall
import io.homeassistant.companion.android.dashboard.moreinfo.lightWhiteCall
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.controls.ControlSlider
import io.homeassistant.companion.android.dashboard.ui.controls.ControlSliderStyle
import io.homeassistant.companion.android.dashboard.ui.controls.HsColorWheel
import io.homeassistant.companion.android.dashboard.ui.controls.StateControlSlider
import io.homeassistant.companion.android.dashboard.ui.controls.StateToggleControl
import io.homeassistant.companion.android.dashboard.ui.controls.WheelChannels
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * The controls of a light's details, port of `more-info-light` (frontend@20260624.6
 * src/dialogs/more-info/controls/more-info-light.ts): the brightness (or a switch, without one) in the middle, the
 * colour or temperature in its place when chosen below, and the effect menu.
 */
@Composable
internal fun MoreInfoLight(
    light: LightMoreInfo,
    state: EntityState,
    hass: HassSnapshot,
    onAction: (CardAction) -> Unit,
) {
    var main by rememberSaveable(state.entityId, saver = MainControlSaver) {
        mutableStateOf<LightMainControl>(LightMainControl.Brightness)
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE6),
    ) {
        light.toggle?.let { StateToggleControl(it, onAction) }
        when (main) {
            LightMainControl.Brightness -> light.brightness?.let { slider ->
                StateControlSlider(slider, valueText = { "${it.roundToInt()}%" }, onAction = onAction)
            }
            LightMainControl.ColorTemp -> light.colorTemp?.let { slider ->
                StateControlSlider(slider, valueText = {
                    "${it.roundToInt()} K"
                }, onAction = onAction, whileMoving = true)
            }
            LightMainControl.Color -> LightColorControl(state, hass, onAction)
        }
        if (light.buttons.isNotEmpty()) LightButtons(light.buttons, main, onShow = { main = it }, onAction = onAction)
        light.effect?.let { EffectMenu(it, onAction) }
    }
}

/** The colour wheel and, for RGBW and RGBWW lights, the sliders of their channels. */
@Composable
private fun LightColorControl(state: EntityState, hass: HassSnapshot, onAction: (CardAction) -> Unit) {
    val picker = remember(state) { hass.lightColorPicker(state) }
    val toWheel = { value: Double? -> value?.let { it * RGB_MAX / PERCENT } }
    var moving by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    val send by rememberUpdatedState { hue: Double, saturation: Double ->
        onAction(lightHueCall(state, hue, saturation, picker.colorBrightness))
    }
    // While dragging, the colour follows at most every half second, as upstream throttles it
    LaunchedEffect(moving != null) {
        var last: Pair<Double, Double>? = null
        while (moving != null) {
            moving?.takeIf { it != last }?.let { (h, s) ->
                last = h to s
                send(h, s)
            }
            delay(THROTTLE_MS)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4)) {
        HsColorWheel(
            hue = picker.hue,
            saturation = picker.saturation,
            channels = WheelChannels(
                colorBrightness = toWheel(picker.colorBrightness),
                white = toWheel(picker.white),
                coldWhite = toWheel(picker.coldWhite),
                warmWhite = toWheel(picker.warmWhite),
                minKelvin = picker.minKelvin,
                maxKelvin = picker.maxKelvin,
            ),
            label = hass.localize("ui.dialogs.more_info_control.light.color"),
            enabled = state.state != UNAVAILABLE,
            onChanged = { h, s -> send(h, s) },
            onMoved = { moving = it },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        picker.sliders.forEach { slider ->
            ChannelSlider(slider, enabled = state.state != UNAVAILABLE) { value ->
                onAction(
                    when (slider.channel) {
                        LightChannel.ColorBrightness -> lightColorBrightnessCall(state, picker.colorBrightness, value)
                        else -> lightWhiteCall(state, slider.channel, value)
                    },
                )
            }
        }
    }
}

/**
 * Port of `ha-labeled-slider`: a caption with its icon over a 0–100 slider, sent when released. Drawn as the
 * details' other sliders, with its handle always showing, so that 0 still reads as a slider.
 */
@Composable
private fun ChannelSlider(slider: LightChannelSlider, enabled: Boolean, onChanged: (Double) -> Unit) {
    val colors = LocalHAColorScheme.current
    val color = colors.colorFillPrimaryLoudResting
    Column(modifier = Modifier.width(STATE_CONTROL_WIDE), verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2)) {
        Text(slider.caption, style = HATextStyle.Body, color = colors.colorTextPrimary)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
        ) {
            DashboardIcon(slider.icon, colors.colorTextSecondary, Modifier.size(HASize.X2L))
            ControlSlider(
                value = slider.value,
                range = 0.0..PERCENT,
                step = 1.0,
                label = slider.caption,
                valueText = { "${it.roundToInt()}%" },
                style = ControlSliderStyle(
                    thickness = CHANNEL_SLIDER_HEIGHT,
                    cornerRadius = HARadius.M,
                    color = color,
                    background = SolidColor(color),
                    backgroundAlpha = CHANNEL_BACKGROUND_ALPHA,
                    tooltipFontSize = HAFontSize.M,
                ),
                onChanged = onChanged,
                modifier = Modifier.weight(1f).height(CHANNEL_SLIDER_HEIGHT),
                showHandle = true,
                enabled = enabled,
            )
        }
    }
}

/** Port of `ha-icon-button-group`: the power button, the main control choices and the white button in a pill. */
@Composable
private fun LightButtons(
    buttons: List<LightButton>,
    main: LightMainControl,
    onShow: (LightMainControl) -> Unit,
    onAction: (CardAction) -> Unit,
) {
    val colors = LocalHAColorScheme.current
    Row(
        modifier = Modifier
            .height(GROUP_HEIGHT)
            .clip(RoundedCornerShape(GROUP_RADIUS))
            .background(GROUP_BACKGROUND),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        buttons.forEach { button ->
            when (button) {
                is LightButton.Power -> IconButton(onClick = { onAction(button.action) }, enabled = button.enabled) {
                    ButtonIcon("mdi:power", button.label, button.enabled)
                }
                is LightButton.White -> IconButton(onClick = { onAction(button.action) }, enabled = button.enabled) {
                    ButtonIcon("mdi:file-word-box", button.label, button.enabled)
                }
                is LightButton.Show -> ShowButton(button, selected = button.control == main) { onShow(button.control) }
                LightButton.Separator -> Box(
                    Modifier
                        .padding(horizontal = HABorderWidth.S)
                        .width(HABorderWidth.S)
                        .height(SEPARATOR_HEIGHT)
                        .background(colors.colorTextPrimary.copy(alpha = SEPARATOR_ALPHA)),
                )
            }
        }
    }
}

@Composable
private fun ButtonIcon(icon: String, label: String, enabled: Boolean) {
    val colors = LocalHAColorScheme.current
    DashboardIcon(
        name = icon,
        tint = if (enabled) colors.colorTextPrimary else colors.colorTextDisabled,
        modifier = Modifier.size(HASize.X2L).semantics { contentDescription = label },
    )
}

/**
 * Port of `ha-icon-button-toggle`: the brightness icon, or a swatch of colours or of whites. Selected, the icon
 * is filled in and the swatches are ringed.
 */
@Composable
private fun ShowButton(button: LightButton.Show, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalHAColorScheme.current
    val shape = RoundedCornerShape(HARadius.X2L)
    val ring = selected && button.enabled
    Box(
        modifier = Modifier
            .size(GROUP_HEIGHT)
            .selectable(selected = selected, enabled = button.enabled, role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = button.label },
        contentAlignment = Alignment.Center,
    ) {
        val highlight = Modifier.size(HIGHLIGHT_SIZE).clip(shape)
        when (button.control) {
            LightMainControl.Brightness -> Box(
                modifier = highlight.background(if (ring) colors.colorTextPrimary else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                DashboardIcon(
                    name = "mdi:brightness-6",
                    tint = when {
                        !button.enabled -> colors.colorTextDisabled
                        ring -> colors.colorSurfaceDefault
                        else -> colors.colorTextPrimary
                    },
                    modifier = Modifier.size(HASize.X2L),
                )
            }
            else -> Box(
                modifier = highlight.then(
                    if (ring) Modifier.border(HABorderWidth.M, colors.colorTextPrimary, shape) else Modifier,
                ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(SWATCH_SIZE)
                        .alpha(if (button.enabled) 1f else DISABLED_ALPHA)
                        .clip(RoundedCornerShape(HARadius.XL))
                        .then(
                            if (button.control == LightMainControl.Color) {
                                Modifier.background(COLOR_SWATCH).background(COLOR_SWATCH_CENTRE)
                            } else {
                                Modifier.background(TEMP_SWATCH)
                            },
                        ),
                )
            }
        }
    }
}

/** Port of the effect `ha-control-select-menu`, as the app's dropdown. */
@Composable
private fun EffectMenu(menu: SelectMenu, onAction: (CardAction) -> Unit) {
    HADropdownMenu(
        items = menu.options.map { HADropdownItem(it.value, it.label) },
        selectedKey = menu.value,
        onItemSelected = { value ->
            if (value != menu.value) menu.options.firstOrNull { it.value == value }?.let { onAction(it.action) }
        },
        label = menu.label,
        enabled = menu.enabled,
        modifier = Modifier.width(STATE_CONTROL_WIDE),
    )
}

private val MainControlSaver = Saver<MutableState<LightMainControl>, String>(
    save = {
        when (it.value) {
            LightMainControl.Brightness -> "brightness"
            LightMainControl.Color -> "color"
            LightMainControl.ColorTemp -> "color_temp"
        }
    },
    restore = {
        mutableStateOf(
            when (it) {
                "color" -> LightMainControl.Color
                "color_temp" -> LightMainControl.ColorTemp
                else -> LightMainControl.Brightness
            },
        )
    },
)

private const val UNAVAILABLE = "unavailable"
private const val RGB_MAX = 255.0
private const val PERCENT = 100.0
private const val THROTTLE_MS = 500L
private const val DISABLED_ALPHA = 0.5f
private const val SEPARATOR_ALPHA = 0.15f
private const val CHANNEL_BACKGROUND_ALPHA = 0.2f
private val CHANNEL_SLIDER_HEIGHT = HADimens.SPACE8
private val GROUP_HEIGHT = HADimens.SPACE12
private val GROUP_RADIUS = HADimens.SPACE7
private val HIGHLIGHT_SIZE = HADimens.SPACE10
private val SWATCH_SIZE = 30.dp
private val SEPARATOR_HEIGHT = HADimens.SPACE10
private val STATE_CONTROL_WIDE = 320.dp

/** `rgba(139, 145, 151, 0.1)`, the group's background in both themes. */
private val GROUP_BACKGROUND = Color(red = 139, green = 145, blue = 151, alpha = 26)

/** The frontend's colour wheel image, as a sweep of hues fading to white at the centre. */
private val COLOR_SWATCH = Brush.sweepGradient(
    listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red),
)

private val COLOR_SWATCH_CENTRE = Brush.radialGradient(listOf(Color.White, Color.Transparent))

/** `linear-gradient(0, rgb(166, 209, 255) 0%, white 50%, rgb(255, 160, 0) 100%)`: cool at the bottom. */
private val TEMP_SWATCH = Brush.verticalGradient(
    listOf(Color(red = 255, green = 160, blue = 0), Color.White, Color(red = 166, green = 209, blue = 255)),
)
