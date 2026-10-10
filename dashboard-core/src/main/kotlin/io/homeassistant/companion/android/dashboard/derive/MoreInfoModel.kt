package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.action.toggleEntity
import io.homeassistant.companion.android.dashboard.display.formatEntityAttributeValue
import io.homeassistant.companion.android.dashboard.display.formatEntityState
import io.homeassistant.companion.android.dashboard.display.relativeTime
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.entity.entityContext
import io.homeassistant.companion.android.dashboard.feature.attributeName
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.moreinfo.AlarmMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.ClimateMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.FanMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.HumidifierMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.LightMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.LockMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.PositionMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.StateToggle
import io.homeassistant.companion.android.dashboard.moreinfo.WaterHeaterMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.alarmMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.climateMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.fanMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.humidifierMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.lightMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.lockMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.positionMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.stateToggle
import io.homeassistant.companion.android.dashboard.moreinfo.waterHeaterMoreInfo
import java.time.Instant
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
 * @property stateHeader whether the state and when it changed show above the controls (not for domains whose
 * controls show their own readings, like thermostats)
 * @property light a light's controls
 * @property climate a thermostat's controls
 * @property waterHeater a water heater's controls
 * @property humidifier a humidifier's controls
 * @property position a cover's or valve's controls
 * @property fan a fan's controls
 * @property lock a lock's controls
 * @property stateToggle the large on/off switch of a switch or input boolean
 * @property alarm an alarm panel's controls
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
    val stateHeader: Boolean,
    val light: LightMoreInfo?,
    val climate: ClimateMoreInfo?,
    val waterHeater: WaterHeaterMoreInfo?,
    val humidifier: HumidifierMoreInfo?,
    val position: PositionMoreInfo?,
    val fan: FanMoreInfo?,
    val lock: LockMoreInfo?,
    val stateToggle: StateToggle?,
    val alarm: AlarmMoreInfo?,
    val media: MediaControlModel?,
    val attributes: List<Pair<String, String>>,
)

/**
 * Derive the quick view of [entityId], or `null` when it does not exist. The name and context follow
 * `computeEntityPickerDisplay`, attributes the filter of `ha-attributes` (frontend@20260624.6
 * src/common/entity/compute_entity_name_display.ts, src/components/ha-attributes.ts); each domain's controls
 * port its more-info dialog's.
 */
fun HassSnapshot.moreInfoModel(entityId: String, now: Instant): MoreInfoModel? {
    val state = states[entityId] ?: return null
    val context = registries.entityContext(entityId)
    val deviceName = context.device?.deviceName()
    val entityName = entityName(state)
    val domain = state.domain
    val mediaCard = CardConfig(
        buildJsonObject {
            put("type", "tile")
            put("entity", entityId)
        },
    )
    val light = lightMoreInfo(state)
    val position = positionMoreInfo(state)
    val fan = fanMoreInfo(state)
    val stateToggle = if (domain in STATE_TOGGLE_DOMAINS) stateToggle(state, "mdi:power", "mdi:power-off") else null
    return MoreInfoModel(
        entityId = entityId,
        name = entityName ?: deviceName ?: entityId,
        context = listOfNotNull(context.area?.name?.trim()?.ifEmpty { null }, deviceName.takeIf { entityName != null })
            .joinToString(" › ").ifEmpty { null },
        icon = entityIcon(entityId),
        state = light?.state ?: position?.state ?: fan?.state ?: formatEntityState(state),
        // A change the server stamped ahead of this device's clock happened just now, not in the future
        changed = formats.relativeTime(minOf(Instant.ofEpochMilli((state.lastChanged * MILLIS).toLong()), now), now)
            .replaceFirstChar { it.uppercaseChar() },
        active = state.isActive(),
        toggle = toggleEntity(entityId).takeIf { domain in HEADER_TOGGLE_DOMAINS && state.state in ON_OFF },
        updatedAt = state.lastUpdated,
        stateHeader = domain !in NO_STATE_HEADER_DOMAINS,
        light = light,
        climate = climateMoreInfo(state),
        waterHeater = waterHeaterMoreInfo(state),
        humidifier = humidifierMoreInfo(state),
        position = position,
        fan = fan,
        lock = lockMoreInfo(state),
        stateToggle = stateToggle,
        alarm = alarmMoreInfo(state),
        media = if (domain == "media_player") mediaControlModel(mediaCard) else null,
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

/** The domains whose controls replace the state header (`more-info-climate` renders none). */
private val NO_STATE_HEADER_DOMAINS = setOf("climate", "humidifier", "water_heater")

/** The domains whose switch is in the header, until they have controls of their own. */
private val HEADER_TOGGLE_DOMAINS = setOf("automation", "remote", "siren")

/** The domains whose details lead with the large on/off switch (`more-info-switch`, `more-info-input_boolean`). */
private val STATE_TOGGLE_DOMAINS = setOf("input_boolean", "switch")

/** Port of `STATE_ATTRIBUTES` (src/data/entity/entity_attributes.ts). */
private val STATE_ATTRIBUTES = setOf(
    "entity_id", "assumed_state", "attribution", "custom_ui_more_info", "custom_ui_state_card", "device_class",
    "editable", "emulated_hue_name", "emulated_hue", "entity_picture", "event_types", "friendly_name",
    "haaska_hidden", "haaska_name", "icon", "initial_state", "last_reset", "restored", "state_class",
    "supported_features", "unit_of_measurement", "available_tones",
)
