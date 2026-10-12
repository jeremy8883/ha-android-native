package io.homeassistant.companion.android.dashboard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.ui.theme.Css
import io.homeassistant.companion.android.dashboard.ui.theme.cssFontPx
import io.homeassistant.companion.android.dashboard.ui.theme.cssPx

/**
 * A dropdown at the frontend's size: a 56px field with the [label] above the chosen option's text and a menu arrow,
 * listing the [options] (value to label) under it when tapped; [onSelect] gets the chosen value.
 *
 * Upstream: `ha-select`, `ha-picker-field` (src/components/ha-select.ts; src/components/ha-picker-field.ts)
 */
@Composable
internal fun HaSelect(
    options: List<Pair<String, String>>,
    value: String?,
    label: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    var widthPx by remember { mutableIntStateOf(0) }
    // `_getValueLabel`: the option's label, else the value itself
    val valueLabel = value?.let { current -> options.firstOrNull { it.first == current }?.second ?: current }
    Box(modifier.onSizeChanged { widthPx = it.width }) {
        PickerField(label, valueLabel, enabled) { open = true }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.width(with(LocalDensity.current) { widthPx.toDp() }),
        ) {
            options.forEach { (option, optionLabel) ->
                DropdownMenuItem(
                    text = { Text(optionLabel, style = Css.Text.Body) },
                    onClick = {
                        open = false
                        if (option != value) onSelect(option)
                    },
                )
            }
        }
    }
}

/**
 * The field of a dropdown: the label small above the value (or the label in the value's place without one), then
 * the menu arrow, over a 1px line.
 *
 * Upstream: `ha-picker-field`, `ha-combo-box-item` (src/components/ha-picker-field.ts;
 * src/components/ha-combo-box-item.ts)
 */
@Composable
private fun PickerField(label: String?, value: String?, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalHAColorScheme.current
    val line = colors.colorBorderNeutralQuiet
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(FIELD_HEIGHT)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .background(
                if (enabled) colors.colorSurfaceDefault else colors.colorFillDisabledNormalResting,
                RoundedCornerShape(topStart = Css.Radius.Sm, topEnd = Css.Radius.Sm),
            )
            .drawBehind {
                val height = Css.BorderWidth.Sm.toPx()
                drawRect(line, topLeft = Offset(0f, size.height - height), size = Size(size.width, height))
            }
            .clickable(enabled = enabled, role = Role.DropdownList, onClick = onClick)
            .padding(start = Css.Space.S4, end = Css.Space.S2),
        horizontalArrangement = Arrangement.spacedBy(Css.Space.S2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (label != null && value != null) {
                // The overline mimics mdc-select's floating label: 0.75 of 1rem at 1.15rem
                Text(
                    label,
                    style = Css.Text.Body.copy(fontSize = OVERLINE_SIZE, lineHeight = OVERLINE_LINE_HEIGHT),
                    color = colors.colorTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                value ?: label.orEmpty(),
                style = Css.Text.Body,
                color = if (value != null) colors.colorTextPrimary else colors.colorTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.width(ARROW_WIDTH), contentAlignment = Alignment.Center) {
            DashboardIcon(ARROW_ICON, colors.colorOnNeutralQuiet, Modifier.size(ARROW_SIZE))
        }
    }
}

private const val ARROW_ICON = "mdi:menu-down"
private const val DISABLED_ALPHA = 0.5f

/** `--md-list-item-one-line-container-height` in `ha-picker-field`. */
private val FIELD_HEIGHT = 56.cssPx

/** `.arrow`: a 20px icon in 32px. */
private val ARROW_SIZE = 20.cssPx
private val ARROW_WIDTH = 32.cssPx
private val OVERLINE_SIZE = 12.cssFontPx
private val OVERLINE_LINE_HEIGHT = 18.4.cssFontPx
