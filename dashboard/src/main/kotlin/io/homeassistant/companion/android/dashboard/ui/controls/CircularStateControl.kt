package io.homeassistant.companion.android.dashboard.ui.controls

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.homeassistant.companion.android.common.compose.theme.HABorderWidth
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HAFontSize
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.moreinfo.BigNumber
import io.homeassistant.companion.android.dashboard.moreinfo.CircularControl
import io.homeassistant.companion.android.dashboard.moreinfo.CircularPrimary
import io.homeassistant.companion.android.dashboard.moreinfo.CircularTarget
import io.homeassistant.companion.android.dashboard.moreinfo.CircularTargets
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.theme.toColor
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.delay

/**
 * A round state control (`state-control-circular-slider-style`): the [control]'s slider with its label, number and
 * minus/plus buttons inside. It shows the user's targets until [targets] (the server's) change; a drag or tap sets
 * one at once, button presses after a second without another (upstream's debounce). [onSet] gets the targets and
 * which one changed.
 */
@Composable
internal fun CircularStateControl(
    control: CircularControl,
    targets: CircularTargets,
    onSet: (CircularTargets, CircularTarget) -> Unit,
    modifier: Modifier = Modifier,
    bottomUnit: Boolean = false,
    secondary: String? = null,
) {
    var local by remember(targets) { mutableStateOf(targets) }
    var selected by remember {
        mutableStateOf<CircularTarget>(if (control.slider.dual) CircularTarget.Low else CircularTarget.Value)
    }
    var presses by remember { mutableIntStateOf(0) }
    val set by rememberUpdatedState(onSet)
    LaunchedEffect(presses) {
        if (presses == 0) return@LaunchedEffect
        delay(BUTTON_DEBOUNCE_MS)
        set(local, selected)
    }
    val slider = control.slider
    Box(modifier = modifier.size(CONTROL_SIZE)) {
        CircularSliderView(
            slider = slider,
            targets = local,
            onChanging = { target, value ->
                selected = target
                value?.let { local = local.with(target, it) }
            },
            onChanged = { target, value ->
                local = local.with(target, value)
                selected = target
                set(local, target)
            },
            modifier = Modifier.fillMaxSize(),
        )
        CircularInfo(control, local, selected, bottomUnit, secondary, onSelect = {
            selected = it
        }, modifier = Modifier.align(Alignment.Center))
        if (control.buttons) {
            val color = when (selected) {
                CircularTarget.Low -> control.lowButtonColor
                CircularTarget.High -> control.highButtonColor
                CircularTarget.Value -> null
            }?.toColor()
            StepButtons(color, Modifier.align(Alignment.BottomCenter).padding(bottom = BUTTONS_BOTTOM)) { sign ->
                local = local.stepped(selected, sign * slider.step, slider.min, slider.max)
                presses++
            }
        }
    }
}

/** The secondary label under the target: the current temperature, with a thermometer (`show-secondary`). */
@Composable
private fun CurrentTemperature(text: String) {
    val colors = LocalHAColorScheme.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE1)) {
        DashboardIcon(THERMOMETER, colors.colorTextPrimary, Modifier.size(HASize.L))
        Text(text, style = HATextStyle.Body.copy(fontWeight = FontWeight.Medium), color = colors.colorTextPrimary)
    }
}

/** The label, the target (or the low and high targets, tap one to choose it), centred in the slider. */
@Composable
private fun CircularInfo(
    control: CircularControl,
    targets: CircularTargets,
    selected: CircularTarget,
    bottomUnit: Boolean,
    secondary: String?,
    onSelect: (CircularTarget) -> Unit,
    modifier: Modifier,
) {
    val colors = LocalHAColorScheme.current
    Column(modifier = modifier.fillMaxWidth(LABEL_WIDTH), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = control.label.orEmpty(),
            style = HATextStyle.Body.copy(fontSize = HAFontSize.L, fontWeight = FontWeight.Medium),
            color = when {
                control.labelDisabled -> colors.colorTextSecondary
                else -> control.slider.actionColor?.toColor() ?: colors.colorTextPrimary
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        when (val primary = control.primary) {
            is CircularPrimary.Target -> BigNumberText(
                primary.number.copy(value = targets.value ?: primary.number.value),
                bottomUnit,
            )
            is CircularPrimary.Range -> Row(horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE6)) {
                listOf(
                    CircularTarget.Low to (targets.low ?: primary.low.value),
                    CircularTarget.High to (targets.high ?: primary.high.value),
                )
                    .forEach { (target, value) ->
                        Box(
                            Modifier
                                .alpha(if (selected == target) 1f else UNSELECTED_ALPHA)
                                .clickable(role = Role.Button) { onSelect(target) },
                        ) { BigNumberText(primary.low.copy(value = value), bottomUnit) }
                    }
            }
            is CircularPrimary.Text -> Text(
                primary.text,
                style = HATextStyle.Headline.copy(fontSize = STATE_SIZE, fontWeight = FontWeight.Normal),
                color = colors.colorTextPrimary,
                textAlign = TextAlign.Center,
            )
            CircularPrimary.None -> Unit
        }
        secondary?.let { CurrentTemperature(it) }
    }
}

/**
 * Port of `ha-big-number`: the whole part large, the decimals and unit small beside it, stacked (unit above), or on
 * one line at the bottom with [bottomUnit].
 */
@Composable
private fun BigNumberText(number: BigNumber, bottomUnit: Boolean) {
    val color = LocalHAColorScheme.current.colorTextPrimary
    val text = BigDecimal.valueOf(number.value).setScale(number.fractionDigits, RoundingMode.HALF_EVEN).toPlainString()
    val integer = text.substringBefore('.')
    val decimal = text.removePrefix(integer)
    val description = "$text ${number.unit}"
    Row(modifier = Modifier.semantics { contentDescription = description }, verticalAlignment = Alignment.Bottom) {
        Text(
            integer,
            style = HATextStyle.Headline.copy(
                fontSize = BIG_SIZE,
                lineHeight = BIG_LINE,
                fontWeight = FontWeight.Normal,
            ),
            color = color,
        )
        if (bottomUnit) {
            Text(
                decimal + number.unit,
                style = HATextStyle.Body.copy(fontSize = UNIT_SIZE),
                color = color,
                modifier = Modifier.padding(bottom = HADimens.SPACE2),
            )
        } else {
            Column(modifier = Modifier.padding(vertical = HADimens.SPACE1)) {
                Text(number.unit, style = HATextStyle.Body.copy(fontSize = UNIT_SIZE), color = color)
                Text(decimal, style = HATextStyle.Body.copy(fontSize = DECIMAL_SIZE), color = color)
            }
        }
    }
}

/** The minus and plus buttons under the number, outlined in [color] (the range end's) or the theme's. */
@Composable
private fun StepButtons(color: Color?, modifier: Modifier, onStep: (Int) -> Unit) {
    val colors = LocalHAColorScheme.current
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE6)) {
        listOf(-1 to "mdi:minus", 1 to "mdi:plus").forEach { (sign, icon) ->
            Box(
                modifier = Modifier
                    .size(HADimens.SPACE12)
                    .clip(CircleShape)
                    .border(HABorderWidth.S, color ?: colors.colorBorderNeutralNormal, CircleShape)
                    .clickable(role = Role.Button) { onStep(sign) },
                contentAlignment = Alignment.Center,
            ) {
                DashboardIcon(icon, colors.colorTextPrimary, Modifier.size(HASize.X2L))
            }
        }
    }
}

private const val THERMOMETER = "mdi:thermometer"
private const val BUTTON_DEBOUNCE_MS = 1000L
private const val LABEL_WIDTH = 0.6f
private const val UNSELECTED_ALPHA = 0.7f
private val CONTROL_SIZE = 320.dp
private val BUTTONS_BOTTOM = 10.dp
private val BIG_SIZE = 57.sp
private val BIG_LINE = 64.sp
private val DECIMAL_SIZE = 24.sp
private val UNIT_SIZE = 19.sp
private val STATE_SIZE = 36.sp
