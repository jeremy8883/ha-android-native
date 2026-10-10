package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import io.homeassistant.companion.android.dashboard.model.array
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.number
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * Differential tests of covers' and valves' details against the real frontend's `more-info-cover` and
 * `more-info-valve` (20260624.6): covers with buttons only, a position, a position and tilt (the cross), open and
 * close only (the switch), and tilt only, and valves with a switch and with a position, each as it is, off and
 * unavailable, with their favourite positions (saved, default and hidden) and every control's call.
 */
class PositionMoreInfoGoldenTest {
    private val fixture = GoldenFixture("test-instance")

    @TestFactory
    fun `Given captured covers and valves when deriving the sliders then they match the position state controls`() = each { hass, state, captured ->
        val positions = captured.obj("main")!!.obj("positions")!!
        val info = hass.positionMoreInfo(state)!!
        val cover = state.domain == COVER
        val expected = listOf(
            positions.obj(if (cover) "cover-position" else "valve-position")?.let(::sliderText),
            positions.obj("cover-tilt-position")?.let(::sliderText),
        )
        assertEquals(expected, listOf(info.position?.let(::sliderText), info.tilt?.let(::sliderText)))
    }

    @TestFactory
    fun `Given captured covers and valves when deriving the buttons then they match the button state controls`() = each { hass, state, captured ->
        // Shown in button mode, which entities with a position switch to
        val shown = captured.obj("pickers")!!.obj("button") ?: captured.obj("main")!!
        val expected = shown.obj("positionButtons")?.let { buttons ->
            buttons.string("layout") to buttons.objects("buttons").map {
                Triple(it.string("button"), it.string("label"), it.boolean("disabled") != true)
            }
        }
        val actual = hass.positionMoreInfo(state)!!.buttons?.let { buttons ->
            (if (buttons is PositionButtons.Cross) "cross" else "line") to buttons.buttons.map {
                Triple(slotName(it.slot), it.label, it.enabled)
            }
        }
        assertEquals(expected, actual)
    }

    @TestFactory
    fun `Given captured covers and valves when deriving the mode switch then it matches the ha-icon-button-group`() = each { hass, state, captured ->
        val expected = captured.obj("main")!!.array("buttons")?.map { (it as JsonObject).string("label") }
        val labels = hass.positionMoreInfo(state)!!.modeLabels
        assertEquals(expected, labels?.let { listOf(it.first, it.second) })
    }

    @TestFactory
    fun `Given captured covers and valves when deriving the favourites then they match the favourite positions`() = each { hass, state, captured ->
        val entry = captured.obj("entry")
        val expected = captured.obj("main")!!.array("favoritePositions")?.map { sectionText(it as JsonObject, editing = false) }
        val favorites = hass.positionFavorites(state, entry, editMode = false)
        val actual = favorites?.sections?.map { sectionText(it, editing = false, enabled = favorites.enabled) }
        assertEquals(expected, actual)
    }

    @TestFactory
    fun `Given captured covers and valves in edit mode when deriving the favourites then they match`() = each(onlyAsIs = true) { hass, state, captured ->
        val expected = captured.array("editMode")!!.map { sectionText(it as JsonObject, editing = true) }
        val favorites = hass.positionFavorites(state, captured.obj("entry"), editMode = true)
        val actual = favorites?.sections?.map { section ->
            sectionText(section, editing = true, enabled = favorites.enabled, done = favorites.doneLabel.takeIf { section.showDone })
        }.orEmpty()
        assertEquals(expected, actual)
    }

    @TestFactory
    fun `Given captured covers and valves when using each control then the calls match the frontend's`() = each(onlyAsIs = true) { hass, state, captured ->
        val info = hass.positionMoreInfo(state)!!
        val favorites = hass.positionFavorites(state, captured.obj("entry"), editMode = false)
        captured.objects("calls").forEach { call ->
            val label = call.string("label")!!
            val actual = when (call.string("control")) {
                "slider" -> {
                    val value = label.substringAfterLast(' ').toDouble()
                    listOfNotNull((if ("tilt" in label) info.tilt else info.position)?.service?.withValue(value))
                }
                "button" -> info.buttons!!.buttons.single { slotName(it.slot) == label }.actions
                "toggle" -> info.toggle!!.let { listOf(if (it.checked) it.turnOff else it.turnOn) }
                else -> listOf(favorites!!.sections.first().favorites[label.substringAfterLast(' ').toInt()].apply)
            }
            assertEquals(call.recordedCalls(), actual, label)
        }
    }

    private fun sliderText(slider: JsonObject): List<Any?> = listOf(
        slider.number("value"),
        slider.string("mode"),
        slider.boolean("showHandle"),
        slider.boolean("disabled") != true,
        slider.string("label"),
        slider.string("unit"),
        slider.string("color"),
        slider.string("inactiveColor"),
    )

    private fun sliderText(slider: ControlSlider): List<Any?> = listOf(
        slider.value,
        when (slider.mode) {
            SliderMode.End -> "end"
            SliderMode.Cursor -> "cursor"
            SliderMode.Start -> "start"
        },
        slider.showHandle,
        slider.enabled,
        slider.label,
        slider.unit,
        slider.color?.css(),
        (slider.color as? io.homeassistant.companion.android.dashboard.derive.DisplayColor.State)
            ?.overrides?.get("state-cover-inactive-color")?.css(),
    )

    private fun sectionText(section: JsonObject, editing: Boolean): List<Any?> = listOf(
        section.string("label"),
        section.objects("items").map { listOf(it.string("label"), it.string("text"), it.boolean("active"), it.boolean("disabled") != true) },
        if (editing) section.array("deleteLabels")!!.map { it.stringOrNull } else null,
        if (editing) section.array("buttonsLabels")!!.map { it.stringOrNull } else null,
    )

    private fun sectionText(
        section: PositionFavoriteSection,
        editing: Boolean,
        enabled: Boolean,
        done: String? = null,
    ): List<Any?> = listOf(
        section.label,
        section.favorites.map { listOf(it.label, it.text, it.active, enabled) },
        if (editing) section.favorites.map { it.deleteLabel } else null,
        if (editing) listOfNotNull(section.addLabel, done) else null,
    )

    private fun slotName(slot: ButtonSlot) = when (slot) {
        ButtonSlot.Open -> "open"
        ButtonSlot.Stop -> "stop"
        ButtonSlot.Close -> "close"
        ButtonSlot.OpenTilt -> "open-tilt"
        ButtonSlot.CloseTilt -> "close-tilt"
    }

    private fun each(
        onlyAsIs: Boolean = false,
        check: (io.homeassistant.companion.android.dashboard.entity.HassSnapshot, io.homeassistant.companion.android.dashboard.entity.EntityState, JsonObject) -> Unit,
    ): List<DynamicTest> = listOf(COVER, VALVE).flatMap { fixture.controlVariants(it, onlyAsIs, check) }
}
