package io.homeassistant.companion.android.dashboard.navigation

import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.string
import java.util.Locale
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * The native sidebar against what the real frontend's `ha-sidebar` listed (outputs/cards.json `sidebar`): the admin
 * variant has a custom order and a hidden panel, the non-admin variant the default order.
 */
class SidebarGoldenTest {

    @TestFactory
    fun `Given captured panels and settings when listing the sidebar then it matches ha-sidebar`(): List<DynamicTest> = GoldenFixture.VARIANTS.map { variant ->
        DynamicTest.dynamicTest(variant) {
            val fixture = GoldenFixture(variant)
            fun value(name: String) = fixture.json("ws/$name.json").obj("result")?.obj("value")
            val panels = parsePanels(fixture.json("ws/get_panels.json").obj("result")!!)
            val items = sidebarItems(
                panels = panels,
                defaultPanel = defaultPanelUrlPath(value("frontend-get_user_data-core"), value("frontend-get_system_data-core"), panels),
                settings = SidebarSettings.fromUserData(value("frontend-get_user_data-sidebar")),
                localize = fixture.hass.localize,
                locale = Locale.US,
            )
            val expected = (fixture.json("outputs/cards.json")["sidebar"] as JsonArray).map {
                (it as JsonObject).string("url_path") to it.string("title")
            }
            assertEquals(expected, items.map { it.urlPath to it.title })
        }
    }
}
