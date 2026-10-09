package io.homeassistant.companion.android.dashboard.logbook

import io.homeassistant.companion.android.dashboard.derive.fallbackDomainIcon
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.string

// How a logbook row's cause badge looks. Ports of `_renderCauseIcon` (frontend@20260624.6
// src/panels/logbook/ha-logbook-entry.ts), and of `computeUserInitials` and the picture `ha-user-badge` shows
// (src/data/user.ts, src/components/user/ha-user-badge.ts).

/** What a cause's badge shows. */
sealed interface CauseBadge {
    /** A user's badge: their person's [picture], else their [initials]. */
    data class User(val initials: String, val picture: String?) : CauseBadge

    /** An icon. */
    data class Icon(val icon: String) : CauseBadge
}

/**
 * The badge of [cause]: a user's (or a system user's icon), a robot for automations, a script for scripts, the
 * integration's icon, else a puzzle piece; `null` for another entity's state change, which shows none.
 */
fun HassSnapshot.causeBadge(cause: LogbookCause): CauseBadge? = when (cause) {
    is LogbookCause.User -> SYSTEM_USER_ICONS[cause.name]?.takeIf { cause.systemUser }?.let(CauseBadge::Icon)
        ?: CauseBadge.User(userInitials(cause.name), personPicture(cause.userId))
    is LogbookCause.Automation -> CauseBadge.Icon(ROBOT_ICON)
    is LogbookCause.Script -> CauseBadge.Icon(SCRIPT_ICON)
    is LogbookCause.State -> null
    is LogbookCause.Integration -> CauseBadge.Icon(cause.brandDomain?.let(::fallbackDomainIcon) ?: PUZZLE_ICON)
    LogbookCause.Scheduled, is LogbookCause.HomeAssistant -> CauseBadge.Icon(PUZZLE_ICON)
}

/** Port of `computeUserInitials`: the first letters of the name's first three words. */
fun userInitials(name: String): String =
    if (name.isEmpty()) "?" else name.trim().split(" ").take(INITIALS_WORDS).joinToString("") { it.take(1) }

/** The picture of [userId]'s person, which their badge shows when there is one. */
private fun HassSnapshot.personPicture(userId: String): String? = states.values
    .firstOrNull { it.domain == "person" && it.attributes.string("user_id") == userId }
    ?.attributes?.string("entity_picture")

private const val ROBOT_ICON = "mdi:robot"
private const val SCRIPT_ICON = "mdi:script-text"
private const val PUZZLE_ICON = "mdi:puzzle"
private const val INITIALS_WORDS = 3

/** The icons of the users Home Assistant makes for its Cloud and Cast integrations, by their fixed names. */
private val SYSTEM_USER_ICONS = mapOf("Home Assistant Cloud" to "mdi:cloud", "Home Assistant Cast" to "mdi:cast")
