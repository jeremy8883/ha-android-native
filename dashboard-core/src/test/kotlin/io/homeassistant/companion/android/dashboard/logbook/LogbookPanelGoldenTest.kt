package io.homeassistant.companion.android.dashboard.logbook

import io.homeassistant.companion.android.dashboard.derive.DisplayColor
import io.homeassistant.companion.android.dashboard.entity.parseStates
import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import io.homeassistant.companion.android.dashboard.model.array
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Differential test of the Logbook panel's rows ("list" layout with causes) against what the frontend drew on a phone
 * (20260624.6, tools/golden/capture.mjs --logbook-panel, logbook/panel.json).
 */
class LogbookPanelGoldenTest {

    private val fixture = GoldenFixture("test-instance")
    private val capture = fixture.json("logbook/panel.json")
    private val hass = fixture.hass.copy(states = parseStates(JsonArray(capture.obj("states")!!.values.toList())))

    @Test
    fun `Given the Logbook panel's entries when naming its rows then they read as the frontend's`() {
        val users = LogbookUsers(
            capture.obj("userIdToName")!!.mapValues { it.value.stringOrNull.orEmpty() },
            capture.array("systemUserIds")!!.mapNotNull { it.stringOrNull }.toSet(),
        )
        val traces = parseTraceContexts(capture.obj("traceContexts")!!)
        val entries = parseLogbookEvents(JsonObject(mapOf("events" to capture["entries"]!!)))!!
        val rows = hass.logbookRows(entries, users, traces, Instant.parse(capture.string("capturedAt")), named = true)

        val expected = capture.objects("rows")
        // The list is virtualized: the rows drawn are the first ones
        assertEquals(true, expected.size in 1..rows.size, "${expected.size} rows drawn of ${rows.size}")
        expected.zip(rows).forEachIndexed { index, (row, actual) ->
            val at = "row $index"
            assertEquals(row.string("subject"), actual.subject?.name, "$at subject")
            assertEquals(row.string("value"), actual.text, "$at value")
            assertEquals(row.string("context").orEmpty(), actual.subject?.context.orEmpty(), "$at context")
            assertEquals(row.string("time"), hass.formats.timeWithSeconds(Instant.ofEpochMilli(actual.whenMillis)), "$at time")
            assertEquals(row.string("dateHeader"), actual.dateHeader, "$at date header")
            assertEquals("last-of-day" in row.array("classes")!!.map { it.stringOrNull }, actual.lastOfDay, "$at last of day")
            assertEquals(row.string("traceLink"), actual.traceLink, "$at trace")
            assertCauseLine(row, actual, at)
            assertDot(row, actual.dot, at)
        }
    }

    private fun assertCauseLine(row: JsonObject, actual: LogbookRow, at: String) {
        val phrase = actual.cause?.let { hass.causePhrase(it) }
        val text = listOfNotNull(phrase?.text?.trim(), phrase?.link?.name, actual.traceLink?.let { VIEW_TRACE })
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        assertEquals(row.string("causeLine"), text.ifEmpty { null }, "$at cause line")
        assertEquals(row.string("causeEntity"), phrase?.link?.entityId, "$at cause entity")
    }

    /** The dot's colour as the CSS variables it falls back through, `null` when the style sheet colours its kind. */
    private fun assertDot(row: JsonObject, actual: LogbookDot, at: String) {
        val expected = row.string("dotColor")?.let { VARIABLE.findAll(it).map { match -> match.groupValues[1] }.toList() }
        when (actual) {
            LogbookDot.Unavailable -> assertEquals("true", row["dotUnavailable"].toString(), "$at hollow dot")
            is LogbookDot.Theme -> assertEquals(null, expected, "$at dot")
            is LogbookDot.State -> assertEquals(expected, (actual.color as? DisplayColor.State)?.variables, "$at dot")
            is LogbookDot.Timeline -> error("$at: a named row's dot has the state's colour, not the timeline's")
        }
    }

    private companion object {
        const val VIEW_TRACE = "View trace"
        val VARIABLE = Regex("--([a-z0-9-]+)")
    }
}
