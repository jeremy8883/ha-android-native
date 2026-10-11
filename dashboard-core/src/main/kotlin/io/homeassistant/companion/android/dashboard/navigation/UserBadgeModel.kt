package io.homeassistant.companion.android.dashboard.navigation

import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.string

/**
 * The current user as the sidebar's profile entry shows them: their name, and their person's picture or else their
 * initials.
 */
data class UserBadgeModel(val name: String, val initials: String, val picture: String?)

/**
 * The current user's badge, or `null` without a user. Port of `ha-user-badge` and `computeUserInitials`
 * (frontend@20260624.6 src/components/user/ha-user-badge.ts, src/data/user.ts).
 */
fun HassSnapshot.userBadge(): UserBadgeModel? {
    val user = user ?: return null
    val name = user.name.orEmpty()
    val person = states.values.firstOrNull {
        it.entityId.startsWith(PERSON_PREFIX) && it.attributes.string("user_id") == user.id
    }
    return UserBadgeModel(
        name = name,
        initials = userInitials(name),
        picture = person?.attributes?.string("entity_picture")?.ifEmpty { null },
    )
}

/** The first letter of each of the first three words of [name], or "?" without a name. */
fun userInitials(name: String): String =
    if (name.isEmpty()) "?" else name.trim().split(" ").take(MAX_INITIALS).joinToString("") { it.take(1) }

private const val PERSON_PREFIX = "person."
private const val MAX_INITIALS = 3
