package io.homeassistant.companion.android.dashboard.golden

import io.homeassistant.companion.android.dashboard.entity.HassConfig
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.entity.HassUser
import io.homeassistant.companion.android.dashboard.entity.IconResources
import io.homeassistant.companion.android.dashboard.entity.JsonTranslations
import io.homeassistant.companion.android.dashboard.entity.Localize
import io.homeassistant.companion.android.dashboard.entity.Registries
import io.homeassistant.companion.android.dashboard.entity.activeRepairsIssues
import io.homeassistant.companion.android.dashboard.entity.applyConfigFlowMessages
import io.homeassistant.companion.android.dashboard.entity.parseAreaRegistry
import io.homeassistant.companion.android.dashboard.entity.parseDeviceRegistry
import io.homeassistant.companion.android.dashboard.entity.parseEntityRegistryDisplay
import io.homeassistant.companion.android.dashboard.entity.parseFloorRegistry
import io.homeassistant.companion.android.dashboard.entity.parseStates
import io.homeassistant.companion.android.dashboard.entity.withFallback
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import io.homeassistant.companion.android.dashboard.strategy.StrategyData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * One variant of the golden fixtures captured from the real frontend by tools/golden/capture.mjs on the local
 * test instance (see tools/golden/README.md), turned into the inputs the Kotlin port takes.
 */
class GoldenFixture(variant: String) {
    private val root = "/fixtures/home/$variant/"

    fun json(path: String): JsonObject = parse(path).jsonObject

    private fun parse(path: String): JsonElement = Json.parseToJsonElement(
        checkNotNull(javaClass.getResourceAsStream(root + path)) { "Missing fixture $root$path" }.reader().readText(),
    )

    private fun wsResult(name: String): JsonElement = checkNotNull(json("ws/$name.json")["result"]) { "No result in $name" }

    /** Answers to the WebSocket calls the strategies made while the frontend generated them. */
    val strategyData: StrategyData = run {
        val calls = parse("ws/strategy-calls.json").jsonArray.map { it.jsonObject }
        fun result(type: String) = calls.firstOrNull { it.obj("request")?.string("type") == type }?.get("result")
        StrategyData(
            energyPrefs = result("energy/get_prefs") as? JsonObject,
            commonControls = (result("usage_prediction/common_control") as? JsonObject)
                ?.get("entities")?.jsonArray?.mapNotNull { it.stringOrNull },
        )
    }

    val homeSystemData: JsonObject? = wsResult("frontend-get_system_data-home").jsonObject.obj("value")

    val hass: HassSnapshot = run {
        val user = wsResult("auth-current_user").jsonObject
        val config = wsResult("get_config").jsonObject
        // Frontend bundle strings the strategies and state display used, then the server's entity translations
        val strings = flatStrings(json("inputs/translations.json")) +
            flatStrings(json("inputs/state-translations.json"))
        val backend = translationResources("entity_component") + translationResources("entity")
        val bundled = checkNotNull(JsonTranslations.bundled()) { "No bundled translations" }
        HassSnapshot(
            states = parseStates(wsResult("get_states").jsonArray),
            registries = Registries(
                entities = parseEntityRegistryDisplay(wsResult("config-entity_registry-list_for_display").jsonObject),
                devices = parseDeviceRegistry(wsResult("config-device_registry-list").jsonArray),
                areas = parseAreaRegistry(wsResult("config-area_registry-list").jsonArray),
                floors = parseFloorRegistry(wsResult("config-floor_registry-list").jsonArray),
            ),
            user = HassUser(
                id = user.string("id").orEmpty(),
                name = user.string("name"),
                isAdmin = user.boolean("is_admin") == true,
                isOwner = user.boolean("is_owner") == true,
            ),
            config = HassConfig(
                state = config.string("state"),
                recoveryMode = config.boolean("recovery_mode") == true,
                version = config.string("version"),
                components = config["components"]?.jsonArray?.mapNotNull { it.stringOrNull }?.toSet().orEmpty(),
                unitSystem = config.obj("unit_system")?.mapValues { unit -> unit.value.stringOrNull.orEmpty() }.orEmpty(),
                locationName = config.string("location_name"),
                currency = config.string("currency"),
            ),
            panels = wsResult("get_panels").jsonObject.keys,
            // then the bundled frontend strings the app ships, then the server's entity translations
            localize = Localize { key -> strings[key] ?: bundled(key) }.withFallback(backend),
            repairsIssues = (json("ws/repairs-list_issues.json")["result"] as? JsonObject)?.let(::activeRepairsIssues),
            discoveredFlows = (json("ws/config_entries-flow-progress.json")["result"] as? JsonArray)?.let { flows ->
                // What the subscription's first event holds: every flow in progress as a `null`-type message
                applyConfigFlowMessages(null, JsonArray(flows.map { JsonObject(mapOf("type" to JsonNull, "flow" to it)) }))
            },
            icons = IconResources.fromResults(
                entityComponent = wsResult("frontend-get_icons-entity_component").jsonObject,
                entity = wsResult("frontend-get_icons-entity").jsonObject,
            ),
        )
    }

    private fun flatStrings(file: JsonObject): Map<String, String> = file.obj("strings")?.mapValues { it.value.stringOrNull.orEmpty() }.orEmpty()

    private fun translationResources(category: String): Map<String, String> = wsResult("frontend-get_translations-$category").jsonObject.obj("resources")
        ?.mapValues { it.value.stringOrNull.orEmpty() }.orEmpty()

    companion object {
        val VARIANTS = listOf("test-instance", "test-instance-nonadmin")
    }
}

/** Key-sorted JSON, so comparisons ignore key order. */
fun JsonElement.sorted(): JsonElement = when (this) {
    is JsonObject -> JsonObject(entries.sortedBy { it.key }.associate { it.key to it.value.sorted() })
    is JsonArray -> JsonArray(map { it.sorted() })
    else -> this
}
