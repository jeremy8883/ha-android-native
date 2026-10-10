package io.homeassistant.companion.android.dashboard.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HAFontSize
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.feature.TileFeature
import io.homeassistant.companion.android.dashboard.ui.controls.ControlSlider
import io.homeassistant.companion.android.dashboard.ui.controls.ControlSliderStyle
import io.homeassistant.companion.android.dashboard.ui.theme.toColor
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * One control under a tile, in the tile's [color] (`--feature-color`) unless it has its own. Values change locally
 * while the user interacts, and are sent once they settle.
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
        is TileFeature.Buttons -> FeatureButtons(feature, onAction)
        is TileFeature.Select -> FeatureSelect(feature, color, onAction)
        is TileFeature.NumberButtons -> Row(horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2)) {
            feature.items.forEach { FeatureNumber(it, available, onAction, Modifier.weight(1f)) }
        }
    }
}

/** Port of the tile's `ha-control-slider`: a bar the height of the other features, which keeps its track when off. */
@Composable
private fun FeatureSlider(slider: TileFeature.Slider, color: Color, onAction: (CardAction) -> Unit) {
    ControlSlider(
        value = slider.value?.toDouble(),
        range = slider.min.toDouble()..slider.max.toDouble(),
        step = slider.step.toDouble(),
        label = slider.label,
        valueText = { "${it.roundToInt()}${slider.unit}" },
        style = ControlSliderStyle(
            thickness = CONTROL_HEIGHT,
            cornerRadius = HARadius.XL,
            color = color,
            background = SolidColor(color),
            backgroundAlpha = SLIDER_BACKGROUND_ALPHA,
            tooltipFontSize = HAFontSize.M,
        ),
        onChanged = { onAction(slider.service.withValue(it)) },
        modifier = Modifier.fillMaxWidth().height(CONTROL_HEIGHT),
        showHandle = slider.showHandle,
        enabled = slider.enabled,
    )
}

@Composable
private fun FeatureButtons(buttons: TileFeature.Buttons, onAction: (CardAction) -> Unit) {
    val colors = LocalHAColorScheme.current
    Row(horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2)) {
        buttons.buttons.forEach { button ->
            FilledTonalIconButton(
                onClick = { onAction(button.action) },
                enabled = button.enabled,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = colors.colorFillNeutralNormalResting,
                    contentColor = colors.colorOnNeutralNormal,
                    disabledContainerColor = colors.colorFillNeutralQuietResting,
                    disabledContentColor = colors.colorOnDisabledQuiet,
                ),
                shape = RoundedCornerShape(HARadius.XL),
                modifier = Modifier.weight(1f).semantics { contentDescription = button.label },
            ) {
                DashboardIcon(
                    name = button.icon,
                    tint = if (button.enabled) colors.colorOnNeutralNormal else colors.colorOnDisabledQuiet,
                    modifier = Modifier.size(HASize.X2L),
                )
            }
        }
    }
}

/** Icon segments in one pill, the selected one tinted, like `ha-control-select` with hidden labels. */
@Composable
private fun FeatureSelect(select: TileFeature.Select, featureColor: Color, onAction: (CardAction) -> Unit) {
    val colors = LocalHAColorScheme.current
    val accent = select.color?.toColor() ?: featureColor
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(CONTROL_HEIGHT)
            .clip(RoundedCornerShape(HARadius.XL))
            .background(colors.colorFillNeutralNormalResting)
            .semantics { contentDescription = select.label },
    ) {
        select.options.forEach { option ->
            val selected = option.value == select.selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(HARadius.XL))
                    .background(if (selected) accent.copy(alpha = SELECTED_ALPHA) else Color.Transparent)
                    .selectable(selected = selected, enabled = select.enabled, role = Role.RadioButton) {
                        if (!selected) onAction(option.action)
                    }
                    .semantics { contentDescription = option.label },
                contentAlignment = Alignment.Center,
            ) {
                DashboardIcon(
                    name = option.icon,
                    tint = when {
                        !select.enabled -> colors.colorTextDisabled
                        selected -> accent
                        else -> colors.colorTextSecondary
                    },
                    modifier = Modifier.size(HASize.L),
                )
            }
        }
    }
}

/** Minus/plus around the value; presses are collected for a second before one call, like upstream's debounce. */
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
    fun step(direction: Int) {
        val current = shown ?: return
        val next = (current + direction * item.step)
            .coerceIn(item.min ?: Double.NEGATIVE_INFINITY, item.max ?: Double.POSITIVE_INFINITY)
        pending = BigDecimal.valueOf(next).setScale(item.fractionDigits, RoundingMode.HALF_UP).toDouble()
    }
    Row(
        modifier = modifier
            .height(CONTROL_HEIGHT)
            .clip(RoundedCornerShape(HARadius.XL))
            .background(colors.colorFillNeutralNormalResting)
            .padding(horizontal = HADimens.SPACE1)
            .semantics { contentDescription = item.label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = { step(-1) }, enabled = enabled) {
            DashboardIcon(
                name = MINUS,
                tint = if (enabled) colors.colorOnNeutralNormal else colors.colorOnDisabledQuiet,
                modifier = Modifier.size(HASize.L),
            )
        }
        Text(
            text = shown?.let(item::display) ?: "–",
            style = HATextStyle.Body,
            color = if (enabled) colors.colorOnNeutralNormal else colors.colorOnDisabledQuiet,
        )
        IconButton(onClick = { step(1) }, enabled = enabled) {
            DashboardIcon(
                name = PLUS,
                tint = if (enabled) colors.colorOnNeutralNormal else colors.colorOnDisabledQuiet,
                modifier = Modifier.size(HASize.L),
            )
        }
    }
}

private const val SELECTED_ALPHA = 0.2f
private const val SLIDER_BACKGROUND_ALPHA = 0.2f
private val CONTROL_HEIGHT = HADimens.SPACE10
private const val NUMBER_DEBOUNCE_MS = 1000L
private const val MINUS = "mdi:minus"
private const val PLUS = "mdi:plus"
