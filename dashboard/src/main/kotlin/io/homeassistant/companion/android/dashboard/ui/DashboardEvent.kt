package io.homeassistant.companion.android.dashboard.ui

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.action.Confirmation

/** One-off effects of the dashboard the screen shows or performs. */
sealed interface DashboardEvent {
    /** A message for the user, already translated. */
    data class Message(val text: String) : DashboardEvent

    /** Ask the user to confirm [action] before it runs. */
    data class Confirm(val confirmation: Confirmation, val action: CardAction) : DashboardEvent

    /** Ask the user for the code [action] needs (`action.code`), then run it with that code. */
    data class EnterCode(val action: CardAction.CallService) : DashboardEvent

    /** Open [url] in the browser. */
    data class OpenUrl(val url: String) : DashboardEvent

    /** Show more information about [entityId]. */
    data class MoreInfo(val entityId: String) : DashboardEvent

    /** [path] leads outside the dashboard, which the native dashboard can't show yet. */
    data class UnsupportedNavigation(val path: String) : DashboardEvent

    /** The action type [type] is not supported natively yet. */
    data class UnsupportedAction(val type: String) : DashboardEvent
}
