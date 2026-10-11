package io.homeassistant.companion.android.dashboard.ui.dialogs.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HAFilledButton
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HAFontSize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.R
import io.homeassistant.companion.android.dashboard.data.Loadable
import io.homeassistant.companion.android.dashboard.display.relativeTime
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.entity.Localize
import io.homeassistant.companion.android.dashboard.layout.NARROW_MAX_WIDTH_DP
import io.homeassistant.companion.android.dashboard.notification.PersistentNotification
import io.homeassistant.companion.android.dashboard.ui.components.DashboardCardSurface
import io.homeassistant.companion.android.dashboard.ui.components.loadErrorText
import io.homeassistant.companion.android.dashboard.ui.lovelace.cards.MarkdownText
import io.homeassistant.companion.android.dashboard.ui.panels.ToolbarIconButton
import java.time.ZonedDateTime

/**
 * The notification drawer while the screen's [NotificationsViewModel] says it is open.
 *
 * Upstream: `show-notification-drawer` (src/dialogs/notifications/show-notification-drawer.ts)
 */
@Composable
internal fun NotificationDrawerHost(hass: State<HassSnapshot?>, now: State<ZonedDateTime?>) {
    val viewModel: NotificationsViewModel = hiltViewModel()
    val open by viewModel.drawerOpen.collectAsStateWithLifecycle()
    if (open) NotificationDrawer(viewModel, hass, now, onDismiss = viewModel::closeDrawer)
}

/**
 * The notification drawer: the persistent notifications newest first, each with its dismiss button, and "Dismiss all"
 * with several; it closes itself once the last one is dismissed. Full width on a phone, else 500dp, from the start.
 *
 * Upstream: `notification-drawer` (src/dialogs/notifications/notification-drawer.ts)
 */
@Composable
private fun NotificationDrawer(
    viewModel: NotificationsViewModel,
    hass: State<HassSnapshot?>,
    now: State<ZonedDateTime?>,
    onDismiss: () -> Unit,
) {
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val localize = hass.value?.localize ?: return
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.failures.collect { error ->
            snackbar.showSnackbar(
                context.getString(R.string.native_dashboard_load_failed, context.loadErrorText(error)),
            )
        }
    }
    // Upstream closes the drawer when the last notification goes, not when it opens empty
    var hadNotifications by remember { mutableStateOf(false) }
    val shown = notifications
    LaunchedEffect(shown) {
        val count = (shown as? Loadable.Ready)?.value?.size ?: return@LaunchedEffect
        if (hadNotifications && count == 0) onDismiss()
        hadNotifications = count > 0
    }
    val narrow = LocalConfiguration.current.screenWidthDp <= NARROW_MAX_WIDTH_DP
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxHeight()
                    .then(if (narrow) Modifier.fillMaxWidth() else Modifier.width(DRAWER_WIDTH))
                    .background(LocalHAColorScheme.current.colorSurfaceDefault)
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                DrawerHeader(localize(TITLE), localize(CLOSE), onDismiss)
                NotificationList(shown, hass, now, viewModel, localize, onDismiss, Modifier.weight(1f))
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/**
 * The drawer's header: its title, then the button that closes it, over a divider.
 *
 * Upstream: part of `notification-drawer`, `ha-header-bar` (src/dialogs/notifications/notification-drawer.ts;
 * src/components/ha-header-bar.ts)
 */
@Composable
private fun DrawerHeader(title: String, closeLabel: String, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(HEADER_HEIGHT).padding(start = HADimens.SPACE4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = HATextStyle.Body.copy(fontSize = HAFontSize.XL, textAlign = TextAlign.Start),
            color = LocalHAColorScheme.current.colorTextPrimary,
            modifier = Modifier.weight(1f),
        )
        ToolbarIconButton(BACK_ICON, closeLabel, onClick = onClose)
    }
    HorizontalDivider(color = LocalHAColorScheme.current.colorBorderNeutralQuiet)
}

/**
 * The notifications, or that there are none; "Dismiss all" under several. While they load, a loader; when they
 * couldn't be, why. "Dismiss all" closes the drawer too ([onClose]).
 *
 * Upstream: part of `notification-drawer` (src/dialogs/notifications/notification-drawer.ts)
 */
@Composable
private fun NotificationList(
    notifications: Loadable<List<PersistentNotification>>,
    hass: State<HassSnapshot?>,
    now: State<ZonedDateTime?>,
    viewModel: NotificationsViewModel,
    localize: Localize,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    when (notifications) {
        Loadable.Loading -> Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { HALoading() }
        is Loadable.Failed -> Column(
            modifier = modifier.padding(HADimens.SPACE4),
            verticalArrangement = Arrangement.spacedBy(HADimens.SPACE3),
        ) {
            Text(
                stringResource(
                    R.string.native_dashboard_load_failed,
                    LocalContext.current.loadErrorText(notifications.error),
                ),
                style = HATextStyle.Body,
            )
            HAAccentButton(text = stringResource(R.string.native_dashboard_retry), onClick = viewModel::retry)
        }
        is Loadable.Ready -> if (notifications.value.isEmpty()) {
            Text(
                localize(EMPTY),
                style = HATextStyle.Body,
                color = LocalHAColorScheme.current.colorTextPrimary,
                modifier = modifier.fillMaxWidth().padding(HADimens.SPACE4),
            )
        } else {
            LazyColumn(modifier = modifier, contentPadding = PaddingValues(top = HADimens.SPACE4)) {
                items(notifications.value, key = { it.id }) { notification ->
                    NotificationItem(notification, hass, now, localize(DISMISS)) { viewModel.dismiss(notification.id) }
                }
            }
            if (notifications.value.size > 1) {
                HorizontalDivider(color = LocalHAColorScheme.current.colorBorderNeutralQuiet)
                Box(Modifier.fillMaxWidth().padding(HADimens.SPACE4), contentAlignment = Alignment.Center) {
                    HAFilledButton(
                        localize(DISMISS_ALL),
                        onClick = {
                            viewModel.dismissAll()
                            onClose()
                        },
                    )
                }
            }
        }
    }
}

/**
 * A notification's card: its title, its message as markdown, how long ago it was created, and its dismiss button.
 *
 * Upstream: `persistent-notification-item`, `notification-item-template`
 * (src/dialogs/notifications/persistent-notification-item.ts;
 * src/dialogs/notifications/notification-item-template.ts)
 */
@Composable
private fun NotificationItem(
    notification: PersistentNotification,
    hass: State<HassSnapshot?>,
    now: State<ZonedDateTime?>,
    dismissLabel: String,
    onDismiss: () -> Unit,
) {
    val colors = LocalHAColorScheme.current
    val created by remember(notification.createdAt) {
        derivedStateOf {
            val formats = hass.value?.formats
            val at = notification.createdAt
            val current = now.value?.toInstant()
            if (formats == null || at == null || current == null) {
                null
            } else {
                formats.relativeTime(minOf(at, current), current).replaceFirstChar { it.uppercaseChar() }
            }
        }
    }
    DashboardCardSurface(
        modifier = Modifier.fillMaxWidth().padding(
            start = HADimens.SPACE4,
            end = HADimens.SPACE4,
            bottom = HADimens.SPACE4,
        ),
    ) {
        Column(Modifier.fillMaxWidth()) {
            notification.title?.let {
                Text(
                    it,
                    style = HATextStyle.Body.copy(
                        fontSize = HAFontSize.X2L,
                        lineHeight = TITLE_LINE_HEIGHT,
                        textAlign = TextAlign.Start,
                    ),
                    color = colors.colorTextPrimary,
                    modifier = Modifier.padding(start = HADimens.SPACE4, top = HADimens.SPACE4, end = HADimens.SPACE4),
                )
            }
            Column(Modifier.fillMaxWidth().padding(HADimens.SPACE4)) {
                MarkdownText(notification.message, Modifier.fillMaxWidth())
                created?.let {
                    Text(
                        it,
                        style = HATextStyle.Body.copy(textAlign = TextAlign.End),
                        color = colors.colorTextSecondary,
                        modifier = Modifier.fillMaxWidth().padding(top = TIME_MARGIN),
                    )
                }
            }
            HorizontalDivider(color = colors.colorBorderNeutralQuiet)
            Box(Modifier.fillMaxWidth().padding(HADimens.SPACE2), contentAlignment = Alignment.CenterEnd) {
                HAPlainButton(dismissLabel, onDismiss)
            }
        }
    }
}

private const val TITLE = "ui.notification_drawer.title"
private const val CLOSE = "ui.notification_drawer.close"
private const val EMPTY = "ui.notification_drawer.empty"
private const val DISMISS_ALL = "ui.notification_drawer.dismiss_all"
private const val DISMISS = "ui.card.persistent_notification.dismiss"

/** `ha-icon-button-prev`'s icon. */
private const val BACK_ICON = "mdi:arrow-left"

/** The drawer's width on a wide screen; a narrow one's is the screen's. */
private val DRAWER_WIDTH = 500.dp

/** `--header-height`. */
private val HEADER_HEIGHT = 56.dp

/** `--ha-line-height-condensed` (1.2) of the 24px title. */
private val TITLE_LINE_HEIGHT = HAFontSize.X3L

/** The time's `margin-top`. */
private val TIME_MARGIN = 6.dp

/**
 * Whether there are notifications, for the dot on the menu button.
 *
 * Upstream: part of `ha-menu-button` (src/components/ha-menu-button.ts)
 */
@Composable
internal fun hasNotifications(): Boolean =
    hiltViewModel<NotificationsViewModel>().hasNotifications.collectAsStateWithLifecycle().value
