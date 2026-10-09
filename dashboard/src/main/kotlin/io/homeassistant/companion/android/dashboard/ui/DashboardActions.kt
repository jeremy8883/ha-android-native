package io.homeassistant.companion.android.dashboard.ui

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.data.Fetched
import io.homeassistant.companion.android.dashboard.data.LoadError
import io.homeassistant.companion.android.dashboard.data.ServerActionsRepository
import io.homeassistant.companion.android.dashboard.entity.Localize
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.coroutines.channels.SendChannel
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import timber.log.Timber

/**
 * Runs what card elements ask for: service calls (with a code when they need one, as upstream), navigation, more-info
 * and links, telling the user through [events] when an action fails, like the frontend does.
 *
 * @param localize the frontend strings, for the failure message
 * @param navigate follows a `navigate` action's path
 */
internal class DashboardActions(
    private val repository: ServerActionsRepository,
    private val events: SendChannel<DashboardEvent>,
    private val language: String,
    private val localize: suspend () -> Localize,
    private val navigate: (String) -> Unit,
) {
    suspend fun run(action: CardAction) {
        when (action) {
            is CardAction.Navigate -> navigate(action.path)
            is CardAction.CallService -> callProtectedService(action)
            is CardAction.MoreInfo -> events.send(DashboardEvent.MoreInfo(action.entityId))
            is CardAction.OpenUrl -> events.send(DashboardEvent.OpenUrl(action.url))
            is CardAction.Failure -> events.send(DashboardEvent.Message(action.message))
            is CardAction.Assist -> events.send(DashboardEvent.UnsupportedAction(ACTION_ASSIST))
            is CardAction.FireDomEvent -> events.send(DashboardEvent.UnsupportedAction(ACTION_FIRE_DOM_EVENT))
        }
    }

    /** Run [action] with the [code] the user entered for it. */
    suspend fun runWithCode(action: CardAction.CallService, code: String) = callService(action.withCode(code))

    /** A service that may need a code: use the entity's default code, or ask the user, as upstream does. */
    private suspend fun callProtectedService(call: CardAction.CallService) {
        val code = call.code ?: return callService(call)
        when (val defaultCode = repository.defaultCode(code.entityId, code.optionsDomain)) {
            is Fetched.Failure -> showActionFailed(call, defaultCode.error)
            // The server applies the default code itself
            is Fetched.Success -> if (defaultCode.value != null) {
                callService(call)
            } else {
                events.send(DashboardEvent.EnterCode(call))
            }
        }
    }

    private suspend fun callService(call: CardAction.CallService) {
        val error = repository.callService(call.domain, call.service, call.data, call.target) ?: return
        Timber.w("Failed to perform the action ${call.domain}/${call.service}: $error")
        showActionFailed(call, error)
    }

    /**
     * Upstream's `notifyOnError` (connection-mixin.ts): a failure haptic and a 10s message, the integration's
     * translation of the error when it has one, otherwise "Failed to perform the action light/turn_on. <why>". Nothing
     * shows when the connection was lost to an action that restarts or stops Home Assistant.
     */
    private suspend fun showActionFailed(call: CardAction.CallService, error: LoadError) {
        if (error == LoadError.NoResponse && call.willDisconnect()) return
        val text = translatedMessage(error) ?: run {
            val failed = localize()(
                "ui.notification_toast.action_failed",
                mapOf("service" to "${call.domain}/${call.service}"),
            )
            val reason = (error as? LoadError.Server)?.message
                ?: if (error == LoadError.NoResponse || error == LoadError.NoServer) CONNECTION_LOST else UNKNOWN_ERROR
            "$failed $reason"
        }
        events.send(DashboardEvent.ActionFailed(text))
    }

    /** The integration's translation of [error], when it has one. */
    private suspend fun translatedMessage(error: LoadError): String? {
        val translation = (error as? LoadError.Server)?.translation ?: return null
        return when (val message = repository.errorMessage(translation, language)) {
            is Fetched.Success -> message.value?.ifEmpty { null }
            is Fetched.Failure -> null.also {
                Timber.w("Failed to load the translation of $translation: ${message.error}")
            }
        }
    }
}

/** Port of `serviceCallWillDisconnect` (src/data/service.ts): actions after which the connection is expected to drop. */
private fun CardAction.CallService.willDisconnect(): Boolean =
    (domain == "homeassistant" && service in setOf("restart", "stop")) ||
        (domain == "update" && service == "install" && data?.string("entity_id") in CORE_UPDATES)

private fun CardAction.CallService.withCode(code: String) =
    copy(data = JsonObject(data.orEmpty() + ("code" to JsonPrimitive(code))), code = null)

private val CORE_UPDATES = setOf("update.home_assistant_core_update", "update.home_assistant_operating_system_update")

/** What upstream's action failure toast says when the server gave no reason (connection-mixin.ts). */
private const val UNKNOWN_ERROR = "unknown error"
private const val CONNECTION_LOST = "connection lost"

private const val ACTION_ASSIST = "assist"
private const val ACTION_FIRE_DOM_EVENT = "fire-dom-event"
