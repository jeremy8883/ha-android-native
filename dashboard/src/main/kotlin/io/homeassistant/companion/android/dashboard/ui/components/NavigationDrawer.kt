package io.homeassistant.companion.android.dashboard.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.R
import io.homeassistant.companion.android.dashboard.data.Loadable
import io.homeassistant.companion.android.dashboard.data.valueOrNull
import io.homeassistant.companion.android.dashboard.derive.DisplayColor
import io.homeassistant.companion.android.dashboard.navigation.SidebarItem
import io.homeassistant.companion.android.dashboard.navigation.UserBadgeModel
import io.homeassistant.companion.android.dashboard.ui.SidebarState
import io.homeassistant.companion.android.dashboard.ui.theme.Css
import io.homeassistant.companion.android.dashboard.ui.theme.toColor

/**
 * The app's navigation drawer, in place of the frontend's sidebar: the panels shown natively in the user's order,
 * then under their own heading those that open in the companion app, then Settings (for admins) and the
 * notifications, like `ha-sidebar`'s fixed entries, and last the account: the user's name and badge over the server's
 * name.
 *
 * @param selected the url path of the panel shown now
 * @param drawerState the drawer's state, which back closes (with Material's predictive back animation)
 * @param sidebar the panels, or why they couldn't be loaded, with [onRetry] to try again
 * @param serverName the server shown, under the user's name
 * @param onOpenAccount opens the account sheet (the profile, switching servers); `null` makes the account entry open
 *   the profile
 * @param onlyDashboards lists only the dashboards, when other pages can't be opened
 * @param onOpenSettings opens the app's own settings, in place of the frontend's Settings and profile; `null` keeps
 *   those
 * @param notificationCount how many persistent notifications there are, shown on their entry; `null` while unknown
 * @param onOpenNotifications opens the notification drawer
 * @param user the current user's name and badge for the profile entry, `null` while unknown
 * @param onOpen called with the path of the chosen entry
 *
 * Upstream: `ha-sidebar` (src/components/ha-sidebar.ts)
 */
@Composable
internal fun NavigationDrawerContent(
    drawerState: DrawerState,
    sidebar: Loadable<SidebarState>,
    selected: String?,
    serverName: String? = null,
    onOpenAccount: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    onlyDashboards: Boolean = false,
    onRetry: () -> Unit = {},
    notificationCount: Int? = null,
    onOpenNotifications: () -> Unit = {},
    user: UserBadgeModel? = null,
    onOpen: (String) -> Unit,
) {
    val colors = LocalHAColorScheme.current

    /**
     * A panel's entry, selected while it is shown.
     *
     * Upstream: part of `ha-sidebar` (src/components/ha-sidebar.ts)
     */
    @Composable
    fun Entry(path: String, title: String, icon: String) {
        SidebarItem(title, path == selected, { onOpen("/$path") }) { tint -> SidebarIcon(icon, tint) }
    }

    /**
     * The entries below the panels: Settings and the notifications.
     *
     * Upstream: part of `ha-sidebar` (src/components/ha-sidebar.ts)
     */
    @Composable
    fun FooterEntries() {
        if (onOpenSettings != null) {
            val label = stringResource(R.string.native_dashboard_settings)
            SidebarItem(label, selected = false, onClick = onOpenSettings) { tint -> SidebarIcon(SETTINGS_ICON, tint) }
        } else if (sidebar.valueOrNull?.isAdmin == true) {
            Entry(CONFIG_PANEL, stringResource(R.string.native_dashboard_settings), SETTINGS_ICON)
        }
        SidebarItem(
            stringResource(R.string.native_dashboard_notifications),
            selected = false,
            onClick = onOpenNotifications,
            badge = notificationCount?.takeIf { it > 0 }?.let { count -> { CountBadge(count) } },
        ) { tint -> SidebarIcon(NOTIFICATIONS_ICON, tint) }
    }

    ModalDrawerSheet(
        drawerState = drawerState,
        // mwc-drawer's 256px
        modifier = Modifier.width(DRAWER_WIDTH),
        // Square, as the frontend's sidebar, instead of Material's rounded open edge
        drawerShape = RectangleShape,
        drawerContainerColor = colors.colorSurfaceDefault,
        // The header's banner reaches up behind the status bar; the bottom keeps clear of the navigation bar itself
        windowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Start),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            if (maxHeight < SHORT_HEIGHT) {
                // Too short to keep the header and the footer in place: everything scrolls together
                PeekColumn(
                    viewportHeight = constraints.maxHeight,
                    bottomPadding = Css.Space.S2 + navigationBar,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    SidebarHeader(slim = true)
                    Spacer(Modifier.height(Css.Space.S2))
                    PanelEntries(sidebar, onlyDashboards, onRetry) { Entry(it.urlPath, it.title, it.icon) }
                    HorizontalDivider()
                    Spacer(Modifier.height(Css.Space.S2))
                    FooterEntries()
                    HorizontalDivider()
                    Spacer(Modifier.height(Css.Space.S2))
                    AccountEntry(user, serverName, selected == PROFILE_PANEL, onlyDashboards, onOpenAccount, onOpen)
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    SidebarHeader(slim = false)
                    BoxWithConstraints(Modifier.weight(1f)) {
                        PeekColumn(
                            viewportHeight = constraints.maxHeight,
                            topPadding = Css.Space.S2,
                            bottomPadding = Css.Space.S2,
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                        ) {
                            PanelEntries(sidebar, onlyDashboards, onRetry) { Entry(it.urlPath, it.title, it.icon) }
                        }
                    }
                    HorizontalDivider()
                    Column(Modifier.padding(vertical = Css.Space.S2)) { FooterEntries() }
                    HorizontalDivider()
                    Column(Modifier.padding(top = Css.Space.S2, bottom = Css.Space.S2 + navigationBar)) {
                        AccountEntry(user, serverName, selected == PROFILE_PANEL, onlyDashboards, onOpenAccount, onOpen)
                    }
                }
            }
        }
    }
}

/**
 * The account: the user's name and badge over the server's name, opening the account sheet ([onOpenAccount]), else
 * the profile (which opens elsewhere, so it needs other pages: left out when [onlyDashboards]).
 *
 * Upstream: part of `ha-sidebar` (src/components/ha-sidebar.ts)
 */
// UPSTREAM-DIFF: the profile entry also names the server and opens the app's account sheet (profile and servers);
// upstream's opens the profile.
@Composable
private fun AccountEntry(
    user: UserBadgeModel?,
    serverName: String?,
    selected: Boolean,
    onlyDashboards: Boolean,
    onOpenAccount: (() -> Unit)?,
    onOpen: (String) -> Unit,
) {
    if (onOpenAccount == null && onlyDashboards) return
    SidebarItem(
        user?.name ?: stringResource(R.string.native_dashboard_profile),
        selected = selected,
        onClick = onOpenAccount ?: { onOpen("/$PROFILE_PANEL") },
        subtitle = serverName,
        badge = onOpenAccount?.let {
            { SidebarIcon(ACCOUNT_MENU_ICON, LocalHAColorScheme.current.colorTextSecondary) }
        },
        user = true,
    ) { tint -> user?.let { UserBadge(it) } ?: SidebarIcon(PROFILE_ICON, tint) }
}

/**
 * An entry of the sidebar: its icon, then its [label] in medium weight over its [subtitle], and its [badge] at the end; 40px high with
 * 4px under it, a slightly rounded highlight while [selected]. The profile ([user]) has its badge 4px in, the others
 * their icon 12px in.
 *
 * Upstream: part of `ha-sidebar`, with `ha-list-item-button` (src/components/ha-sidebar.ts;
 * src/components/item/ha-list-item-button.ts)
 */
@Composable
private fun SidebarItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    badge: (@Composable () -> Unit)? = null,
    subtitle: String? = null,
    user: Boolean = false,
    icon: @Composable (Color) -> Unit,
) {
    val colors = LocalHAColorScheme.current
    val shape = RoundedCornerShape(Css.Radius.Sm)
    val tint = if (selected) colors.colorOnPrimaryNormal else colors.colorTextSecondary
    Row(
        modifier = Modifier
            .padding(start = Css.Space.S1, end = Css.Space.S1, bottom = Css.Space.S1)
            .width(ITEM_WIDTH)
            .heightIn(min = Css.Space.S10)
            .clip(shape)
            .background(if (selected) colors.colorFillPrimaryQuietResting else Color.Transparent, shape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(start = if (user) Css.Space.S1 else Css.Space.S3, end = Css.Space.S3),
        horizontalArrangement = Arrangement.spacedBy(Css.Space.S4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon(tint)
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = Css.Text.Body.copy(fontWeight = Css.FontWeight.Medium),
                color = if (selected) colors.colorOnPrimaryNormal else colors.colorTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    it,
                    style = Css.Text.Body.copy(fontSize = Css.FontSize.S, textAlign = TextAlign.Start),
                    color = colors.colorTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        badge?.invoke()
    }
}

/**
 * An entry's 24px icon.
 *
 * Upstream: part of `ha-sidebar` (src/components/ha-sidebar.ts)
 */
@Composable
private fun SidebarIcon(icon: String, tint: Color) {
    DashboardIcon(icon, tint, Modifier.size(HASize.X2L))
}

/**
 * The panels: the native ones, then under their own heading those that open in the companion app (left out when
 * [onlyDashboards]); else that they load, or why they couldn't, with [onRetry].
 *
 * Upstream: part of `ha-sidebar` (src/components/ha-sidebar.ts)
 */
@Composable
private fun PanelEntries(
    sidebar: Loadable<SidebarState>,
    onlyDashboards: Boolean,
    onRetry: () -> Unit,
    entry: @Composable (SidebarItem) -> Unit,
) {
    // UPSTREAM-DIFF: pages that open in the companion app are grouped under their own heading; upstream keeps one
    // list in the user's order.
    val colors = LocalHAColorScheme.current
    when (sidebar) {
        Loadable.Loading -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            HALoading(Modifier.padding(Css.Space.S4))
        }
        is Loadable.Failed -> Column(
            modifier = Modifier.padding(Css.Space.S4),
            verticalArrangement = Arrangement.spacedBy(Css.Space.S3),
        ) {
            Text(
                text = stringResource(
                    R.string.native_dashboard_load_failed,
                    LocalContext.current.loadErrorText(sidebar.error),
                ),
                style = Css.Text.Body.copy(textAlign = TextAlign.Center),
                color = colors.colorTextSecondary,
            )
            HAAccentButton(text = stringResource(R.string.native_dashboard_retry), onClick = onRetry)
        }
        is Loadable.Ready -> {
            sidebar.value.dashboardItems.forEach { entry(it) }
            // The pages that open in the companion app, apart under their own heading
            val elsewhere = sidebar.value.elsewhereItems.takeUnless { onlyDashboards }.orEmpty()
            if (elsewhere.isNotEmpty()) {
                Text(
                    stringResource(R.string.native_dashboard_elsewhere_heading),
                    style = Css.Text.Body.copy(textAlign = TextAlign.Start),
                    color = colors.colorTextSecondary,
                    modifier = Modifier.padding(
                        start = Css.Space.S4,
                        end = Css.Space.S4,
                        top = Css.Space.S4,
                        bottom = Css.Space.S2,
                    ),
                )
                elsewhere.forEach { entry(it) }
            }
        }
    }
}

/**
 * The sidebar's header: the app's logo and name on a banner in the app icon's blue, reaching up behind the status bar;
 * [slim] when the screen is short.
 *
 * Upstream: part of `ha-sidebar` (src/components/ha-sidebar.ts)
 */
// UPSTREAM-DIFF: a banner with this app's logo and name (HADash), where upstream's header is a plain toolbar-high row
// saying "Home Assistant".
@Composable
private fun SidebarHeader(slim: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(BANNER_LIGHT, BANNER_DARK)))
            .statusBarsPadding()
            .height(if (slim) SLIM_BANNER_HEIGHT else BANNER_HEIGHT)
            .padding(horizontal = HEADER_PADDING),
        horizontalArrangement = Arrangement.spacedBy(Css.Space.S3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.hadash_logo),
            contentDescription = null,
            modifier = Modifier.height(if (slim) SLIM_LOGO_HEIGHT else LOGO_HEIGHT),
            contentScale = ContentScale.FillHeight,
        )
        Text(
            stringResource(R.string.native_dashboard_sidebar_title),
            style = Css.Text.Body.copy(
                fontSize = if (slim) Css.FontSize.Xl else Css.FontSize.X2l,
                fontWeight = Css.FontWeight.Medium,
                textAlign = TextAlign.Start,
            ),
            color = Color.White,
            maxLines = 1,
        )
    }
}

/**
 * The count on the notifications entry, a pill in the accent colour.
 *
 * Upstream: part of `ha-sidebar` (src/components/ha-sidebar.ts)
 */
@Composable
private fun CountBadge(count: Int) {
    Text(
        count.toString(),
        // `.badge`'s `line-height: normal`
        style = Css.Text.Body.copy(lineHeight = TextUnit.Unspecified),
        color = DisplayColor.State(listOf(TEXT_ACCENT)).toColor() ?: LocalHAColorScheme.current.colorOnPrimaryLoud,
        modifier = Modifier
            .clip(RoundedCornerShape(Css.Radius.Xl))
            .background(
                DisplayColor.State(listOf(ACCENT)).toColor() ?: LocalHAColorScheme.current.colorFillPrimaryLoudResting,
            )
            .padding(horizontal = BADGE_PADDING_H, vertical = BADGE_PADDING_V),
    )
}

/**
 * The user's badge: their person's picture, else their initials on the light primary colour, in a 40dp circle.
 *
 * Upstream: `ha-user-badge` (src/components/user/ha-user-badge.ts)
 */
@Composable
private fun UserBadge(user: UserBadgeModel) {
    Box(
        modifier = Modifier
            .size(USER_BADGE_SIZE)
            .clip(CircleShape)
            .background(
                DisplayColor.State(listOf(LIGHT_PRIMARY)).toColor()
                    ?: LocalHAColorScheme.current.colorFillPrimaryQuietResting,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (user.picture != null) {
            ServerImage(user.picture, contentDescription = null, modifier = Modifier.size(USER_BADGE_SIZE))
        } else {
            Text(
                user.initials,
                style = Css.Text.Body.copy(
                    textAlign = TextAlign.Center,
                    fontSize = if (user.initials.length > 2) Css.FontSize.S else Css.FontSize.L,
                ),
                color =
                DisplayColor.State(listOf(TEXT_LIGHT_PRIMARY)).toColor()
                    ?: LocalHAColorScheme.current.colorTextPrimary,
                maxLines = 1,
            )
        }
    }
}

private const val CONFIG_PANEL = "config"
private const val PROFILE_PANEL = "profile"
private const val SETTINGS_ICON = "mdi:cog"
private const val PROFILE_ICON = "mdi:account"
private const val ACCOUNT_MENU_ICON = "mdi:unfold-more-horizontal"
private const val NOTIFICATIONS_ICON = "mdi:bell"
private const val ACCENT = "accent-color"
private const val TEXT_ACCENT = "text-primary-color"
private const val LIGHT_PRIMARY = "light-primary-color"
private const val TEXT_LIGHT_PRIMARY = "text-light-primary-color"

/** `ha-user-badge`'s size in the sidebar. */
private val USER_BADGE_SIZE = 40.dp

/** The count's `padding: 2px 6px`. */
private val BADGE_PADDING_H = 6.dp
private val BADGE_PADDING_V = 2.dp

/** The header banner's height below the status bar, and when the screen is short. */
private val BANNER_HEIGHT = 80.dp
private val SLIM_BANNER_HEIGHT = 48.dp

/** The logo's height on the banner, and on the slim one. */
private val LOGO_HEIGHT = 36.dp
private val SLIM_LOGO_HEIGHT = 26.dp

/** The app icon's background gradient, lighter at the top left. */
private val BANNER_LIGHT = Color(0xFF35BAFC)
private val BANNER_DARK = Color(0xFF0B6BF8)

/** Below this height the header and footer scroll with the entries rather than staying in place. */
private val SHORT_HEIGHT = 480.dp

/** The menu's 4px padding and the narrow title's 16px. */
private val HEADER_PADDING = 20.dp

/** mwc-drawer's width, and the narrow sidebar's `ha-list-item-button` width. */
private val DRAWER_WIDTH = 256.dp
private val ITEM_WIDTH = 240.dp
