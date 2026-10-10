package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.supportsFeature
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.feature.attributeName
import io.homeassistant.companion.android.dashboard.feature.entityData
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Port of `more-info-update` (frontend@20260624.6 src/dialogs/more-info/controls/more-info-update.ts) with its
// helpers (src/data/update.ts). The backup switch's texts and default are in UpdateBackup.kt.

/**
 * An update's details: its progress while installing, the title, the installed and latest versions, the release
 * announcement, the release notes (fetched, or the summary), and the skip and install buttons.
 *
 * @property fetchNotes whether the release notes are fetched (`update/release_notes`), rather than [summary]
 * @property install the install button, `null` when the entity can't install
 */
data class UpdateMoreInfo(
    val progress: UpdateProgress?,
    val title: String?,
    val versions: List<Pair<String, String>>,
    val releaseUrl: String?,
    val releaseLabel: String,
    val fetchNotes: Boolean,
    val summary: String?,
    val skip: UpdateSkip,
    val install: UpdateInstall?,
)

/** An install's progress: a percentage, or unknown. */
sealed interface UpdateProgress {
    /** [percent] done. */
    data class Percent(val percent: Double) : UpdateProgress

    /** Under way, how far unknown. */
    data object Indeterminate : UpdateProgress
}

/** Skip the latest version (asking to turn off automatic updates first), or clear a skipped one. */
data class UpdateSkip(
    val label: String,
    val enabled: Boolean,
    val action: CardAction.CallService,
    val autoUpdateTitle: String?,
    val autoUpdateText: String?,
)

/**
 * The install button: [installing] while under way, with the backup switch when the entity can back up first.
 *
 * @property backupType what the update updates, which decides the backup switch; `null` without one
 */
data class UpdateInstall(
    val label: String,
    val enabled: Boolean,
    val installing: Boolean,
    val backupType: UpdateType?,
    private val entityId: String,
    private val version: String?,
) {
    /** The install call, backing up first when [backup]. */
    fun call(backup: Boolean): CardAction.CallService {
        val data = listOfNotNull(
            "entity_id" to JsonPrimitive(entityId),
            ("backup" to JsonPrimitive(true)).takeIf { backup && backupType != null },
            version?.let { "version" to JsonPrimitive(it) },
        )
        return CardAction.CallService(UPDATE, "install", JsonObject(data.toMap()), target = null)
    }
}

/** The details of an update, or `null` for another entity or while unavailable or unknown (upstream draws none). */
fun HassSnapshot.updateMoreInfo(state: EntityState): UpdateMoreInfo? {
    if (state.domain != UPDATE || state.state == "unavailable" || state.state == "unknown") return null
    val attributes = state.attributes
    val installing = attributes.boolean("in_progress") == true
    val percent = attributes.numberOrNull("update_percentage")
    val strings = "ui.dialogs.more_info_control.update"
    val version = { key: String ->
        attributeName(state, key) to
            (attributes.string(key) ?: localize("state.default.unavailable"))
    }
    return UpdateMoreInfo(
        progress = when {
            !installing -> null
            state.supportsFeature(FEATURE_PROGRESS) && percent != null -> UpdateProgress.Percent(percent)
            else -> UpdateProgress.Indeterminate
        },
        title = attributes.string("title"),
        versions = listOf(version("installed_version"), version("latest_version")),
        releaseUrl = attributes.string("release_url")?.ifEmpty { null },
        releaseLabel = localize("$strings.release_announcement"),
        fetchNotes = state.supportsFeature(FEATURE_RELEASE_NOTES),
        summary = attributes.string("release_summary")?.ifEmpty { null },
        skip = updateSkip(state, installing),
        install = if (state.supportsFeature(FEATURE_INSTALL)) {
            UpdateInstall(
                label = localize("$strings.update"),
                enabled = !(state.state == OFF && !latestSkipped(state)),
                installing = installing,
                backupType = updateType(state).takeIf { state.supportsFeature(FEATURE_BACKUP) },
                entityId = state.entityId,
                version = attributes.string("latest_version").takeIf {
                    state.supportsFeature(FEATURE_SPECIFIC_VERSION)
                },
            )
        } else {
            null
        },
    )
}

private fun HassSnapshot.updateSkip(state: EntityState, installing: Boolean): UpdateSkip {
    val strings = "ui.dialogs.more_info_control.update"
    val call = { service: String -> CardAction.CallService(UPDATE, service, entityData(state), target = null) }
    return if (state.state == OFF && state.attributes.string("skipped_version") != null) {
        UpdateSkip(localize("$strings.clear_skipped"), true, call("clear_skipped"), null, null)
    } else {
        val auto = state.attributes.boolean("auto_update") == true
        UpdateSkip(
            label = localize("$strings.skip"),
            enabled = !latestSkipped(state) && state.state != OFF && !installing,
            action = call("skip"),
            autoUpdateTitle = localize("$strings.auto_update_enabled_title").takeIf { auto },
            autoUpdateText = localize("$strings.auto_update_enabled_text").takeIf { auto },
        )
    }
}

/** Port of `latestVersionIsSkipped`. */
private fun latestSkipped(state: EntityState): Boolean {
    val latest = state.attributes.string("latest_version")
    return latest != null && latest == state.attributes.string("skipped_version")
}

private const val UPDATE = "update"
private const val OFF = "off"
private const val FEATURE_INSTALL = 1
private const val FEATURE_SPECIFIC_VERSION = 2
private const val FEATURE_PROGRESS = 4
private const val FEATURE_BACKUP = 8
private const val FEATURE_RELEASE_NOTES = 16
