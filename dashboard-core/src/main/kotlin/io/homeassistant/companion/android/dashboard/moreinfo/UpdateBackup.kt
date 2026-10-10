package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.display.parseJsDate
import io.homeassistant.companion.android.dashboard.display.relativeTime
import io.homeassistant.companion.android.dashboard.energy.WsCommand
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.string
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

// The backup switch of an update's details. Ports of `getUpdateType` (frontend@20260624.6 src/data/update.ts),
// `getSupervisorUpdateConfig` (src/data/supervisor/update.ts), `fetchBackupConfig` (src/data/backup.ts) and
// `_computeCreateBackupTexts` and `_fetchUpdateBackupConfig` (src/dialogs/more-info/controls/more-info-update.ts).

/** What an update updates, which decides its backup switch. */
sealed interface UpdateType {
    /** A Supervisor app (add-on). */
    data object App : UpdateType

    /** Home Assistant Core. */
    data object HomeAssistant : UpdateType

    /** Home Assistant OS. */
    data object HomeAssistantOs : UpdateType

    /** Anything else, including the Supervisor itself. */
    data object Generic : UpdateType
}

/** Whether this is Home Assistant or its OS, which back up with the automatic backup settings. */
val UpdateType.isHomeAssistant: Boolean get() = this == UpdateType.HomeAssistant || this == UpdateType.HomeAssistantOs

/** [state]'s type: by its title for the Supervisor's (`hassio`) updates, else generic. */
fun HassSnapshot.updateType(state: EntityState): UpdateType {
    if (registries.entities[state.entityId]?.platform != HASSIO) return UpdateType.Generic
    return when (state.attributes.string("title").orEmpty()) {
        CORE_TITLE -> UpdateType.HomeAssistant
        OS_TITLE -> UpdateType.HomeAssistantOs
        SUPERVISOR_TITLE -> UpdateType.Generic
        else -> UpdateType.App
    }
}

/** Whether to back up before updates by default (`hassio/update/config/info`). */
data class SupervisorUpdateConfig(val appBackup: Boolean, val coreBackup: Boolean)

/**
 * The automatic backup settings that matter to an update (`backup/config/info`): whether automatic backups are
 * fully [configured] (scheduled, encrypted and stored somewhere), and when the last one completed.
 */
data class AutomaticBackupSettings(val configured: Boolean, val lastCompleted: Instant?)

/** The settings an update's backup switch reads, each `null` while not loaded or when it couldn't be. */
data class UpdateBackupSettings(
    val supervisor: SupervisorUpdateConfig? = null,
    val automatic: AutomaticBackupSettings? = null,
)

/** The commands [type]'s backup switch reads its settings with: the Supervisor's when it runs, and the automatic. */
fun updateBackupCommands(type: UpdateType, components: Set<String>): List<WsCommand> = listOfNotNull(
    WsCommand(SUPERVISOR_CONFIG, JsonObject(emptyMap()))
        .takeIf { HASSIO in components && (type == UpdateType.App || type.isHomeAssistant) },
    WsCommand(BACKUP_CONFIG, JsonObject(emptyMap())).takeIf { type.isHomeAssistant },
)

/** Read `hassio/update/config/info`; `null` when it isn't one. */
fun parseSupervisorUpdateConfig(result: JsonObject): SupervisorUpdateConfig? {
    val app = result.boolean("add_on_backup_before_update") ?: return null
    return SupervisorUpdateConfig(appBackup = app, coreBackup = result.boolean("core_backup_before_update") == true)
}

/** Read `backup/config/info`; `null` when it isn't one. The password itself isn't kept, only that there is one. */
fun parseAutomaticBackupSettings(result: JsonObject): AutomaticBackupSettings? {
    val config = result.obj("config") ?: return null
    val create = config.obj("create_backup")
    val password = create?.string("password")
    val agents = create?.get("agent_ids") as? JsonArray
    val configured = config.boolean("automatic_backups_configured") == true &&
        !password.isNullOrEmpty() &&
        agents?.isNotEmpty() == true
    val last = config.string("last_completed_automatic_backup")?.let { parseJsDate(it, ZoneOffset.UTC) }
    return AutomaticBackupSettings(configured, last)
}

/** The backup switch: its title, the description under it, and whether it starts on. */
data class UpdateBackupOption(val title: String, val description: String?, val defaultOn: Boolean)

/** The backup switch of [state]'s update of [type] with [settings], at [now] (for the last backup's age). */
fun HassSnapshot.updateBackupOption(
    state: EntityState,
    type: UpdateType,
    settings: UpdateBackupSettings,
    now: Instant,
): UpdateBackupOption {
    val strings = "ui.dialogs.more_info_control.update.create_backup"
    val automatic = settings.automatic?.takeIf { it.configured }
    val version = state.attributes.string("installed_version")?.ifEmpty { null }
    return when {
        type.isHomeAssistant && automatic == null -> UpdateBackupOption(
            localize("$strings.manual"),
            localize("$strings.manual_description"),
            defaultOn = settings.supervisor?.coreBackup == true,
        )
        type.isHomeAssistant -> UpdateBackupOption(
            localize("$strings.automatic"),
            automatic?.lastCompleted?.let { last ->
                localize(
                    "$strings.automatic_description_last",
                    mapOf("relative_time" to formats.relativeTime(last, now)),
                )
            } ?: localize("$strings.automatic_description_none"),
            defaultOn = settings.supervisor?.coreBackup == true,
        )
        type == UpdateType.App -> UpdateBackupOption(
            localize("$strings.app"),
            version?.let { localize("$strings.app_description", mapOf("version" to it)) },
            defaultOn = settings.supervisor?.appBackup == true,
        )
        else -> UpdateBackupOption(localize("$strings.generic"), null, defaultOn = false)
    }
}

private const val HASSIO = "hassio"
private const val SUPERVISOR_CONFIG = "hassio/update/config/info"
private const val BACKUP_CONFIG = "backup/config/info"
private const val CORE_TITLE = "Home Assistant Core"
private const val OS_TITLE = "Home Assistant Operating System"
private const val SUPERVISOR_TITLE = "Home Assistant Supervisor"
