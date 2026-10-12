package io.homeassistant.companion.android.dashboard.ui.lovelace.cardfeatures

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.feature.LightColorFavoritesFeature
import io.homeassistant.companion.android.dashboard.feature.PositionFavoritesFeature
import io.homeassistant.companion.android.dashboard.feature.TileFeature
import io.homeassistant.companion.android.dashboard.feature.TrendGraphFeature
import io.homeassistant.companion.android.dashboard.ui.components.ControlSlider
import io.homeassistant.companion.android.dashboard.ui.components.ControlSliderStyle
import io.homeassistant.companion.android.dashboard.ui.components.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.theme.Css
import io.homeassistant.companion.android.dashboard.ui.theme.toColor
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * One control under a tile, in the tile's [color] (`--feature-color`) unless it has its own. Values change locally
 * while the user interacts, and are sent once they settle.
 *
 * Upstream: `hui-card-features`, `hui-card-feature` (src/panels/lovelace/card-features/hui-card-features.ts;
 * src/panels/lovelace/card-features/hui-card-feature.ts)
 */
@Composable
internal fun TileFeatureControl(
    feature: TileFeature,
    available: Boolean,
    color: Color,
    onAction: (CardAction) -> Unit,
) {
    when (feature) {
        is TileFeature.Slider -> FeatureSlider(feature, color, onAction)
        is TileFeature.ValueSlider -> FeatureValueSlider(feature, color, onAction)
        is TileFeature.Buttons -> FeatureButtons(feature, color, onAction)
        is TileFeature.Select -> FeatureSelect(feature, color, onAction)
        is TileFeature.Menu -> FeatureMenu(feature, onAction)
        is TileFeature.Switch -> FeatureSwitch(feature, color, onAction)
        is TileFeature.NumberButtons -> FeatureNumbers(feature, available, color, onAction)
        else -> OtherFeatureControl(feature, color, onAction)
    }
}

/**
 * The features that aren't one of the shared controls.
 *
 * Upstream: `hui-card-feature` (src/panels/lovelace/card-features/hui-card-feature.ts)
 */
@Composable
private fun OtherFeatureControl(feature: TileFeature, color: Color, onAction: (CardAction) -> Unit) {
    when (feature) {
        is TileFeature.OpenDoor -> FeatureOpenDoor(feature, onAction)
        is TileFeature.DateSet -> FeatureDateSet(feature, onAction)
        is TileFeature.BarGauge -> FeatureBarGauge(feature, color)
        is PositionFavoritesFeature -> FeaturePositionFavorites(feature, color, onAction)
        is LightColorFavoritesFeature -> FeatureLightColorFavorites(feature, onAction)
        is TrendGraphFeature -> FeatureTrendGraph(feature, color)
        // Drawn by TileFeatureControl
        else -> Unit
    }
}

/**
 * The number controls side by side, then their trailing button.
 *
 * Upstream: `ha-control-number-buttons` (src/components/ha-control-number-buttons.ts)
 */
@Composable
private fun FeatureNumbers(
    feature: TileFeature.NumberButtons,
    available: Boolean,
    color: Color,
    onAction: (CardAction) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(FEATURE_BUTTON_SPACING)) {
        feature.items.forEach { FeatureNumber(it, available, onAction, Modifier.weight(1f)) }
        feature.trailing?.let { FeatureButton(it, color, onAction, Modifier.size(FEATURE_HEIGHT)) }
    }
}

/**
 * Port of the tile's `ha-control-slider`: a bar the height of the other features, which keeps its track when off.
 *
 * Upstream: `ha-control-slider` (src/components/ha-control-slider.ts)
 */
@Composable
private fun FeatureSlider(slider: TileFeature.Slider, color: Color, onAction: (CardAction) -> Unit) {
    ControlSlider(
        value = slider.value?.toDouble(),
        range = slider.min.toDouble()..slider.max.toDouble(),
        step = slider.step.toDouble(),
        label = slider.label,
        valueText = { "${it.roundToInt()}${slider.unit}" },
        style = ControlSliderStyle(
            thickness = FEATURE_HEIGHT,
            cornerRadius = FEATURE_RADIUS,
            color = color,
            background = SolidColor(color),
            backgroundAlpha = SLIDER_BACKGROUND_ALPHA,
            tooltipFontSize = Css.FontSize.M,
        ),
        onChanged = { onAction(slider.service.withValue(it)) },
        modifier = Modifier.fillMaxWidth().height(FEATURE_HEIGHT),
        showHandle = slider.showHandle,
        enabled = slider.enabled,
    )
}

/**
 * A feature's row of buttons, sharing the width; on a tile narrower than 200px with more than three buttons, those
 * marked so are left out.
 *
 * Upstream: `ha-control-button-group`, `ha-control-button` (src/components/ha-control-button-group.ts;
 * src/components/ha-control-button.ts)
 */
@Composable
private fun FeatureButtons(buttons: TileFeature.Buttons, featureColor: Color, onAction: (CardAction) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val narrow = maxWidth < NARROW_WIDTH && buttons.buttons.size > NARROW_MAX_BUTTONS
        Row(horizontalArrangement = Arrangement.spacedBy(FEATURE_BUTTON_SPACING)) {
            buttons.buttons.filterNot { narrow && it.hiddenWhenNarrow }.forEach { button ->
                FeatureButton(button, featureColor, onAction, Modifier.weight(1f).height(FEATURE_HEIGHT))
            }
        }
    }
}

/**
 * One `ha-control-button` of a feature: its icon or text on the neutral fill, or white on its colour while active.
 * A button with a question asks it first; one with an entity to show opens its details.
 *
 * Upstream: `ha-control-button` (src/components/ha-control-button.ts)
 */
@Composable
internal fun FeatureButton(
    button: TileFeature.Button,
    featureColor: Color,
    onAction: (CardAction) -> Unit,
    modifier: Modifier,
) {
    val colors = LocalHAColorScheme.current
    var asking by remember { mutableStateOf(false) }
    val activeColor = button.color?.toColor() ?: featureColor
    val content = when {
        button.active -> Color.White
        button.icon == null -> colors.colorTextPrimary
        else -> colors.colorOnNeutralNormal
    }
    Box(
        modifier = modifier
            .alpha(if (button.enabled) 1f else DISABLED_ALPHA)
            .clip(RoundedCornerShape(FEATURE_RADIUS))
            .background(if (button.active) activeColor else colors.colorFillNeutralNormalResting)
            .clickable(enabled = button.enabled, role = Role.Button) {
                when {
                    button.moreInfo != null -> onAction(CardAction.MoreInfo(button.moreInfo.orEmpty()))
                    button.question != null -> asking = true
                    else -> onAction(button.action)
                }
            }
            .semantics { contentDescription = button.label },
        contentAlignment = Alignment.Center,
    ) {
        if (button.icon != null) {
            DashboardIcon(name = button.icon, tint = content, modifier = Modifier.size(HASize.X2L))
        } else {
            Text(
                button.text.orEmpty(),
                style = Css.Text.Body,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = Css.Space.S2),
            )
        }
    }
    button.question?.takeIf { asking }?.let { question ->
        FeatureQuestionDialog(question, onDismiss = { asking = false }, onAction = onAction)
    }
}

/**
 * Segments in one pill, the selected one filled with the colour and drawn in white, like `ha-control-select`: icons, or the options' labels.
 *
 * Upstream: `ha-control-select` (src/components/ha-control-select.ts)
 */
@Composable
private fun FeatureSelect(select: TileFeature.Select, featureColor: Color, onAction: (CardAction) -> Unit) {
    val colors = LocalHAColorScheme.current
    val accent = select.color?.toColor() ?: featureColor
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(FEATURE_HEIGHT)
            .clip(RoundedCornerShape(FEATURE_RADIUS))
            .background(colors.colorFillNeutralNormalResting)
            .semantics { contentDescription = select.label },
    ) {
        select.options.forEach { option ->
            val selected = option.value == select.selected
            val tint = when {
                !select.enabled -> colors.colorTextDisabled
                selected -> Color.White
                else -> colors.colorTextPrimary
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(FEATURE_RADIUS))
                    .background(if (selected) accent else Color.Transparent)
                    .selectable(selected = selected, enabled = select.enabled, role = Role.RadioButton) {
                        if (!selected) onAction(option.action)
                    }
                    .semantics { contentDescription = option.label },
                contentAlignment = Alignment.Center,
            ) {
                if (select.showLabels || option.icon == null) {
                    Text(option.label, style = Css.Text.Body, color = tint, maxLines = 1, overflow = TextOverflow.Clip)
                } else {
                    DashboardIcon(name = option.icon, tint = tint, modifier = Modifier.size(HASize.L))
                }
            }
        }
    }
}

/**
 * Minus/plus around the value; presses are collected for a second before one call, like upstream's debounce.
 *
 * Upstream: `ha-control-number-buttons` (src/components/ha-control-number-buttons.ts)
 */
@Composable
private fun FeatureNumber(
    item: TileFeature.NumberItem,
    available: Boolean,
    onAction: (CardAction) -> Unit,
    modifier: Modifier,
) {
    val colors = LocalHAColorScheme.current
    var pending by remember(item.value) { mutableStateOf<Double?>(null) }
    val shown = pending ?: item.value
    LaunchedEffect(pending) {
        val value = pending ?: return@LaunchedEffect
        delay(NUMBER_DEBOUNCE_MS)
        item.service?.let { onAction(it.withValue(value)) }
    }
    val enabled = available && item.service != null && shown != null
    val content = if (enabled) colors.colorOnNeutralNormal else colors.colorOnDisabledQuiet
    BoxWithConstraints(
        modifier = modifier
            .height(FEATURE_HEIGHT)
            .clip(RoundedCornerShape(FEATURE_RADIUS))
            .background(colors.colorFillNeutralNormalResting)
            .semantics { contentDescription = item.label },
        contentAlignment = Alignment.Center,
    ) {
        // The unit hides in a container 100px wide or less
        val shownItem = if (maxWidth <= NUMBER_UNIT_MIN_WIDTH) item.copy(unit = null) else item
        Text(
            text = shown?.let(shownItem::display) ?: "–",
            style = Css.Text.Body.copy(fontWeight = Css.FontWeight.Medium),
            color = content,
            maxLines = 1,
        )
        val current = shown
        listOf(-1 to Alignment.CenterStart, 1 to Alignment.CenterEnd).forEach { (direction, end) ->
            NumberStep(
                icon = if (direction < 0) MINUS else PLUS,
                enabled = enabled && current != null && item.canStep(current, direction),
                tint = content,
                modifier = Modifier.align(end),
            ) { current?.let { pending = item.stepped(it, direction) } }
        }
    }
}

/**
 * A minus or plus button at one end of [FeatureNumber], 35px wide.
 *
 * Upstream: part of `ha-control-number-buttons` (src/components/ha-control-number-buttons.ts)
 */
@Composable
private fun NumberStep(icon: String, enabled: Boolean, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .width(NUMBER_BUTTON_WIDTH)
            .fillMaxHeight()
            .alpha(if (enabled) 1f else NUMBER_DISABLED_ALPHA)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        DashboardIcon(name = icon, tint = tint, modifier = Modifier.size(HASize.X2L))
    }
}

/** `ha-control-number-buttons`' buttons (over the value, which stays centred), and the width hiding the unit. */
private val NUMBER_BUTTON_WIDTH = 35.dp
private val NUMBER_UNIT_MIN_WIDTH = 100.dp
private const val NUMBER_DISABLED_ALPHA = 0.4f

internal const val SLIDER_BACKGROUND_ALPHA = 0.2f
internal const val DISABLED_ALPHA = 0.5f

/** `hui-card-features`' `--feature-height`. */
internal val FEATURE_HEIGHT = 42.dp

/** `--feature-border-radius`: `--ha-border-radius-lg`. */
internal val FEATURE_RADIUS = Css.Radius.Lg

/** `--feature-button-spacing`. */
internal val FEATURE_BUTTON_SPACING = Css.Space.S3

/** The playback feature's narrow width, under which it drops previous and next when it has more than three. */
private val NARROW_WIDTH = 200.dp
private const val NARROW_MAX_BUTTONS = 3

private const val NUMBER_DEBOUNCE_MS = 1000L
private const val MINUS = "mdi:minus"
private const val PLUS = "mdi:plus"
