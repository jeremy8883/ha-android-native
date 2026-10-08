package io.homeassistant.companion.android.dashboard

import io.homeassistant.companion.android.dashboard.entity.EntityStates
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.entity.HassUser
import io.homeassistant.companion.android.dashboard.entity.Localize
import io.homeassistant.companion.android.dashboard.entity.Registries
import io.homeassistant.companion.android.dashboard.entity.applyEntityEvent
import io.homeassistant.companion.android.dashboard.entity.parseAreaRegistry
import io.homeassistant.companion.android.dashboard.entity.parseDeviceRegistry
import io.homeassistant.companion.android.dashboard.entity.parseEntityRegistryDisplay
import io.homeassistant.companion.android.dashboard.entity.parseFloorRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** Parse JSON text in tests. */
fun json(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

/** States from a compressed `{"a": {...}}` snapshot body, i.e. `{"light.x": {"s": "on", "a": {}}}`. */
fun states(compressed: String): EntityStates = applyEntityEvent(emptyMap(), json("""{"a": $compressed}"""))

/** Registries from the raw WS results. */
fun registries(
    entities: String = """{"entities": [], "entity_categories": {"0": "config", "1": "diagnostic"}}""",
    devices: String = "[]",
    areas: String = "[]",
    floors: String = "[]",
): Registries = Registries(
    entities = parseEntityRegistryDisplay(json(entities)),
    devices = parseDeviceRegistry(Json.parseToJsonElement(devices).jsonArray),
    areas = parseAreaRegistry(Json.parseToJsonElement(areas).jsonArray),
    floors = parseFloorRegistry(Json.parseToJsonElement(floors).jsonArray),
)

/** A snapshot whose localize echoes the key, so tests can assert which translation was used. */
fun hass(
    states: EntityStates,
    registries: Registries = Registries.EMPTY,
    isAdmin: Boolean = true,
    panels: Set<String> = emptySet(),
): HassSnapshot = HassSnapshot(
    states = states,
    registries = registries,
    user = HassUser(id = "u1", name = "Dev", isAdmin = isAdmin, isOwner = isAdmin),
    panels = panels,
    localize = Localize { "[$it]" },
)
