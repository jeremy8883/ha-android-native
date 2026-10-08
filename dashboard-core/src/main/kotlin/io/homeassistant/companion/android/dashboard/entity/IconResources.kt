package io.homeassistant.companion.android.dashboard.entity

import io.homeassistant.companion.android.dashboard.model.obj
import kotlinx.serialization.json.JsonObject

/**
 * Icon translations from the server (`frontend/get_icons`), which map entity states to icons.
 *
 * @property entityComponent `category: entity_component` resources: domain → device class (or `_`) → icons
 * @property platforms `category: entity` resources: integration → domain → translation key → icons
 */
data class IconResources(val entityComponent: JsonObject, val platforms: JsonObject) {
    companion object {
        val EMPTY = IconResources(JsonObject(emptyMap()), JsonObject(emptyMap()))

        /** Build from the `frontend/get_icons` results of the `entity_component` and `entity` categories. */
        fun fromResults(entityComponent: JsonObject?, entity: JsonObject?): IconResources = IconResources(
            entityComponent = entityComponent?.obj("resources") ?: JsonObject(emptyMap()),
            platforms = entity?.obj("resources") ?: JsonObject(emptyMap()),
        )
    }
}
