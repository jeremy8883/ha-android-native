package io.homeassistant.companion.android.dashboard.energy

import io.homeassistant.companion.android.dashboard.model.array
import io.homeassistant.companion.android.dashboard.model.number
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.model.stringOrNull
import kotlinx.serialization.json.JsonObject

/**
 * The energy dashboard's configuration, the `energy/get_prefs` result. Port of `EnergyPreferences`
 * (frontend@20260624.6 src/data/energy.ts).
 *
 * @property energySources the grid connections, solar, batteries, gas and water, in configured order
 * @property deviceConsumption the individual devices' electricity
 * @property deviceConsumptionWater the individual devices' water
 */
data class EnergyPreferences(
    val energySources: List<EnergySource>,
    val deviceConsumption: List<DeviceConsumption>,
    val deviceConsumptionWater: List<DeviceConsumption>,
) {
    /** Whether nothing is configured, which upstream shows as the setup wizard. */
    val isEmpty: Boolean get() = deviceConsumption.isEmpty() && energySources.isEmpty()

    companion object {
        /** Read from the `energy/get_prefs` result; sources of an unknown type are skipped, as upstream ignores them. */
        fun fromJson(json: JsonObject): EnergyPreferences = EnergyPreferences(
            energySources = json.objects("energy_sources").mapNotNull(::energySource),
            deviceConsumption = json.objects("device_consumption").mapNotNull(::deviceConsumption),
            deviceConsumptionWater = json.objects("device_consumption_water").mapNotNull(::deviceConsumption),
        )
    }
}

/** A configured source of energy (or of gas or water), by type. */
sealed interface EnergySource {
    /** The meter of what this source provides; for the grid, `null` when only exporting. */
    val statEnergyFrom: String?

    /** The power or flow rate statistic, if any. */
    val statRate: String?

    /** The user's name for the source, if any. */
    val name: String?

    /**
     * A grid connection.
     *
     * @property statEnergyTo the export meter
     * @property statCost the import cost statistic
     * @property statCompensation the export compensation statistic
     * @property costAdjustmentDay a fixed cost per day
     */
    data class Grid(
        override val statEnergyFrom: String?,
        val statEnergyTo: String?,
        val statCost: String?,
        val entityEnergyPrice: String?,
        val numberEnergyPrice: Double?,
        val statCompensation: String?,
        val entityEnergyPriceExport: String?,
        val numberEnergyPriceExport: Double?,
        override val statRate: String?,
        val powerConfig: PowerConfig?,
        val costAdjustmentDay: Double,
        override val name: String?,
    ) : EnergySource

    /** Solar production, with the config entries forecasting it. */
    data class Solar(
        override val statEnergyFrom: String,
        override val statRate: String?,
        val configEntrySolarForecast: List<String>?,
        override val name: String?,
    ) : EnergySource

    /** A battery: [statEnergyFrom] is discharged, [statEnergyTo] charged, [statSoc] its state of charge. */
    data class Battery(
        override val statEnergyFrom: String,
        val statEnergyTo: String,
        override val statRate: String?,
        val powerConfig: PowerConfig?,
        val statSoc: String?,
        override val name: String?,
    ) : EnergySource

    /** Gas or water consumption ([type]), with its cost. */
    data class Utility(
        val type: UtilityType,
        override val statEnergyFrom: String,
        override val statRate: String?,
        val statCost: String?,
        val entityEnergyPrice: String?,
        val numberEnergyPrice: Double?,
        val unitOfMeasurement: String?,
        override val name: String?,
    ) : EnergySource
}

/** The consumption sources that share the gas and water configuration. */
enum class UtilityType { GAS, WATER }

/** How power is measured: one sensor, one inverted sensor, or one for each direction. */
data class PowerConfig(
    val statRate: String?,
    val statRateInverted: String?,
    val statRateFrom: String?,
    val statRateTo: String?,
)

/** A device's consumption, and the device it is part of ([includedInStat], that device's [statConsumption]). */
data class DeviceConsumption(
    val statConsumption: String,
    val statRate: String?,
    val name: String?,
    val includedInStat: String?,
)

private fun energySource(json: JsonObject): EnergySource? = when (json.string("type")) {
    "grid" -> EnergySource.Grid(
        statEnergyFrom = json.string("stat_energy_from"),
        statEnergyTo = json.string("stat_energy_to"),
        statCost = json.string("stat_cost"),
        entityEnergyPrice = json.string("entity_energy_price"),
        numberEnergyPrice = json.number("number_energy_price"),
        statCompensation = json.string("stat_compensation"),
        entityEnergyPriceExport = json.string("entity_energy_price_export"),
        numberEnergyPriceExport = json.number("number_energy_price_export"),
        statRate = json.string("stat_rate"),
        powerConfig = json.obj("power_config")?.let(::powerConfig),
        costAdjustmentDay = json.number("cost_adjustment_day") ?: 0.0,
        name = json.string("name"),
    )
    "solar" -> json.string("stat_energy_from")?.let { from ->
        EnergySource.Solar(
            statEnergyFrom = from,
            statRate = json.string("stat_rate"),
            configEntrySolarForecast = json.array("config_entry_solar_forecast")?.mapNotNull { it.stringOrNull },
            name = json.string("name"),
        )
    }
    "battery" -> battery(json)
    "gas" -> utility(json, UtilityType.GAS)
    "water" -> utility(json, UtilityType.WATER)
    else -> null
}

private fun battery(json: JsonObject): EnergySource.Battery? {
    val from = json.string("stat_energy_from")
    val to = json.string("stat_energy_to")
    if (from == null || to == null) return null
    return EnergySource.Battery(
        statEnergyFrom = from,
        statEnergyTo = to,
        statRate = json.string("stat_rate"),
        powerConfig = json.obj("power_config")?.let(::powerConfig),
        statSoc = json.string("stat_soc"),
        name = json.string("name"),
    )
}

private fun utility(json: JsonObject, type: UtilityType): EnergySource.Utility? =
    json.string("stat_energy_from")?.let { from ->
        EnergySource.Utility(
            type = type,
            statEnergyFrom = from,
            statRate = json.string("stat_rate"),
            statCost = json.string("stat_cost"),
            entityEnergyPrice = json.string("entity_energy_price"),
            numberEnergyPrice = json.number("number_energy_price"),
            unitOfMeasurement = json.string("unit_of_measurement"),
            name = json.string("name"),
        )
    }

private fun powerConfig(json: JsonObject) = PowerConfig(
    statRate = json.string("stat_rate"),
    statRateInverted = json.string("stat_rate_inverted"),
    statRateFrom = json.string("stat_rate_from"),
    statRateTo = json.string("stat_rate_to"),
)

private fun deviceConsumption(json: JsonObject): DeviceConsumption? = json.string("stat_consumption")?.let {
    DeviceConsumption(
        statConsumption = it,
        statRate = json.string("stat_rate"),
        name = json.string("name"),
        includedInStat = json.string("included_in_stat"),
    )
}
