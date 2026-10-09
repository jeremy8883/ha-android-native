package io.homeassistant.companion.android.dashboard.logbook

import io.homeassistant.companion.android.dashboard.derive.deviceName
import io.homeassistant.companion.android.dashboard.derive.entityName
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.entity.entityContext

// Who or what caused a logbook entry. Port of `computeLogbookCause`, `entityDisplay` and `parseTriggerSource`
// (frontend@20260624.6 src/panels/logbook/logbook-entry-model.ts, src/data/logbook.ts).

/** What caused a logbook entry, [name] naming it. */
sealed interface LogbookCause {
    val name: String

    /** A user did, [systemUser] when it is one Home Assistant made for itself (Cloud, Cast). */
    data class User(override val name: String, val userId: String, val systemUser: Boolean) : LogbookCause

    /** An automation's run did. */
    data class Automation(override val name: String, val entityId: String?) : LogbookCause

    /** A script's run did. */
    data class Script(override val name: String, val entityId: String?) : LogbookCause

    /** Another entity's state change did. */
    data class State(override val name: String, val entityId: String?) : LogbookCause

    /** A time trigger did. */
    data object Scheduled : LogbookCause {
        override val name: String = ""
    }

    /** Home Assistant starting or stopping did. */
    data class HomeAssistant(override val name: String) : LogbookCause

    /** An integration did, [brandDomain] being it when known. */
    data class Integration(override val name: String, val brandDomain: String? = null) : LogbookCause
}

/** Port of `computeLogbookCause`: what caused [entry], when anything is known to. */
internal fun HassSnapshot.logbookCause(entry: LogbookEntry, users: LogbookUsers): LogbookCause? {
    val context = entry.context
    return userCause(context, users)
        ?: runCause(context)
        ?: context.domain?.takeIf { context.eventType == CALL_SERVICE }?.let { domain ->
            LogbookCause.Integration(domainToName(domain), domain)
        }
        ?: stateCause(context)
        ?: triggerCause(entry)
        ?: context.name?.let { LogbookCause.Integration(it, context.domain) }
}

private fun userCause(context: LogbookContext, users: LogbookUsers): LogbookCause? {
    val userId = context.userId ?: return null
    return users.names[userId]?.takeIf { it.isNotEmpty() }?.let { name ->
        LogbookCause.User(name, userId, userId in users.systemUserIds)
    }
}

private fun HassSnapshot.runCause(context: LogbookContext): LogbookCause? {
    val script = context.eventType == SCRIPT_STARTED
    val name = context.takeIf { script || it.eventType == AUTOMATION_TRIGGERED }
        ?.let { it.entityId?.let(::entityPrimaryName) ?: it.name }
        ?: return null
    return if (script) LogbookCause.Script(name, context.entityId) else LogbookCause.Automation(name, context.entityId)
}

private fun HassSnapshot.stateCause(context: LogbookContext): LogbookCause? {
    val entityId = context.entityId?.takeIf { !context.state.isNullOrEmpty() } ?: return null
    return (entityPrimaryName(entityId) ?: context.entityIdName)?.let { LogbookCause.State(it, entityId) }
}

/** The cause an automation's or script's own run names in its trigger [LogbookEntry.source], when it has no context. */
private fun HassSnapshot.triggerCause(entry: LogbookEntry): LogbookCause? {
    val source = entry.source?.takeIf { entry.domain in TRIGGER_DOMAINS && !entry.hasContext() } ?: return null
    val (platform, entityId) = parseTriggerSource(source)
    return when (platform) {
        null -> null
        STATE, NUMERIC_STATE -> LogbookCause.State(
            entityId?.let { entityPrimaryName(it) ?: it } ?: triggerTypeName(platform),
            entityId,
        )
        TIME, TIME_PATTERN -> LogbookCause.Scheduled
        HOMEASSISTANT -> {
            val key = if (source.startsWith(STARTING_PHRASE)) "homeassistant_starting" else "homeassistant_stopping"
            LogbookCause.HomeAssistant(localize("$LOGBOOK_STRINGS.$key").ifEmpty { source })
        }
        else -> LogbookCause.Integration(triggerTypeName(platform))
    }
}

private fun HassSnapshot.triggerTypeName(platform: String) =
    localize("$LOGBOOK_STRINGS.trigger_type.$platform").ifEmpty { platform }

/** Whether [this] says what caused it (`hasContext`). */
internal fun LogbookEntry.hasContext(): Boolean =
    !context.eventType.isNullOrEmpty() || !context.state.isNullOrEmpty() || !context.message.isNullOrEmpty()

/**
 * Port of `parseTriggerSource`: the trigger platform the backend's English `source` phrase names, with the entity
 * it ends with, if any.
 */
private fun parseTriggerSource(source: String): Pair<String?, String?> {
    val (phrase, platform) = TRIGGER_PHRASES.firstOrNull { (phrase, _) -> source.startsWith(phrase) }
        ?: return null to null
    val rest = source.substring(phrase.length).trim()
    return platform to rest.takeIf { ENTITY_ID.matches(it) }
}

/** Port of `entityDisplay(...).primary`: the entity's own name, else its device's, else its id; `null` when gone. */
internal fun HassSnapshot.entityPrimaryName(entityId: String): String? {
    val state = states[entityId] ?: return null
    return entityName(state)?.ifEmpty { null }
        ?: registries.entityContext(entityId).device?.deviceName()?.ifEmpty { null }
        ?: entityId
}

/** Port of `domainToName`: the integration's title, else its domain. */
internal fun HassSnapshot.domainToName(domain: String): String = localize("component.$domain.title").ifEmpty { domain }

internal val TRIGGER_DOMAINS = setOf("automation", "script")
internal const val LOGBOOK_STRINGS = "ui.components.logbook"

private const val CALL_SERVICE = "call_service"
private const val AUTOMATION_TRIGGERED = "automation_triggered"
private const val SCRIPT_STARTED = "script_started"
private const val STATE = "state"
private const val NUMERIC_STATE = "numeric_state"
private const val TIME = "time"
private const val TIME_PATTERN = "time_pattern"
private const val HOMEASSISTANT = "homeassistant"
private const val STARTING_PHRASE = "Home Assistant starting"
private val ENTITY_ID = Regex("^[a-z_]+\\.[a-z0-9_]+$")

/** The backend's English trigger phrases and their platforms; "time pattern" is tested before "time". */
private val TRIGGER_PHRASES = listOf(
    "numeric state of" to NUMERIC_STATE,
    "state of" to STATE,
    "event" to "event",
    "time pattern" to TIME_PATTERN,
    "time" to TIME,
    "Home Assistant stopping" to HOMEASSISTANT,
    STARTING_PHRASE to HOMEASSISTANT,
)
