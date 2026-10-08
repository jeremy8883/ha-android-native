package io.homeassistant.companion.android.dashboard.entity

/** Looks up a frontend translation by key, for example `panel.light`. Returns "" when unknown. */
fun interface Localize {
    operator fun invoke(key: String): String
}

/** The current user, from `auth/current_user`. */
data class HassUser(val id: String, val name: String?, val isAdmin: Boolean, val isOwner: Boolean)

/**
 * Everything pure dashboard logic (strategies, conditions, derivations) may read: the Kotlin equivalent
 * of the frontend's `hass` object, restricted to raw server data plus translations.
 *
 * @property panels url paths of the registered panels (`get_panels` keys)
 */
data class HassSnapshot(
    val states: EntityStates,
    val registries: Registries,
    val user: HassUser?,
    val panels: Set<String>,
    val localize: Localize,
)
