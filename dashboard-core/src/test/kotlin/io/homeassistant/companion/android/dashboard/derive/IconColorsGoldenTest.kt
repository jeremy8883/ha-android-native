package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.condition.ConditionContext
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.entity.parseStates
import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import io.homeassistant.companion.android.dashboard.layout.viewHeader
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.ViewConfig
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.theme.parseCssColor
import io.homeassistant.companion.android.dashboard.theme.resolveDisplayColor
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * The icon colour audit: every entity of the test instance's icon colour as the frontend (20260624.6) resolved it
 * in a tile, an entities row (as is and with `state_color`), a heading badge coloured by state and a view badge
 * (tools/golden/capture.mjs, more-info/controls.json `icons`), against the native colour as it resolves, fallbacks
 * included.
 */
class IconColorsGoldenTest {
    private val fixture = GoldenFixture("test-instance")
    private val icons = fixture.json("more-info/controls.json").obj("icons")!!
    private val hass: HassSnapshot = fixture.hass.copy(states = parseStates(JsonArray(icons.obj("states")!!.values.toList())))

    @TestFactory
    fun `Given each entity when drawing its icon in a tile then its colour is the frontend's`() = perEntity("tile") { id, captured ->
        val tile = hass.tileModel(CardConfig(json("""{"type": "tile", "entity": "$id"}""")), Instant.EPOCH)!!
        assertEquals(argb(captured.string("resolved")), resolveDisplayColor(tile.color, dark = false), "tile")
    }

    @TestFactory
    fun `Given each entity when drawing its row then its badge colour is the frontend's`() = perEntity("row") { id, captured ->
        assertRow(id, captured, stateColor = null)
    }

    @TestFactory
    fun `Given each entity when drawing its row with state colours then its badge colour is the frontend's`() = perEntity("rowStateColor") { id, captured -> assertRow(id, captured, stateColor = true) }

    private fun assertRow(id: String, captured: JsonObject, stateColor: Boolean?) {
        val card = buildJsonObject {
            put("type", "entities")
            stateColor?.let { put("state_color", it) }
            put("entities", buildJsonArray { add(JsonPrimitive(id)) })
        }
        val badge = hass.entitiesModel(CardConfig(card), Instant.EPOCH).rows.single().badge!!
        assertEquals(captured.boolean("picture"), badge.picture != null, "picture")
        if (badge.picture != null) return
        val tint = badge.color?.let { resolveDisplayColor(it, dark = false) }
            ?: theme(if (badge.unavailable) "state-unavailable-color" else "state-icon-color")
        assertEquals(argb(captured.string("resolved")), tint, "colour")
        assertEquals(captured.string("filter")?.ifEmpty { null }, badge.brightness?.let { "brightness(${percent(it)}%)" }, "brightness")
    }

    @TestFactory
    fun `Given each entity when drawing its heading badge coloured by state then its colour is the frontend's`() = perEntity("heading") { id, captured ->
        val heading = CardConfig(json("""{"type": "heading", "heading": "Icons", "badges": [{"type": "entity", "entity": "$id", "color": "state"}]}"""))
        val badge = hass.headingModel(heading, ConditionContext(maxColumns = 1), Instant.EPOCH).badges.single() as HeadingBadgeModel.Entity
        // Without a colour of its own, the icon inherits the badge's text colour
        val tint = badge.color?.let { resolveDisplayColor(it, dark = false) } ?: theme("secondary-text-color")
        assertEquals(argb(captured.string("resolved")), tint)
    }

    @TestFactory
    fun `Given each entity when drawing its view badge then its colour is the frontend's`() = perEntity("badge") { id, captured ->
        val view = ViewConfig(json("""{"badges": [{"type": "entity", "entity": "$id"}]}"""))
        val badge = hass.viewHeader(view, ConditionContext(maxColumns = 1), Instant.EPOCH)!!.badges.single()
        val tint = badge.color?.let { resolveDisplayColor(it, dark = false) }
            ?: theme(if (badge.active) "primary-color" else "state-inactive-color")
        assertEquals(argb(captured.string("resolved")), tint)
    }

    @TestFactory
    fun `Given each entity when drawing it in a glance card then its colour is the frontend's`() = perEntity("glance") { id, captured ->
        val entity = hass.glanceCardModel(CardConfig(json("""{"type": "glance", "entities": ["$id"]}""")), Instant.EPOCH).entities.single()
        assertEquals(captured.boolean("picture"), entity.picture != null, "picture")
        if (entity.picture != null) return@perEntity
        val tint = entity.color?.let { resolveDisplayColor(it, dark = false) }
            ?: theme(if (entity.unavailable) "state-unavailable-color" else "state-icon-color")
        assertEquals(argb(captured.string("resolved")), tint, "colour")
        assertEquals(captured.string("filter")?.ifEmpty { null }, entity.brightness?.let { "brightness(${percent(it)}%)" }, "brightness")
    }

    @TestFactory
    fun `Given each entity when drawing its button card then its colour is the frontend's`() = perEntity("button") { id, captured ->
        val button = hass.buttonCardModel(CardConfig(json("""{"type": "button", "entity": "$id"}""")))
        assertCardIcon(captured, button.color, button.brightness)
    }

    @TestFactory
    fun `Given each entity when drawing its entity card then its colour is the frontend's`() = perEntity("entity") { id, captured ->
        val card = hass.entityCardModel(CardConfig(json("""{"type": "entity", "entity": "$id"}"""))) as EntityCardModel.Shown
        assertCardIcon(captured, card.color, card.brightness)
    }

    /** A card icon: its colour, else `--state-icon-color` (the unavailable colour for an unavailable entity). */
    private fun assertCardIcon(captured: JsonObject, color: DisplayColor?, brightness: Double?) {
        val tint = color?.let { resolveDisplayColor(it, dark = false) } ?: theme("state-icon-color")
        assertEquals(argb(captured.string("resolved")), tint, "colour")
        assertEquals(captured.string("filter")?.ifEmpty { null }, brightness?.let { "brightness(${percent(it)}%)" }, "brightness")
    }

    private fun perEntity(context: String, check: (String, JsonObject) -> Unit): List<DynamicTest> = icons.obj("entities")!!.mapNotNull { (id, value) ->
        (value as JsonObject).obj(context)?.let { captured -> DynamicTest.dynamicTest("$context $id") { check(id, captured) } }
    }

    private fun theme(variable: String) = resolveDisplayColor(DisplayColor.State(listOf(variable)), dark = false)

    /** A colour the browser wrote: `#rrggbb` or `rgb(r, g, b)`. */
    private fun argb(css: String?): Long? = css?.let(::parseCssColor)

    /** [factor] as CSS writes the filter's percentage ("85" for 0.85, "50.2" for 0.502). */
    private fun percent(factor: Double): String {
        val value = factor * HUNDRED
        return if (value == Math.rint(value)) value.toLong().toString() else value.toBigDecimal().stripTrailingZeros().toPlainString()
    }

    private fun json(text: String): JsonObject = io.homeassistant.companion.android.dashboard.json(text)

    private companion object {
        const val HUNDRED = 100.0
    }
}
