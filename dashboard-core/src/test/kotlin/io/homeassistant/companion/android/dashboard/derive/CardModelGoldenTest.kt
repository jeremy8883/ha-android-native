package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import io.homeassistant.companion.android.dashboard.json
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * Differential tests of card display models against what the real frontend's card elements rendered for the same
 * configs (outputs/cards.json, captured by tools/golden/capture.mjs).
 */
class CardModelGoldenTest {

    @TestFactory
    fun `Given captured server data when deriving home summaries then they match hui-home-summary-card`(): List<DynamicTest> = GoldenFixture.VARIANTS.flatMap { variant ->
        val fixture = GoldenFixture(variant)
        val captured = fixture.json("outputs/cards.json").obj("home-summary") ?: JsonObject(emptyMap())
        captured.map { (summary, expected) ->
            DynamicTest.dynamicTest("$variant $summary") {
                expected as JsonObject
                val model = fixture.hass.homeSummaryModel(CardConfig(json("""{"type": "home-summary", "summary": "$summary"}""")))
                assertEquals(
                    Triple(expected.string("primary"), expected.string("secondary"), expected.boolean("loading")),
                    Triple(model?.label, model?.secondary, model?.loading),
                )
            }
        }
    }

    @TestFactory
    fun `Given captured server data when deriving info cards then text and self-hiding match the frontend`(): List<DynamicTest> = GoldenFixture.VARIANTS.flatMap { variant ->
        val fixture = GoldenFixture(variant)
        val captured = fixture.json("outputs/cards.json").obj("info") ?: JsonObject(emptyMap())
        captured.map { (key, expected) ->
            DynamicTest.dynamicTest("$variant $key") {
                expected as JsonObject
                val type = key.substringBefore(':')
                val hideEmpty = key.endsWith(":hide_empty")
                val card = CardConfig(json("""{"type": "$type", "hide_empty": $hideEmpty}"""))
                val hidden = fixture.hass.cardHidesItself(card)
                val model = when (type) {
                    "repairs" -> fixture.hass.repairsModel(card)
                    "updates" -> fixture.hass.updatesModel(card)
                    else -> fixture.hass.discoveredDevicesModel(card)
                }.takeUnless { hidden }
                assertEquals(
                    Triple(expected.boolean("hidden"), expected.string("primary"), expected.string("secondary")),
                    Triple(hidden, model?.label, model?.secondary),
                )
            }
        }
    }
}
