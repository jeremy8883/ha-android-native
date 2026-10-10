package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.hass
import io.homeassistant.companion.android.dashboard.json
import io.homeassistant.companion.android.dashboard.registries
import io.homeassistant.companion.android.dashboard.states
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UpdateBackupTest {
    private val hass = hass(
        states(
            """{"update.core": {"s": "on", "a": {"title": "Home Assistant Core", "installed_version": "2026.7.4"}},
                "update.os": {"s": "on", "a": {"title": "Home Assistant Operating System"}},
                "update.supervisor": {"s": "on", "a": {"title": "Home Assistant Supervisor"}},
                "update.app": {"s": "on", "a": {"title": "Music Assistant", "installed_version": "2.5.0"}},
                "update.hacs": {"s": "on", "a": {"title": "HACS"}}}""",
        ),
        registries(
            entities = """{"entities": [{"ei": "update.core", "pl": "hassio"}, {"ei": "update.os", "pl": "hassio"},
                {"ei": "update.supervisor", "pl": "hassio"}, {"ei": "update.app", "pl": "hassio"},
                {"ei": "update.hacs", "pl": "hacs"}], "entity_categories": {}}""",
        ),
    )
    private val now = Instant.parse("2026-10-10T12:00:00Z")
    private fun state(id: String) = hass.states.getValue(id)
    private fun option(id: String, settings: UpdateBackupSettings) = hass.updateBackupOption(state(id), hass.updateType(state(id)), settings, now)

    @Test
    fun `Given the Supervisor's updates when typing them then their titles tell Home Assistant, its OS and apps apart`() {
        assertEquals(
            listOf(UpdateType.HomeAssistant, UpdateType.HomeAssistantOs, UpdateType.Generic, UpdateType.App, UpdateType.Generic),
            listOf("update.core", "update.os", "update.supervisor", "update.app", "update.hacs").map { hass.updateType(state(it)) },
        )
    }

    @Test
    fun `Given configured automatic backups when updating Home Assistant then it offers them, on as the Supervisor says`() {
        val settings = UpdateBackupSettings(
            SupervisorUpdateConfig(appBackup = false, coreBackup = true),
            AutomaticBackupSettings(configured = true, lastCompleted = Instant.parse("2026-10-10T09:00:00Z")),
        )
        assertEquals(
            UpdateBackupOption(
                "[ui.dialogs.more_info_control.update.create_backup.automatic]",
                "[ui.dialogs.more_info_control.update.create_backup.automatic_description_last]",
                defaultOn = true,
            ),
            option("update.core", settings),
        )
    }

    @Test
    fun `Given unconfigured or unread automatic backups when updating Home Assistant then it offers a manual one, off`() {
        val manual = UpdateBackupOption(
            "[ui.dialogs.more_info_control.update.create_backup.manual]",
            "[ui.dialogs.more_info_control.update.create_backup.manual_description]",
            defaultOn = false,
        )
        assertEquals(manual, option("update.os", UpdateBackupSettings()))
        assertEquals(manual, option("update.os", UpdateBackupSettings(automatic = AutomaticBackupSettings(false, null))))
    }

    @Test
    fun `Given an app when updating it then it offers to keep its version, as the Supervisor's setting says`() {
        val settings = UpdateBackupSettings(SupervisorUpdateConfig(appBackup = true, coreBackup = false))
        assertEquals(
            UpdateBackupOption(
                "[ui.dialogs.more_info_control.update.create_backup.app]",
                "[ui.dialogs.more_info_control.update.create_backup.app_description]",
                defaultOn = true,
            ),
            option("update.app", settings),
        )
        assertEquals("[ui.dialogs.more_info_control.update.create_backup.generic]", option("update.hacs", settings).title)
    }

    @Test
    fun `Given the backup settings responses when reading them then only a fully set up schedule counts`() {
        val config = { password: String, agents: String ->
            json(
                """{"config": {"automatic_backups_configured": true, "create_backup": {"password": $password, "agent_ids": $agents},
                    "last_completed_automatic_backup": "2026-10-09T03:00:00+00:00"}}""",
            )
        }
        assertEquals(
            AutomaticBackupSettings(true, Instant.parse("2026-10-09T03:00:00Z")),
            parseAutomaticBackupSettings(config("\"secret\"", "[\"hassio.local\"]")),
        )
        assertEquals(false, parseAutomaticBackupSettings(config("null", "[\"hassio.local\"]"))?.configured)
        assertEquals(false, parseAutomaticBackupSettings(config("\"secret\"", "[]"))?.configured)
        assertEquals(
            SupervisorUpdateConfig(appBackup = false, coreBackup = true),
            parseSupervisorUpdateConfig(json("""{"add_on_backup_before_update": false, "add_on_backup_retain_copies": 1, "core_backup_before_update": true}""")),
        )
    }

    @Test
    fun `Given each type when choosing what to read then only the settings its switch uses are asked for`() {
        val withSupervisor = setOf("hassio", "backup")
        assertEquals(listOf("hassio/update/config/info", "backup/config/info"), updateBackupCommands(UpdateType.HomeAssistant, withSupervisor).map { it.type })
        assertEquals(listOf("hassio/update/config/info"), updateBackupCommands(UpdateType.App, withSupervisor).map { it.type })
        assertEquals(emptyList<String>(), updateBackupCommands(UpdateType.Generic, withSupervisor).map { it.type })
        assertEquals(listOf("backup/config/info"), updateBackupCommands(UpdateType.HomeAssistant, setOf("backup")).map { it.type })
    }
}
