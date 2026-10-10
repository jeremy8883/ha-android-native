package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonObject

/**
 * The icon for [value] of [state]'s [attribute], as `ha-attribute-icon` shows it: the platform's icon translation
 * for the entity's translation key, else its domain's for its device class or by default; `null` when neither has
 * one. Port of `attributeIcon` (frontend@20260624.6 src/data/icons.ts).
 */
fun HassSnapshot.attributeIcon(state: EntityState, attribute: String, value: String): String? {
    val entry = registries.entities[state.entityId]
    val platform = entry?.platform
    val translationKey = entry?.translationKey
    val platformIcon = if (platform != null && translationKey != null) {
        icons.platforms.obj(platform)?.obj(state.domain)?.obj(translationKey)?.attributeTranslations(attribute)
            ?.let { iconFromTranslations(value, it) }
    } else {
        null
    }
    return platformIcon ?: componentAttributeIcon(state, attribute, value)
}

private fun HassSnapshot.componentAttributeIcon(state: EntityState, attribute: String, value: String): String? {
    val componentIcons = icons.entityComponent.obj(state.domain) ?: return null
    val deviceClass = state.attributes.string("device_class")?.ifEmpty { null }
    val translations = deviceClass?.let { componentIcons.obj(it)?.attributeTranslations(attribute) }
        ?: componentIcons.obj(DEFAULT_TRANSLATION)?.attributeTranslations(attribute)
    return translations?.let { iconFromTranslations(value, it) }
}

private fun JsonObject.attributeTranslations(attribute: String) = obj("state_attributes")?.obj(attribute)

private const val DEFAULT_TRANSLATION = "_"
