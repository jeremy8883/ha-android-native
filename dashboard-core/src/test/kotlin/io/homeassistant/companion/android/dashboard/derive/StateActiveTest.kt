package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.entity.EntityState
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class StateActiveTest {
    private fun entity(entityId: String, state: String) = EntityState(entityId, state, JsonObject(emptyMap()), contextId = null, lastChanged = 0.0, lastUpdated = 0.0)

    @ParameterizedTest(name = "{0} = {1} -> {2}")
    @CsvSource(
        "light.a, on, true",
        "light.a, off, false",
        "light.a, unavailable, false",
        "sensor.a, unknown, false",
        "sensor.a, 21.5, true",
        "alert.a, off, true",
        "alert.a, idle, false",
        "button.a, unknown, true",
        "button.a, unavailable, false",
        "scene.a, 2026-10-08T10:00:00+00:00, true",
        "cover.a, closed, false",
        "cover.a, opening, true",
        "lock.a, locked, false",
        "lock.a, unlocked, true",
        "person.a, not_home, false",
        "person.a, home, true",
        "vacuum.a, docked, false",
        "vacuum.a, cleaning, true",
        "media_player.a, standby, false",
        "media_player.a, idle, true",
        "alarm_control_panel.a, disarmed, false",
        "group.a, home, true",
        "group.a, not_home, false",
        "timer.a, idle, false",
        "plant.a, ok, false",
        "camera.a, idle, false",
    )
    fun `Given entity state when checking active then matches upstream stateActive`(entityId: String, state: String, expected: Boolean) {
        assertEquals(expected, entity(entityId, state).isActive())
    }
}
