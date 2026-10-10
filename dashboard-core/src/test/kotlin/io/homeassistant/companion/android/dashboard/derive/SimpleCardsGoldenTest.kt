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
import io.homeassistant.companion.android.dashboard.model.array
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.number
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import io.homeassistant.companion.android.dashboard.moreinfo.css
import io.homeassistant.companion.android.dashboard.moreinfo.recordedCalls
import io.homeassistant.companion.android.dashboard.weather.ForecastKey
import io.homeassistant.companion.android.dashboard.weather.WeatherIcon
import io.homeassistant.companion.android.dashboard.weather.parseForecastEvent
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
            // The browser spaces out the colours it was given
            listOf(captured.string("name"), captured.string("value"), captured.string("unit"), captured.boolean("unitFirst"), captured.string("color")?.replace(" ", "")),
            listOf(model.name, model.value.value, model.value.unit, model.value.unitFirst, model.color?.css()?.replace(" ", "")),
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

    @TestFactory
    fun `Given captured light cards when deriving them then they show as the frontend's`() = cards("light") { config, shown ->
        val model = lightCardModel(config)
        val light = shown.obj("light")
        if (light == null) {
            assertEquals(shown.string("warning"), (model as? LightCardModel.Warning)?.text)
            return@cards
        }
        model as LightCardModel.Shown
        // Lit's comment markers aside, the info is the state or the (hidden) brightness, then the name
        val info = light.array("info")!!.map { it.stringOrNull }.filterNot { it.orEmpty().startsWith("?lit") }
        assertEquals(info, listOf(model.stateText ?: "%", model.name))
        assertEquals(light.number("brightness")?.toInt(), model.brightness)
        assertEquals(light.boolean("sliderVisible"), model.supportsBrightness)
        assertEquals(light.boolean("disabled"), model.disabled)
        val classes = light.array("classes")!!.map { it.stringOrNull }
        val expectedColor = light.string("color")?.let { DisplayColor.Literal(it.replace(", ", ",")) }
            ?: when {
                "state-on" in classes -> DisplayColor.State(listOf("state-light-active-color"))
                "state-unavailable" in classes -> DisplayColor.State(listOf("state-unavailable-color"))
                else -> DisplayColor.State(listOf("state-icon-color"))
            }
        assertEquals(expectedColor, model.color)
        light.string("icon")?.let { assertEquals(it, model.icon) }
    }

    @TestFactory
    fun `Given captured alarm panel cards when deriving them then they show and arm as the frontend's`() = cards("alarm-panel") { config, shown ->
        val model = alarmPanelCardModel(config)
        val alarm = shown.obj("alarm")
        if (alarm == null) {
            assertEquals(shown.string("warning"), (model as? AlarmPanelCardModel.Warning)?.text)
            return@cards
        }
        model as AlarmPanelCardModel.Shown
        val pulsing = alarm.array("classes")!!.any { it.stringOrNull in setOf("triggered", "arming", "pending") }
        assertEquals(
            listOf(alarm.string("name"), alarm.string("state"), alarm.string("color"), pulsing),
            listOf(model.name, model.stateLabel, model.stateColor?.css(), model.pulsing),
        )
        assertEquals(
            alarm.objects("actions").map { Triple(it.string("label"), "alarm_" + it.string("action"), it.string("variant") == "danger") },
            model.actions.map { Triple(it.label, it.service, it.disarm) },
        )
        assertEquals(alarm.boolean("input"), model.code != null)
        assertEquals(alarm.boolean("keypad"), model.code?.keypad == true)
    }

    @TestFactory
    fun `Given captured weather forecast cards when deriving them then they show as the frontend's`() = cards("weather-forecast") { config, shown ->
        val weather = shown.obj("weather")
        val event = weather?.obj("event")?.let(::parseForecastEvent)
        val hass = if (event == null) this else copy(forecasts = mapOf(ForecastKey(config.entity!!, event.type) to event))
        val model = hass.weatherForecastCardModel(config, Instant.ofEpochMilli(weather?.number("now")?.toLong() ?: 0))
        if (weather == null) {
            assertEquals(shown.string("warning"), (model as? WeatherForecastCardModel.Warning)?.text)
            return@cards
        }
        model as WeatherForecastCardModel.Shown
        val current = weather.obj("current")
        assertEquals(current == null, model.current == null, "current")
        model.current?.let { actual ->
            current!!
            assertEquals(
                listOf(
                    current.drawingClasses(),
                    current.boolean("stateIcon"),
                    current.string("state"),
                    current.string("name"),
                    current.string("temp"),
                    current.string("attribute"),
                    current.boolean("attributeIcon"),
                ),
                listOf(
                    actual.condition.classes(),
                    actual.condition == null,
                    actual.state,
                    actual.name,
                    actual.temperature?.let { "$it ${actual.temperatureUnit}" },
                    actual.secondary?.let { listOfNotNull(it.label, it.value).joinToString(" ") },
                    actual.secondary?.icon != null,
                ),
            )
        }
        val items = model.forecast?.groups?.flatten()
        assertEquals(
            (weather["forecast"] as? JsonArray)?.map { it as JsonObject }?.map {
                listOf(it.string("header"), it.string("label"), it.drawingClasses(), it.string("temp"), it.string("templow"))
            },
            items?.map { listOf(it.dayHeader, it.label, it.condition.classes(), it.temperature, it.low) },
        )
    }

    @TestFactory
    fun `Given captured thermostat cards when deriving them then they show as the frontend's`() = cards("thermostat") { config, shown ->
        val model = thermostatCardModel(config)
        val thermostat = shown.obj("thermostat")
        if (thermostat == null) {
            assertEquals(shown.string("warning"), (model as? ThermostatCardModel.Warning)?.text)
            return@cards
        }
        model as ThermostatCardModel.Shown
        assertEquals(listOf(thermostat.string("name"), thermostat.string("secondary")), listOf(model.name, model.secondary))
    }

    private fun JsonObject.drawingClasses(): List<String?>? = (this["drawing"] as? JsonArray)?.map { it.stringOrNull }

    /** The drawing's parts by their `weatherSVGStyles` class. */
    private fun WeatherIcon?.classes(): List<String>? = (this as? WeatherIcon.Drawing)?.parts?.map { it.paint.name.lowercase().replace('_', '-') }

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
