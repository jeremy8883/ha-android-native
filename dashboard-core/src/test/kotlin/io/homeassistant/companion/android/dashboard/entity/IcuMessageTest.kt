package io.homeassistant.companion.android.dashboard.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class IcuMessageTest {

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            "Hello {name}                                                     | 1 | Hello Ada",
            "{count} {count, plural,\\n one {person}\\n other {people}\\n} home | 1 | 1 person home",
            "{count} {count, plural,\\n one {person}\\n other {people}\\n} home | 3 | 3 people home",
            "{count, plural, =0 {none} one {# item} other {# items}}        | 0 | none",
            "{count, plural, =0 {none} one {# item} other {# items}}        | 7 | 7 items",
            "{kind, select, light {Lamp} other {Thing}}                      | 1 | Thing",
            "It''s {name}                                                     | 1 | It's Ada",
            "Broken {count, plural, one {x}                                   | 1 | Broken {count, plural, one {x}",
        ],
    )
    fun `Given an ICU message when formatting then arguments, plurals and selects are applied`(
        pattern: String,
        count: String,
        expected: String,
    ) {
        val message = pattern.replace("\\n", "\n")
        assertEquals(expected, formatIcuMessage(message, mapOf("name" to "Ada", "count" to count, "kind" to "fan")))
    }
}
