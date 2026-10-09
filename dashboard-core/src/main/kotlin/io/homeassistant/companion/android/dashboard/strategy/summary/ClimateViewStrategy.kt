package io.homeassistant.companion.android.dashboard.strategy.summary

import io.homeassistant.companion.android.dashboard.entity.AreaEntry
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.strategy.areaTileCard
import io.homeassistant.companion.android.dashboard.strategy.home.CLIMATE_FILTERS
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put

/**
 * The view of the climate panel: each area's temperature and humidity with trend graphs and its climate
 * devices (thermostats, fans, covers, windows...) by floor, then the devices without an area.
 *
 * Port of `ClimateViewStrategy.generate` (frontend@20260624.6
 * src/panels/climate/strategies/climate-view-strategy.ts). The strategy config is `{type: "climate"}`.
 */
fun HassSnapshot.climateView(): JsonObject = summaryView(summarySections(CLIMATE_VIEW).sections)

private val CLIMATE_VIEW = SummaryViewSpec(
    filters = CLIMATE_FILTERS,
    areaCards = { area, entities -> climateAreaCards(area, entities) },
    unassignedCard = { climateTile(it) },
    otherAreasKey = OTHER_AREAS_KEY,
    devicesKey = "ui.panel.lovelace.strategy.climate.devices",
    otherDevicesKey = "ui.panel.lovelace.strategy.climate.other_devices",
)

/** Port of `processAreasForClimate` for one area: its temperature and humidity first, then its entities. */
private fun HassSnapshot.climateAreaCards(area: AreaEntry, entities: List<String>): List<JsonObject> {
    val temperature = area.temperatureEntityId
    val humidity = area.humidityEntityId
    val areaSensors = listOfNotNull(
        temperature?.takeIf { it in states }?.let { areaSensorTile(it, "temperature", "Temperature") },
        humidity?.takeIf { it in states }?.let { areaSensorTile(it, "humidity", "Humidity") },
    )
    val others = entities.filter { it != temperature && it != humidity }.map { entityId ->
        val deviceClass = states[entityId]?.attributes?.string("device_class")
        climateTile(entityId).let { if (deviceClass in TREND_DEVICE_CLASSES) it.withTrendGraph() else it }
    }
    val cards = areaSensors + others
    return if (cards.isEmpty()) emptyList() else listOf(areaHeading(area)) + cards
}

/** The area's [deviceClass] sensor, named after its kind (falling back to [fallbackName], as upstream). */
private fun HassSnapshot.areaSensorTile(entityId: String, deviceClass: String, fallbackName: String): JsonObject {
    val name = localize("component.sensor.entity_component.$deviceClass.name").ifEmpty { fallbackName }
    return JsonObject(climateTile(entityId) + ("name" to JsonPrimitive(name))).withTrendGraph()
}

private fun HassSnapshot.climateTile(entityId: String) = areaTileCard(entityId, prefix = "", includeFeature = true)

private fun JsonObject.withTrendGraph() =
    JsonObject(this + ("features" to buildJsonArray { addJsonObject { put("type", "trend-graph") } }))

private val TREND_DEVICE_CLASSES = setOf("temperature", "humidity")
