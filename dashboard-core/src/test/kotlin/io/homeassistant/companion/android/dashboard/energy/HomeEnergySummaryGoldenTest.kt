package io.homeassistant.companion.android.dashboard.energy

import io.homeassistant.companion.android.dashboard.derive.homeSummaryModel
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.strategy.energy.HOME_ENERGY_COLLECTION_KEY
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Differential test of the home dashboard's energy summary against the frontend's tile. */
class HomeEnergySummaryGoldenTest {

    private val energy = EnergyFixture()
    private val card = CardConfig(JsonObject(mapOf("type" to JsonPrimitive("home-summary"), "summary" to JsonPrimitive("energy"))))

    @Test
    fun `Given today's energy when showing the energy summary then it reads like the frontend's tile`() {
        // The tile loads today on its own collection, which fetches what the energy dashboard's today does
        val today = energy.periods.first { it.name == "today" }
        val expected = today.card("home-summary-energy")
        val hass = energy.fixture.hass.copy(energy = mapOf(HOME_ENERGY_COLLECTION_KEY to collection(today.data)))

        val tile = hass.homeSummaryModel(card)!!

        assertEquals(expected.string("primary"), tile.label)
        assertEquals(expected.string("secondary"), tile.secondary)
        assertEquals(false, tile.loading)
    }

    @Test
    fun `Given the energy not loaded yet when showing the energy summary then it is loading`() {
        val tile = energy.fixture.hass.homeSummaryModel(card)!!

        assertTrue(tile.loading)
        assertEquals(false, tile.failed)
    }

    @Test
    fun `Given the energy failed to load when showing the energy summary then it says so rather than nothing`() {
        val failed = collection(null).copy(failed = true)
        val tile = energy.fixture.hass.copy(energy = mapOf(HOME_ENERGY_COLLECTION_KEY to failed)).homeSummaryModel(card)!!

        assertTrue(tile.failed)
        assertEquals(false, tile.loading)
    }

    private fun collection(data: EnergyData?) = EnergyCollection(
        period = EnergyPeriod(energy.capturedAt.atZone(java.time.ZoneOffset.UTC).toLocalDate(), energy.capturedAt.atZone(java.time.ZoneOffset.UTC).toLocalDate()),
        compareMode = null,
        data = data,
        loading = false,
        failed = false,
    )
}
