package io.homeassistant.companion.android.dashboard.entity

import io.homeassistant.companion.android.dashboard.hass
import io.homeassistant.companion.android.dashboard.registries
import io.homeassistant.companion.android.dashboard.states
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EntityFilterTest {
    private val hass = hass(
        states = states(
            """
            {
              "light.kitchen": {"s": "on", "a": {}},
              "light.hidden": {"s": "on", "a": {}},
              "sensor.battery": {"s": "80", "a": {"device_class": "battery"}},
              "sensor.temp": {"s": "21", "a": {"device_class": "temperature"}},
              "sensor.diag": {"s": "1", "a": {}},
              "switch.no_registry": {"s": "off", "a": {}}
            }
            """,
        ),
        registries = registries(
            entities = """
            {"entity_categories": {"0": "config", "1": "diagnostic"}, "entities": [
              {"ei": "light.kitchen", "di": "dev1", "pl": "demo", "lb": ["fav"]},
              {"ei": "light.hidden", "ai": "kitchen", "pl": "demo", "lb": [], "hb": true},
              {"ei": "sensor.battery", "di": "dev1", "pl": "demo", "lb": []},
              {"ei": "sensor.temp", "ai": "bedroom", "di": "dev1", "pl": "template", "lb": []},
              {"ei": "sensor.diag", "pl": "demo", "lb": [], "ec": 1}
            ]}
            """,
            devices = """[{"id": "dev1", "name": "Device 1", "area_id": "kitchen"}]""",
            areas = """[{"area_id": "kitchen", "name": "Kitchen", "floor_id": "ground"}, {"area_id": "bedroom", "name": "Bedroom"}]""",
            floors = """[{"floor_id": "ground", "name": "Ground", "level": 0}]""",
        ),
    )
    private val all = hass.states.keys.toList()

    private fun matching(filter: EntityFilter) = hass.filterEntities(all, filter)

    @Test
    fun `Given area filter when matching then entity area wins over device area and hidden entities are excluded`() {
        assertEquals(listOf("light.kitchen", "sensor.battery"), matching(EntityFilter(areas = setOf("kitchen"))))
        assertEquals(listOf("sensor.temp"), matching(EntityFilter(areas = setOf("bedroom"))))
    }

    @Test
    fun `Given null in area filter when matching then entities without area match`() {
        assertEquals(listOf("sensor.diag", "switch.no_registry"), matching(EntityFilter(areas = setOf(null))))
    }

    @Test
    fun `Given floor filter when matching then floor comes from the resolved area`() {
        assertEquals(listOf("light.kitchen", "sensor.battery"), matching(EntityFilter(floors = setOf("ground"))))
    }

    @Test
    fun `Given device class and category filters when matching then missing values count as none`() {
        assertEquals(listOf("sensor.diag"), matching(EntityFilter(domains = setOf("sensor"), deviceClasses = setOf("none"))))
        assertEquals(listOf("sensor.diag"), matching(EntityFilter(entityCategories = setOf("diagnostic"))))
        assertEquals(
            listOf("light.kitchen", "sensor.battery", "sensor.temp", "switch.no_registry"),
            matching(EntityFilter(entityCategories = setOf(ENTITY_CATEGORY_NONE))),
        )
    }

    @Test
    fun `Given label and hidden platform filters when matching then entities without registry entry never match`() {
        assertEquals(listOf("light.kitchen"), matching(EntityFilter(labels = setOf("fav"))))
        assertEquals(listOf("sensor.temp"), matching(EntityFilter(hiddenPlatforms = setOf("demo"))))
    }

    @Test
    fun `Given several filters when finding entities then results follow filter order without duplicates`() {
        val found = hass.findEntities(
            all,
            listOf(EntityFilter(domains = setOf("sensor")), EntityFilter(domains = setOf("light")), EntityFilter(areas = setOf("kitchen"))),
        )
        assertEquals(listOf("sensor.battery", "sensor.temp", "sensor.diag", "light.kitchen"), found)
    }
}
