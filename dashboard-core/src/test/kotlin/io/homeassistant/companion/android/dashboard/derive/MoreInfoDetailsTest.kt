package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.feature.attributeName
import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import io.homeassistant.companion.android.dashboard.json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MoreInfoDetailsTest {
    private val fixture = GoldenFixture("test-instance").hass
    private val light = EntityState(
        entityId = "light.desk",
        state = "on",
        attributes = json(
            """{"friendly_name": "Desk", "brightness": 255, "supported_features": 40, "effect_list": ["a"],
                "10": "ten", "2": "two"}""",
        ),
        contextId = null,
        lastChanged = 1760176800.123,
        lastUpdated = 1760176800.5,
    )
    private val hass = fixture.copy(states = fixture.states + (light.entityId to light))

    @Test
    fun `Given a light when listing the details' attributes then the shown come first, then the built-in ones`() {
        val keys = listOf("2", "10", "brightness", "effect_list", "friendly_name", "supported_features")
        assertEquals(keys.map { hass.attributeName(light, it) }, hass.detailAttributes(light).map { it.first })
    }

    @Test
    fun `Given supported features when listing the details' attributes then they are named`() {
        assertEquals("Flash, Transition", hass.detailAttributes(light).last().second)
        val none = hass.detailAttributes(light.copy(attributes = json("""{"supported_features": 0}"""))).single()
        assertEquals(hass.localize("ui.common.none"), none.second)
    }

    @Test
    fun `Given a light when dumping the details then it is ha-yaml-editor's text, with On quoted as a YAML 1-1 boolean`() {
        val expected = """
            |state:
            |  translated: "On"
            |  raw: "on"
            |  last_changed: "2025-10-11T10:00:00.123Z"
            |  last_updated: "2025-10-11T10:00:00.500Z"
            |attributes:
            |  "2": two
            |  "10": ten
            |  friendly_name: Desk
            |  brightness: 255
            |  supported_features: 40
            |  effect_list:
            |    - a
            |
        """.trimMargin()
        assertEquals(expected, hass.moreInfoDetailsYaml(light.entityId))
    }
}
