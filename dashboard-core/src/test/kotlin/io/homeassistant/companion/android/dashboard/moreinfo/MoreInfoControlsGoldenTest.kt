package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.DisplayColor
import io.homeassistant.companion.android.dashboard.derive.moreInfoModel
import io.homeassistant.companion.android.dashboard.display.jsNumberString
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.entity.parseStates
import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import io.homeassistant.companion.android.dashboard.model.array
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.jsNumber
import io.homeassistant.companion.android.dashboard.model.number
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * Differential tests of the more-info controls against the real frontend (20260624.6): tools/golden/capture.mjs
 * rendered `more-info-<domain>` for entities of the test instance, as they were and turned off and unavailable,
 * and recorded what each drew (more-info/controls.json).
 */
class MoreInfoControlsGoldenTest {
    private val fixture = GoldenFixture("test-instance")

    @TestFactory
    fun `Given captured entities when deriving the details then the state header matches ha-more-info-state-header`() = perVariant { hass, state, captured ->
        val model = hass.moreInfoModel(state.entityId, Instant.EPOCH)!!
        // Domains whose controls show their own readings have no state header
        assertEquals(captured.obj("main")!!.string("state"), model.state.takeIf { model.stateHeader })
    }

    @TestFactory
    fun `Given captured lights when deriving the brightness slider then it matches ha-state-control-light-brightness`() = perVariant(LIGHT) { hass, state, captured ->
        assertSlider(captured.obj("main")!!.obj("brightness"), hass.lightMoreInfo(state)!!.brightness)
    }

    @TestFactory
    fun `Given captured lights when deriving the temperature slider then it matches light-color-temp-picker`() = perVariant(LIGHT) { hass, state, captured ->
        val picker = captured.obj("pickers")!!.obj("color_temp")?.obj("colorTemp")
        assertSlider(picker, hass.lightMoreInfo(state)!!.colorTemp)
    }

    @TestFactory
    fun `Given captured lights when deriving the buttons then they match the ha-icon-button-group`() = perVariant(LIGHT) { hass, state, captured ->
        val expected = captured.obj("main")!!.array("buttons")?.map { (it as JsonObject).buttonText() }.orEmpty()
        val actual = hass.lightMoreInfo(state)!!.buttons.map { button ->
            when (button) {
                is LightButton.Power -> "Power ${button.label} enabled=${button.enabled}"
                is LightButton.Show -> "Show ${button.label} enabled=${button.enabled} ${button.control.key()}"
                is LightButton.White -> "White ${button.label} enabled=${button.enabled}"
                LightButton.Separator -> "|"
            }
        }
        assertEquals(expected, actual)
    }

    @TestFactory
    fun `Given captured lights when deriving the effect menu then it matches ha-control-select-menu`() = perVariant(LIGHT) { hass, state, captured ->
        val expected = captured.obj("main")!!.objects("menus").map { menu ->
            listOf(
                menu.string("label"),
                menu.string("value"),
                !(menu.boolean("disabled") ?: false),
                menu.objects("options").map { it.string("value") to it.string("label") },
            )
        }
        val actual = listOfNotNull(hass.lightMoreInfo(state)!!.effect).map { menu ->
            listOf(menu.label, menu.value, menu.enabled, menu.options.map { it.value to it.label })
        }
        assertEquals(expected, actual)
    }

    @TestFactory
    fun `Given captured lights when deriving the colour picker then it matches light-color-rgb-picker`() = perVariant(LIGHT) { hass, state, captured ->
        val rgb = captured.obj("pickers")!!.obj("color")?.obj("rgb") ?: return@perVariant
        val picker = hass.lightColorPicker(state)
        val wheel = { value: Double? -> value?.let { it * RGB_MAX / PERCENT } }
        val expected = listOf(
            rgb.array("hs")?.map { jsNumber(it) },
            rgb.number("colorBrightness"),
            rgb.number("wv"),
            rgb.number("cw"),
            rgb.number("ww"),
            rgb.number("minKelvin"),
            rgb.number("maxKelvin"),
            rgb.objects("sliders").map { Triple(it.string("caption"), it.string("icon"), it.number("value")) },
        )
        val actual = listOf(
            picker.hue?.let { listOf(it, picker.saturation) },
            wheel(picker.colorBrightness),
            wheel(picker.white),
            wheel(picker.coldWhite),
            wheel(picker.warmWhite),
            picker.minKelvin,
            picker.maxKelvin,
            picker.sliders.map { Triple(it.caption, it.icon, it.value) },
        )
        assertEquals(expected, actual)
    }

    @TestFactory
    fun `Given captured lights when setting each control then the calls match the frontend's`(): List<DynamicTest> = fixture.json("more-info/controls.json").obj("entities")!!
        .filterKeys { it.startsWith("$LIGHT.") }
        .flatMap { (entityId, variants) ->
            val captured = (variants as JsonObject).obj("as_is")!!
            val stateObj = captured.obj("stateObj")!!
            val states = parseStates(JsonArray(listOf(stateObj)))
            val hass = fixture.hass.copy(states = fixture.hass.states + states)
            val state = states.getValue(entityId)
            // Favourite taps need the registry entry: LightFavoritesGoldenTest checks them
            captured.objects("calls").filter { it.string("control") != "favorite" }.map { call ->
                DynamicTest.dynamicTest("$entityId ${call.string("label")}") {
                    val expected = call.objects("calls").map {
                        CardAction.CallService(it.string("domain")!!, it.string("service")!!, it.obj("data"), null)
                    }
                    assertEquals(expected, listOfNotNull(hass.lightCall(state, call)))
                }
            }
        }

    @TestFactory
    fun `Given captured switches when deriving the on-off control then it matches ha-state-control-toggle`() = perVariant { hass, state, captured ->
        val expected = captured.obj("main")!!.obj("toggle")?.let { toggle ->
            val switch = toggle.obj("switch")
            if (switch != null) {
                listOf(
                    "switch",
                    switch.boolean("checked"),
                    switch.boolean("showHandle"),
                    switch.boolean("disabled") != true,
                    switch.string("label"),
                    switch.string("onColor"),
                    switch.string("offColor"),
                )
            } else {
                val (on, off) = toggle.objects("buttons")
                listOf(
                    "buttons",
                    on.string("label") to on.boolean("active"),
                    off.string("label") to off.boolean("active"),
                    on.boolean("disabled") != true,
                    on.string("color"),
                    off.string("color"),
                )
            }
        }
        val toggle = hass.lightMoreInfo(state)?.toggle ?: hass.positionMoreInfo(state)?.toggle ?: hass.fanMoreInfo(state)?.toggle
            ?: hass.lockMoreInfo(state)?.toggle
            ?: hass.moreInfoModel(state.entityId, Instant.EPOCH)?.stateToggle
        val actual = toggle?.let {
            if (it.buttons) {
                listOf(
                    "buttons",
                    it.turnOnLabel to it.checked,
                    it.turnOffLabel to it.offActive,
                    it.enabled,
                    it.onColor?.css(),
                    it.offColor?.css(),
                )
            } else {
                listOf("switch", it.checked, it.showHandle, it.enabled, it.label, it.onColor?.css(), it.offColor?.css())
            }
        }
        assertEquals(expected, actual)
    }

    /** What the native controls call for one of the frontend's recorded interactions. */
    private fun HassSnapshot.lightCall(state: EntityState, call: JsonObject): CardAction.CallService? {
        val light = lightMoreInfo(state)!!
        val value = call.obj("detail")?.get("value")
        val label = call.string("label")!!
        return when (call.string("control")) {
            "brightness" -> light.brightness?.service?.withValue(jsNumber(value))
            "color_temp" -> light.colorTemp?.service?.withValue(jsNumber(value))
            "color" -> {
                val picker = lightColorPicker(state)
                if (label.startsWith("hs")) {
                    val (hue, saturation) = (value as JsonArray).map { jsNumber(it) }
                    lightHueCall(state, hue, saturation, picker.colorBrightness)
                } else {
                    val slider = picker.sliders.getOrNull(label.split(" ")[1].toInt()) ?: return null
                    when (slider.channel) {
                        LightChannel.ColorBrightness ->
                            lightColorBrightnessCall(state, picker.colorBrightness, jsNumber(value))
                        else -> lightWhiteCall(state, slider.channel, jsNumber(value))
                    }
                }
            }
            "button" -> light.buttons.firstNotNullOfOrNull {
                when {
                    it is LightButton.Power && it.label == label -> it.action
                    it is LightButton.White && it.label == label -> it.action
                    else -> null
                }
            }
            else -> light.effect?.options?.firstOrNull { it.value == label }?.action
        }
    }

    private fun assertSlider(captured: JsonObject?, slider: ControlSlider?) {
        val expected = captured?.let {
            listOf(
                it.number("value"),
                it.number("min"),
                it.number("max"),
                it.number("step"),
                it.string("mode") ?: "start",
                it.boolean("inverted") ?: false,
                it.boolean("showHandle") ?: false,
                !(it.boolean("disabled") ?: false),
                it.string("label"),
                it.string("unit"),
                it.string("color"),
                it.string("background") ?: it.string("gradient"),
            )
        }
        val actual = slider?.let {
            listOf(
                it.value,
                it.min,
                it.max,
                it.step,
                if (it.mode == SliderMode.Cursor) "cursor" else "start",
                it.inverted,
                it.showHandle,
                it.enabled,
                it.label,
                it.unit,
                it.color?.css(),
                when (val background = it.background) {
                    is SliderBackground.Tint -> background.color?.css()
                    is SliderBackground.Stripes -> background.color?.css()
                    is SliderBackground.Gradient ->
                        background.stops.joinToString(", ") { (stop, hex) -> "$hex ${jsNumberString(stop * PERCENT)}%" }
                },
            )
        }
        assertEquals(expected, actual)
    }

    private fun JsonObject.buttonText(): String = when {
        boolean("separator") == true -> "|"
        string("tag") == "ha-icon-button" && string("label") == "Set white" ->
            "White ${string("label")} enabled=${boolean("disabled") != true}"
        string("tag") == "ha-icon-button" -> "Power ${string("label")} enabled=${boolean("disabled") != true}"
        else -> "Show ${string("label")} enabled=${boolean("disabled") != true} ${string("control")}"
    }

    private fun LightMainControl.key() = when (this) {
        LightMainControl.Brightness -> "brightness"
        LightMainControl.Color -> "color"
        LightMainControl.ColorTemp -> "color_temp"
    }

    /** The frontend's CSS for [this]: `var(--a, var(--b))` for state colours. */
    private fun DisplayColor.css(): String = when (this) {
        is DisplayColor.Literal -> css
        is DisplayColor.Theme -> "var(--$name-color)"
        is DisplayColor.State -> variables.reversed().fold("") { inner, variable ->
            if (inner.isEmpty()) "var(--$variable)" else "var(--$variable, $inner)"
        }
    }

    /** One test per captured entity and variant (as is, off, unavailable), of [domain] when given. */
    private fun perVariant(
        domain: String? = null,
        check: (HassSnapshot, EntityState, JsonObject) -> Unit,
    ): List<DynamicTest> = fixture.json("more-info/controls.json").obj("entities")!!
        .filterKeys { domain == null || it.startsWith("$domain.") }
        .flatMap { (entityId, variants) ->
            (variants as JsonObject).map { (name, captured) ->
                DynamicTest.dynamicTest("$entityId $name") {
                    val stateObj = (captured as JsonObject).obj("stateObj")!!
                    val states = parseStates(JsonArray(listOf(stateObj)))
                    val hass = fixture.hass.copy(states = fixture.hass.states + states)
                    check(hass, states.getValue(entityId), captured)
                }
            }
        }

    private companion object {
        const val LIGHT = "light"
        const val PERCENT = 100.0
        const val RGB_MAX = 255.0
    }
}
