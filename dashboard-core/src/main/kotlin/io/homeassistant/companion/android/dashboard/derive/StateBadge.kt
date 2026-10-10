package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.feature.number
import io.homeassistant.companion.android.dashboard.model.string

/**
 * An entity's badge as `state-badge` draws it (frontend@20260624.6 src/components/entity/state-badge.ts): its
 * [picture] when it has one (and no icon is configured), else its [icon] in [color], dimmed to [brightness].
 *
 * @property color its `_iconStyle` colour, `null` for the badge's own: the unavailable colour when [unavailable]
 * (`iconColorCSS`), else `--state-icon-color`
 * @property brightness the CSS `brightness()` factor of a dimmed light's icon, `null` when not dimmed
 */
data class StateBadge(
    val icon: String?,
    val picture: String?,
    val color: DisplayColor?,
    val brightness: Double?,
    val unavailable: Boolean,
)

/**
 * The badge of [state] with a configured [overrideIcon], coloured when [stateColor] (by default only lights are).
 */
fun HassSnapshot.stateBadge(state: EntityState, overrideIcon: String?, stateColor: Boolean?): StateBadge {
    val picture = (state.attributes.string("entity_picture_local") ?: state.attributes.string("entity_picture"))
        ?.ifEmpty { null }
        ?.takeIf { overrideIcon == null }
    val colored = stateColor ?: (state.domain == LIGHT)
    return StateBadge(
        icon = entityIcon(state.entityId, configIcon = overrideIcon),
        picture = picture,
        color = if (colored) badgeColor(state)?.withInactiveUnset() else null,
        brightness = iconBrightness(state).takeIf { colored },
        unavailable = state.state == STATE_UNAVAILABLE,
    )
}

/**
 * Port of `stateColorBrightness`: a light's icon dims with its brightness, to about half at the lowest; `null`
 * without a brightness (and for plants).
 */
fun iconBrightness(state: EntityState): Double? {
    val brightness = number(state.attributes["brightness"])?.takeIf { it != 0.0 && !it.isNaN() } ?: return null
    return if (state.domain == PLANT) null else (brightness + BRIGHTNESS_OFFSET) / BRIGHTNESS_DIVISOR / PERCENT
}

private const val LIGHT = "light"
private const val PLANT = "plant"
private const val BRIGHTNESS_OFFSET = 245.0
private const val BRIGHTNESS_DIVISOR = 5.0
private const val PERCENT = 100.0
