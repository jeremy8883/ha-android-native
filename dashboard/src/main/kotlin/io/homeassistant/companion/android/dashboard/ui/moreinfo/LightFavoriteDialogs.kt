package io.homeassistant.companion.android.dashboard.ui.moreinfo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.ButtonVariant
import io.homeassistant.companion.android.common.compose.composable.HACheckbox
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.R
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.entityName
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.moreinfo.LightButton
import io.homeassistant.companion.android.dashboard.moreinfo.LightColor
import io.homeassistant.companion.android.dashboard.moreinfo.LightMainControl
import io.homeassistant.companion.android.dashboard.moreinfo.lightMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.parseLightColor
import io.homeassistant.companion.android.dashboard.ui.controls.StateControlSlider
import kotlin.math.roundToInt

/**
 * Port of `dialog-light-color-favorite` (frontend@20260624.6 src/dialogs/more-info/components/lights/): the colour
 * or temperature picker, which sets the light as it's moved, and saves the colour last set. It starts from
 * [initial], or the light's own colour.
 */
@Composable
internal fun FavoriteColorDialog(
    state: EntityState,
    hass: HassSnapshot,
    title: String,
    initial: LightColor?,
    onAction: (CardAction) -> Unit,
    onSave: (LightColor) -> Unit,
    onDismiss: () -> Unit,
) {
    val light = remember(state) { hass.lightMoreInfo(state) } ?: return
    val modes = listOfNotNull(
        LightMainControl.Color.takeIf { light.color },
        LightMainControl.ColorTemp.takeIf { light.colorTemp != null },
    )
    var color by remember { mutableStateOf(initial) }
    var mode by remember {
        mutableStateOf(if (initial is LightColor.ColorTemp) LightMainControl.ColorTemp else modes.firstOrNull())
    }
    // What the pickers set is also the colour to save
    val picking: (CardAction) -> Unit = { action ->
        onAction(action)
        (action as? CardAction.CallService)?.data?.let(::parseLightColor)?.let { color = it }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .padding(HADimens.SPACE4)
                .fillMaxWidth()
                .background(LocalHAColorScheme.current.colorSurfaceDefault, RoundedCornerShape(DIALOG_RADIUS))
                .verticalScroll(rememberScrollState())
                .padding(HADimens.SPACE6),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = HATextStyle.HeadlineMedium.copy(textAlign = TextAlign.Start),
                    modifier = Modifier.weight(1f),
                )
                if (modes.size > 1) {
                    modes.forEach { candidate ->
                        val key = if (candidate == LightMainControl.ColorTemp) "color_temp" else "color"
                        val label = hass.localize("ui.dialogs.more_info_control.light.color_picker.mode.$key")
                        ShowButton(LightButton.Show(label, true, candidate), selected = mode == candidate) {
                            mode = candidate
                        }
                    }
                }
            }
            when (mode) {
                LightMainControl.ColorTemp -> light.colorTemp?.let { slider ->
                    StateControlSlider(slider, { "${it.roundToInt()} K" }, picking, whileMoving = true)
                }
                LightMainControl.Color -> LightColorControl(state, hass, picking)
                else -> Unit
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                HAPlainButton(stringResource(commonR.string.cancel), onDismiss)
                HAPlainButton(stringResource(commonR.string.save), { color?.let(onSave) }, enabled = color != null)
            }
        }
    }
}

/** A question before something that can't be undone, with its [confirm] button styled as dangerous. */
@Composable
internal fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = HATextStyle.HeadlineMedium) },
        text = { Text(text, style = HATextStyle.Body.copy(textAlign = TextAlign.Start)) },
        confirmButton = { HAPlainButton(confirm, onConfirm, variant = ButtonVariant.DANGER) },
        dismissButton = { HAPlainButton(stringResource(commonR.string.cancel), onDismiss) },
    )
}

/**
 * Port of the light favourites' copy dialog (`copyFavoriteOptionsToEntities`, src/dialogs/more-info/favorites.ts):
 * the lights that take every kind of colour among the favourites, to pick which receive them.
 */
@Composable
internal fun CopyFavoritesDialog(
    hass: HassSnapshot,
    targets: List<String>,
    onCopy: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var chosen by remember { mutableStateOf(emptySet<String>()) }
    val strings = "ui.dialogs.more_info_control.light"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(hass.localize("$strings.copy_favorites"), style = HATextStyle.HeadlineMedium) },
        text = {
            Column(Modifier.heightIn(max = LIST_MAX_HEIGHT).verticalScroll(rememberScrollState())) {
                Text(
                    hass.localize("$strings.copy_favorites_helper"),
                    style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.Start),
                )
                targets.forEach { entityId ->
                    val checked = entityId in chosen
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Checkbox) {
                                chosen =
                                    if (checked) chosen - entityId else chosen + entityId
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HACheckbox(checked = checked, onCheckedChange = { on ->
                            chosen =
                                if (on) chosen + entityId else chosen - entityId
                        })
                        Text(
                            hass.states[entityId]?.let { hass.entityName(it) } ?: entityId,
                            style = HATextStyle.Body.copy(textAlign = TextAlign.Start),
                        )
                    }
                }
            }
        },
        confirmButton = {
            HAPlainButton(stringResource(R.string.native_dashboard_copy), {
                onCopy(targets.filter { it in chosen })
            }, enabled = chosen.isNotEmpty())
        },
        dismissButton = { HAPlainButton(stringResource(commonR.string.cancel), onDismiss) },
    )
}

private val DIALOG_RADIUS = 28.dp
private val LIST_MAX_HEIGHT = 360.dp
