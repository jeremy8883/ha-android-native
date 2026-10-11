package io.homeassistant.companion.android.dashboard.target

import io.homeassistant.companion.android.dashboard.entity.Registries
import io.homeassistant.companion.android.dashboard.entity.parseAreaRegistry
import io.homeassistant.companion.android.dashboard.entity.parseDeviceRegistry
import io.homeassistant.companion.android.dashboard.entity.parseEntityRegistryDisplay
import io.homeassistant.companion.android.dashboard.entity.parseFloorRegistry
import io.homeassistant.companion.android.dashboard.entity.parseLabelRegistry
import io.homeassistant.companion.android.dashboard.entity.parseStates
import io.homeassistant.companion.android.dashboard.entity.withFallback
import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import io.homeassistant.companion.android.dashboard.logbook.logbookCompatible
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * The Logbook panel's target picker against the frontend's (20260624.6, tools/golden/capture.mjs --target-picker,
 * target-picker/logbook.json): every section's items for each search, what expanding each item gives, the entities
 * each target stands for, and the chips drawn for it.
 */
class TargetPickerGoldenTest {
    private val fixture = GoldenFixture("test-instance")
    private val capture = fixture.json("target-picker/logbook.json")
    private val hass = fixture.hass.copy(
        states = parseStates(capture["states"]!!.jsonArray),
        registries = Registries(
            entities = parseEntityRegistryDisplay(capture.obj("entityRegistry")!!),
            devices = parseDeviceRegistry(capture["devices"]!!.jsonArray),
            areas = parseAreaRegistry(capture["areas"]!!.jsonArray),
            floors = parseFloorRegistry(capture["floors"]!!.jsonArray),
            labels = parseLabelRegistry(capture["labels"]!!.jsonArray),
        ),
        localize = fixture.hass.localize.withFallback(capture.obj("titles")!!.mapValues { it.value.stringOrNull.orEmpty() }),
    )
    private val source = TargetPickerSource(
        hass = hass,
        entityFilter = { hass.logbookCompatible(it) },
        configEntryDomains = capture.objects("configEntries").associate { it.string("entry_id")!! to it.string("domain")!! },
    )

    @TestFactory
    fun `Given a search and a section when listing the picker's items then they are the frontend's`(): List<DynamicTest> {
        val items = TargetPickerItems(source, Target())
        return capture.obj("items")!!.flatMap { (search, sections) ->
            sections.jsonObject.map { (section, expected) ->
                DynamicTest.dynamicTest("'$search' in $section") {
                    val rows = items.rows(search, TARGET_SECTIONS.firstOrNull { it.key == section })
                    assertEquals(expected.jsonArray.map(::expectedRow), rows.mapNotNull(::drawnRow))
                }
            }
        }
    }

    @TestFactory
    fun `Given a floor, area or label when expanding it then it is replaced by its parts as upstream's`(): List<DynamicTest> = capture.objects("expansions").map { expansion ->
        val type = TargetType.entries.single { it.key == expansion.string("type") }
        DynamicTest.dynamicTest("${type.key} ${expansion.string("id")}") {
            val target = Target.fromJson(expansion.obj("value")!!)
            val expanded = source.expand(target, TargetRef(type, expansion.string("id")!!))
            assertEquals(expansion.obj("expanded")!!.normalized(), expanded.toJson().normalized())
        }
    }

    @TestFactory
    fun `Given a target when listing its entries then it stands for the frontend's entities`(): List<DynamicTest> = capture.objects("resolved").map { resolved ->
        DynamicTest.dynamicTest(resolved.obj("target").toString()) {
            val expected = (resolved["entityIds"] as? JsonArray)?.mapNotNull { it.stringOrNull }.orEmpty()
            assertEquals(expected, hass.resolveEntityIds(Target.fromJson(resolved.obj("target")!!)))
        }
    }

    @TestFactory
    fun `Given a target when drawing its chips then they name and show what the frontend's do`(): List<DynamicTest> = capture.objects("chips").map { drawn ->
        DynamicTest.dynamicTest(drawn.obj("target").toString()) {
            val expected = drawn.objects("chips").map { chip ->
                listOf(chip.string("type"), chip.string("id"), chip.string("name"), chip.string("icon"), chip["expand"].toString())
            }
            val chips = source.chips(Target.fromJson(drawn.obj("target")!!)).map { chip ->
                // Only icons by name are drawn with `ha-icon`; the fallback icons are paths
                val icon = (chip.icon as? TargetIcon.Named)?.icon?.takeIf { it != chipFallback(chip.ref.type) }
                listOf(chip.ref.type.key, chip.ref.id, chip.name, icon, chip.expandable.toString())
            }
            assertEquals(expected, chips)
        }
    }

    /** The icons upstream draws from a path when the item has none. */
    private fun chipFallback(type: TargetType): String? = when (type) {
        TargetType.AREA -> AREA_ICON
        TargetType.LABEL -> LABEL_ICON
        else -> null
    }

    private fun expectedRow(row: kotlinx.serialization.json.JsonElement): List<String?> {
        val obj = row.jsonObject
        obj.string("title")?.let { return listOf("title", it) }
        return listOf(
            obj.string("id"),
            obj.string("primary"),
            obj.string("secondary"),
            obj.string("domainName"),
            obj.string("color"),
            obj["last"].toString().takeIf { obj.string("type") == "area" },
        )
    }

    private fun drawnRow(row: TargetPickerRow): List<String?>? = when (row) {
        is TargetPickerRow.Title -> listOf("title", row.text)
        is TargetPickerRow.Item -> row.item.let {
            listOf(
                "${it.ref.type.key}________${it.ref.id}",
                it.primary,
                it.secondary,
                it.domainName,
                it.color,
                it.last.toString().takeIf { _ -> it.ref.type == TargetType.AREA },
            )
        }
        // The frontend's list has no row for "nothing found" in this capture's searches
        is TargetPickerRow.Empty -> null
    }

    /** A target with each field as a list, as the order of its fields doesn't matter. */
    private fun JsonObject.normalized(): Map<String, List<String>> = mapValues { (_, value) ->
        when (value) {
            is JsonArray -> value.mapNotNull { it.stringOrNull }
            is JsonPrimitive -> listOfNotNull(value.stringOrNull)
            else -> emptyList()
        }
    }
}
