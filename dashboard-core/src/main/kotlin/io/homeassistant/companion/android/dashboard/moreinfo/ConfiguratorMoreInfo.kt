package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Port of `more-info-configurator` (frontend@20260624.6 src/dialogs/more-info/controls/more-info-configurator.ts):
 * while it asks to configure, its description, errors, fields and submit button.
 */
data class ConfiguratorMoreInfo(
    val description: String?,
    val errors: String?,
    val fields: List<ConfiguratorField>,
    val submit: String?,
    private val configureId: JsonPrimitive?,
) {
    /** The `configurator.configure` data for the fields' [values], by field id. */
    fun configureData(values: Map<String, String>): JsonObject = JsonObject(
        listOfNotNull(
            configureId?.let { "configure_id" to it },
            "fields" to JsonObject(values.mapValues { JsonPrimitive(it.value) }),
        ).toMap(),
    )
}

/** A configurator field: its [id], [name] and input [type] ("text", "password", ...). */
data class ConfiguratorField(val id: String, val name: String, val type: String?)

internal fun HassSnapshot.configuratorMoreInfo(state: EntityState): ConfiguratorMoreInfo? {
    if (state.state != "configure") return null
    val attributes = state.attributes
    return ConfiguratorMoreInfo(
        description = attributes.string("description"),
        errors = attributes.string("errors")?.ifEmpty { null },
        fields = (attributes["fields"] as? JsonArray).orEmpty().mapNotNull { field ->
            val obj = field as? JsonObject ?: return@mapNotNull null
            val id = obj.string("id") ?: return@mapNotNull null
            ConfiguratorField(id, obj.string("name") ?: id, obj.string("type"))
        },
        submit = attributes.string("submit_caption")?.ifEmpty { null },
        configureId = attributes["configure_id"] as? JsonPrimitive,
    )
}
