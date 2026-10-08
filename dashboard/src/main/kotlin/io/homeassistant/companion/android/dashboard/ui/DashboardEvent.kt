package io.homeassistant.companion.android.dashboard.ui

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.action.Confirmation
import io.homeassistant.companion.android.dashboard.data.LoadError

/** One-off effects of the dashboard the screen shows or performs. */
sealed interface DashboardEvent {
    /** A message for the user, already translated. */
    data class Message(val text: String) : DashboardEvent

    /** Ask the user to confirm [action] before it runs. */
    data class Confirm(val confirmation: Confirmation, val action: CardAction) : DashboardEvent

    /** Ask the user for the code [action] needs (`action.code`), then run it with that code. */
    data class EnterCode(val action: CardAction.CallService) : DashboardEvent

    /** Open [uri] inside the app, such as a `homeassistant://navigate` deep link to the web frontend. */
    data class OpenAppLink(val uri: String) : DashboardEvent

    /** Open [url] in the browser. */
    data class OpenUrl(val url: String) : DashboardEvent

    /** Show more information about [entityId]. */
    data class MoreInfo(val entityId: String) : DashboardEvent

    /** Open [path] (a panel other than a native dashboard) in the web frontend. */
    data class OpenWeb(val path: String) : DashboardEvent

    /** A native dashboard was opened, so it should show in place of the web frontend. */
    data object ShowDashboard : DashboardEvent

    /** What the user asked for needs data that couldn't be loaded. */
    data class LoadFailed(val error: LoadError) : DashboardEvent

    /** The action type [type] is not supported natively yet. */
    data class UnsupportedAction(val type: String) : DashboardEvent
}
