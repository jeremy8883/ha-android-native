package io.homeassistant.companion.android.dashboard.entity

import io.homeassistant.companion.android.dashboard.display.DisplayFormats
import io.homeassistant.companion.android.dashboard.display.JdkDisplayFormats
import kotlinx.serialization.json.JsonObject

/** Looks up a frontend translation by key, for example `panel.light`. Returns "" when unknown. */
fun interface Localize {
    operator fun invoke(key: String): String

    /** The translation formatted with [args], as an ICU message (see [formatIcuMessage]). */
    operator fun invoke(key: String, args: Map<String, String>): String = formatIcuMessage(invoke(key), args)
}

/** [Localize] that falls back to [fallback] (flat keys, as `frontend/get_translations` returns) for unknown keys. */
fun Localize.withFallback(fallback: Map<String, String>): Localize =
    if (fallback.isEmpty()) this else Localize { key -> invoke(key).ifEmpty { fallback[key].orEmpty() } }

/** The current user, from `auth/current_user`. */
data class HassUser(val id: String, val name: String?, val isAdmin: Boolean, val isOwner: Boolean)

/** Server state from `get_config`. */
data class HassConfig(
    val state: String?,
    val recoveryMode: Boolean,
    val version: String?,
    val components: Set<String>,
    /** `unit_system`: the unit of each measure, for example `temperature` "°C" and `length` "km". */
    val unitSystem: Map<String, String> = emptyMap(),
) {
    /** `unit_system.temperature`, for example "°C". */
    val temperatureUnit: String? get() = unitSystem["temperature"]

    companion object {
        val UNKNOWN = HassConfig(state = null, recoveryMode = false, version = null, components = emptySet())

        /** Core's `state` value while Home Assistant is still starting. */
        const val STATE_NOT_RUNNING = "NOT_RUNNING"
    }
}

/**
 * Everything pure dashboard logic (strategies, conditions, derivations) may read: the Kotlin equivalent
 * of the frontend's `hass` object, restricted to raw server data plus translations.
 *
 * @property panels url paths of the registered panels (`get_panels` keys)
 * @property localize frontend strings plus the server's entity translations (state names, units)
 * @property icons the server's icon translations for entity states
 * @property formats number and date formatting in the user's language and time zone (the frontend's `locale`)
 * @property repairsIssues active, non-ignored repair issues; `null` while loading
 * @property discoveredFlows config flows started by discovery; `null` while loading
 */
data class HassSnapshot(
    val states: EntityStates,
    val registries: Registries,
    val user: HassUser?,
    val config: HassConfig,
    val panels: Set<String>,
    val localize: Localize,
    val icons: IconResources = IconResources.EMPTY,
    val formats: DisplayFormats = JdkDisplayFormats.DEFAULT,
    val repairsIssues: List<JsonObject>? = null,
    val discoveredFlows: List<JsonObject>? = null,
)
