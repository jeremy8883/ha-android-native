package io.homeassistant.companion.android.dashboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import io.homeassistant.companion.android.common.compose.composable.HAModalBottomSheet
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.R
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.MoreInfoModel
import io.homeassistant.companion.android.dashboard.derive.moreInfoModel
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.history.showsHistory
import io.homeassistant.companion.android.dashboard.logbook.showsLogbook
import io.homeassistant.companion.android.dashboard.moreinfo.humidifierHumidityCall
import io.homeassistant.companion.android.dashboard.moreinfo.humidifierTarget
import io.homeassistant.companion.android.dashboard.moreinfo.waterHeaterTarget
import io.homeassistant.companion.android.dashboard.moreinfo.waterHeaterTemperatureCall
import io.homeassistant.companion.android.dashboard.ui.cards.CardInteractions
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.cards.EntityToggle
import io.homeassistant.companion.android.dashboard.ui.controls.StateToggleControl
import io.homeassistant.companion.android.dashboard.ui.moreinfo.FavoritesMenu
import io.homeassistant.companion.android.dashboard.ui.moreinfo.MoreInfoAlarm
import io.homeassistant.companion.android.dashboard.ui.moreinfo.MoreInfoClimate
import io.homeassistant.companion.android.dashboard.ui.moreinfo.MoreInfoFan
import io.homeassistant.companion.android.dashboard.ui.moreinfo.MoreInfoHistory
import io.homeassistant.companion.android.dashboard.ui.moreinfo.MoreInfoLight
import io.homeassistant.companion.android.dashboard.ui.moreinfo.MoreInfoLock
import io.homeassistant.companion.android.dashboard.ui.moreinfo.MoreInfoLogbook
import io.homeassistant.companion.android.dashboard.ui.moreinfo.MoreInfoMediaPlayer
import io.homeassistant.companion.android.dashboard.ui.moreinfo.MoreInfoPosition
import io.homeassistant.companion.android.dashboard.ui.moreinfo.MoreInfoVacuum
import io.homeassistant.companion.android.dashboard.ui.moreinfo.SingleDialControls
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
        MoreInfoHeader(model, interactions) {
            hass.states[model.entityId]?.takeIf { it.domain in FAVORITES_DOMAINS }?.let { FavoritesMenu(it, hass) }
        }
        if (model.stateHeader) MoreInfoState(model)
        hass.states[model.entityId]?.let { DomainControls(model, it, hass, now, interactions.onAction) }
        if (hass.showsHistory(model.entityId)) MoreInfoHistory(model.entityId, hass, now, interactions)
        if (hass.showsLogbook(model.entityId)) MoreInfoLogbook(model.entityId, hass, now, interactions)
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

/** The controls of the entity's domain, as its `more-info-<domain>` leads with them. */
@Composable
private fun DomainControls(
    model: MoreInfoModel,
    state: EntityState,
    hass: HassSnapshot,
    now: Instant,
    onAction: (CardAction) -> Unit,
) {
    model.light?.let { MoreInfoLight(it, state, hass, onAction) }
    model.climate?.let { MoreInfoClimate(it, state, onAction) }
    model.position?.let { MoreInfoPosition(it, state, hass, onAction) }
    model.fan?.let { MoreInfoFan(it, onAction) }
    model.lock?.let { MoreInfoLock(it, onAction) }
    model.alarm?.let { MoreInfoAlarm(it, onAction) }
    model.mediaPlayer?.let { MoreInfoMediaPlayer(it, now, onAction) }
    model.vacuum?.let { MoreInfoVacuum(it, onAction) }
    model.waterHeater?.let { heater ->
        SingleDialControls(
            current = heater.current,
            control = heater.temperature,
            target = waterHeaterTarget(state),
            menus = heater.menus,
            onSet = { onAction(waterHeaterTemperatureCall(state, it)) },
            onAction = onAction,
        )
    }
    model.humidifier?.let { humidifier ->
        SingleDialControls(
            current = humidifier.current,
            control = humidifier.humidity,
            target = humidifierTarget(state),
            menus = humidifier.menus,
            onSet = { onAction(humidifierHumidityCall(state, it)) },
            onAction = onAction,
            bottomUnit = true,
        )
    }
    model.stateToggle?.let { toggle ->
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { StateToggleControl(toggle, onAction) }
    }
}

/** The entity's icon, name and context, with its switch when it turns on and off. */
@Composable
private fun MoreInfoHeader(model: MoreInfoModel, interactions: CardInteractions, menu: @Composable () -> Unit) {
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
        menu()
    }
}

/** Port of `ha-more-info-state-header`: the state, and when it last changed, centred. */
@Composable
private fun MoreInfoState(model: MoreInfoModel) {
    val colors = LocalHAColorScheme.current
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            model.state,
            style = HATextStyle.Headline.copy(fontSize = STATE_FONT_SIZE, fontWeight = FontWeight.Normal),
            color = colors.colorTextPrimary,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
        ) {
            Text(
                model.changed,
                style = HATextStyle.Body.copy(fontWeight = FontWeight.Medium),
                color = colors.colorTextPrimary,
            )
            // The `after-time` slot: a vacuum's battery
            model.vacuum?.battery?.let { battery ->
                battery.text?.let { Text(it, style = HATextStyle.Body, color = colors.colorTextSecondary) }
                battery.icon?.let { DashboardIcon(it, colors.colorTextSecondary, Modifier.size(HASize.XL)) }
            }
        }
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

/** The domains with favourites, whose menu the header shows. */
private val FAVORITES_DOMAINS = setOf("light", "cover", "valve")

/** The state's size in the header (36px). */
private val STATE_FONT_SIZE = 36.sp

/** At the bottom of the window, centred. */
private object WindowBottom : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ) = IntOffset((windowSize.width - popupContentSize.width) / 2, windowSize.height - popupContentSize.height)
}
