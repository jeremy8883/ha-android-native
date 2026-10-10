package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.entity.parseStates
import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import io.homeassistant.companion.android.dashboard.model.array
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/**
 * Differential tests of a light's favourite colours and effect icons against the real frontend (20260624.6): the
 * capture rendered `more-info-light` with each light's registry entry (one with saved favourites of every kind,
 * one with none saved, the others with the defaults) and recorded the favourites, their labels in and out of edit
 * mode, the call each makes and the icon of each effect (more-info/controls.json).
 */
class LightFavoritesGoldenTest {
    private val fixture = GoldenFixture("test-instance")

    @TestFactory
    fun `Given captured lights when deriving their favourites then they match ha-more-info-light-favorite-colors`() = perVariant { hass, state, captured ->
        val expected = captured.obj("main")!!.obj("favorites")?.favoritesText()
        val entry = captured.obj("entry")
        assertEquals(expected, hass.lightFavorites(state, entry, editMode = false)?.text())
    }

    @TestFactory
    fun `Given captured lights in edit mode when deriving their favourites then the labels match`() = perVariant(onlyAsIs = true) { hass, state, captured ->
        val edit = captured.obj("editMode")!!
        val favorites = hass.lightFavorites(state, captured.obj("entry"), editMode = true)!!
        assertEquals(edit.favoritesText(), favorites.text())
        assertEquals(edit.array("deleteLabels")!!.map { it.stringOrNull }, favorites.favorites.map { it.deleteLabel })
        assertEquals(edit.array("buttonsLabels")!!.map { it.stringOrNull }, listOf(favorites.addLabel, favorites.doneLabel))
    }

    @TestFactory
    fun `Given captured lights when tapping a favourite then the call matches the frontend's`() = perVariant(onlyAsIs = true) { hass, state, captured ->
        val expected = captured.objects("calls").filter { it.string("control") == "favorite" }.map { call ->
            call.objects("calls").single().let {
                CardAction.CallService(it.string("domain")!!, it.string("service")!!, it.obj("data"), null)
            }
        }
        val favorites = hass.lightFavorites(state, captured.obj("entry"), editMode = false)
        assertEquals(expected, favorites?.favorites?.map { it.apply }.orEmpty())
    }

    @TestFactory
    fun `Given captured lights when deriving the effect icons then they match ha-attribute-icon`() = perVariant(onlyAsIs = true) { hass, state, captured ->
        val expected = captured.obj("effectIcons")!!.mapValues { it.value.stringOrNull }
        val actual = hass.lightMoreInfo(state)!!.effect?.options?.associate { it.value to it.icon }.orEmpty()
        assertEquals(expected, actual)
    }

    @Test
    fun `Given favourites when saving and resetting them then the registry update is upstream's`() {
        val colors = listOf(LightColor.Hs(240.0, 100.0), LightColor.ColorTemp(3000.0))
        assertEquals(
            """{"entity_id":"light.a","options_domain":"light","options":{"favorite_colors":""" +
                """[{"hs_color":[240,100]},{"color_temp_kelvin":3000}]}}""",
            favoriteColorsUpdate("light.a", colors).params.toString(),
        )
        assertEquals(
            """{"entity_id":"light.a","options_domain":"light","options":{}}""",
            favoriteColorsUpdate("light.a", null).params.toString(),
        )
    }

    @Test
    fun `Given favourites of whites and RGBW colours when copying them then only lights taking both are offered`() {
        val hass = fixture.hass
        val whites = listOf(LightColor.ColorTemp(3000.0))
        assertEquals(
            listOf("light.bed_light", "light.ceiling_lights", "light.kitchen_lights"),
            hass.favoriteCopyTargets("light.living_room_rgbww_lights", whites).sorted(),
        )
        val rgbw = listOf(LightColor.Rgbw(listOf(0.0, 255.0, 0.0, 40.0)))
        assertEquals(emptyList<String>(), hass.favoriteCopyTargets("light.office_rgbw_lights", rgbw))
    }

    /** The favourites as the frontend recorded them: each colour, with its label, swatch and outline. */
    private fun JsonObject.favoritesText(): List<String> {
        val colors = array("colors")!!.map { parseLightColor(it as JsonObject) }
        return colors.zip(objects("buttons")) { color, button ->
            "$color ${button.string("label")} ${button.string("background")} " +
                "outlined=${button.string("border") != "transparent"} enabled=${button.boolean("disabled") != true}"
        }
    }

    private fun LightFavorites.text(): List<String> = favorites.map { favorite ->
        val (r, g, b) = favorite.swatch.removePrefix("#").chunked(2).map { it.toInt(HEX) }
        "${favorite.color} ${favorite.label} rgb($r, $g, $b) outlined=${favorite.outlined} enabled=$enabled"
    }

    private fun perVariant(
        onlyAsIs: Boolean = false,
        check: (HassSnapshot, EntityState, JsonObject) -> Unit,
    ): List<DynamicTest> = fixture.json("more-info/controls.json").obj("entities")!!
        .filterKeys { it.startsWith("light.") }
        .flatMap { (entityId, variants) ->
            (variants as JsonObject).filterKeys { !onlyAsIs || it == "as_is" }.map { (name, captured) ->
                DynamicTest.dynamicTest("$entityId $name") {
                    val stateObj = (captured as JsonObject).obj("stateObj")!!
                    val states = parseStates(JsonArray(listOf(stateObj)))
                    val hass = fixture.hass.copy(states = fixture.hass.states + states)
                    check(hass, states.getValue(entityId), captured)
                }
            }
        }

    private companion object {
        const val HEX = 16
    }
}
