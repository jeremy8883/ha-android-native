package io.homeassistant.companion.android.dashboard.ui.moreinfo

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import io.homeassistant.companion.android.common.compose.composable.HAHorizontalDivider
import io.homeassistant.companion.android.common.compose.composable.HAModalBottomSheet
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.MoreInfoModel
import io.homeassistant.companion.android.dashboard.derive.moreInfoModel
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.history.showsHistory
import io.homeassistant.companion.android.dashboard.logbook.showsLogbook
import io.homeassistant.companion.android.dashboard.moreinfo.MoreInfoExtra
import io.homeassistant.companion.android.dashboard.moreinfo.humidifierHumidityCall
import io.homeassistant.companion.android.dashboard.moreinfo.humidifierTarget
import io.homeassistant.companion.android.dashboard.moreinfo.waterHeaterTarget
import io.homeassistant.companion.android.dashboard.moreinfo.waterHeaterTemperatureCall
import io.homeassistant.companion.android.dashboard.ui.components.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.lovelace.common.CardInteractions
import io.homeassistant.companion.android.dashboard.ui.moreinfo.FavoritesMenuHost
import io.homeassistant.companion.android.dashboard.ui.moreinfo.FavoritesMenuItems
import io.homeassistant.companion.android.dashboard.ui.moreinfo.LogbookSection
import io.homeassistant.companion.android.dashboard.ui.moreinfo.MoreInfoExtraView
import io.homeassistant.companion.android.dashboard.ui.moreinfo.MoreInfoHistory
import io.homeassistant.companion.android.dashboard.ui.moreinfo.SimpleDomainControls
import io.homeassistant.companion.android.dashboard.ui.moreinfo.controls.MoreInfoAlarm
import io.homeassistant.companion.android.dashboard.ui.moreinfo.controls.MoreInfoClimate
import io.homeassistant.companion.android.dashboard.ui.moreinfo.controls.MoreInfoConversation
import io.homeassistant.companion.android.dashboard.ui.moreinfo.controls.MoreInfoFan
import io.homeassistant.companion.android.dashboard.ui.moreinfo.controls.MoreInfoLight
import io.homeassistant.companion.android.dashboard.ui.moreinfo.controls.MoreInfoLock
import io.homeassistant.companion.android.dashboard.ui.moreinfo.controls.MoreInfoMediaPlayer
import io.homeassistant.companion.android.dashboard.ui.moreinfo.controls.MoreInfoPosition
import io.homeassistant.companion.android.dashboard.ui.moreinfo.controls.MoreInfoUpdateFooter
import io.homeassistant.companion.android.dashboard.ui.moreinfo.controls.MoreInfoVacuum
import io.homeassistant.companion.android.dashboard.ui.moreinfo.controls.SingleDialControls
import io.homeassistant.companion.android.dashboard.ui.moreinfo.logbookItems
import io.homeassistant.companion.android.dashboard.ui.moreinfo.rememberLogbookSection
import io.homeassistant.companion.android.dashboard.ui.statecontrol.StateToggleControl
import io.homeassistant.companion.android.dashboard.ui.statesummary.StateCardRow
import io.homeassistant.companion.android.dashboard.ui.theme.Css
import io.homeassistant.companion.android.dashboard.ui.theme.cssFontPx
import java.time.Instant
import java.time.ZonedDateTime

/**
 * The native quick view of an entity: its state, main control, recent history and attributes, with a link to upstream's full
 * more-info dialog. Live: it follows the entity while open. The sheet covers the screen, so [snackbar]'s messages
 * (such as a failed action) show over it too.
 *
 * Upstream: `ha-more-info-dialog`, `ha-bottom-sheet` (src/dialogs/more-info/ha-more-info-dialog.ts;
 * src/components/ha-bottom-sheet.ts)
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
    // The details' YAML mode, left with the view
    var yamlMode by remember(entityId) { mutableStateOf(false) }
    val showView = { next: MoreInfoView ->
        view = next
        yamlMode = false
    }
    // Upstream's sheet is the screen's height less the larger of the status bar and 48px, whatever it shows
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    HAModalBottomSheet(bottomSheetState = sheetState, onDismissRequest = onDismiss, dragHandle = null) {
        val snapshot = hass.value ?: return@HAModalBottomSheet
        val instant = now.value?.toInstant() ?: Instant.EPOCH
        // Back returns to the main view first, as the dialog's back button does
        BackHandler(enabled = view != MoreInfoView.Main) { showView(MoreInfoView.Main) }
        Column(Modifier.fillMaxWidth().height(sheetHeight()).navigationBarsPadding()) {
            WithFavoritesMenu(snapshot.states[entityId], snapshot) { favorites ->
                MoreInfoToolbar(
                    model = model,
                    view = view,
                    onView = showView,
                    onToggleYaml = { yamlMode = !yamlMode },
                    onClose = onDismiss,
                    onShowFull = onShowFull,
                    localize = snapshot.localize,
                    favorites = favorites,
                )
            }
            when (view) {
                MoreInfoView.Main -> MoreInfoMain(model, snapshot, instant, interactions, onShowFull)
                MoreInfoView.History -> MoreInfoHistoryView(model.entityId, snapshot, instant, interactions)
                MoreInfoView.Details -> if (yamlMode) {
                    MoreInfoDetailsYamlView(model.entityId, snapshot)
                } else {
                    MoreInfoDetailsView(model, snapshot.localize)
                }
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
 *
 * Upstream: `ha-more-info-info` (src/dialogs/more-info/ha-more-info-info.ts)
 */
@Composable
private fun ColumnScope.MoreInfoMain(
    model: MoreInfoModel,
    hass: HassSnapshot,
    now: Instant,
    interactions: CardInteractions,
    onShowFull: (() -> Unit)?,
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
    val conversation = model.extra as? MoreInfoExtra.Conversation
    if (conversation != null) {
        // The chat fills the sheet, its messages from the bottom over the field, as `ha-assist-chat`
        MoreInfoConversation(
            conversation.agentId,
            hass,
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = Css.Space.S6),
        )
        return
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().weight(1f),
        contentPadding = PaddingValues(horizontal = Css.Space.S6),
    ) { mainItems(MainList(model, hass, now, interactions, logbook, onShowFull)) }
    if (update != null && state != null) {
        HAHorizontalDivider()
        Box(Modifier.padding(horizontal = Css.Space.S6, vertical = Css.Space.S4)) {
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
    val onShowFull: (() -> Unit)?,
)

private fun LazyListScope.mainItems(list: MainList) {
    val (model, hass, now) = Triple(list.model, list.hass, list.now)
    val interactions = list.interactions
    val section = Modifier.padding(bottom = Css.Space.S4)
    model.stateCard?.let { card ->
        item(key = "state-card") { Box(section) { StateCardRow(card, now, interactions.onAction) } }
    }
    if (model.stateHeader) {
        item(key = "state") { Box(section) { MoreInfoStateText(model) } }
    }
    hass.states[model.entityId]?.let { state ->
        item(key = "controls") {
            Column(section, verticalArrangement = Arrangement.spacedBy(Css.Space.S4)) {
                DomainControls(model, state, hass, now, interactions.onAction, list.onShowFull)
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
                Modifier.padding(vertical = Css.Space.S4),
                verticalArrangement = Arrangement.spacedBy(Css.Space.S4),
            ) {
                MoreInfoAttributes(model.attributes, hass.localize("ui.dialogs.more_info_control.attributes"))
            }
        }
    }
}

/**
 * Port of `ha-more-info-history-and-logbook`: the history, then the activity.
 *
 * Upstream: `ha-more-info-history-and-logbook` (src/dialogs/more-info/ha-more-info-history-and-logbook.ts)
 */
@Composable
private fun ColumnScope.MoreInfoHistoryView(
    entityId: String,
    hass: HassSnapshot,
    now: Instant,
    interactions: CardInteractions,
) {
    val logbook = if (hass.showsLogbook(entityId)) rememberLogbookSection(entityId, hass, now) else null
    LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(horizontal = Css.Space.S6)) {
        if (hass.showsHistory(entityId)) {
            item(key = "history") {
                Box(Modifier.padding(bottom = Css.Space.S4)) { MoreInfoHistory(entityId, hass, now, interactions) }
            }
        }
        logbook?.let { logbookItems(it, hass, now, interactions) }
    }
}

/**
 * The controls of the entity's domain, as its `more-info-<domain>` leads with them; [onShowFull] opens the full
 * dialog for what they leave to it.
 *
 * Upstream: `more-info-content` (src/dialogs/more-info/more-info-content.ts)
 */
@Composable
internal fun DomainControls(
    model: MoreInfoModel,
    state: EntityState,
    hass: HassSnapshot,
    now: Instant,
    onAction: (CardAction) -> Unit,
    onShowFull: (() -> Unit)? = null,
) {
    model.light?.let { MoreInfoLight(it, state, hass, onAction) }
    model.climate?.let { MoreInfoClimate(it, state, onAction) }
    model.position?.let { MoreInfoPosition(it, state, hass, onAction) }
    model.fan?.let { MoreInfoFan(it, onAction) }
    model.lock?.let { MoreInfoLock(it, onAction) }
    model.alarm?.let { MoreInfoAlarm(it, onAction) }
    model.mediaPlayer?.let { MoreInfoMediaPlayer(it, state.entityId, hass, now, onAction) }
    model.vacuum?.let { MoreInfoVacuum(it, state, hass, onAction, onShowFull) }
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
    model.extra?.let { MoreInfoExtraView(it, state, hass, now, onAction) }
}

/**
 * [content] with the favourites' menu items of [state], for the domains with favourites.
 *
 * Upstream: part of `ha-more-info-dialog` (src/dialogs/more-info/ha-more-info-dialog.ts;
 * src/dialogs/more-info/favorites.ts)
 */
@Composable
private fun WithFavoritesMenu(
    state: EntityState?,
    hass: HassSnapshot,
    content: @Composable (FavoritesMenuItems?) -> Unit,
) {
    if (state != null && state.domain in FAVORITES_DOMAINS) FavoritesMenuHost(state, hass, content) else content(null)
}

/**
 * Port of `ha-more-info-state-header`: the state, and when it last changed, centred.
 *
 * Upstream: `ha-more-info-state-header` (src/dialogs/more-info/components/ha-more-info-state-header.ts)
 */
@Composable
internal fun MoreInfoStateText(model: MoreInfoModel) {
    val colors = LocalHAColorScheme.current
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            model.state,
            style = Css.Text.Primary.copy(
                fontSize = STATE_FONT_SIZE,
                lineHeight = Css.LineHeight.Condensed,
                textAlign = TextAlign.Center,
            ),
            color = colors.colorTextPrimary,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Css.Space.S2),
        ) {
            Text(
                model.changed,
                style = Css.Text.Body.copy(
                    fontSize = Css.FontSize.L,
                    lineHeight = Css.LineHeight.Normal,
                    fontWeight = Css.FontWeight.Medium,
                ),
                color = colors.colorTextPrimary,
            )
            // The `after-time` slot: a vacuum's or lawn mower's battery
            (model.vacuum?.battery ?: model.lawnMower?.battery)?.let { battery ->
                battery.text?.let { Text(it, style = Css.Text.Body, color = colors.colorTextSecondary) }
                battery.icon?.let { DashboardIcon(it, colors.colorTextSecondary, Modifier.size(HASize.XL)) }
            }
        }
    }
}

/**
 * The attributes under their title, after the controls of the domains without details of their own.
 *
 * Upstream: `ha-attributes` (src/components/ha-attributes.ts)
 */
@Composable
private fun MoreInfoAttributes(attributes: List<Pair<String, String>>, title: String) {
    val colors = LocalHAColorScheme.current
    Text(
        title,
        style = Css.Text.Body.copy(textAlign = TextAlign.Start),
        color = colors.colorTextPrimary,
    )
    attributes.forEach { (name, value) ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, style = Css.Text.Body.copy(textAlign = TextAlign.Start), modifier = Modifier.weight(1f))
            Text(value, style = Css.Text.Body.copy(textAlign = TextAlign.End), color = colors.colorTextPrimary)
        }
    }
}

/** The domains with favourites, whose items the menu has. */
private val FAVORITES_DOMAINS = setOf("light", "cover", "valve")

/** The state's size in the header (36px). */
private val STATE_FONT_SIZE = 36.cssFontPx

/** At the bottom of the window, centred. */
private object WindowBottom : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ) = IntOffset((windowSize.width - popupContentSize.width) / 2, windowSize.height - popupContentSize.height)
}
