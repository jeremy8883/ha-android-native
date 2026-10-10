package io.homeassistant.companion.android.dashboard.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.homeassistant.companion.android.common.compose.composable.HAHorizontalDivider
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.R
import io.homeassistant.companion.android.dashboard.derive.MoreInfoModel
import io.homeassistant.companion.android.dashboard.entity.Localize
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.moreinfo.FavoritesMenuItems

// The details' toolbar and views. Port of the toolbar of `ha-more-info-dialog` (frontend@20260624.6
// src/dialogs/more-info/ha-more-info-dialog.ts).

/** The views of the details, as upstream's dialog switches between them. */
internal sealed interface MoreInfoView {
    /** The state and controls (`info`). */
    data object Main : MoreInfoView

    /** The history and activity (`history`). */
    data object History : MoreInfoView

    /** The state entries and attributes (`details`). */
    data object Details : MoreInfoView
}

/**
 * `100dvh - max(var(--safe-area-inset-top), 48px)`, less the navigation bar the sheet adds under its content.
 */
@Composable
internal fun sheetHeight(): Dp {
    val density = LocalDensity.current
    val window = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return window - maxOf(statusBar, MIN_TOP_GAP) - navigationBar
}

/**
 * Port of the dialog's toolbar: close (or back from another view), the entity's name under its area and device,
 * then on the main view the history button and a menu with [favorites]' items, the details view and the frontend's
 * full dialog.
 */
@Composable
internal fun MoreInfoToolbar(
    model: MoreInfoModel,
    view: MoreInfoView,
    onView: (MoreInfoView) -> Unit,
    onClose: () -> Unit,
    onShowFull: (() -> Unit)?,
    localize: Localize,
    favorites: FavoritesMenuItems?,
) {
    val colors = LocalHAColorScheme.current
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = HADimens.SPACE1, vertical = HADimens.SPACE2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val main = view == MoreInfoView.Main
        ToolbarButton(
            if (main) CLOSE_ICON else BACK_ICON,
            localize(if (main) "ui.common.close" else "ui.common.back"),
        ) { if (main) onClose() else onView(MoreInfoView.Main) }
        ToolbarTitle(model, view, localize, Modifier.weight(1f).padding(horizontal = HADimens.SPACE2))
        if (main) {
            if (model.historyButton) {
                ToolbarButton(HISTORY_ICON, localize("ui.dialogs.more_info_control.history")) {
                    onView(MoreInfoView.History)
                }
            }
            // UPSTREAM-DIFF: no settings button, and the menu has no device, edit, related or "add to" items (they
            // stay in the companion app); it is for every user, not only admins, and adds the frontend's full dialog
            Box {
                ToolbarButton(MENU_ICON, localize("ui.common.menu")) { menu = true }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    favorites?.let { items ->
                        items { menu = false }
                        HAHorizontalDivider()
                    }
                    DropdownMenuItem(
                        text = { Text(localize("ui.dialogs.more_info_control.details")) },
                        leadingIcon = {
                            DashboardIcon(DETAILS_ICON, colors.colorTextSecondary, Modifier.size(HASize.X2L))
                        },
                        onClick = {
                            menu = false
                            onView(MoreInfoView.Details)
                        },
                    )
                    onShowFull?.let { show ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.native_dashboard_more_info_full)) },
                            leadingIcon = {
                                DashboardIcon(FULL_ICON, colors.colorTextSecondary, Modifier.size(HASize.X2L))
                            },
                            onClick = {
                                menu = false
                                show()
                            },
                        )
                    }
                }
            }
        }
    }
}

/** The title: the entity's name (or the view's) under the breadcrumb of its area and device, which the name ends. */
@Composable
private fun ToolbarTitle(model: MoreInfoModel, view: MoreInfoView, localize: Localize, modifier: Modifier) {
    val colors = LocalHAColorScheme.current
    Column(modifier) {
        model.context?.let { breadcrumb ->
            Text(
                breadcrumb,
                style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.Start, lineHeight = BREADCRUMB_LINE_HEIGHT),
                color = colors.colorTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            when (view) {
                MoreInfoView.Main -> model.name
                MoreInfoView.History -> localize("ui.dialogs.more_info_control.history")
                MoreInfoView.Details -> localize("ui.dialogs.more_info_control.details")
            },
            style = HATextStyle.HeadlineMedium.copy(textAlign = TextAlign.Start, fontSize = TITLE_FONT_SIZE),
            color = colors.colorTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ToolbarButton(icon: String, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = label }) {
        DashboardIcon(icon, LocalHAColorScheme.current.colorTextPrimary, Modifier.size(HASize.X2L))
    }
}

private const val CLOSE_ICON = "mdi:close"
private const val BACK_ICON = "mdi:arrow-left"
private const val HISTORY_ICON = "mdi:chart-box-outline"
private const val MENU_ICON = "mdi:dots-vertical"
private const val DETAILS_ICON = "mdi:format-list-bulleted-square"
private const val FULL_ICON = "mdi:open-in-new"
private val MIN_TOP_GAP = 48.dp

/** The breadcrumb's 16px line. */
private val BREADCRUMB_LINE_HEIGHT = 16.sp

/** The toolbar title, `--ha-font-size-xl`. */
private val TITLE_FONT_SIZE = 20.sp
