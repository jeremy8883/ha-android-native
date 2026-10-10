package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.action.Gesture
import io.homeassistant.companion.android.dashboard.action.resolveAction
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.entity.parseStates
import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import io.homeassistant.companion.android.dashboard.history.parseHistoryStates
import io.homeassistant.companion.android.dashboard.history.sensorGraphCoordinates
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.number
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.moreinfo.css
import io.homeassistant.companion.android.dashboard.moreinfo.recordedCalls
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * Differential tests of the simple cards against the real frontend's (20260624.6, drawn by
 * tools/golden/capture.mjs from the configs of its CARD_CONFIGS, more-info/controls.json `cards`): what each shows,
 * and what a button's tap does.
 */
class SimpleCardsGoldenTest {
    private val fixture = GoldenFixture("test-instance")

    private companion object {
        const val COORDINATE_TOLERANCE = 1e-6
    }

    @TestFactory
    fun `Given captured button cards when deriving them then they show and act as the frontend's`() = cards("button") { config, shown ->
        val model = buttonCardModel(config)
        val button = shown.obj("button")
        assertEquals(shown.string("warning") != null, model.missing != null)
        if (button == null || model.missing != null) return@cards
        assertEquals(
            listOf(button.string("name"), button.string("state"), button.boolean("showsIcon"), button.string("color")),
            listOf(model.name.orEmpty().ifEmpty { null }, model.state, model.icon != null, model.color?.css()),
        )
        val tap = shown.obj("tap")!!
        val action = resolveAction(model.actions.config, Gesture.TAP)?.action
        // Opening the details isn't a call; the capture can't see the dialog open, so only that nothing was called
        // A failure (no entity for the details) is a message, not a call, as upstream's toast
        assertEquals(tap.recordedCalls(), listOfNotNull(action.takeUnless { it is CardAction.MoreInfo || it is CardAction.Failure }))
    }

    @TestFactory
    fun `Given captured glance cards when deriving them then each entity shows as the frontend's`() = cards("glance") { config, shown ->
        val glance = shown.obj("glance")!!
        val model = glanceCardModel(config, Instant.EPOCH)
        assertEquals(glance.string("title"), model.title)
        assertEquals(
            glance.objects("entities").map {
                listOf(it.boolean("warning"), it.string("name"), it.boolean("showsBadge"), it.string("color"), it.boolean("picture"))
            },
            model.entities.map {
                listOf(it.missing, it.name, (it.icon != null || it.picture != null) && !it.missing, it.color?.css(), it.picture != null)
            },
        )
        // Relative times depend on when they were drawn (and upstream draws them in a shadow root the capture
        // reads as empty); the others are the formatted state
        glance.objects("entities").zip(model.entities).filter { (_, entity) -> !entity.missing }.forEach { (captured, entity) ->
            val text = captured.string("state")
            if (!text.isNullOrEmpty() && !text.endsWith("ago") && !text.startsWith("In ")) assertEquals(text, entity.state)
        }
    }

    @TestFactory
    fun `Given captured gauge cards when deriving them then they draw as ha-gauge`() = cards("gauge") { config, shown ->
        val model = gaugeCardModel(config)
        assertEquals(shown.string("warning"), model.warning)
        val gauge = shown.obj("gauge") ?: return@cards
        assertEquals(
            listOf(
                gauge.number("min"),
                gauge.number("max"),
                gauge.number("value"),
                gauge.string("text"),
                gauge.string("color"),
                gauge.boolean("needle"),
                gauge.objects("levels").map { Triple(it.number("level"), it.string("stroke"), it.string("label")) },
                gauge.string("name"),
            ),
            listOf(
                model.min,
                model.max,
                model.value,
                model.valueText,
                model.color?.css(),
                model.needle,
                model.levels.map { Triple(it.level, it.color.css(), it.label) },
                model.name,
            ),
        )
    }

    @TestFactory
    fun `Given captured entity and sensor cards when deriving them then they show as the frontend's`() = cards("entity") { config, shown -> assertEntityCard(config, shown) } +
        cards("sensor") { config, shown -> assertEntityCard(config, shown) }

    private fun HassSnapshot.assertEntityCard(config: CardConfig, shown: JsonObject) {
        val model = entityCardModel(config)
        val captured = shown.obj("entity")
        if (captured == null) {
            assertEquals(shown.string("warning"), (model as? EntityCardModel.Warning)?.text)
            return
        }
        model as EntityCardModel.Shown
        assertEquals(
            listOf(captured.string("name"), captured.string("value"), captured.string("unit"), captured.boolean("unitFirst"), captured.string("color")),
            listOf(model.name, model.value.value, model.value.unit, model.value.unitFirst, model.color?.css()),
        )
        captured.string("icon")?.let { assertEquals(it, model.icon) }
        val graph = shown.obj("graph")
        val sensorGraph = model.graph
        assertEquals(graph != null, sensorGraph != null, "graph")
        if (graph == null || sensorGraph == null) return
        val history = graph.obj("history")?.let(::parseHistoryStates)?.get(sensorGraph.entityId)
        val coordinates = sensorGraphCoordinates(
            sensorGraph,
            history,
            states[sensorGraph.entityId],
            graph.number("width")!!,
            graph.number("now")!!,
        )
        val expected = (graph["coordinates"] as JsonArray).map { point ->
            (point as JsonArray).map { (it as JsonPrimitive).double }
        }
        assertEquals(expected.size, coordinates.points.size, "points")
        expected.zip(coordinates.points).forEach { (e, a) ->
            assertEquals(e[0], a.x, COORDINATE_TOLERANCE, "x")
            assertEquals(e[1], a.y, COORDINATE_TOLERANCE, "y")
        }
    }

    /** Each captured card of [type], checked against the snapshot with the states it was drawn with. */
    private fun cards(type: String, check: HassSnapshot.(CardConfig, JsonObject) -> Unit): List<DynamicTest> = fixture.json("more-info/controls.json").objects("cards")
        .filter { it.obj("config")!!.string("type") == type }
        .mapIndexed { index, shown ->
            DynamicTest.dynamicTest("$type $index ${shown.obj("config")}") {
                val drawn = parseStates(JsonArray(shown.obj("states")!!.values.toList()))
                fixture.hass.copy(states = fixture.hass.states + drawn).check(CardConfig(shown.obj("config")!!), shown)
            }
        }
}
