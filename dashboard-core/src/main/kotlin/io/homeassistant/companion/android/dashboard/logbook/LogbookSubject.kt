package io.homeassistant.companion.android.dashboard.logbook

import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.panels.pickerEntity

// What the Logbook panel's rows name: what each is about, and the line saying what caused it. Ports of
// `entityDisplay` (frontend@20260624.6 src/panels/logbook/logbook-entry-model.ts) and of `_renderListCauseLine` and
// `_renderCausePhrase` (src/panels/logbook/ha-logbook-entry.ts).

/**
 * Port of `entityDisplay` with every detail: the entity's own name (else its device's, else its id), with its area
 * and its device (when the entity has a name of its own) as where it is. An entry without an entity, or whose
 * entity is gone, is named by the entry.
 */
internal fun HassSnapshot.logbookSubject(entry: LogbookEntry): LogbookSubject {
    val entityId = entry.entityId?.takeIf { it in states } ?: return LogbookSubject(entry.name, null, null)
    val entity = pickerEntity(entityId)
    return LogbookSubject(entity.name, entity.context, entityId)
}

/** The line saying what caused an entry: [text], then the entity it names ([link]) opening its details. */
data class CausePhrase(val text: String, val link: CauseLink? = null)

/** An entity a cause names, by its [name]. */
data class CauseLink(val name: String, val entityId: String)

/**
 * Port of `_renderListCauseLine`'s cause: "By automation: " and the automation that ran, "By state change: " and the
 * entity that changed, "By Jane", "Scheduled", "Via Zigbee"…
 */
fun HassSnapshot.causePhrase(cause: LogbookCause): CausePhrase = when (cause) {
    is LogbookCause.Automation -> named("by_automation", cause.name, cause.entityId)
    is LogbookCause.Script -> named("by_script", cause.name, cause.entityId)
    is LogbookCause.State -> named("by_state_change", cause.name, cause.entityId)
    is LogbookCause.User -> named("by", cause.name, entityId = null)
    is LogbookCause.Integration -> named("via", cause.name, entityId = null)
    LogbookCause.Scheduled -> CausePhrase(localize("$CAUSE.scheduled"))
    is LogbookCause.HomeAssistant -> CausePhrase(cause.name)
}

/** The phrase [key] naming [name]: as a link after it when it is the entity [entityId], else within it. */
private fun HassSnapshot.named(key: String, name: String, entityId: String?): CausePhrase = if (entityId != null) {
    CausePhrase(localize("$CAUSE.$key", mapOf(NAME to "")), CauseLink(name, entityId))
} else {
    CausePhrase(localize("$CAUSE.$key", mapOf(NAME to name)))
}

private const val CAUSE = "$LOGBOOK_STRINGS.cause"
private const val NAME = "name"
