package io.homeassistant.companion.android.dashboard.ui.lovelace.cardfeatures

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.display.blankBeforeUnit
import io.homeassistant.companion.android.dashboard.feature.TileFeature
import io.homeassistant.companion.android.dashboard.moreinfo.SelectMenu
import io.homeassistant.companion.android.dashboard.moreinfo.SliderBackground
import io.homeassistant.companion.android.dashboard.ui.components.ControlSlider
import io.homeassistant.companion.android.dashboard.ui.components.ControlSliderStyle
import io.homeassistant.companion.android.dashboard.ui.components.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.components.SelectMenuOptions
import io.homeassistant.companion.android.dashboard.ui.theme.Css
import io.homeassistant.companion.android.dashboard.ui.theme.cssFontPx
import io.homeassistant.companion.android.dashboard.ui.theme.cssPx
import io.homeassistant.companion.android.dashboard.ui.theme.parseCssColor
import io.homeassistant.companion.android.dashboard.ui.theme.resolveVariable
import io.homeassistant.companion.android.dashboard.ui.theme.toColor
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.delay

/**
 * A slider the height of the other features, in the feature colour unless it has its own, over its track: a tint,
 * a gradient from start to end, or a cover tilt's stripes. A mute button can follow it.
 *
 * Upstream: `ha-control-slider` as `cardFeatureStyles` styles it (src/components/ha-control-slider.ts;
 * src/panels/lovelace/card-features/common/card-feature-styles.ts)
 */
@Composable
internal fun FeatureValueSlider(feature: TileFeature.ValueSlider, featureColor: Color, onAction: (CardAction) -> Unit) {
    val slider = feature.slider
    val color = slider.color?.toColor() ?: featureColor
    val (background, alpha) = when (val track = slider.background) {
        is SliderBackground.Tint -> SolidColor(track.color?.toColor() ?: color) to track.opacity
        is SliderBackground.Stripes -> SolidColor(track.color?.toColor() ?: color) to track.opacity
        is SliderBackground.Gradient -> Brush.horizontalGradient(
            track.stops.map { (_, hex) -> parseCssColor(hex) ?: Color.Unspecified },
        ) to 1f
    }
    val digits = BigDecimal.valueOf(slider.step).stripTrailingZeros().scale().coerceAtLeast(0)
    Row(horizontalArrangement = Arrangement.spacedBy(FEATURE_BUTTON_SPACING)) {
        ControlSlider(
            value = slider.value,
            range = slider.min..slider.max,
            step = slider.step,
            label = slider.label,
            valueText = { value ->
                val number = BigDecimal.valueOf(value).setScale(digits, RoundingMode.HALF_UP).toPlainString()
                slider.unit?.let { number + blankBeforeUnit(it) + it } ?: number
            },
            style = ControlSliderStyle(
                thickness = FEATURE_HEIGHT,
                cornerRadius = FEATURE_RADIUS,
                color = color,
                background = background,
                backgroundAlpha = alpha,
                tooltipFontSize = Css.FontSize.M,
                overlay = (slider.background as? SliderBackground.Stripes)?.let { tiltStripes(color) },
                overlayAlpha = STRIPES_ALPHA,
            ),
            onChanged = { onAction(slider.service.withValue(it)) },
            modifier = Modifier.weight(1f).height(FEATURE_HEIGHT),
            inverted = slider.inverted,
            mode = slider.mode,
            showHandle = slider.showHandle,
            enabled = slider.enabled,
        )
        feature.trailing?.let { FeatureButton(it, featureColor, onAction, Modifier.size(FEATURE_HEIGHT)) }
    }
}

/**
 * Port of `generateTiltSliderTrackBackgroundGradient`, horizontal: 24 stripes of [color] from the start, each
 * wider than the one before.
 */
private fun tiltStripes(color: Color): Brush {
    val stops = (0 until STRIPES).flatMap { i ->
        val start = i.toFloat() / STRIPES
        val end = start + i.toFloat() / (STRIPES * STRIPES) * (1 - MIN_STRIPE) + MIN_STRIPE / STRIPES
        listOf(start to Color.Transparent, start to color, end to color, end to Color.Transparent)
    }
    return object : ShaderBrush() {
        override fun createShader(size: Size): Shader = LinearGradientShader(
            from = Offset.Zero,
            to = Offset(size.width, 0f),
            colors = stops.map { it.second },
            colorStops = stops.map { it.first },
        )
    }
}

/**
 * Port of `ha-control-select-menu` as a feature: the width of the tile and its height, the selected option's icon
 * (or the feature's), the label unless hidden, the selected option, and an arrow.
 *
 * Upstream: `ha-control-select-menu` (src/components/ha-control-select-menu.ts)
 */
@Composable
internal fun FeatureMenu(menu: TileFeature.Menu, onAction: (CardAction) -> Unit) {
    val colors = LocalHAColorScheme.current
    var expanded by remember { mutableStateOf(false) }
    val selected = menu.options.firstOrNull { it.value == menu.value }
    val icon = if (menu.value != null && menu.optionIcons) selected?.icon else menu.icon
    val textColor = if (menu.enabled) colors.colorTextPrimary else colors.colorTextDisabled
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(FEATURE_HEIGHT)
                .clip(RoundedCornerShape(FEATURE_RADIUS))
                .background(colors.colorFillDisabledLoudResting.copy(alpha = MENU_BACKGROUND_ALPHA))
                .clickable(enabled = menu.enabled, role = Role.DropdownList) { expanded = true }
                .padding(horizontal = MENU_PADDING_H, vertical = MENU_PADDING_V)
                .semantics { contentDescription = menu.label },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MENU_PADDING_H),
        ) {
            if (icon != null) DashboardIcon(icon, textColor, Modifier.size(HASize.XL))
            Column(modifier = Modifier.weight(1f)) {
                if (!menu.hideLabel) {
                    Text(
                        menu.label,
                        style = if (selected != null) LABEL_STYLE else VALUE_STYLE,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                selected?.let {
                    Text(
                        it.label,
                        style = VALUE_STYLE,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            DashboardIcon(MENU_DOWN, textColor, Modifier.size(HASize.XL))
        }
        SelectMenuOptions(
            SelectMenu(menu.label, menu.icon.orEmpty(), menu.value, menu.enabled, menu.options, menu.optionIcons),
            expanded,
            onDismiss = { expanded = false },
            onAction = onAction,
        )
    }
}

/**
 * Port of `ha-control-switch` as a feature: a bar in the feature colour at a fifth, its half-width knob sliding to
 * the end when on, with the on or off icon.
 *
 * Upstream: `ha-control-switch` (src/components/ha-control-switch.ts)
 */
@Composable
internal fun FeatureSwitch(switch: TileFeature.Switch, color: Color, onAction: (CardAction) -> Unit) {
    val animated by animateColorAsState(color, tween(SWITCH_TRANSITION_MS), label = "switch-color")
    val position by animateFloatAsState(if (switch.checked) 1f else 0f, tween(SWITCH_TRANSITION_MS), label = "knob")
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(FEATURE_HEIGHT)
            .alpha(if (switch.enabled) 1f else DISABLED_ALPHA)
            .clip(RoundedCornerShape(FEATURE_RADIUS))
            .background(animated.copy(alpha = SLIDER_BACKGROUND_ALPHA))
            .toggleable(value = switch.checked, enabled = switch.enabled, role = Role.Switch) { on ->
                onAction(if (on) switch.turnOn else switch.turnOff)
            }
            .semantics { contentDescription = switch.label },
    ) {
        Box(
            modifier = Modifier
                .offset(x = maxWidth / 2 * position)
                .width(maxWidth / 2)
                .fillMaxHeight()
                .clip(RoundedCornerShape(FEATURE_RADIUS))
                .background(animated),
            contentAlignment = Alignment.Center,
        ) {
            DashboardIcon(
                name = if (switch.checked) switch.onIcon else switch.offIcon,
                tint = Color.White,
                modifier = Modifier.size(HASize.X2L),
            )
        }
    }
}

/**
 * The open door button: a first press turns it orange asking to confirm for five seconds, a second opens the door
 * and shows a green "Done" for two.
 *
 * Upstream: `hui-lock-open-door-card-feature` (src/panels/lovelace/card-features/hui-lock-open-door-card-feature.ts)
 */
@Composable
internal fun FeatureOpenDoor(feature: TileFeature.OpenDoor, onAction: (CardAction) -> Unit) {
    val colors = LocalHAColorScheme.current
    val dark = isSystemInDarkTheme()
    var step by remember { mutableStateOf<DoorStep>(DoorStep.Normal) }
    LaunchedEffect(step) {
        when (step) {
            DoorStep.Confirm -> delay(CONFIRM_TIMEOUT_MS)
            DoorStep.Done -> delay(DONE_TIMEOUT_MS)
            DoorStep.Normal -> return@LaunchedEffect
        }
        step = DoorStep.Normal
    }
    if (step == DoorStep.Done) {
        val success = resolveVariable(SUCCESS_COLOR, dark) ?: colors.colorOnSuccessNormal
        Row(
            modifier = Modifier.fillMaxWidth().height(FEATURE_HEIGHT),
            horizontalArrangement = Arrangement.spacedBy(Css.Space.S2, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DashboardIcon(CHECK, success, Modifier.size(HASize.X2L))
            Text(
                feature.doneLabel,
                style = Css.Text.Body.copy(textAlign = TextAlign.Center, fontWeight = Css.FontWeight.Medium),
                color = success,
            )
        }
        return
    }
    val tint = if (step == DoorStep.Confirm) {
        resolveVariable(WARNING_COLOR, dark) ?: colors.colorFillWarningLoudResting
    } else {
        colors.colorFillDisabledLoudResting
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(FEATURE_HEIGHT)
            .alpha(if (feature.enabled) 1f else DISABLED_ALPHA)
            .clip(RoundedCornerShape(FEATURE_RADIUS))
            .background(tint.copy(alpha = SLIDER_BACKGROUND_ALPHA))
            .clickable(enabled = feature.enabled, role = Role.Button) {
                if (step == DoorStep.Confirm) {
                    onAction(feature.action)
                    step = DoorStep.Done
                } else {
                    step = DoorStep.Confirm
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (step == DoorStep.Confirm) feature.confirmLabel else feature.label,
            style = Css.Text.Body.copy(textAlign = TextAlign.Center),
            color = colors.colorTextPrimary,
        )
    }
}

/** Where the open door button is: waiting, asking to confirm, or just opened. */
private sealed interface DoorStep {
    /** Waiting for a first press. */
    data object Normal : DoorStep

    /** Asking to confirm. */
    data object Confirm : DoorStep

    /** Just opened. */
    data object Done : DoorStep
}

/**
 * A "Set date" button opening Android's date picker; the chosen day sets the entity.
 *
 * Upstream: `hui-date-set-card-feature` (src/panels/lovelace/card-features/hui-date-set-card-feature.ts)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeatureDateSet(feature: TileFeature.DateSet, onAction: (CardAction) -> Unit) {
    val colors = LocalHAColorScheme.current
    var open by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(FEATURE_HEIGHT)
            .alpha(if (feature.enabled) 1f else DISABLED_ALPHA)
            .clip(RoundedCornerShape(FEATURE_RADIUS))
            .background(colors.colorFillNeutralNormalResting)
            .clickable(enabled = feature.enabled, role = Role.Button) { open = true },
        contentAlignment = Alignment.Center,
    ) {
        Text(feature.label, style = Css.Text.Body.copy(textAlign = TextAlign.Center), color = colors.colorTextPrimary)
    }
    if (!open) return
    // The picker works in UTC days
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = feature.date?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = { open = false },
        confirmButton = {
            HAPlainButton(stringResource(commonR.string.ok), {
                open = false
                picker.selectedDateMillis?.let { millis ->
                    val day = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    onAction(feature.request.call(day, ZoneId.systemDefault()))
                }
            })
        },
        dismissButton = { HAPlainButton(stringResource(commonR.string.cancel), { open = false }) },
    ) { DatePicker(state = picker) }
}

/**
 * A bar in the feature colour filled to the value, over the rest of it at a fifth.
 *
 * Upstream: `hui-bar-gauge-card-feature` (src/panels/lovelace/card-features/hui-bar-gauge-card-feature.ts)
 */
@Composable
internal fun FeatureBarGauge(gauge: TileFeature.BarGauge, color: Color) {
    val fraction by animateFloatAsState(gauge.fraction.toFloat(), tween(SWITCH_TRANSITION_MS), label = "gauge")
    Row(Modifier.fillMaxWidth().height(FEATURE_HEIGHT).clip(RoundedCornerShape(FEATURE_RADIUS))) {
        if (fraction > 0f) Box(Modifier.weight(fraction).fillMaxHeight().background(color))
        if (fraction < 1f) {
            Box(Modifier.weight(1f - fraction).fillMaxHeight().background(color.copy(alpha = SLIDER_BACKGROUND_ALPHA)))
        }
    }
}

/**
 * The yes/no question a feature button asks first (whether to back up before updating).
 *
 * Upstream: `dialog-update-backup` (src/dialogs/update_backup/dialog-update-backup.ts)
 */
@Composable
internal fun FeatureQuestionDialog(
    question: TileFeature.Question,
    onDismiss: () -> Unit,
    onAction: (CardAction) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(question.title, style = Css.Text.DialogTitle) },
        text = { Text(question.text, style = Css.Text.Body) },
        confirmButton = {
            HAPlainButton(question.yesLabel, {
                onDismiss()
                onAction(question.yes)
            })
        },
        dismissButton = {
            HAPlainButton(question.noLabel, {
                onDismiss()
                onAction(question.no)
            })
        },
    )
}

private const val MENU_BACKGROUND_ALPHA = 0.2f

/** `--control-select-menu-padding: 6px 10px`. */
private val MENU_PADDING_V = 6.cssPx
private val MENU_PADDING_H = 10.cssPx

/** `.label`: `--ha-font-size-s`, 0.4px apart; the feature's `line-height: --ha-line-height-condensed`. */
private val LABEL_STYLE = TextStyle(
    fontSize = Css.FontSize.S,
    fontWeight = Css.FontWeight.Normal,
    lineHeight = Css.LineHeight.Condensed,
    letterSpacing = 0.4.cssFontPx,
)

/** `.value`: `--ha-font-size-m`, 0.25px apart (`.select-anchor`). */
private val VALUE_STYLE = TextStyle(
    fontSize = Css.FontSize.M,
    fontWeight = Css.FontWeight.Normal,
    lineHeight = Css.LineHeight.Condensed,
    letterSpacing = 0.25.cssFontPx,
)

private const val MENU_DOWN = "mdi:menu-down"
private const val CHECK = "mdi:check"
private const val SUCCESS_COLOR = "success-color"
private const val WARNING_COLOR = "warning-color"
private const val SWITCH_TRANSITION_MS = 180
private const val CONFIRM_TIMEOUT_MS = 5_000L
private const val DONE_TIMEOUT_MS = 2_000L
private const val STRIPES = 24
private const val MIN_STRIPE = 0.2f
private const val STRIPES_ALPHA = 0.6f
