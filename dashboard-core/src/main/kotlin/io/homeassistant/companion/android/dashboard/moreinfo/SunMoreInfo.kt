package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.display.formatEntityAttributeValue
import io.homeassistant.companion.android.dashboard.display.parseJsDate
import io.homeassistant.companion.android.dashboard.display.relativeTime
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.string
import java.time.Instant

/**
 * Port of `more-info-sun` (frontend@20260624.6 src/dialogs/more-info/controls/more-info-sun.ts): the next rising and
 * setting at [now], soonest first, each with when it happens and its time, then the elevation and azimuth.
 */
internal fun HassSnapshot.sunMoreInfo(state: EntityState, now: Instant): MoreInfoExtra.Sun {
    val strings = "ui.dialogs.more_info_control.sun"
    val at = { key: String -> state.attributes.string(key)?.let { parseJsDate(it, formats.zone) } }
    val rising = at("next_rising")
    val setting = at("next_setting")
    val event = { key: String, time: Instant? ->
        SunRow(
            localize("$strings.$key"),
            time?.let { formats.relativeTime(it, now) },
            time?.let { formats.time(it) } ?: localize("ui.panel.lovelace.components.timestamp-display.invalid"),
        )
    }
    val events = listOf(event("rising", rising), event("setting", setting))
    val ordered = if (rising != null && setting != null && rising > setting) events.reversed() else events
    return MoreInfoExtra.Sun(
        ordered + listOf(
            SunRow(localize("$strings.elevation"), null, formatEntityAttributeValue(state, "elevation")),
            SunRow(localize("$strings.azimuth"), null, formatEntityAttributeValue(state, "azimuth")),
        ),
    )
}
