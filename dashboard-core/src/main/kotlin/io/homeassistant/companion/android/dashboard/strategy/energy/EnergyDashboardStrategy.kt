package io.homeassistant.companion.android.dashboard.strategy.energy

import io.homeassistant.companion.android.dashboard.energy.EnergyPreferences
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** The energy panel's url path and component name. */
const val ENERGY_PANEL = "energy"

/** The data the energy views' cards read, by collection; the "Now" view has its own, real-time one. */
const val DEFAULT_ENERGY_COLLECTION_KEY = "energy_dashboard"
const val DEFAULT_POWER_COLLECTION_KEY = "energy_dashboard_now"

/** The collection of the home dashboard's energy summary, which always shows today. */
const val HOME_ENERGY_COLLECTION_KEY = "energy_home_dashboard"

/**
 * The energy panel's dashboard: a view for each kind of source configured, the overview first when there are
 * several, and the setup wizard when nothing is configured ([prefs] `null` when energy was never set up). Views
 * whose cards the user all hid ([hiddenCards], from the `energy` system data) are left out unless all are.
 *
 * Port of `EnergyDashboardStrategy.generate` (frontend@20260624.6
 * src/panels/energy/strategies/energy-dashboard-strategy.ts), titled with the panel's name as its top bar is.
 */
fun HassSnapshot.energyDashboard(prefs: EnergyPreferences?, hiddenCards: List<String>?): JsonObject {
    if (prefs == null || prefs.isEmpty) return wizardDashboard()
    val candidates = candidateViews(prefs)
    // Keep at least one view so the dashboard never renders blank
    val views = candidates.filterNot { prefs.isEnergyViewEmpty(it.path, hiddenCards) }.ifEmpty { candidates }
    return buildJsonObject {
        put("title", localize("panel.$ENERGY_PANEL"))
        putJsonArray("views") {
            views.forEach { view ->
                addJsonObject {
                    put("path", view.path.path)
                    putJsonObject("strategy") {
                        put("type", view.strategyType)
                        put("collection_key", view.collectionKey)
                        hiddenCards?.let { hidden -> putJsonArray("hidden_cards") { hidden.forEach { add(it) } } }
                    }
                    put("title", localize("ui.panel.energy.title.${view.path.path}"))
                }
            }
        }
    }
}

/** The views of the kinds of sources configured, the overview first when there are several or power. */
private fun candidateViews(prefs: EnergyPreferences): List<EnergyView> = buildList {
    if (prefs.hasEnergySource || prefs.hasDeviceConsumption) add(ENERGY_VIEW)
    if (prefs.hasGasSource) add(GAS_VIEW)
    if (prefs.hasWaterSource || prefs.hasWaterDevices) add(WATER_VIEW)
    if (prefs.hasPowerSources || prefs.hasPowerDevices) add(POWER_VIEW)
    val kinds = listOf(prefs.hasEnergySource, prefs.hasGasSource, prefs.hasWaterSource).count { it }
    if (prefs.hasPowerSources || kinds > 1) add(0, OVERVIEW_VIEW)
}

/** Upstream shows the setup wizard, which belongs to the settings, as a panel view. */
private fun HassSnapshot.wizardDashboard() = buildJsonObject {
    put("title", localize("panel.$ENERGY_PANEL"))
    putJsonArray("views") {
        addJsonObject {
            put("type", "panel")
            put("path", "setup")
            putJsonArray("cards") { addJsonObject { put("type", "custom:energy-setup-wizard-card") } }
        }
    }
}

private class EnergyView(val path: EnergyViewPath, val strategyType: String, val collectionKey: String)

private val OVERVIEW_VIEW = EnergyView(EnergyViewPath.OVERVIEW, "energy-overview", DEFAULT_ENERGY_COLLECTION_KEY)
private val ENERGY_VIEW = EnergyView(EnergyViewPath.ELECTRICITY, "energy", DEFAULT_ENERGY_COLLECTION_KEY)
private val GAS_VIEW = EnergyView(EnergyViewPath.GAS, "gas", DEFAULT_ENERGY_COLLECTION_KEY)
private val WATER_VIEW = EnergyView(EnergyViewPath.WATER, "water", DEFAULT_ENERGY_COLLECTION_KEY)
private val POWER_VIEW = EnergyView(EnergyViewPath.NOW, "power", DEFAULT_POWER_COLLECTION_KEY)
