package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.DisplayColor
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.entity.parseStates
import io.homeassistant.companion.android.dashboard.golden.GoldenFixture
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.DynamicTest

// Helpers for the golden tests of the more-info controls (more-info/controls.json, recorded by
// tools/golden/capture.mjs from each `more-info-<domain>` as the entity is and turned off and unavailable).

/** The frontend's CSS for [this]: `var(--a, var(--b))` for state colours. */
internal fun DisplayColor.css(): String = when (this) {
    is DisplayColor.Literal -> css
    is DisplayColor.Theme -> "var(--$name-color)"
    is DisplayColor.State -> variables.reversed().fold("") { inner, variable ->
        if (inner.isEmpty()) "var(--$variable)" else "var(--$variable, $inner)"
    }
}

/** One test per captured entity of [domain] and variant (as is, off, unavailable; only as is with [onlyAsIs]). */
internal fun GoldenFixture.controlVariants(
    domain: String,
    onlyAsIs: Boolean = false,
    check: (HassSnapshot, EntityState, JsonObject) -> Unit,
): List<DynamicTest> = json("more-info/controls.json").obj("entities")!!
    .filterKeys { it.startsWith("$domain.") }
    .flatMap { (entityId, variants) ->
        (variants as JsonObject).filterKeys { !onlyAsIs || it == "as_is" }.map { (name, captured) ->
            DynamicTest.dynamicTest("$entityId $name") {
                val (hass, state) = withState((captured as JsonObject).obj("stateObj")!!)
                check(hass, state, captured)
            }
        }
    }

/** This fixture's snapshot with [stateObj] as its entity's state, and that state. */
internal fun GoldenFixture.withState(stateObj: JsonObject): Pair<HassSnapshot, EntityState> {
    val states = parseStates(JsonArray(listOf(stateObj)))
    return hass.copy(states = hass.states + states) to states.values.single()
}

/** The calls the frontend recorded for one interaction. */
internal fun JsonObject.recordedCalls(): List<CardAction.CallService> = objects("calls").map {
    CardAction.CallService(it.string("domain")!!, it.string("service")!!, it.obj("data"), null)
}

/** [menus] as the frontend's menus are recorded: label, value, enabled and each option's value and label. */
internal fun menusText(menus: List<SelectMenu>): List<List<Any?>> = menus.map { menu ->
    listOf(menu.label, menu.value, menu.enabled, menu.options.map { it.value to it.label })
}

/** The recorded menus in [menusText]'s form. */
internal fun JsonObject.capturedMenus(): List<List<Any?>> = objects("menus").map { menu ->
    listOf(
        menu.string("label"),
        menu.string("value"),
        menu["disabled"]?.toString() != "true",
        menu.objects("options").map { it.string("value") to it.string("label") },
    )
}
