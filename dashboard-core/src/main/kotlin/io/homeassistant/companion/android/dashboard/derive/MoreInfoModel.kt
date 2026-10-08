package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.action.toggleEntity
import io.homeassistant.companion.android.dashboard.display.formatEntityAttributeValue
import io.homeassistant.companion.android.dashboard.display.formatEntityState
import io.homeassistant.companion.android.dashboard.display.relativeTime
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.entity.entityContext
import io.homeassistant.companion.android.dashboard.feature.TileFeature
import io.homeassistant.companion.android.dashboard.feature.attributeName
import io.homeassistant.companion.android.dashboard.feature.tileFeatures
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.string
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The native quick view of an entity, shown before (or instead of) upstream's full more-info dialog.
 *
 * @property context the area and device, for example "Kitchen › Kitchen speaker"
 * @property changed when the state last changed, as a relative time
 * @property toggle a switch for entities that turn on and off
 * @property updatedAt when the entity's state object last changed (epoch seconds), so a switch knows when the server
 * answered
 * @property controls the entity's main controls, as tile features
 * @property media the media controls of a media player
 * @property attributes the displayable attributes, as (name, formatted value)
 */
data class MoreInfoModel(
    val entityId: String,
    val name: String,
    val context: String?,
    val icon: String?,
    val state: String,
    val changed: String,
    val active: Boolean,
    val toggle: CardAction.CallService?,
    val updatedAt: Double,
    val controls: List<TileFeature>,
    val media: MediaControlModel?,
    val attributes: List<Pair<String, String>>,
)

/**
 * Derive the quick view of [entityId], or `null` when it does not exist. The name and context follow
 * `computeEntityPickerDisplay`, attributes the filter of `ha-attributes` (frontend@20260624.6
 * src/common/entity/compute_entity_name_display.ts, src/components/ha-attributes.ts); the controls reuse the card
 * features each domain's more-info dialog leads with.
 */
fun HassSnapshot.moreInfoModel(entityId: String, now: Instant): MoreInfoModel? {
    val state = states[entityId] ?: return null
    val context = registries.entityContext(entityId)
    val deviceName = context.device?.deviceName()
    val entityName = entityName(state)
    val domain = state.domain
    val features = MORE_INFO_FEATURES[domain].orEmpty()
    val controlCard = CardConfig(
        buildJsonObject {
            put("type", "tile")
            put("entity", entityId)
            put("features", JsonArray(features.map { JsonObject(mapOf("type" to JsonPrimitive(it))) }))
        },
    )
    return MoreInfoModel(
        entityId = entityId,
        name = entityName ?: deviceName ?: entityId,
        context = listOfNotNull(context.area?.name?.trim()?.ifEmpty { null }, deviceName.takeIf { entityName != null })
            .joinToString(" › ").ifEmpty { null },
        icon = entityIcon(entityId),
        state = formatEntityState(state),
        changed = formats.relativeTime(Instant.ofEpochMilli((state.lastChanged * MILLIS).toLong()), now)
            .replaceFirstChar { it.uppercaseChar() },
        active = state.isActive(),
        toggle = toggleEntity(entityId).takeIf { domain in TOGGLE_DOMAINS && state.state in ON_OFF },
        updatedAt = state.lastUpdated,
        controls = tileFeatures(controlCard),
        media = if (domain == "media_player") mediaControlModel(CardConfig(controlCard.json)) else null,
        attributes = displayAttributes(state).map { attributeName(state, it) to formatEntityAttributeValue(state, it) },
    )
}

/** Port of `ha-attributes`' filter: attributes that are not internal, nor shown elsewhere. */
private fun displayAttributes(state: EntityState): List<String> {
    val domainFilters = if (state.domain == "sensor" && state.attributes.string("device_class") == "enum") {
        listOf("options")
    } else {
        emptyList()
    }
    return state.attributes.keys.filter { it !in STATE_ATTRIBUTES && it !in domainFilters }
}

private const val MILLIS = 1000.0
private val ON_OFF = setOf("on", "off")
private val TOGGLE_DOMAINS = setOf(
    "automation",
    "fan",
    "humidifier",
    "input_boolean",
    "light",
    "remote",
    "siren",
    "switch",
)

/** The control each domain's more-info dialog leads with, as card features. */
private val MORE_INFO_FEATURES = mapOf(
    "light" to listOf("light-brightness"),
    "cover" to listOf("cover-open-close"),
    "climate" to listOf("target-temperature"),
    "water_heater" to listOf("target-temperature"),
    "fan" to listOf("fan-speed"),
    "lock" to listOf("lock-commands"),
    "alarm_control_panel" to listOf("alarm-modes"),
)

/** Port of `STATE_ATTRIBUTES` (src/data/entity/entity_attributes.ts). */
private val STATE_ATTRIBUTES = setOf(
    "entity_id", "assumed_state", "attribution", "custom_ui_more_info", "custom_ui_state_card", "device_class",
    "editable", "emulated_hue_name", "emulated_hue", "entity_picture", "event_types", "friendly_name",
    "haaska_hidden", "haaska_name", "icon", "initial_state", "last_reset", "restored", "state_class",
    "supported_features", "unit_of_measurement", "available_tones",
)
