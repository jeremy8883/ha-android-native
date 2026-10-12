package io.homeassistant.companion.android.dashboard.quickbar

import io.homeassistant.companion.android.dashboard.entity.HassUser
import io.homeassistant.companion.android.dashboard.entity.Registries
import io.homeassistant.companion.android.dashboard.entity.parseAreaRegistry
import io.homeassistant.companion.android.dashboard.entity.parseDeviceRegistry
import io.homeassistant.companion.android.dashboard.entity.parseEntityRegistryDisplay
import io.homeassistant.companion.android.dashboard.entity.parseFloorRegistry
import io.homeassistant.companion.android.dashboard.entity.parseStates
import io.homeassistant.companion.android.dashboard.entity.withFallback
import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import io.homeassistant.companion.android.dashboard.navigation.parsePanels
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/**
 * A dashboard's quick bar ("Search Home Assistant") against the frontend's (20260624.6, tools/golden/capture.mjs
 * --quick-bar, quick-bar/items.json), for an admin and a non-admin: every section's items for each search, with
 * their names, places, integrations and icons.
 */
class QuickBarGoldenTest {
    @TestFactory
    fun `Given a search and a section when listing an admin's quick bar then it is the frontend's`() = cases("test-instance")

    @TestFactory
    fun `Given a search and a section when listing a non-admin's quick bar then it is the frontend's`() = cases("test-instance-nonadmin")

    @Test
    fun `Given other pages can't be opened when listing the quick bar then only what is handled here is offered`() {
        val items = QuickBarItems(source("test-instance", nativeOnly = true))
        assertEquals(listOf(QuickBarSection.NAVIGATE, QuickBarSection.COMMAND, QuickBarSection.ENTITY), items.sections)
        val paths = items.rows("", QuickBarSection.NAVIGATE).mapNotNull {
            ((it as? QuickBarRow.Item)?.item?.action as? QuickBarAction.Navigate)?.path
        }
        assertTrue("/logbook" in paths && "/energy" in paths && "/dashboard-test" in paths)
        assertTrue(paths.none { it.startsWith("/config") || it == "/profile" || it == "/media-browser" })
        assertTrue(items.rows("kitchen", null).none { it is QuickBarRow.Title && it.text == "Devices" })
    }

    private fun cases(variant: String): List<DynamicTest> {
        val capture = GoldenFixture(variant).json("quick-bar/items.json")
        val items = QuickBarItems(source(variant))
        return capture.obj("items")!!.flatMap { (search, sections) ->
            sections.jsonObject.map { (section, expected) ->
                DynamicTest.dynamicTest("'$search' in $section") {
                    val rows = items.rows(search, QuickBarSection.entries.firstOrNull { it.key == section })
                    val shown = expected.jsonArray.map { it.jsonObject }.filter(::isPorted).map(::expectedRow)
                    assertEquals(shown, rows.mapNotNull(::drawnRow))
                }
            }
        }
    }

    private fun source(variant: String, nativeOnly: Boolean = false): QuickBarSource {
        val fixture = GoldenFixture(variant)
        val capture = fixture.json("quick-bar/items.json")
        val user = capture.obj("user")!!
        val hass = fixture.hass.copy(
            states = parseStates(capture["states"]!!.jsonArray),
            registries = Registries(
                entities = parseEntityRegistryDisplay(capture.obj("entityRegistry")!!),
                devices = parseDeviceRegistry(capture["devices"]!!.jsonArray),
                areas = parseAreaRegistry(capture["areas"]!!.jsonArray),
                floors = parseFloorRegistry(capture["floors"]!!.jsonArray),
            ),
            user = HassUser(user.string("id")!!, user.string("name"), user.boolean("is_admin") == true, false),
            config = fixture.hass.config.copy(
                components = capture["components"]!!.jsonArray.mapNotNull { it.stringOrNull }.toSet(),
            ),
            services = capture.obj("services")!!.mapValues { (_, services) ->
                services.jsonObject.mapValues { it.value.jsonObject }
            },
            localize = fixture.hass.localize.withFallback(
                capture.obj("titles")!!.mapValues { it.value.stringOrNull.orEmpty() },
            ),
        )
        return QuickBarSource(
            hass = hass,
            panels = parsePanels(capture.obj("panels")!!),
            configEntryDomains = capture.objects("configEntries").associate {
                it.string("entry_id")!! to it.string("domain")!!
            },
            nativeOnly = nativeOnly,
        )
    }

    /** Upstream's "Shortcuts" (keyboard shortcuts) isn't offered. */
    private fun isPorted(row: JsonObject): Boolean = row.string("path") != ""

    private fun expectedRow(row: JsonObject): List<String?> {
        row.string("title")?.let { return listOf("title", it) }
        val icon = when {
            row.string("entityId") != null -> "entity:${row.string("entityId")}"
            // Panels and areas name their icon; other icons are paths, which only the item's own type knows
            row.string("icon") != null -> row.string("icon")
            row.string("iconColor") != null -> "colored:${row.string("iconColor")}"
            else -> null
        }
        return listOf(row.string("id"), row.string("primary"), row.string("secondary"), row.string("domainName"), icon)
    }

    private fun drawnRow(row: QuickBarRow): List<String?>? = when (row) {
        is QuickBarRow.Title -> listOf("title", row.text)
        is QuickBarRow.Item -> row.item.let {
            val icon = when (val shown = it.icon) {
                is QuickBarIcon.Entity -> "entity:${shown.entityId}"
                is QuickBarIcon.Colored -> "colored:${shown.color}"
                is QuickBarIcon.Plain -> shown.icon.takeIf { _ -> it.isNamedUpstream() }
                is QuickBarIcon.Brand -> null
            }
            listOf(it.id, it.primary, it.secondary, it.domainName, icon)
        }
        // The capture records the items only, not the combo box's "nothing found" row
        is QuickBarRow.Empty -> null
    }

    /** Whether upstream gives the item's icon by name (a panel's, an area's own), not as a path. */
    private fun QuickBarItem.isNamedUpstream(): Boolean = when (section) {
        QuickBarSection.NAVIGATE -> true
        QuickBarSection.AREA -> icon != QuickBarIcon.Plain("mdi:texture-box")
        else -> false
    }
}
