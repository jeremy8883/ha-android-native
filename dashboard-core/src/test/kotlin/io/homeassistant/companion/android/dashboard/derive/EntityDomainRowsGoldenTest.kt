package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.number
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.moreinfo.withState
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * The entities card's domain rows (group, valve, cover, climate and water heater, humidifier, event, update, media
 * player, weather) against the frontend's (20260624.6, tools/golden/capture.mjs, more-info/controls.json `rows`):
 * the switch, the icon buttons, the texts beside and under, and the volume.
 */
class EntityDomainRowsGoldenTest {
    private val fixture = GoldenFixture("test-instance")

    @TestFactory
    fun `Given a domain with its own row when deriving it then it shows what the frontend's does`(): List<DynamicTest> = fixture.json("more-info/controls.json").obj("rows")!!.filterKeys { it.substringBefore('.') in DOMAINS }
        .flatMap { (entityId, variants) ->
            (variants as JsonObject).map { (name, captured) ->
                DynamicTest.dynamicTest("$entityId $name") { assertRow(entityId, captured.jsonObject) }
            }
        }

    private fun assertRow(entityId: String, captured: JsonObject) {
        val (hass, _) = fixture.withState(captured.obj("stateObj")!!)
        val drawn = captured.obj("row")!!
        val card = CardConfig(JsonObject(mapOf("type" to JsonPrimitive("entities"), "entities" to JsonArray(listOf(JsonPrimitive(entityId))))))
        val row = hass.entitiesModel(card, Instant.EPOCH).rows.single()
        val control = row.control
        // The switch, checked as upstream's
        assertEquals(drawn.obj("toggle")?.let { it["checked"].toString() }, (control as? RowControl.Toggle)?.checked?.toString(), "switch")
        assertEquals(drawn.objects("iconButtons").filter { it["hidden"].toString() != "true" }.map { it.string("label") }, iconLabels(control), "icon buttons")
        drawn.obj("volume")?.let { assertEquals(it.number("value"), (control as RowMedia).volume?.level, "volume") }
        drawn.string("secondary")?.let { assertEquals(it, row.domain?.nameSecondary, "under the name") }
        val right = listOfNotNull(drawn.string("right"), drawn.string("what")).joinToString(" ")
        when (entityId.substringBefore('.')) {
            // The weather row's line under the temperature comes from its forecast subscription
            "weather" -> assertEquals(true, squash(right).startsWith(squash(row.state)), "$right starts with ${row.state}")
            "climate", "water_heater", "humidifier" -> assertEquals(squash(right), squash(listOfNotNull(row.state, row.domain?.stateSecondary).joinToString(" ")))
            // Its time is relative to when it was drawn
            "event" -> assertEquals(drawn.string("what"), row.domain?.stateSecondary)
            "update", "group", "valve" -> if (control == null) assertEquals(true, drawn.string("text")?.endsWith(row.state.orEmpty()), "state")
        }
    }

    /** The shown icon buttons of a row, in the order drawn: the row's, then the volume line's. */
    private fun iconLabels(control: RowControl?): List<String?> = when (control) {
        is RowControl.IconButtons -> control.buttons.filter { it.shown }.map { it.label }
        is RowMedia -> (control.top + listOfNotNull(control.volume?.mute) + control.volume?.steps.orEmpty() + control.bottom).map { it.label }
        else -> emptyList()
    }

    /** Text without its spacing, which the browser's layout decides. */
    private fun squash(text: String?): String = text.orEmpty().filterNot { it.isWhitespace() }

    private companion object {
        val DOMAINS = setOf("group", "valve", "cover", "climate", "water_heater", "humidifier", "event", "update", "media_player", "weather")
    }
}
