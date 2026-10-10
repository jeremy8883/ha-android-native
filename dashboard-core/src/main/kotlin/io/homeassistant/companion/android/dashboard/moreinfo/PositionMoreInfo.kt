package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.DisplayColor
import io.homeassistant.companion.android.dashboard.derive.entityIcon
import io.homeassistant.companion.android.dashboard.derive.isActive
import io.homeassistant.companion.android.dashboard.derive.stateColor
import io.homeassistant.companion.android.dashboard.derive.supportsFeature
import io.homeassistant.companion.android.dashboard.display.formatEntityAttributeValue
import io.homeassistant.companion.android.dashboard.display.formatEntityState
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.feature.ValueService
import io.homeassistant.companion.android.dashboard.feature.assumedState
import io.homeassistant.companion.android.dashboard.feature.attributeName
import io.homeassistant.companion.android.dashboard.feature.available
import io.homeassistant.companion.android.dashboard.feature.entityData

// Ports of `more-info-cover` and `more-info-valve` (frontend@20260624.6 src/dialogs/more-info/controls/) with their
// state controls (src/state-control/cover/, src/state-control/valve/) and helpers (src/data/cover.ts, valve.ts).
// Their buttons are in PositionButtons.kt.

/**
 * What a cover's or valve's details show: the state with the position ("Open · 40%"), and either the position
 * sliders or the buttons (a switch for one that only opens and closes), with a pair of buttons to switch between
 * them when it has both.
 *
 * @property position the position slider, open at the top
 * @property tilt a cover's tilt slider, beside the position's
 * @property toggle the tall switch of one that only opens and closes
 * @property buttons the open, stop and close buttons
 * @property modeLabels the labels of the position and buttons switches, when it has both
 * @property positionFirst whether the sliders show first, rather than the buttons
 */
data class PositionMoreInfo(
    val state: String,
    val position: ControlSlider?,
    val tilt: ControlSlider?,
    val toggle: StateToggle?,
    val buttons: PositionButtons?,
    val modeLabels: Pair<String, String>?,
    val positionFirst: Boolean,
)

/** What a cover or valve supports, as its details read it. */
private class PositionSupport(state: EntityState) {
    private val cover = state.domain == COVER
    val position = state.supportsFeature(FEATURE_SET_POSITION)
    val tiltPosition = cover && state.supportsFeature(FEATURE_SET_TILT_POSITION)
    val openClose = listOf(FEATURE_OPEN, FEATURE_CLOSE, FEATURE_STOP).any { state.supportsFeature(it) }
    val tilt =
        cover && listOf(FEATURE_OPEN_TILT, FEATURE_CLOSE_TILT, FEATURE_STOP_TILT).any { state.supportsFeature(it) }
    val anyPosition = position || tiltPosition
    private val openAndClose = state.supportsFeature(FEATURE_OPEN) && state.supportsFeature(FEATURE_CLOSE)
    val openCloseOnly = openAndClose && !state.supportsFeature(FEATURE_STOP) && !tilt && !anyPosition
    val buttons = !openCloseOnly && (openClose || tilt)
}

/** The details of a cover or a valve, or `null` for another entity. */
fun HassSnapshot.positionMoreInfo(state: EntityState): PositionMoreInfo? {
    if (state.domain != COVER && state.domain != VALVE) return null
    val supports = PositionSupport(state)
    val modes = "ui.dialogs.more_info_control.${state.domain}.switch_mode"
    return PositionMoreInfo(
        state = positionStateDisplay(state),
        position = if (supports.position) positionSlider(state) else null,
        tilt = if (supports.tiltPosition) tiltSlider(state) else null,
        toggle = if (supports.openCloseOnly) openCloseToggle(state) else null,
        buttons = if (supports.buttons) positionButtons(state) else null,
        modeLabels = (localize("$modes.position") to localize("$modes.button"))
            .takeIf { supports.anyPosition && (supports.openClose || supports.tilt) },
        positionFirst = supports.anyPosition,
    )
}

/** The state header: the state, with the position when open part way (`computeCoverPositionStateDisplay`). */
private fun HassSnapshot.positionStateDisplay(state: EntityState): String {
    val position = state.attributes.numberOrNull(CURRENT_POSITION)
        ?: state.attributes.numberOrNull(CURRENT_TILT_POSITION).takeIf { state.domain == COVER }
    val shown = position?.takeIf { state.isActive() && it != 0.0 && it != FULL }
        ?.let { formatEntityAttributeValue(state, CURRENT_POSITION, jsonNumber(Math.round(it).toDouble())) }
    return listOfNotNull(formatEntityState(state), shown).joinToString(" · ")
}

/** The position slider: filled from the top for a cover (`mode="end"`), from the bottom for a valve. */
private fun HassSnapshot.positionSlider(state: EntityState): ControlSlider {
    val cover = state.domain == COVER
    val color = sliderColor(state)
    return ControlSlider(
        label = attributeName(state, CURRENT_POSITION),
        value = state.attributes.numberOrNull(CURRENT_POSITION)?.let { Math.round(it).toDouble() },
        min = 0.0,
        max = FULL,
        step = 1.0,
        unit = "%",
        mode = if (cover) SliderMode.End else SliderMode.Start,
        inverted = false,
        showHandle = true,
        enabled = state.available(),
        color = color,
        background = SliderBackground.Tint(color, BACKGROUND_OPACITY),
        service = ValueService(
            state.domain,
            if (cover) "set_cover_position" else "set_valve_position",
            entityData(state),
            "position",
        ),
    )
}

/** A cover's tilt slider: a cursor over stripes. */
private fun HassSnapshot.tiltSlider(state: EntityState): ControlSlider {
    val color = sliderColor(state)
    return ControlSlider(
        label = attributeName(state, CURRENT_TILT_POSITION),
        value = state.attributes.numberOrNull(CURRENT_TILT_POSITION)?.let { Math.round(it).toDouble() },
        min = 0.0,
        max = FULL,
        step = 1.0,
        unit = "%",
        mode = SliderMode.Cursor,
        inverted = false,
        showHandle = false,
        enabled = state.available(),
        color = color,
        background = SliderBackground.Stripes(color, BACKGROUND_OPACITY),
        service = ValueService(COVER, "set_cover_tilt_position", entityData(state), "tilt_position"),
    )
}

/**
 * The sliders' colour: the state's, where a cover's sliders set its inactive colour to the open one (so a closed
 * cover's slider shows the open colour unless the theme has one for closed).
 */
private fun sliderColor(state: EntityState): DisplayColor? {
    val color = stateColor(state)
    val open = stateColor(state, OPEN)
    return if (state.domain == COVER && color is DisplayColor.State && open != null) {
        color.copy(overrides = mapOf("state-cover-inactive-color" to open))
    } else {
        color
    }
}

/** Port of `ha-state-control-cover-toggle` and `-valve-toggle`: open at the top, with the state icons. */
private fun HassSnapshot.openCloseToggle(state: EntityState): StateToggle {
    val domain = state.domain
    val on = state.state in setOf(OPEN, "closing", "opening")
    val strings = "ui.card.$domain"
    val noun = if (domain == COVER) "cover" else "valve"
    return StateToggle(
        label = localize(if (on) "$strings.close_$noun" else "$strings.open_$noun"),
        checked = on,
        offActive = state.state == CLOSED,
        showHandle = false,
        enabled = state.available(),
        buttons = state.assumedState() || state.state == UNKNOWN,
        onColor = stateColor(state, OPEN),
        offColor = stateColor(state, CLOSED),
        onIcon = entityIcon(state.entityId, stateValue = OPEN).orEmpty(),
        offIcon = entityIcon(state.entityId, stateValue = CLOSED).orEmpty(),
        turnOnLabel = localize("$strings.open_$noun"),
        turnOffLabel = localize("$strings.close_$noun"),
        turnOn = CardAction.CallService(domain, "open_$noun", entityData(state), target = null),
        turnOff = CardAction.CallService(domain, "close_$noun", entityData(state), target = null),
    )
}

internal const val COVER = "cover"
internal const val VALVE = "valve"
internal const val CURRENT_POSITION = "current_position"
internal const val CURRENT_TILT_POSITION = "current_tilt_position"
internal const val FULL = 100.0
private const val OPEN = "open"
private const val CLOSED = "closed"
private const val UNKNOWN = "unknown"
private const val BACKGROUND_OPACITY = 0.2f
internal const val FEATURE_OPEN = 1
internal const val FEATURE_CLOSE = 2
internal const val FEATURE_SET_POSITION = 4
internal const val FEATURE_STOP = 8
internal const val FEATURE_OPEN_TILT = 16
internal const val FEATURE_CLOSE_TILT = 32
internal const val FEATURE_STOP_TILT = 64
internal const val FEATURE_SET_TILT_POSITION = 128
