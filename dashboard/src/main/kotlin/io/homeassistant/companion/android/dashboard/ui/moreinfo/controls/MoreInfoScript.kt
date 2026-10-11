package io.homeassistant.companion.android.dashboard.ui.moreinfo.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.common.compose.composable.HACheckbox
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.moreinfo.ScriptField
import io.homeassistant.companion.android.dashboard.moreinfo.ScriptMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.scriptMoreInfo
import io.homeassistant.companion.android.dashboard.ui.components.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.components.selector.FieldEditor
import io.homeassistant.companion.android.dashboard.ui.lovelace.cards.MarkdownText
import io.homeassistant.companion.android.dashboard.ui.moreinfo.MoreInfoEntryViewModel
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.json.JsonElement

/**
 * Port of `more-info-script` below its header: the description, the queue, the fields to run it with (an optional
 * field sent only when ticked) and cancel and run. The script's action is named by its registry entry's unique ID
 * once known.
 *
 * Upstream: `more-info-script` (src/dialogs/more-info/controls/more-info-script.ts)
 */
@Composable
internal fun MoreInfoScript(
    script: ScriptMoreInfo,
    state: EntityState,
    hass: HassSnapshot,
    now: Instant,
    onAction: (CardAction) -> Unit,
) {
    val entry = hiltViewModel<MoreInfoEntryViewModel>(key = "entry-${state.entityId}")
    LaunchedEffect(state.entityId) { entry.show(state.entityId) }
    val uniqueId by entry.uniqueId.collectAsStateWithLifecycle()
    val shown = remember(script, uniqueId) { uniqueId?.let { hass.scriptMoreInfo(state, now, it) } ?: script }
    val data =
        remember(state.entityId, shown.fields) {
            mutableStateMapOf<String, JsonElement>().apply { putAll(shown.initialData) }
        }
    val colors = LocalHAColorScheme.current
    Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4)) {
        shown.description?.let { MarkdownText(it, Modifier.fillMaxWidth()) }
        shown.queue?.let { Text(it, style = HATextStyle.Body, color = colors.colorTextPrimary) }
        shown.fields?.let { fields ->
            Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4)) {
                Text(
                    shown.fieldsTitle,
                    style = HATextStyle.Body.copy(fontWeight = FontWeight.Medium, textAlign = TextAlign.Start),
                    color = colors.colorTextPrimary,
                )
                val hasOptional = fields.any { it.optional }
                fields.forEach { field ->
                    ScriptFieldRow(
                        field,
                        data[field.key],
                        hasOptional,
                        now.atZone(hass.formats.zone).toLocalDate(),
                    ) { value ->
                        if (value == null) data.remove(field.key) else data[field.key] = value
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE3)) {
            ScriptButton(STOP_ICON, shown.cancel, shown.cancelEnabled, Modifier.weight(1f)) {
                onAction(shown.cancelCall())
            }
            ScriptButton(RUN_ICON, shown.run, shown.canRun(data), Modifier.weight(1f)) {
                onAction(shown.runCall(data.toMap()))
            }
        }
    }
}

/**
 * A field as `ha-settings-row` draws it on a phone: the box including an optional one (a gap in its place for the
 * others when some are optional), the name and description, then the editor, disabled while not ticked.
 *
 * Upstream: part of `more-info-script`, with `ha-settings-row` (src/dialogs/more-info/controls/more-info-script.ts;
 * src/components/ha-settings-row.ts)
 */
@Composable
private fun ScriptFieldRow(
    field: ScriptField,
    value: JsonElement?,
    hasOptional: Boolean,
    today: LocalDate,
    onChange: (JsonElement?) -> Unit,
) {
    val colors = LocalHAColorScheme.current
    val ticked = !field.optional || value != null
    Row(verticalAlignment = Alignment.Top) {
        when {
            field.optional -> HACheckbox(
                checked = value != null,
                onCheckedChange = { on -> onChange(if (on) field.tickedValue() else null) },
            )
            hasOptional -> Box(Modifier.width(CHECKBOX_SPACE))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HADimens.SPACE1)) {
            Text(
                field.name,
                style = HATextStyle.Body.copy(textAlign = TextAlign.Start),
                color = colors.colorTextPrimary,
                modifier = if (field.optional) {
                    Modifier.clickable {
                        onChange(
                            if (value ==
                                null
                            ) {
                                field.tickedValue()
                            } else {
                                null
                            },
                        )
                    }
                } else {
                    Modifier
                },
            )
            field.description?.let { MarkdownText(it, Modifier.fillMaxWidth()) }
            FieldEditor(
                selector = field.selector,
                value = value,
                enabled = ticked,
                placeholder = field.default?.toString()?.trim('"'),
                today = today,
                onChange = onChange,
            )
        }
    }
}

/**
 * `ha-control-button` with its icon and label, as the script's cancel and run.
 *
 * Upstream: part of `more-info-script`, with `ha-control-button` (src/dialogs/more-info/controls/more-info-script.ts;
 * src/components/ha-control-button.ts)
 */
@Composable
private fun ScriptButton(icon: String, label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalHAColorScheme.current
    Row(
        modifier = modifier
            .height(BUTTON_HEIGHT)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(RoundedCornerShape(HARadius.X3L))
            .background(colors.colorFillDisabledLoudResting.copy(alpha = TINT_ALPHA))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DashboardIcon(icon, colors.colorTextPrimary, Modifier.size(HASize.X2L))
        Text(label, style = HATextStyle.Body, color = colors.colorTextPrimary)
    }
}

private const val STOP_ICON = "mdi:stop"
private const val RUN_ICON = "mdi:play"
private const val DISABLED_ALPHA = 0.5f
private const val TINT_ALPHA = 0.2f
private val BUTTON_HEIGHT = 60.dp
private val CHECKBOX_SPACE = 48.dp
