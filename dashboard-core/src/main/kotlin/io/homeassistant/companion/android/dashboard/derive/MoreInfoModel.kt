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
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.moreinfo.AlarmMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.AutomationMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.ClimateMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.FanMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.HumidifierMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.LawnMowerMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.LightMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.LockMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.MediaPlayerMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.MoreInfoAction
import io.homeassistant.companion.android.dashboard.moreinfo.PositionMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.SelectMenu
import io.homeassistant.companion.android.dashboard.moreinfo.SirenMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.StateToggle
import io.homeassistant.companion.android.dashboard.moreinfo.TimerMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.UpdateMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.VacuumMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.WaterHeaterMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.alarmMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.automationMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.climateMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.counterActions
import io.homeassistant.companion.android.dashboard.moreinfo.fanMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.humidifierMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.lawnMowerMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.lightMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.lockMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.mediaPlayerMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.positionMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.remoteActivity
import io.homeassistant.companion.android.dashboard.moreinfo.sirenMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.stateToggle
import io.homeassistant.companion.android.dashboard.moreinfo.timerMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.updateMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.vacuumMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.waterHeaterMoreInfo
import java.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

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
 * @property mediaPlayer a media player's controls
 * @property vacuum a vacuum's controls
 * @property lawnMower a lawn mower's controls
 * @property siren a siren's controls
 * @property counter a counter's buttons
 * @property automation an automation's last run and run button
 * @property timer a timer's duration and buttons
 * @property remote a remote's activity menu
 * @property update an update's versions, notes and buttons
 * @property input the entity's row control (a number, select, text, date or time), which upstream's dialog leads with
 * @property attributes the displayable attributes, as (name, formatted value)
 * @property fullHeight whether the details open at the screen's full height (`DOMAINS_FULL_HEIGHT_MORE_INFO`)
 * @property stateCard the row upstream's dialog leads with instead of the state header (`state-card-content`), for
 * the domains ported that way (updates for now)
 * @property sections whether the history, activity and attributes follow the controls; upstream keeps them in other
 * views of the dialog, which the native details show inline except for [stateCard] domains
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
    val mediaPlayer: MediaPlayerMoreInfo?,
    val vacuum: VacuumMoreInfo?,
    val lawnMower: LawnMowerMoreInfo?,
    val siren: SirenMoreInfo?,
    val counter: List<MoreInfoAction>?,
    val automation: AutomationMoreInfo?,
    val timer: TimerMoreInfo?,
    val remote: SelectMenu?,
    val update: UpdateMoreInfo?,
    val input: RowControl?,
    val attributes: List<Pair<String, String>>,
    val fullHeight: Boolean = false,
    val stateCard: StateCard? = null,
    val sections: Boolean = true,
)

/**
 * Port of `state-card-display` in the dialog: the entity's [badge] (coloured by its state), its own [name] and
 * when it [changed], with its formatted [state] on the right.
 */
data class StateCard(val badge: StateBadge, val name: String, val changed: String, val state: String)

/**
 * Derive the quick view of [entityId], or `null` when it does not exist. The name and context follow
 * `computeEntityPickerDisplay`, attributes the filter of `ha-attributes` (frontend@20260624.6
 * src/common/entity/compute_entity_name_display.ts, src/components/ha-attributes.ts); each domain's controls
 * port its more-info dialog's.
 */
fun HassSnapshot.moreInfoModel(entityId: String, now: Instant): MoreInfoModel? {
    val state = states[entityId] ?: return null
    val (name, context) = entityPickerDisplay(state)
    val domain = state.domain
    val light = lightMoreInfo(state)
    val position = positionMoreInfo(state)
    val fan = fanMoreInfo(state)
    val vacuum = vacuumMoreInfo(state)
    val stateToggle = if (domain in STATE_TOGGLE_DOMAINS) stateToggle(state, "mdi:power", "mdi:power-off") else null
    return MoreInfoModel(
        entityId = entityId,
        name = name,
        context = context,
        icon = entityIcon(entityId),
        state = light?.state ?: position?.state ?: fan?.state ?: vacuum?.state ?: formatEntityState(state),
        // A change the server stamped ahead of this device's clock happened just now, not in the future
        changed = formats.relativeTime(minOf(Instant.ofEpochMilli((state.lastChanged * MILLIS).toLong()), now), now)
            .replaceFirstChar { it.uppercaseChar() },
        active = state.isActive(),
        toggle = toggleEntity(entityId).takeIf { domain in HEADER_TOGGLE_DOMAINS && state.state in ON_OFF },
        updatedAt = state.lastUpdated,
        stateHeader = domain !in NO_STATE_HEADER_DOMAINS && domain !in STATE_CARD_DOMAINS,
        light = light,
        climate = climateMoreInfo(state),
        waterHeater = waterHeaterMoreInfo(state),
        humidifier = humidifierMoreInfo(state),
        position = position,
        fan = fan,
        lock = lockMoreInfo(state),
        stateToggle = stateToggle,
        alarm = alarmMoreInfo(state),
        mediaPlayer = mediaPlayerMoreInfo(state),
        vacuum = vacuum,
        lawnMower = lawnMowerMoreInfo(state),
        siren = sirenMoreInfo(state),
        counter = counterActions(state),
        automation = automationMoreInfo(state, now),
        timer = timerMoreInfo(state),
        remote = remoteActivity(state),
        update = updateMoreInfo(state),
        input = if (domain in INPUT_DOMAINS) inputRowControl(state, name) else null,
        attributes = displayAttributes(state).map { attributeName(state, it) to formatEntityAttributeValue(state, it) },
        fullHeight = domain in FULL_HEIGHT_DOMAINS,
        stateCard = if (domain in STATE_CARD_DOMAINS) stateCard(state, now) else null,
        sections = domain !in STATE_CARD_DOMAINS,
    )
}

private fun HassSnapshot.stateCard(state: EntityState, now: Instant) = StateCard(
    badge = stateBadge(state, overrideIcon = null, stateColor = true),
    name = entityNameDisplay(state, JsonObject(mapOf("type" to JsonPrimitive("entity")))),
    changed = formats.relativeTime(minOf(Instant.ofEpochMilli((state.lastChanged * MILLIS).toLong()), now), now)
        .replaceFirstChar { it.uppercaseChar() },
    state = formatEntityState(state),
)

/**
 * The name and context ("Kitchen › Kitchen speaker") of [state] as entity pickers show them (the details' header,
 * the speakers to group): its name, else its device's; under it its area, and its device when it has a name of its
 * own. Port of `computeEntityPickerDisplay`.
 */
fun HassSnapshot.entityPickerDisplay(state: EntityState): Pair<String, String?> {
    val context = registries.entityContext(state.entityId)
    val deviceName = context.device?.deviceName()
    val entityName = entityName(state)
    return (entityName ?: deviceName ?: state.entityId) to
        listOfNotNull(context.area?.name?.trim()?.ifEmpty { null }, deviceName.takeIf { entityName != null })
            .joinToString(" › ").ifEmpty { null }
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

/** The domains whose details lead with their row's input (`state-card-content`). */
private val INPUT_DOMAINS = setOf(
    "input_number", "number", "input_select", "select", "input_text", "text", "input_datetime", "date", "time",
    "datetime",
)

/** Port of `DOMAINS_FULL_HEIGHT_MORE_INFO`. */
private val FULL_HEIGHT_DOMAINS = setOf("update")

/** The domains whose details lead with `state-card-content`, as upstream's, rather than the native state header. */
private val STATE_CARD_DOMAINS = setOf("update")

/** The domains whose controls replace the state header (`more-info-climate` renders none). */
private val NO_STATE_HEADER_DOMAINS = setOf("climate", "humidifier", "media_player", "water_heater")

/** The domains whose switch is in the header, until they have controls of their own. */
private val HEADER_TOGGLE_DOMAINS = setOf("automation", "remote")

/** The domains whose details lead with the large on/off switch (`more-info-switch`, `more-info-input_boolean`). */
private val STATE_TOGGLE_DOMAINS = setOf("input_boolean", "switch")

/** Port of `STATE_ATTRIBUTES` (src/data/entity/entity_attributes.ts). */
private val STATE_ATTRIBUTES = setOf(
    "entity_id", "assumed_state", "attribution", "custom_ui_more_info", "custom_ui_state_card", "device_class",
    "editable", "emulated_hue_name", "emulated_hue", "entity_picture", "event_types", "friendly_name",
    "haaska_hidden", "haaska_name", "icon", "initial_state", "last_reset", "restored", "state_class",
    "supported_features", "unit_of_measurement", "available_tones",
)
