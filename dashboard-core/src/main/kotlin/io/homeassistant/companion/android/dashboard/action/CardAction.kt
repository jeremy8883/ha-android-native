package io.homeassistant.companion.android.dashboard.action

import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.jsTruthy
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A user gesture on a card or one of its parts. */
enum class Gesture(val configKey: String) {
    TAP("tap_action"),
    HOLD("hold_action"),
    DOUBLE_TAP("double_tap_action"),
}

/** What running a card action does. */
sealed interface CardAction {
    /** Open the more-info dialog of [entityId]. */
    data class MoreInfo(val entityId: String) : CardAction

    /** Go to [path] in the app (a view of this dashboard when it has no leading `/`). */
    data class Navigate(val path: String, val replace: Boolean) : CardAction

    /** Open [url] outside the dashboard. */
    data class OpenUrl(val url: String) : CardAction

    /** Call `[domain].[service]` with [data] and [target]. */
    data class CallService(val domain: String, val service: String, val data: JsonObject?, val target: JsonObject?) :
        CardAction

    /** Open Assist. */
    data class Assist(val pipelineId: String, val startListening: Boolean) : CardAction

    /** A `fire-dom-event` action, for custom handlers; [config] is the whole action config. */
    data class FireDomEvent(val config: JsonObject) : CardAction

    /** The action can't run; tell the user [message] (upstream shows a toast). */
    data class Failure(val message: String) : CardAction
}

/** A text asking the user to confirm before [action] runs. */
data class Confirmation(val text: String, val title: String?, val confirmText: String?, val dismissText: String?)

/** An action to run, optionally after the user confirms it. */
data class ResolvedAction(val action: CardAction, val confirmation: Confirmation?)

/**
 * Whether [config] defines a real action for [gesture]. Port of `hasAction`
 * (frontend@20260624.6 src/panels/lovelace/common/has-action.ts).
 */
fun hasAction(config: JsonObject, gesture: Gesture): Boolean =
    config.obj(gesture.configKey)?.let { it.string("action") != ACTION_NONE } == true

/**
 * The action [gesture] runs for a card or element whose config is [config] (holding `entity` and the
 * `*_action` options), or `null` when it does nothing (`action: none`, or an action type the app does not
 * know). A missing action defaults to more-info, as upstream.
 *
 * Port of `handleAction` (src/panels/lovelace/common/handle-action.ts).
 */
fun HassSnapshot.resolveAction(config: JsonObject, gesture: Gesture): ResolvedAction? {
    val actionConfig = config.obj(gesture.configKey) ?: buildJsonObject { put("action", ACTION_MORE_INFO) }
    val action = actionFor(config, actionConfig) ?: return null
    return ResolvedAction(action, confirmationFor(actionConfig, action))
}

private fun HassSnapshot.actionFor(config: JsonObject, actionConfig: JsonObject): CardAction? {
    fun failure(key: String) = CardAction.Failure(localize("ui.panel.lovelace.cards.actions.$key"))
    return when (actionConfig.string("action")) {
        ACTION_MORE_INFO -> {
            val entityId = actionConfig.string("entity")?.ifEmpty { null }
                ?: listOf("entity", "camera_image", "image_entity").firstNotNullOfOrNull {
                    config.string(it)?.ifEmpty { null }
                }
            entityId?.let { CardAction.MoreInfo(it) } ?: failure("no_entity_more_info")
        }
        "navigate" -> actionConfig.string("navigation_path")?.ifEmpty { null }
            ?.let { CardAction.Navigate(it, actionConfig.boolean("navigation_replace") == true) }
            ?: failure("no_navigation_path")
        "url" -> actionConfig.string("url_path")?.ifEmpty { null }?.let { CardAction.OpenUrl(it) } ?: failure("no_url")
        "toggle" -> config.string("entity")?.ifEmpty { null }?.let { toggleEntity(it) } ?: failure("no_entity_toggle")
        "perform-action", "call-service" -> {
            val name = (actionConfig.string("perform_action") ?: actionConfig.string("service"))?.ifEmpty { null }
                ?: return failure("no_action")
            CardAction.CallService(
                domain = name.substringBefore('.'),
                service = name.substringAfter('.', ""),
                data = actionConfig.obj("data") ?: actionConfig.obj("service_data"),
                target = actionConfig.obj("target"),
            )
        }
        "assist" -> CardAction.Assist(
            pipelineId = actionConfig.string("pipeline_id") ?: "last_used",
            startListening = actionConfig.boolean("start_listening") == true,
        )
        "fire-dom-event" -> CardAction.FireDomEvent(actionConfig)
        else -> null
    }
}

/**
 * The confirmation [actionConfig] asks for, unless the current user is exempt. `confirmation: true` uses the
 * default text. Upstream names the service from the server's service translations; the app names the action
 * instead (for example "Perform action"), until those translations are loaded.
 */
private fun HassSnapshot.confirmationFor(actionConfig: JsonObject, action: CardAction): Confirmation? {
    val confirmation = actionConfig["confirmation"]?.takeIf(::jsTruthy) ?: return null
    val options = confirmation as? JsonObject ?: JsonObject(emptyMap())
    if (options.objects("exemptions").any { it.string("user") == user?.id }) return null
    if (action is CardAction.Failure) return null
    val type = actionConfig.string("action").orEmpty()
    val actionName = localize("ui.panel.lovelace.editor.action-editor.actions.$type").ifEmpty { type }
    return Confirmation(
        text = options.string("text")
            ?: localize("ui.panel.lovelace.cards.actions.action_confirmation", mapOf("action" to actionName)),
        title = options.string("title"),
        confirmText = options.string("confirm_text"),
        dismissText = options.string("dismiss_text"),
    )
}

/**
 * Turn [entityId] on when it is off, closed or locked, otherwise off. Ports of `toggleEntity` and
 * `turnOnOffEntity` (src/panels/lovelace/common/entity/).
 */
fun HassSnapshot.toggleEntity(entityId: String): CardAction.CallService {
    val turnOn = states[entityId]?.state in STATES_OFF
    val domain = entityId.substringBefore('.')
    val service = when (domain) {
        "lock" -> if (turnOn) "unlock" else "lock"
        "cover" -> if (turnOn) "open_cover" else "close_cover"
        "button", "input_button" -> "press"
        "scene" -> "turn_on"
        "valve" -> if (turnOn) "open_valve" else "close_valve"
        else -> if (turnOn) "turn_on" else "turn_off"
    }
    return CardAction.CallService(
        domain = if (domain == "group") "homeassistant" else domain,
        service = service,
        data = buildJsonObject { put("entity_id", entityId) },
        target = null,
    )
}

private const val ACTION_NONE = "none"
private const val ACTION_MORE_INFO = "more-info"

/** Port of `STATES_OFF` (src/common/const.ts). */
private val STATES_OFF = setOf("closed", "locked", "off")
