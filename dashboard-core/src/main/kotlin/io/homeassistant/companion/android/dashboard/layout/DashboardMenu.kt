package io.homeassistant.companion.android.dashboard.layout

/**
 * An entry of a dashboard's toolbar. Search is drawn natively; the others open the dashboard shown in the frontend,
 * with [webParam] (as `?<webParam>=1`) asking it to do the same there.
 *
 * @property labelKey the frontend's translation key of its label
 */
sealed class DashboardMenuAction(val icon: String, val labelKey: String, val webParam: String?) {
    /** The quick bar ("Search Home Assistant"), drawn natively. */
    data object Search : DashboardMenuAction(
        icon = "mdi:magnify",
        labelKey = "ui.panel.lovelace.menu.search_home_assistant",
        webParam = null,
    )

    /** The Assist dialog. */
    data object Assist : DashboardMenuAction(
        icon = "mdi:comment-processing-outline",
        labelKey = "ui.panel.lovelace.menu.assist_tooltip",
        webParam = "conversation",
    )

    /** Editing the dashboard, for admins. */
    data object EditDashboard : DashboardMenuAction(
        icon = "mdi:pencil",
        labelKey = "ui.panel.lovelace.menu.configure_ui",
        webParam = "edit",
    )
}

/**
 * What the toolbar's actions do ([onAction]), and which of them can be done here ([offered]): without the companion
 * app, only those drawn natively.
 */
class DashboardMenuHandler(
    val offered: Set<DashboardMenuAction>,
    private val onAction: (DashboardMenuAction) -> Unit,
) : (DashboardMenuAction) -> Unit {
    override fun invoke(action: DashboardMenuAction) = onAction(action)
}

/** A dashboard's toolbar actions: [buttons] shown as icons, then [overflow] behind the menu button. */
data class DashboardMenu(val buttons: List<DashboardMenuAction>, val overflow: List<DashboardMenuAction>)

/**
 * The toolbar actions of a dashboard, outside edit mode: Search and Assist (when the `conversation` integration is
 * loaded) as buttons, or in the menu on a [narrow] screen; Edit dashboard for admins out of recovery mode, in the
 * menu unless it is alone there; only those [offered] when given. Port of `hui-root`'s `_renderActionItems`
 * (frontend@20260624.6 src/panels/lovelace/hui-root.ts), without the YAML mode's entries.
 */
fun dashboardMenu(
    narrow: Boolean,
    isAdmin: Boolean,
    recoveryMode: Boolean,
    components: Set<String>,
    offered: Set<DashboardMenuAction>? = null,
): DashboardMenu {
    val items = buildList {
        add(DashboardMenuAction.Search to narrow)
        if (CONVERSATION in components) add(DashboardMenuAction.Assist to narrow)
        if (isAdmin && !recoveryMode) add(DashboardMenuAction.EditDashboard to true)
    }.filter { (action, _) -> offered == null || action in offered }
    val overflow = items.filter { (_, inOverflow) -> inOverflow }.map { it.first }
    // A lone Edit dashboard is promoted to a button (`overflow_can_promote`)
    val promote = overflow == listOf(DashboardMenuAction.EditDashboard)
    return DashboardMenu(
        buttons = items.filter { (_, inOverflow) -> !inOverflow || promote }.map { it.first },
        overflow = if (promote) emptyList() else overflow,
    )
}

/** The `max-width` (in dp) up to which the frontend lays out for a narrow screen (`home-assistant-main`). */
const val NARROW_MAX_WIDTH_DP = 870

private const val CONVERSATION = "conversation"
