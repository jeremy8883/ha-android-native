package io.homeassistant.companion.android.dashboard.logbook

import io.homeassistant.companion.android.dashboard.derive.DisplayColor
import io.homeassistant.companion.android.dashboard.display.shadeRgb
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.entity.parseStates
import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import io.homeassistant.companion.android.dashboard.history.TimelineColor
import io.homeassistant.companion.android.dashboard.model.array
import io.homeassistant.companion.android.dashboard.model.number
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import io.homeassistant.companion.android.dashboard.theme.themeColorArgb
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/** Differential tests of the more-info dialog's logbook, against what the frontend loaded and drew. */
class LogbookGoldenTest {

    private val fixture = GoldenFixture(GoldenFixture.VARIANTS.first())
    private val capture = fixture.json("logbook/more-info.json")
    private val recorded = capture.obj("entities")!!
    private val capturedAt = Instant.parse(capture.string("capturedAt")!!)

    @TestFactory
    fun `Given an entity's logbook stream when gathering its entries then they are the frontend's`(): List<DynamicTest> = recorded.map { (entityId, value) ->
        DynamicTest.dynamicTest(entityId) {
            val entity = value as JsonObject
            val expected = entity.objects("entries")
            assertEquals(expected.map { it.number("when") }, entity.entries().map { it.whenSeconds })
            assertEquals(expected.map { it.string("state") }, entity.entries().map { it.state })
        }
    }

    @TestFactory
    fun `Given an entity's details when subscribing to its logbook then the request is the frontend's`(): List<DynamicTest> = recorded.map { (entityId, value) ->
        DynamicTest.dynamicTest(entityId) {
            val entity = value as JsonObject
            val request = entity.objects("requests").single { it.string("type") == "logbook/event_stream" }
            val start = Instant.parse(request.string("start_time")!!)
            val command = logbookStreamCommand(entityId, start.plusSeconds(LOGBOOK_RECENT_SECONDS))
            assertEquals(request, JsonObject(command.params + ("type" to kotlinx.serialization.json.JsonPrimitive(command.type))))
            assertTrue(entity.hass().showsLogbook(entityId))
        }
    }

    @Test
    fun `Given continuous or camera entities when showing their details then they have no logbook`() {
        val hass = fixture.hass
        listOf("sensor.house_power", "counter.coffee_cups").forEach { assertFalse(hass.showsLogbook(it), it) }
        hass.states.keys.filter { it.startsWith("camera.") }.forEach { assertFalse(hass.showsLogbook(it), it) }
    }

    @TestFactory
    fun `Given an entity's logbook when drawing its rows then they read as the frontend's`(): List<DynamicTest> = recorded.map { (entityId, value) ->
        DynamicTest.dynamicTest(entityId) {
            val entity = value as JsonObject
            val hass = entity.hass()
            val users = hass.logbookUsers(entity.result("config/auth/list") as? JsonArray)
            assertEquals(entity.obj("userIdToName")!!.mapValues { it.value.stringOrNull }, users.names)
            assertEquals(entity.array("systemUserIds")!!.map { it.stringOrNull }.toSet(), users.systemUserIds)
            val traces = (entity.result("trace/contexts") as? JsonObject)?.let(::parseTraceContexts).orEmpty()
            assertEquals(entity.obj("traceContexts")!!.keys, traces.keys)

            val rows = hass.logbookRows(entity.entries(), users, traces, capturedAt)

            val expected = entity.objects("rows")
            assertEquals(expected.size, rows.size)
            expected.zip(rows).forEach { (row, actual) ->
                val at = Instant.ofEpochMilli(actual.whenMillis)
                assertEquals(row.string("primary"), actual.text, "text")
                assertEquals(row.string("time"), hass.formats.timeWithSeconds(at), "time")
                assertEquals(row.string("dateHeader"), actual.dateHeader, "date header")
                val node = row.array("nodeClasses")!!.map { it.stringOrNull }
                assertEquals("rail-trim-top" in node, actual.firstOfDay, "first of day")
                assertEquals("rail-trim-bottom" in node, actual.lastOfDay, "last of day")
                assertEquals(row.string("traceLink"), actual.traceLink, "trace")
                assertCause(row.obj("cause"), actual.cause)
                assertDot(row, actual.dot)
            }
        }
    }

    private fun assertCause(expected: JsonObject?, actual: LogbookCause?) {
        // A state change's cause shows no icon, but its badge still has the name
        assertEquals(expected?.string("tooltip"), actual?.name, "cause")
        val icon = when (actual) {
            is LogbookCause.User -> if (actual.systemUser && actual.name in SYSTEM_USERS) "ha-svg-icon" else "ha-user-badge"
            is LogbookCause.State, null -> null
            is LogbookCause.Integration -> if (actual.brandDomain != null) "ha-domain-icon" else "ha-svg-icon"
            else -> "ha-svg-icon"
        }
        assertEquals(expected?.string("icon"), icon, "cause icon")
    }

    private fun assertDot(row: JsonObject, actual: LogbookDot) {
        val expected = row.string("dotColor")
        when (actual) {
            is LogbookDot.Unavailable -> assertTrue(row.boolean("dotUnavailable"), "hollow dot")
            // Left to the style sheet, which colours the row's kind
            is LogbookDot.Theme -> assertEquals(null, expected, "dot")
            is LogbookDot.State -> assertEquals(expected, (actual.color as? DisplayColor.State)?.variables?.firstNotNullOfOrNull(::hex), "dot")
            is LogbookDot.Timeline -> {
                val color = resolve(actual.color)
                // A state's own colour, picked from the palette in the order states first showed in the page
                if (color == null) assertTrue(expected in PALETTE, "$expected is a palette colour") else assertEquals(expected, color, "dot")
            }
        }
    }

    private fun JsonObject.boolean(key: String) = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.content == "true"

    /** The entries as the stream's messages built them, each added as it came, old ones dropped from then. */
    private fun JsonObject.entries(): List<LogbookEntry> = objects("messages").filter { "events" in it }.fold(emptyList()) { acc, message ->
        acc.withStreamEvents(parseLogbookEvents(message)!!, message.number("receivedAt")!! / MILLIS - LOGBOOK_RECENT_SECONDS)
    }

    private fun JsonObject.result(type: String) = objects("messages").firstOrNull { it.obj("request")?.string("type") == type }?.get("result")

    private fun JsonObject.hass(): HassSnapshot {
        val state = this["state"] as? JsonObject
        return fixture.hass.copy(states = fixture.hass.states + state?.let { parseStates(JsonArray(listOf(it))) }.orEmpty())
    }

    private fun resolve(color: TimelineColor): String? {
        color.variables.firstNotNullOfOrNull { themeColorArgb(it, dark = false) }?.let { argb ->
            val rgb = (argb and RGB).toInt()
            return "#%06x".format(color.shade?.let { shadeRgb(rgb, it, brighten = true) } ?: rgb)
        }
        return color.paletteIndex?.let { PALETTE[it % PALETTE.size] }
    }

    private fun hex(variable: String) = themeColorArgb(variable, dark = false)?.let { "#%06x".format((it and RGB).toInt()) }

    private companion object {
        const val MILLIS = 1000.0
        const val RGB = 0xFFFFFFL
        val SYSTEM_USERS = setOf("Home Assistant Cloud", "Home Assistant Cast")

        /** The graph palette, `--color-1`... */
        val PALETTE = (1..54).mapNotNull { i -> themeColorArgb("color-$i", dark = false)?.let { "#%06x".format((it and RGB).toInt()) } }
    }
}
