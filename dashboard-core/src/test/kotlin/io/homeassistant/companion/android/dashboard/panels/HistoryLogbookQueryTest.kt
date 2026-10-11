package io.homeassistant.companion.android.dashboard.panels

import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HistoryLogbookQueryTest {

    private val hass = GoldenFixture("test-instance").hass

    @Test
    fun `Given the details' Show more link when opening the panel then it lists that entity from that start`() {
        val query = hass.historyLogbookQuery("entity_id=lock.back_door_lock&start_date=2026-10-09T13%3A00%3A00.000Z&back=1") { true }

        assertEquals(listOf("lock.back_door_lock"), query.entityIds)
        assertEquals(Instant.parse("2026-10-09T13:00:00Z"), query.start)
        assertNull(query.end)
    }

    @Test
    fun `Given no target when opening the panel then it lists every entity`() {
        assertNull(hass.historyLogbookQuery(null) { true }.entityIds)
    }

    @Test
    fun `Given an area when opening the panel then it lists the area's entities that pass the filter`() {
        val area = hass.registries.areas.keys.first { id -> hass.registries.entities.values.any { it.areaId == id } }

        val entityIds = hass.historyLogbookQuery("area_id=$area") { !it.startsWith("sensor.") }.entityIds!!

        assertTrue(entityIds.isNotEmpty())
        assertTrue(entityIds.none { it.startsWith("sensor.") })
    }

    @Test
    fun `Given a search when picking entities then every word must match the name, place or id`() {
        val entities = listOf(
            PickerEntity("lock.back_door_lock", "Back door lock", "Kitchen"),
            PickerEntity("light.kitchen", "Kitchen Lights", "Kitchen"),
        )

        assertEquals(listOf("lock.back_door_lock"), entities.matching("kitchen LOCK").map { it.entityId })
        assertEquals(2, entities.matching("").size)
    }
}
