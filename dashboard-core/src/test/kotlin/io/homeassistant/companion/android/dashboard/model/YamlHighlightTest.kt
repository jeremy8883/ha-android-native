package io.homeassistant.companion.android.dashboard.model

import io.homeassistant.companion.android.dashboard.model.YamlTokenKind.ATOM
import io.homeassistant.companion.android.dashboard.model.YamlTokenKind.BLOCK_HEADER
import io.homeassistant.companion.android.dashboard.model.YamlTokenKind.KEY
import io.homeassistant.companion.android.dashboard.model.YamlTokenKind.NUMBER
import io.homeassistant.companion.android.dashboard.model.YamlTokenKind.PLAIN
import io.homeassistant.companion.android.dashboard.model.YamlTokenKind.SEPARATOR
import io.homeassistant.companion.android.dashboard.model.YamlTokenKind.STRING
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class YamlHighlightTest {
    private fun kinds(yaml: String) = yamlHighlight(yaml).map { line -> line.map { it.text to it.kind } }

    @Test
    fun `Given keys and scalars when highlighting then keys, separators and each scalar type are told apart`() {
        val yaml = "state:\n  raw: cool\n  \"2\": \"off\"\n  min: 7\n  step: 0.5\n  on: true\n  none: null\n"
        assertEquals(
            listOf(
                listOf("state" to KEY, ":" to SEPARATOR),
                listOf("  " to PLAIN, "raw" to KEY, ":" to SEPARATOR, " " to PLAIN, "cool" to STRING),
                listOf("  " to PLAIN, "\"2\"" to KEY, ":" to SEPARATOR, " " to PLAIN, "\"off\"" to STRING),
                listOf("  " to PLAIN, "min" to KEY, ":" to SEPARATOR, " " to PLAIN, "7" to NUMBER),
                listOf("  " to PLAIN, "step" to KEY, ":" to SEPARATOR, " " to PLAIN, "0.5" to NUMBER),
                listOf("  " to PLAIN, "on" to KEY, ":" to SEPARATOR, " " to PLAIN, "true" to ATOM),
                listOf("  " to PLAIN, "none" to KEY, ":" to SEPARATOR, " " to PLAIN, "null" to ATOM),
                emptyList(),
            ),
            kinds(yaml),
        )
    }

    @Test
    fun `Given sequences when highlighting then dashes are separators, also before a key`() {
        assertEquals(
            listOf(
                listOf("modes" to KEY, ":" to SEPARATOR),
                listOf("  " to PLAIN, "-" to SEPARATOR, " " to PLAIN, "heat" to STRING),
                listOf("  " to PLAIN, "-" to SEPARATOR, " " to PLAIN, "a" to KEY, ":" to SEPARATOR, " " to PLAIN, "{}" to SEPARATOR),
            ),
            kinds("modes:\n  - heat\n  - a: {}"),
        )
    }

    @Test
    fun `Given a block scalar when highlighting then its header is marked and its lines are plain`() {
        assertEquals(
            listOf(
                listOf("text" to KEY, ":" to SEPARATOR, " " to PLAIN, "|-" to BLOCK_HEADER),
                listOf("  a: b" to PLAIN),
                listOf("  - c" to PLAIN),
                listOf("next" to KEY, ":" to SEPARATOR, " " to PLAIN, "http://x" to STRING),
            ),
            kinds("text: |-\n  a: b\n  - c\nnext: http://x"),
        )
    }
}
