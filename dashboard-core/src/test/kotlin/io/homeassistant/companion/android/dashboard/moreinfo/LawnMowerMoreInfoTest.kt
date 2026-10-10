package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * Tests of a lawn mower's details against upstream's `more-info-lawn_mower` logic (frontend@20260624.6): the test
 * instance has no lawn mower to capture, so these follow the source.
 */
class LawnMowerMoreInfoTest {
    private val fixture = GoldenFixture("test-instance")

    private fun details(state: String, features: Int) = fixture.withState(
        Json.parseToJsonElement(
            """{"entity_id": "lawn_mower.garden", "state": "$state",
                "attributes": {"friendly_name": "Garden", "supported_features": $features}}""",
        ) as JsonObject,
    ).let { (hass, mower) -> hass.lawnMowerMoreInfo(mower)!! }

    @ParameterizedTest
    @CsvSource(
        "mowing, 7, Pause, pause, true, Return to dock, true",
        "docked, 7, Start mowing, start_mowing, true, Return to dock, false",
        "paused, 7, Start mowing, start_mowing, true, Return to dock, true",
        "mowing, 5, Start mowing, pause, true, Return to dock, true",
        "unavailable, 7, Start mowing, start_mowing, false, Return to dock, false",
    )
    fun `Given a lawn mower when deriving its buttons then they follow its state and features`(
        state: String,
        features: Int,
        startLabel: String,
        startService: String,
        startEnabled: Boolean,
        dockLabel: String,
        dockEnabled: Boolean,
    ) {
        val (start, dock) = details(state, features).buttons
        assertEquals(listOf(startLabel, startService, startEnabled), listOf(start.label, start.action.service, start.enabled))
        assertEquals(listOf(dockLabel, "dock", dockEnabled), listOf(dock.label, dock.action.service, dock.enabled))
    }

    @Test
    fun `Given a lawn mower that only docks when deriving its buttons then only dock shows`() {
        assertEquals(listOf("dock"), details("mowing", 4).buttons.map { it.action.service })
    }

    @Test
    fun `Given a mowing or returning lawn mower when deriving its status then it is busy`() {
        assertEquals(listOf(true, true, false), listOf("mowing", "returning", "docked").map { details(it, 7).busy })
    }
}
