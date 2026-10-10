package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.TestFactory

/**
 * Differential tests of the media browser against the real frontend's `ha-media-player-browse` (20260624.6): the
 * demo browse player's media sources and its Camera source, each page's layout and children, and the call of
 * playing an item.
 */
class MediaBrowserGoldenTest {
    private val fixture = GoldenFixture("test-instance")

    @TestFactory
    fun `Given captured browser pages when deriving them then they match the frontend's pages and play call`() = fixture.controlVariants("media_player", onlyAsIs = true) { hass, state, captured ->
        val browser = captured.obj("browser") ?: return@controlVariants
        val pages = browser.objects("pages")
        assertTrue(pages.isNotEmpty(), "the browser opened")
        pages.forEach { shown ->
            val result = JsonObject(shown.obj("item")!! + ("children" to JsonArray(shown.objects("children"))))
            val page = hass.mediaBrowsePage(state.entityId, result)
            assertEquals(shown.string("layout"), if (page.layout is MediaBrowseLayout.Grid) "grid" else "list")
            assertEquals(
                shown.objects("children").map { child ->
                    listOf(child.string("title"), child["can_play"].toString() == "true", child["can_expand"].toString() == "true")
                },
                page.children.map { listOf(it.title, it.play != null, it.open != null) },
            )
        }
        val played = browser["played"] as JsonArray
        if (played.isNotEmpty()) {
            val camera = pages.last()
            val result = JsonObject(camera.obj("item")!! + ("children" to JsonArray(camera.objects("children"))))
            val first = hass.mediaBrowsePage(state.entityId, result).children.first { it.play != null }
            assertEquals(JsonObject(mapOf("calls" to played)).recordedCalls(), listOf(first.play))
        }
    }
}
