package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.display.attributeTimestampDisplay
import io.homeassistant.companion.android.dashboard.display.formatEntityAttributeValue
import io.homeassistant.companion.android.dashboard.display.formatEntityAttributeValueParts
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.string
import java.time.Instant
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

// The attribute row of an entities card (frontend@20260624.6 src/panels/lovelace/special-rows/hui-attribute-row.ts).

/**
 * Port of `HuiAttributeRow`: an entity's row showing one of its attributes, between the `prefix` and `suffix`; a
 * date in its `time_format` when it has one, "—" when the attribute is missing.
 */
internal fun HassSnapshot.attributeRow(config: JsonObject, now: Instant): EntitiesRow {
    val entityId = config.string(ENTITY)?.ifEmpty { null }
    val attribute = config.string("attribute")?.ifEmpty { null }
    if (entityId == null || attribute == null) return invalidRow()
    val row = entityRow(config, now, EntityRowKind.SIMPLE)
    val state = states[entityId]
    return EntitiesRow.Entity(
        if (state ==
            null
        ) {
            row
        } else {
            row.copy(state = attributeText(config, state, attribute, now))
        },
    )
}

/** The text of an attribute row for [attribute] of [state]: the prefix, the value, then the suffix. */
private fun HassSnapshot.attributeText(
    config: JsonObject,
    state: EntityState,
    attribute: String,
    now: Instant,
): String {
    val value = state.attributes[attribute]
    val suffix = config.string("suffix")
    val text = config.string("time_format")?.ifEmpty { null }?.let { attributeTimestampDisplay(value, now, it) }
        ?: when {
            value == null -> NO_VALUE
            // `ha-attribute-value` hides the unit when the row has its own suffix
            !suffix.isNullOrEmpty() && value !is JsonNull -> formatEntityAttributeValueParts(state, attribute).value
            else -> formatEntityAttributeValue(state, attribute)
        }
    return config.string("prefix").orEmpty() + text + suffix.orEmpty()
}

private const val ENTITY = "entity"
private const val NO_VALUE = "—"
