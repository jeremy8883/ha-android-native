package io.homeassistant.companion.android.dashboard.panels

import io.homeassistant.companion.android.dashboard.derive.deviceName
import io.homeassistant.companion.android.dashboard.derive.entityName
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.entity.entityContext
import io.homeassistant.companion.android.dashboard.target.Target
import java.net.URLDecoder
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

// What the History and Logbook panels read from their address. Port of `decodeHistoryLogbookQueryParams` and
// `historyLogbookTargetFromQueryParams` (frontend@20260624.6 src/common/url/history-logbook-query-params.ts).

/** The panel's address: what to show ([target], `null` when it names none) and the period's ends when given. */
data class HistoryLogbookQuery(val target: Target?, val start: Instant?, val end: Instant?)

/** Reads [query] ("entity_id=light.a,light.b&area_id=kitchen&start_date=…"). */
fun historyLogbookQuery(query: String?): HistoryLogbookQuery {
    val params = query.orEmpty().split('&').filter { '=' in it }.associate { pair ->
        pair.substringBefore('=') to URLDecoder.decode(pair.substringAfter('='), Charsets.UTF_8)
    }
    return HistoryLogbookQuery(
        target = Target.fromQuery(query),
        start = params[START_DATE]?.let(::parseDate),
        end = params[END_DATE]?.let(::parseDate),
    )
}

/** An entity as `entityDisplay` names it: its [name], with where it is ([context], "Kitchen ▸ Motion sensor"). */
data class PickerEntity(val entityId: String, val name: String, val context: String?)

/** [entityId] named as `entityDisplay` names it; [entityId] alone when it doesn't exist. */
fun HassSnapshot.pickerEntity(entityId: String): PickerEntity {
    val state = states[entityId] ?: return PickerEntity(entityId, entityId, null)
    val entityName = entityName(state)?.ifEmpty { null }
    val context = registries.entityContext(entityId)
    val deviceName = context.device?.deviceName()?.ifEmpty { null }
    val where = listOfNotNull(context.area?.name?.trim()?.ifEmpty { null }, deviceName.takeIf { entityName != null })
    return PickerEntity(
        entityId,
        entityName ?: deviceName ?: entityId,
        where.takeIf {
            it.isNotEmpty()
        }?.joinToString(" ▸ "),
    )
}

private fun parseDate(text: String): Instant? = try {
    OffsetDateTime.parse(text).toInstant()
} catch (_: DateTimeParseException) {
    null
}

private const val START_DATE = "start_date"
private const val END_DATE = "end_date"
