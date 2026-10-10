package io.homeassistant.companion.android.dashboard.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import io.homeassistant.companion.android.common.compose.composable.HAHorizontalDivider
import io.homeassistant.companion.android.common.compose.composable.HAModalBottomSheet
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.MoreInfoModel
import io.homeassistant.companion.android.dashboard.derive.StateCard
import io.homeassistant.companion.android.dashboard.derive.moreInfoModel
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.entity.Localize
import io.homeassistant.companion.android.dashboard.history.showsHistory
import io.homeassistant.companion.android.dashboard.logbook.showsLogbook
import io.homeassistant.companion.android.dashboard.moreinfo.humidifierHumidityCall
import io.homeassistant.companion.android.dashboard.moreinfo.humidifierTarget
import io.homeassistant.companion.android.dashboard.moreinfo.waterHeaterTarget
import io.homeassistant.companion.android.dashboard.moreinfo.waterHeaterTemperatureCall
import io.homeassistant.companion.android.dashboard.ui.cards.CardInteractions
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.cards.RowControlContent
import io.homeassistant.companion.android.dashboard.ui.cards.ServerImage
import io.homeassistant.companion.android.dashboard.ui.controls.StateToggleControl
import io.homeassistant.companion.android.dashboard.ui.moreinfo.FavoritesMenu
import io.homeassistant.companion.android.dashboard.ui.moreinfo.LogbookSection
import io.homeassistant.companion.android.dashboard.ui.moreinfo.MoreInfoAlarm
import io.homeassistant.companion.android.dashboard.ui.moreinfo.MoreInfoClimate
import io.homeassistant.companion.android.dashboard.ui.moreinfo.MoreInfoFan
import io.homeassistant.companion.android.dashboard.ui.moreinfo.MoreInfoHistory
import io.homeassistant.companion.android.dashboard.ui.moreinfo.MoreInfoLight
import io.homeassistant.companion.android.dashboard.ui.moreinfo.MoreInfoLock
import io.homeassistant.companion.android.dashboard.ui.moreinfo.MoreInfoMediaPlayer
import io.homeassistant.companion.android.dashboard.ui.moreinfo.MoreInfoPosition
import io.homeassistant.companion.android.dashboard.ui.moreinfo.MoreInfoUpdateFooter
import io.homeassistant.companion.android.dashboard.ui.moreinfo.MoreInfoVacuum
import io.homeassistant.companion.android.dashboard.ui.moreinfo.SimpleDomainControls
import io.homeassistant.companion.android.dashboard.ui.moreinfo.SingleDialControls
import io.homeassistant.companion.android.dashboard.ui.moreinfo.logbookItems
import io.homeassistant.companion.android.dashboard.ui.moreinfo.rememberLogbookSection
import io.homeassistant.companion.android.dashboard.ui.theme.entityIconTint
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
    var view by remember(entityId) { mutableStateOf<MoreInfoView>(MoreInfoView.Main) }
    // Upstream's sheet is the screen's height less the larger of the status bar and 48px, whatever it shows
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    HAModalBottomSheet(bottomSheetState = sheetState, onDismissRequest = onDismiss, dragHandle = null) {
        val snapshot = hass.value ?: return@HAModalBottomSheet
        val instant = now.value?.toInstant() ?: Instant.EPOCH
        // Back returns to the main view first, as the dialog's back button does
        BackHandler(enabled = view != MoreInfoView.Main) { view = MoreInfoView.Main }
        Column(Modifier.fillMaxWidth().height(sheetHeight()).navigationBarsPadding()) {
            MoreInfoToolbar(
                model = model,
                view = view,
                showsHistory = snapshot.showsHistory(entityId) || snapshot.showsLogbook(entityId),
                onView = { view = it },
                onClose = onDismiss,
                onShowFull = onShowFull,
                localize = snapshot.localize,
            )
            when (view) {
                MoreInfoView.Main -> MoreInfoMain(model, snapshot, instant, interactions)
                MoreInfoView.History -> MoreInfoHistoryView(model.entityId, snapshot, instant, interactions)
                MoreInfoView.Details -> MoreInfoDetailsView(model, snapshot.localize)
            }
        }
        // The sheet's content may extend below the screen, so messages go at the bottom of the window instead
        if (snackbar?.currentSnackbarData != null) {
            Popup(popupPositionProvider = WindowBottom) {
                SnackbarHost(snackbar, Modifier.navigationBarsPadding())
            }
        }
    }
}

/**
 * Port of `ha-more-info-info`: the state card (or the state header), the controls, and for domains without
 * details of their own the history, activity and attributes after them. One lazy list, the logbook one item per
 * row, so only what's on screen is built however long the day's logbook is. An update's buttons are pinned under
 * it.
 */
@Composable
private fun ColumnScope.MoreInfoMain(
    model: MoreInfoModel,
    hass: HassSnapshot,
    now: Instant,
    interactions: CardInteractions,
) {
    val logbook = if (model.inlineHistory && hass.showsLogbook(model.entityId)) {
        rememberLogbookSection(model.entityId, hass, now)
    } else {
        null
    }
    val state = hass.states[model.entityId]
    val update = model.update
    // Each opening starts at the top: a saved scroll position would carry over to the next details
    val listState = remember(model.entityId) { LazyListState() }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().weight(1f),
        contentPadding = PaddingValues(horizontal = HADimens.SPACE6),
    ) { mainItems(MainList(model, hass, now, interactions, logbook)) }
    if (update != null && state != null) {
        HAHorizontalDivider()
        Box(Modifier.padding(horizontal = HADimens.SPACE6, vertical = HADimens.SPACE4)) {
            MoreInfoUpdateFooter(update, state, hass, now, interactions.onAction)
        }
    }
}

/** What the main view's list shows. */
private class MainList(
    val model: MoreInfoModel,
    val hass: HassSnapshot,
    val now: Instant,
    val interactions: CardInteractions,
    val logbook: LogbookSection?,
)

private fun LazyListScope.mainItems(list: MainList) {
    val (model, hass, now) = Triple(list.model, list.hass, list.now)
    val interactions = list.interactions
    val section = Modifier.padding(bottom = HADimens.SPACE4)
    model.stateCard?.let { card ->
        item(key = "state-card") { Box(section) { StateCardRow(card, now, interactions.onAction) } }
    }
    if (model.stateHeader) {
        item(key = "state") {
            Box(section) {
                MoreInfoState(model) {
                    hass.states[model.entityId]?.takeIf {
                        it.domain in FAVORITES_DOMAINS
                    }?.let { FavoritesMenu(it, hass) }
                }
            }
        }
    }
    hass.states[model.entityId]?.let { state ->
        item(key = "controls") {
            Column(section, verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4)) {
                DomainControls(model, state, hass, now, interactions.onAction)
            }
        }
    }
    if (model.inlineHistory && hass.showsHistory(model.entityId)) {
        item(key = "history") { Box(section) { MoreInfoHistory(model.entityId, hass, now, interactions) } }
    }
    list.logbook?.let { logbookItems(it, hass, now, interactions) }
    if (model.inlineAttributes && model.attributes.isNotEmpty()) {
        item(key = "attributes") {
            Column(
                Modifier.padding(vertical = HADimens.SPACE4),
                verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
            ) {
                MoreInfoAttributes(model.attributes, hass.localize("ui.dialogs.more_info_control.attributes"))
            }
        }
    }
}

/** Port of `ha-more-info-history-and-logbook`: the history, then the activity. */
@Composable
private fun ColumnScope.MoreInfoHistoryView(
    entityId: String,
    hass: HassSnapshot,
    now: Instant,
    interactions: CardInteractions,
) {
    val logbook = if (hass.showsLogbook(entityId)) rememberLogbookSection(entityId, hass, now) else null
    LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(horizontal = HADimens.SPACE6)) {
        if (hass.showsHistory(entityId)) {
            item(key = "history") {
                Box(Modifier.padding(bottom = HADimens.SPACE4)) { MoreInfoHistory(entityId, hass, now, interactions) }
            }
        }
        logbook?.let { logbookItems(it, hass, now, interactions) }
    }
}

/** Port of `ha-more-info-details`: the state's entries, then the attributes. */
@Composable
private fun ColumnScope.MoreInfoDetailsView(model: MoreInfoModel, localize: Localize) {
    LazyColumn(
        Modifier.fillMaxWidth().weight(1f),
        contentPadding = PaddingValues(horizontal = HADimens.SPACE6, vertical = HADimens.SPACE4),
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
    ) {
        item(key = "state") {
            MoreInfoAttributes(model.details, localize("ui.components.entity.entity-state-picker.state"))
        }
        if (model.attributes.isNotEmpty()) {
            item(key = "attributes") {
                MoreInfoAttributes(model.attributes, localize("ui.dialogs.more_info_control.attributes"))
            }
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
    model.mediaPlayer?.let { MoreInfoMediaPlayer(it, state.entityId, hass, now, onAction) }
    model.vacuum?.let { MoreInfoVacuum(it, state, hass, onAction) }
    SimpleDomainControls(model, state, onAction)
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

/**
 * Port of `state-card-content` in the dialog: the badge, the name over when it changed, then the entity's row
 * control (a switch, buttons or an input), else its state.
 */
@Composable
private fun StateCardRow(card: StateCard, now: Instant, onAction: (CardAction) -> Unit) {
    val colors = LocalHAColorScheme.current
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
    ) {
        val picture = card.badge.picture
        if (picture != null) {
            ServerImage(picture, card.name, Modifier.size(STATE_BADGE_SIZE).clip(CircleShape))
        } else {
            DashboardIcon(
                card.badge.icon,
                entityIconTint(card.badge.color, card.badge.unavailable, card.badge.brightness),
                Modifier.size(HASize.X2L),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                card.name,
                style = HATextStyle.Body.copy(textAlign = TextAlign.Start),
                color = colors.colorTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                card.changed,
                style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.Start),
                color = colors.colorTextSecondary,
            )
        }
        RowControlContent(card.control, card.state, colors.colorTextPrimary, now, onAction)
    }
}

/** Port of `ha-more-info-state-header`: the state, and when it last changed, centred; [menu] at its end. */
@Composable
private fun MoreInfoState(model: MoreInfoModel, menu: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.align(Alignment.TopEnd)) { menu() }
        MoreInfoStateText(model)
    }
}

@Composable
private fun MoreInfoStateText(model: MoreInfoModel) {
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
            // The `after-time` slot: a vacuum's or lawn mower's battery
            (model.vacuum?.battery ?: model.lawnMower?.battery)?.let { battery ->
                battery.text?.let { Text(it, style = HATextStyle.Body, color = colors.colorTextSecondary) }
                battery.icon?.let { DashboardIcon(it, colors.colorTextSecondary, Modifier.size(HASize.XL)) }
            }
        }
    }
}

/** A titled list of names and values: the attributes, or the details' state entries. */
@Composable
private fun MoreInfoAttributes(attributes: List<Pair<String, String>>, title: String) {
    val colors = LocalHAColorScheme.current
    Text(
        title,
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

/** `state-badge`'s 40 × 40. */
private val STATE_BADGE_SIZE = 40.dp

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
