package io.homeassistant.companion.android.dashboard.ui.cards

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.homeassistant.companion.android.common.compose.composable.ButtonSize
import io.homeassistant.companion.android.common.compose.composable.ButtonVariant
import io.homeassistant.companion.android.common.compose.composable.HAFilledButton
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.AlarmAction
import io.homeassistant.companion.android.dashboard.derive.AlarmCodeEntry
import io.homeassistant.companion.android.dashboard.derive.AlarmPanelCardModel
import io.homeassistant.companion.android.dashboard.derive.DisplayColor
import io.homeassistant.companion.android.dashboard.derive.alarmPanelCardModel
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.ui.theme.toColor

/**
 * An alarm panel card, port of `hui-alarm-panel-card` (frontend@20260624.6
 * src/panels/lovelace/cards/hui-alarm-panel-card.ts): the panel's name and its state in a coloured chip, the arm (or
 * disarm) buttons, and the code with a keypad when one is needed.
 */
@Composable
internal fun AlarmPanelCard(
    card: CardConfig,
    hass: State<HassSnapshot?>,
    interactions: CardInteractions,
    modifier: Modifier = Modifier,
) {
    val panel by remember(card) { derivedStateOf { hass.value?.alarmPanelCardModel(card) } }
    val model = when (val shown = panel) {
        null -> return
        is AlarmPanelCardModel.Warning -> return CardWarning(shown.text, modifier)
        is AlarmPanelCardModel.Shown -> shown
    }
    var code by remember { mutableStateOf("") }
    DashboardCardSurface(modifier = modifier) {
        Column(
            Modifier.fillMaxWidth().padding(bottom = HADimens.SPACE4),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AlarmHeader(model) { interactions.onAction(CardAction.MoreInfo(model.entityId)) }
            AlarmActions(model) { action ->
                interactions.onAction(action.call(model.entityId, code))
                code = ""
            }
            model.code?.let { entry ->
                AlarmCode(entry, code) { code = it }
            }
        }
    }
}

/** The name, and the state chip opening the panel's details. */
@Composable
private fun AlarmHeader(model: AlarmPanelCardModel.Shown, onMoreInfo: () -> Unit) {
    val colors = LocalHAColorScheme.current
    Row(
        Modifier.fillMaxWidth().padding(
            start = HADimens.SPACE4,
            top = HADimens.SPACE6,
            end = HADimens.SPACE4,
            bottom = HADimens.SPACE4,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            model.name,
            // The card header's `--ha-font-size-2xl` at the normal weight
            style = HATextStyle.HeadlineMedium.copy(textAlign = TextAlign.Start, fontWeight = FontWeight.Normal),
            color = colors.colorTextPrimary,
            modifier = Modifier.weight(1f),
        )
        StateChip(model, onMoreInfo)
    }
}

/** `ha-assist-chip filled` in the state's colour, blinking while triggered, arming or pending. */
@Composable
private fun StateChip(model: AlarmPanelCardModel.Shown, onClick: () -> Unit) {
    val colors = LocalHAColorScheme.current
    val pulse by rememberInfiniteTransition(label = "alarm").animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(PULSE_MS / 2), RepeatMode.Reverse),
        label = "alarm",
    )
    val background = model.stateColor?.toColor()
        ?: DisplayColor.State(listOf(INACTIVE)).toColor()
        ?: colors.colorFillDisabledLoudResting
    val onChip = DisplayColor.State(listOf(TEXT_PRIMARY)).toColor() ?: Color.White
    Row(
        Modifier
            .alpha(if (model.pulsing) pulse else 1f)
            .clip(RoundedCornerShape(HARadius.Pill))
            .background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = HADimens.SPACE3, vertical = HADimens.SPACE1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
    ) {
        DashboardIcon(model.stateIcon, onChip, Modifier.size(HASize.L))
        Text(model.stateLabel, style = HATextStyle.BodyMedium, color = onChip)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlarmActions(model: AlarmPanelCardModel.Shown, onClick: (AlarmAction) -> Unit) {
    FlowRow(
        Modifier.padding(horizontal = HADimens.SPACE4),
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
    ) {
        model.actions.forEach { action ->
            HAFilledButton(
                action.label,
                onClick = { onClick(action) },
                variant = if (action.disarm) ButtonVariant.DANGER else ButtonVariant.PRIMARY,
                size = ButtonSize.SMALL,
            )
        }
    }
}

/** The code field, and for a number code the keypad typing into it. */
@Composable
private fun AlarmCode(entry: AlarmCodeEntry, code: String, onChange: (String) -> Unit) {
    HATextField(
        value = code,
        onValueChange = onChange,
        label = { Text(entry.label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (entry.keypad) KeyboardType.NumberPassword else KeyboardType.Password,
        ),
        modifier = Modifier.padding(HADimens.SPACE2).widthIn(max = CODE_MAX_WIDTH),
    )
    if (entry.keypad) Keypad(entry.clearLabel, clearEnabled = code.isNotEmpty()) { key -> onChange(key(code)) }
}

/** The 3 × 4 keypad: digits, and a red key clearing the code. */
@Composable
private fun Keypad(clearLabel: String, clearEnabled: Boolean, onKey: ((String) -> String) -> Unit) {
    Column(Modifier.padding(HADimens.SPACE3), verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4)) {
        KEYS.chunked(KEYPAD_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE4)) {
                row.forEach { key ->
                    when (key) {
                        "" -> Spacer(Modifier.size(KEY_SIZE))
                        CLEAR -> KeypadKey(clearLabel, clear = true, enabled = clearEnabled) { onKey { "" } }
                        else -> KeypadKey(key, clear = false, enabled = true) { onKey { it + key } }
                    }
                }
            }
        }
    }
}

/** `ha-control-button`: a rounded key with a digit, or the clear icon on red. */
@Composable
private fun KeypadKey(label: String, clear: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalHAColorScheme.current
    val red = DisplayColor.Theme(RED).toColor() ?: colors.colorFillDangerLoudResting
    val tint = if (clear) red else colors.colorFillDisabledLoudResting
    Box(
        Modifier
            .size(KEY_SIZE)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(RoundedCornerShape(KEY_RADIUS))
            .background(tint.copy(alpha = KEY_TINT_ALPHA))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (clear) {
            DashboardIcon(CLEAR_ICON, red, Modifier.size(HASize.X2L))
        } else {
            Text(label, style = HATextStyle.Body.copy(fontSize = KEY_FONT_SIZE), color = colors.colorTextPrimary)
        }
    }
}

private const val CLEAR = "clear"
private const val CLEAR_ICON = "mdi:close"
private const val RED = "red"
private const val INACTIVE = "state-inactive-color"
private const val TEXT_PRIMARY = "text-primary-color"
private const val KEYPAD_COLUMNS = 3
private const val PULSE_MS = 1000
private const val KEY_TINT_ALPHA = 0.2f
private const val DISABLED_ALPHA = 0.5f

/** Port of `BUTTONS`. */
private val KEYS = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", CLEAR, "0", "")
private val KEY_SIZE = 56.dp
private val KEY_RADIUS = 24.dp
private val KEY_FONT_SIZE = 24.sp
private val CODE_MAX_WIDTH = 150.dp
