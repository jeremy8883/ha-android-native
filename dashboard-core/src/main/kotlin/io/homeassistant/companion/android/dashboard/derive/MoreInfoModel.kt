package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.display.attributeValueDisplay
import io.homeassistant.companion.android.dashboard.display.formatEntityState
import io.homeassistant.companion.android.dashboard.display.relativeTime
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.entity.entityContext
import io.homeassistant.companion.android.dashboard.feature.attributeName
import io.homeassistant.companion.android.dashboard.history.showsHistory
import io.homeassistant.companion.android.dashboard.logbook.showsLogbook
import io.homeassistant.companion.android.dashboard.model.jsKeys
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
import io.homeassistant.companion.android.dashboard.moreinfo.MoreInfoExtra
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
import io.homeassistant.companion.android.dashboard.moreinfo.moreInfoExtra
import io.homeassistant.companion.android.dashboard.moreinfo.positionMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.remoteActivity
import io.homeassistant.companion.android.dashboard.moreinfo.sirenMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.stateToggle
import io.homeassistant.companion.android.dashboard.moreinfo.timerMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.updateMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.vacuumMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.waterHeaterMoreInfo
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The native quick view of an entity, shown before (or instead of) upstream's full more-info dialog.
 *
 * @property context the area and device above the name, for example "Kitchen ▸ Kitchen speaker" (the dialog's
 * breadcrumb)
 * @property changed when the state last changed, as a relative time
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
 * @property attributes the displayable attributes, as (name, formatted value)
 * @property fullHeight whether the content fills the dialog (`ha-more-info-info`'s `isFullHeight`: the newer
 * details and `DOMAINS_FULL_HEIGHT_MORE_INFO`), so a thermostat's dial is centred in the room left and an update's
 * buttons sit at its bottom
 * @property stateCard the row the dialog leads with (`state-card-content`), for the domains without the newer
 * details (`DOMAINS_WITH_NEW_MORE_INFO`)
 * @property inlineHistory whether the history and activity follow the controls (`ha-more-info-info`: domains
 * without details of their own); for the others they are only in the history view
 * @property inlineAttributes whether the attributes follow them (`more-info-default`); else only in the details view
 * @property historyButton whether the toolbar has the history button (`_shouldShowHistory`: the domains whose
 * history isn't inline, when it has a history or activity)
 * @property extra the details of the domains with controls of their own beyond the shared ones (sun, script,
 * weather, group...)
 * @property details the details view's state entries (`ha-more-info-details`), as (label, value)
 * @property detailAttributes the details view's attributes: those shown elsewhere too, then the built-in ones, the
 * supported features by name
 */
data class MoreInfoModel(
    val entityId: String,
    val name: String,
    val context: String?,
    val icon: String?,
    val state: String,
    val changed: String,
    val active: Boolean,
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
    val attributes: List<Pair<String, String>>,
    val fullHeight: Boolean = false,
    val stateCard: StateCard? = null,
    val inlineHistory: Boolean = true,
    val inlineAttributes: Boolean = true,
    val historyButton: Boolean = false,
    val extra: MoreInfoExtra? = null,
    val details: List<Pair<String, String>> = emptyList(),
    val detailAttributes: List<Pair<String, String>> = emptyList(),
)

/**
 * Port of `state-card-content` in the dialog: the entity's [badge] (coloured by its state), its own [name] and
 * when it [changed] (in the dialog's own card), then what its card shows on the right ([right]: its state, or a switch, buttons or an input).
 */
data class StateCard(
    val entityId: String,
    val badge: StateBadge,
    val name: String,
    val changed: String?,
    val right: StateCardRight,
)

/**
 * Derive the quick view of [entityId], or `null` when it does not exist. The name and context follow
 * `computeEntityPickerDisplay`, attributes the filter of `ha-attributes` (frontend@20260624.6
 * src/common/entity/compute_entity_name_display.ts, src/components/ha-attributes.ts); each domain's controls
 * port its more-info dialog's.
 */
fun HassSnapshot.moreInfoModel(entityId: String, now: Instant): MoreInfoModel? {
    val state = states[entityId] ?: return null
    val (name, context) = entityNameParts(state)
    val domain = state.domain
    val light = lightMoreInfo(state)
    val position = positionMoreInfo(state)
    val fan = fanMoreInfo(state)
    val vacuum = vacuumMoreInfo(state)
    val stateToggle = if (domain in STATE_TOGGLE_DOMAINS) stateToggle(state, "mdi:power", "mdi:power-off") else null
    val extra = moreInfoExtra(state, now)
    // A script's header shows whether it runs, and when it last ran (`stateOverride`, `changedOverride`)
    val script = (extra as? MoreInfoExtra.Script)?.script
    return MoreInfoModel(
        entityId = entityId,
        name = name,
        context = context.joinToString(" ▸ ").ifEmpty { null },
        icon = entityIcon(entityId),
        state = headerState(
            state,
            listOfNotNull(script?.state, light?.state, position?.state, fan?.state, vacuum?.state),
        ),
        // A change the server stamped ahead of this device's clock happened just now, not in the future
        changed = headerChanged(state, script?.changed, now),
        active = state.isActive(),
        updatedAt = state.lastUpdated,
        stateHeader = domain in STATE_HEADER_DOMAINS,
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
        attributes = displayAttributes(state).map { attributeName(state, it) to attributeValueDisplay(state, it) },
        fullHeight = domain in FULL_HEIGHT_DOMAINS || showsNewMoreInfo(state),
        stateCard = if (!showsNewMoreInfo(state) && domain !in NO_INFO_DOMAINS) stateCard(state, now) else null,
        inlineHistory = domain !in WITH_MORE_INFO_DOMAINS,
        inlineAttributes = domain !in WITH_MORE_INFO_DOMAINS && domain !in HIDE_DEFAULT_MORE_INFO_DOMAINS,
        historyButton = domain in WITH_MORE_INFO_DOMAINS &&
            domain !in NO_HISTORY_DOMAINS &&
            (showsHistory(entityId) || showsLogbook(entityId)),
        extra = extra,
        details = detailEntries(state),
        detailAttributes = detailAttributes(state),
    )
}

/** The header's state: the first of the domain's own [readings], else the formatted state. */
private fun HassSnapshot.headerState(state: EntityState, readings: List<String>): String =
    readings.firstOrNull() ?: formatEntityState(state)

/** When the header says the entity changed: a script's last run, else its state's change at [now]. */
private fun HassSnapshot.headerChanged(state: EntityState, script: String?, now: Instant): String = script
    ?: formats.relativeTime(minOf(Instant.ofEpochMilli((state.lastChanged * MILLIS).toLong()), now), now)
        .replaceFirstChar { it.uppercaseChar() }

/**
 * Port of `computeShowNewMoreInfo`: the domains with the newer details, and a group whose members share one of them
 * (its details are theirs, without the group's state card).
 */
private fun showsNewMoreInfo(state: EntityState): Boolean {
    if (state.domain != "group") return state.domain in NEW_MORE_INFO_DOMAINS
    val domains = (state.attributes["entity_id"] as? JsonArray).orEmpty()
        .mapNotNull { (it as? JsonPrimitive)?.content?.substringBefore('.') }.distinct()
    return domains.singleOrNull()?.let { it != "group" && it in NEW_MORE_INFO_DOMAINS } == true
}

/**
 * The state card of [state], as the dialog leads with it ([inDialog], with when it changed) and a group lists its
 * members (without).
 */
fun HassSnapshot.stateCard(state: EntityState, now: Instant, inDialog: Boolean = true): StateCard = StateCard(
    entityId = state.entityId,
    badge = stateBadge(state, overrideIcon = null, stateColor = true),
    name = entityNameDisplay(state, JsonObject(mapOf("type" to JsonPrimitive("entity")))),
    changed = formats.relativeTime(minOf(Instant.ofEpochMilli((state.lastChanged * MILLIS).toLong()), now), now)
        .replaceFirstChar { it.uppercaseChar() }.takeIf { inDialog },
    right = stateCardRight(state, now),
)

/** Port of `_getDetailData`'s state entries: the translated and raw state, and when it changed and updated. */
private fun HassSnapshot.detailEntries(state: EntityState): List<Pair<String, String>> {
    val strings = "ui.dialogs.more_info_control"
    val at = { seconds: Double -> formats.dateTimeWithSeconds(Instant.ofEpochMilli((seconds * MILLIS).toLong())) }
    return listOf(
        localize("$strings.translated") to formatEntityState(state),
        localize("$strings.raw") to state.state,
        localize("$strings.last_changed") to at(state.lastChanged),
        localize("$strings.last_updated") to at(state.lastUpdated),
    )
}

/**
 * The name and context ("Kitchen › Kitchen speaker") of [state] as entity pickers show them (the details' header,
 * the speakers to group): its name, else its device's; under it its area, and its device when it has a name of its
 * own. Port of `computeEntityPickerDisplay`.
 */
fun HassSnapshot.entityPickerDisplay(state: EntityState): Pair<String, String?> {
    val (name, context) = entityNameParts(state)
    return name to context.joinToString(" › ").ifEmpty { null }
}

/** The name of [state] and its context (its area, and its device when it has a name of its own). */
private fun HassSnapshot.entityNameParts(state: EntityState): Pair<String, List<String>> {
    val context = registries.entityContext(state.entityId)
    val deviceName = context.device?.deviceName()
    val entityName = entityName(state)
    return (entityName ?: deviceName ?: state.entityId) to
        listOfNotNull(context.area?.name?.trim()?.ifEmpty { null }, deviceName.takeIf { entityName != null })
}

/** Port of `ha-attributes`' filter (`computeShownAttributes`): attributes that are not internal, nor shown elsewhere. */
internal fun displayAttributes(state: EntityState): List<String> {
    val domainFilters = if (state.domain == "sensor" && state.attributes.string("device_class") == "enum") {
        listOf("options")
    } else {
        emptyList()
    }
    return state.attributes.jsKeys().filter { it !in STATE_ATTRIBUTES && it !in domainFilters }
}

private const val MILLIS = 1000.0

/** Port of `DOMAINS_FULL_HEIGHT_MORE_INFO`. */
private val FULL_HEIGHT_DOMAINS = setOf("update")

/** Port of `DOMAINS_MORE_INFO_NO_HISTORY`. */
private val NO_HISTORY_DOMAINS = setOf("camera", "configurator")

/** Port of `DOMAINS_NO_INFO`: no state card. */
private val NO_INFO_DOMAINS = setOf("camera", "configurator")

/** Port of `DOMAINS_WITH_NEW_MORE_INFO`: their controls replace the state card. */
private val NEW_MORE_INFO_DOMAINS = setOf(
    "alarm_control_panel", "cover", "climate", "conversation", "fan", "humidifier", "input_boolean", "lawn_mower",
    "light", "lock", "siren", "script", "switch", "vacuum", "valve", "water_heater", "weather", "media_player",
)

/** The new details that lead with `ha-more-info-state-header` (the others show their own readings). */
private val STATE_HEADER_DOMAINS = setOf(
    "alarm_control_panel", "cover", "fan", "input_boolean", "lawn_mower", "light", "lock", "script", "siren",
    "switch", "vacuum", "valve",
)

/** Port of `DOMAINS_WITH_MORE_INFO`: details of their own, whose history is in its own view. */
internal val WITH_MORE_INFO_DOMAINS = setOf(
    "alarm_control_panel", "automation", "camera", "climate", "configurator", "conversation", "counter", "cover",
    "date", "datetime", "fan", "group", "humidifier", "image", "input_boolean", "input_datetime", "lawn_mower",
    "light", "lock", "media_player", "person", "remote", "script", "scene", "siren", "sun", "switch", "time",
    "timer", "update", "vacuum", "valve", "water_heater", "weather",
)

/** Port of `DOMAINS_HIDE_DEFAULT_MORE_INFO`: no attributes in the main view. */
private val HIDE_DEFAULT_MORE_INFO_DOMAINS = setOf(
    "input_number",
    "input_select",
    "input_text",
    "number",
    "scene",
    "select",
    "text",
    "update",
)

/** The domains whose details lead with the large on/off switch (`more-info-switch`, `more-info-input_boolean`). */
private val STATE_TOGGLE_DOMAINS = setOf("input_boolean", "switch")

/** Port of `STATE_ATTRIBUTES` (src/data/entity/entity_attributes.ts). */
private val STATE_ATTRIBUTES = setOf(
    "entity_id", "assumed_state", "attribution", "custom_ui_more_info", "custom_ui_state_card", "device_class",
    "editable", "emulated_hue_name", "emulated_hue", "entity_picture", "event_types", "friendly_name",
    "haaska_hidden", "haaska_name", "icon", "initial_state", "last_reset", "restored", "state_class",
    "supported_features", "unit_of_measurement", "available_tones",
)
