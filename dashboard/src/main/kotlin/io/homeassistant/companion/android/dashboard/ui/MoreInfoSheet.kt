package io.homeassistant.companion.android.dashboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import io.homeassistant.companion.android.common.compose.composable.HAModalBottomSheet
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.R
import io.homeassistant.companion.android.dashboard.derive.moreInfoModel
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.ui.cards.CardInteractions
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.cards.TileFeatureControl
import java.time.Instant
import java.time.ZonedDateTime

/**
 * The native quick view of an entity: its state, main control and attributes, with a link to upstream's full
 * more-info dialog. Live: it follows the entity while open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MoreInfoSheet(
    entityId: String,
    hass: State<HassSnapshot?>,
    now: State<ZonedDateTime?>,
    interactions: CardInteractions,
    onShowFull: () -> Unit,
    onDismiss: () -> Unit,
) {
    val info by remember(entityId) {
        derivedStateOf { hass.value?.moreInfoModel(entityId, now.value?.toInstant() ?: Instant.EPOCH) }
    }
    val model = info ?: return
    val colors = LocalHAColorScheme.current
    HAModalBottomSheet(bottomSheetState = rememberModalBottomSheetState(), onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = HADimens.SPACE6)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DashboardIcon(
                    name = model.icon,
                    tint = if (model.active) colors.colorFillPrimaryLoudResting else colors.colorTextSecondary,
                    modifier = Modifier.size(HASize.X3L),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        model.name,
                        style = HATextStyle.HeadlineMedium.copy(textAlign = TextAlign.Start),
                        color = colors.colorTextPrimary,
                    )
                    model.context?.let { Text(it, style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.Start)) }
                }
                model.toggle?.let { toggle ->
                    Switch(
                        checked = model.active,
                        onCheckedChange = { interactions.onAction(toggle) },
                        colors = SwitchDefaults.colors(checkedTrackColor = colors.colorFillPrimaryLoudResting),
                    )
                }
            }
            Column {
                Text(
                    model.state,
                    style = HATextStyle.HeadlineMedium.copy(textAlign = TextAlign.Start),
                    color = colors.colorTextPrimary,
                )
                Text(
                    stringResource(
                        R.string.native_dashboard_more_info_changed,
                        model.changed.replaceFirstChar {
                            it.lowercaseChar()
                        },
                    ),
                    style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.Start),
                )
            }
            model.controls.forEach { TileFeatureControl(it, available = true, onAction = interactions.onAction) }
            model.media?.let { media ->
                Row(horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2)) {
                    media.controls.forEach { control ->
                        HAPlainButton(control.label, { interactions.onAction(control.action) })
                    }
                }
            }
            if (model.attributes.isNotEmpty()) {
                Text(
                    stringResource(R.string.native_dashboard_more_info_attributes),
                    style = HATextStyle.Body.copy(textAlign = TextAlign.Start),
                    color = colors.colorTextPrimary,
                )
                model.attributes.forEach { (name, value) ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            name,
                            style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.Start),
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            value,
                            style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.End),
                            color = colors.colorTextPrimary,
                        )
                    }
                }
            }
            HAPlainButton(
                stringResource(R.string.native_dashboard_more_info_full),
                onShowFull,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}
