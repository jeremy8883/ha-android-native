package io.homeassistant.companion.android.dashboard.data

import io.homeassistant.companion.android.dashboard.moreinfo.UpdateBackupSettings
import io.homeassistant.companion.android.dashboard.moreinfo.UpdateType
import io.homeassistant.companion.android.dashboard.moreinfo.parseAutomaticBackupSettings
import io.homeassistant.companion.android.dashboard.moreinfo.parseSupervisorUpdateConfig
import io.homeassistant.companion.android.dashboard.moreinfo.updateBackupCommands
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonObject
import timber.log.Timber

/**
 * The backup settings an update's backup switch reads: whether to back up by default (the Supervisor's), and the
 * automatic backup settings, read once as the update's details open them.
 */
class UpdateBackupRepository @Inject constructor(private val sessions: ServerSessions) {
    /**
     * The settings of [type]'s switch, on a server with [components]. Upstream ignores a failure to read them (the
     * switch then starts off, and offers a manual backup), so one that fails is left out, and logged.
     */
    fun settings(type: UpdateType, components: Set<String>): Flow<Loadable<UpdateBackupSettings>> =
        sessions.withServer { session ->
            flow {
                var settings = UpdateBackupSettings()
                updateBackupCommands(type, components).forEach { command ->
                    when (val result = session.request(command.type, command.params).expect<JsonObject>()) {
                        is Fetched.Success -> settings = settings.with(command.type, result.value)
                        is Fetched.Failure -> Timber.w("Couldn't read ${command.type}: ${result.error}")
                    }
                    emit(Loadable.Ready(settings))
                }
            }
        }

    private fun UpdateBackupSettings.with(type: String, result: JsonObject) = when (type) {
        SUPERVISOR_CONFIG -> copy(supervisor = parseSupervisorUpdateConfig(result))
        else -> copy(automatic = parseAutomaticBackupSettings(result))
    }

    private companion object {
        const val SUPERVISOR_CONFIG = "hassio/update/config/info"
    }
}
