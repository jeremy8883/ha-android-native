package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.action.retargeted
import io.homeassistant.companion.android.dashboard.derive.MoreInfoModel
import io.homeassistant.companion.android.dashboard.derive.RowDateTime
import io.homeassistant.companion.android.dashboard.derive.StateCard
import io.homeassistant.companion.android.dashboard.derive.dateTimeRowControl
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.string
import java.time.Instant

/**
 * The details of the domains drawn with controls of their own beyond the shared ones (frontend@20260624.6
 * src/dialogs/more-info/controls/more-info-<domain>.ts).
 */
sealed interface MoreInfoExtra {
    /** `more-info-sun`: the next rising and setting (soonest first), then the elevation and azimuth. */
    data class Sun(val rows: List<SunRow>) : MoreInfoExtra

    /** `more-info-date`, `-time`, `-datetime` and `-input_datetime`: the date and time fields. */
    data class DateTime(val control: RowDateTime) : MoreInfoExtra

    /** `more-info-image`: the image ([path] on the server), described by [name]. */
    data class Image(val path: String, val name: String) : MoreInfoExtra

    /**
     * `more-info-group`: the details of the members' domain when they share one with details ([inner], for the
     * stand-in entity [innerState] whose calls go to the group, [groupId]), then each member's state card.
     */
    data class Group(
        val groupId: String,
        val inner: MoreInfoModel?,
        val innerState: EntityState?,
        val members: List<StateCard>,
    ) : MoreInfoExtra {
        /** [action] of the inner details, sent to the group instead of the stand-in. */
        fun toGroup(action: CardAction): CardAction =
            innerState?.let { action.retargeted(it.entityId, groupId) } ?: action
    }

    /**
     * `more-info-person`: where they are on a map (their [picture] as the marker, else the initials of their name,
     * [markerLabel]), and for admins "Create zone" when they have coordinates.
     */
    data class Person(
        val location: MapLocation?,
        val createZone: String?,
        val picture: String? = null,
        val markerLabel: String = "",
    ) : MoreInfoExtra

    /** `more-info-script`: its state, description, queue, fields and run and cancel buttons. */
    data class Script(val script: ScriptMoreInfo) : MoreInfoExtra

    /** `more-info-camera`: the live stream, and the snapshot download (and what a failed one says). */
    data class Camera(val download: String, val downloadEnabled: Boolean, val downloadFailed: String = "") :
        MoreInfoExtra

    /** `more-info-weather`: the conditions, the readings and the forecast. */
    data class Weather(val weather: WeatherMoreInfo) : MoreInfoExtra

    /** `more-info-conversation`: a text chat with the agent [agentId]. */
    data class Conversation(val agentId: String) : MoreInfoExtra

    /** `more-info-configurator`: a legacy configuration request. */
    data class Configurator(val configurator: ConfiguratorMoreInfo) : MoreInfoExtra
}

/** A row of the sun's details: [name] (with when it happens, [relative], for the rising and setting) and [value]. */
data class SunRow(val name: String, val relative: String?, val value: String)

/** A point on a map, with the accuracy circle's radius in metres when known. */
data class MapLocation(val latitude: Double, val longitude: Double, val accuracy: Double?)

/** The extra details of [state], `null` for the domains without. */
fun HassSnapshot.moreInfoExtra(state: EntityState, now: Instant): MoreInfoExtra? = when (state.domain) {
    "sun" -> sunMoreInfo(state, now)
    "date", "time", "datetime", "input_datetime" -> dateTimeMoreInfo(state)
    "image" -> imageMoreInfo(state)
    "group" -> groupMoreInfo(state, now)
    "person" -> personMoreInfo(state)
    "script" -> scriptMoreInfo(state, now)?.let(MoreInfoExtra::Script)
    "camera" -> MoreInfoExtra.Camera(
        localize("ui.dialogs.more_info_control.camera.download_snapshot"),
        state.state != "unavailable",
        localize("ui.dialogs.more_info_control.camera.failed_to_download"),
    )
    "weather" -> MoreInfoExtra.Weather(weatherMoreInfo(state, now))
    "conversation" -> MoreInfoExtra.Conversation(state.entityId)
    "configurator" -> configuratorMoreInfo(state)?.let(MoreInfoExtra::Configurator)
    else -> null
}

/** The date and time fields; unavailable, upstream draws none (an input datetime's stay, disabled). */
private fun HassSnapshot.dateTimeMoreInfo(state: EntityState): MoreInfoExtra.DateTime? =
    state.takeIf { it.state != "unavailable" || it.domain == "input_datetime" }
        ?.let { dateTimeRowControl(it, "") }?.let { MoreInfoExtra.DateTime(it.copy(label = null)) }

/** Port of `computeImageUrl`: the image proxy with the entity's token, `null` without one. */
private fun imageMoreInfo(state: EntityState): MoreInfoExtra.Image? =
    state.attributes.string("access_token")?.let { token ->
        MoreInfoExtra.Image(
            "/api/image_proxy/${state.entityId}?token=$token&state=${state.state}",
            state.attributes.string("friendly_name") ?: state.entityId,
        )
    }
