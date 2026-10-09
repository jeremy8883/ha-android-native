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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import io.homeassistant.companion.android.common.compose.composable.HAModalBottomSheet
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.R
import io.homeassistant.companion.android.dashboard.derive.MoreInfoModel
import io.homeassistant.companion.android.dashboard.derive.moreInfoModel
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.history.showsHistory
import io.homeassistant.companion.android.dashboard.ui.cards.CardInteractions
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.cards.EntityToggle
import io.homeassistant.companion.android.dashboard.ui.cards.TileFeatureControl
import io.homeassistant.companion.android.dashboard.ui.moreinfo.MoreInfoHistory
import java.time.Instant
import java.time.ZonedDateTime

/**
 * The native quick view of an entity: its state, main control, recent history and attributes, with a link to upstream's full
 * more-info dialog. Live: it follows the entity while open. The sheet covers the screen, so [snackbar]'s messages
 * (such as a failed action) show over it too.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MoreInfoSheet(
    entityId: String,
    hass: State<HassSnapshot?>,
    now: State<ZonedDateTime?>,
    interactions: CardInteractions,
    onShowFull: (() -> Unit)?,
    onDismiss: () -> Unit,
    snackbar: SnackbarHostState? = null,
) {
    val info by remember(entityId) {
        derivedStateOf { hass.value?.moreInfoModel(entityId, now.value?.toInstant() ?: Instant.EPOCH) }
    }
    val model = info ?: return
    HAModalBottomSheet(bottomSheetState = rememberModalBottomSheetState(), onDismissRequest = onDismiss) {
        val snapshot = hass.value ?: return@HAModalBottomSheet
        MoreInfoContent(model, snapshot, now.value?.toInstant() ?: Instant.EPOCH, interactions, onShowFull)
        // The sheet's content may extend below the screen, so messages go at the bottom of the window instead
        if (snackbar?.currentSnackbarData != null) {
            Popup(popupPositionProvider = WindowBottom) {
                SnackbarHost(snackbar, Modifier.navigationBarsPadding())
            }
        }
    }
}

@Composable
private fun MoreInfoContent(
    model: MoreInfoModel,
    hass: HassSnapshot,
    now: Instant,
    interactions: CardInteractions,
    onShowFull: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HADimens.SPACE6)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
    ) {
        MoreInfoHeader(model, interactions)
        MoreInfoState(model)
        model.controls.forEach { TileFeatureControl(it, available = true, onAction = interactions.onAction) }
        model.media?.let { media ->
            Row(horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2)) {
                media.controls.forEach { control ->
                    HAPlainButton(control.label, { interactions.onAction(control.action) })
                }
            }
        }
        if (hass.showsHistory(model.entityId)) MoreInfoHistory(model.entityId, hass, now, interactions)
        if (model.attributes.isNotEmpty()) MoreInfoAttributes(model.attributes)
        onShowFull?.let {
            HAPlainButton(
                stringResource(R.string.native_dashboard_more_info_full),
                it,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

/** The entity's icon, name and context, with its switch when it turns on and off. */
@Composable
private fun MoreInfoHeader(model: MoreInfoModel, interactions: CardInteractions) {
    val colors = LocalHAColorScheme.current
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
            EntityToggle(checked = model.active, updatedAt = model.updatedAt, onToggle = {
                interactions.onAction(toggle)
            })
        }
    }
}

/** The state, and when it last changed. */
@Composable
private fun MoreInfoState(model: MoreInfoModel) {
    Column {
        Text(
            model.state,
            style = HATextStyle.HeadlineMedium.copy(textAlign = TextAlign.Start),
            color = LocalHAColorScheme.current.colorTextPrimary,
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
}

/** The displayable attributes, as name and formatted value. */
@Composable
private fun MoreInfoAttributes(attributes: List<Pair<String, String>>) {
    val colors = LocalHAColorScheme.current
    Text(
        stringResource(R.string.native_dashboard_more_info_attributes),
        style = HATextStyle.Body.copy(textAlign = TextAlign.Start),
        color = colors.colorTextPrimary,
    )
    attributes.forEach { (name, value) ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.Start), modifier = Modifier.weight(1f))
            Text(value, style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.End), color = colors.colorTextPrimary)
        }
    }
}

/** At the bottom of the window, centred. */
private object WindowBottom : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ) = IntOffset((windowSize.width - popupContentSize.width) / 2, windowSize.height - popupContentSize.height)
}
