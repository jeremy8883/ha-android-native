package io.homeassistant.companion.android.dashboard.energy

import io.homeassistant.companion.android.dashboard.display.jsRoundTo
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import java.math.BigDecimal
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// The energy gauge cards. Ports of the `render` computations of frontend@20260624.6
// src/panels/lovelace/cards/energy/hui-energy-{self-sufficiency,grid-neutrality,solar-consumed,carbon-consumed}-gauge-card.ts
// and of `calculateSolarConsumedGauge` (src/data/energy.ts).

/** The energy gauges. */
enum class EnergyGaugeType(val cardType: String) {
    SELF_SUFFICIENCY("energy-self-sufficiency-gauge"),
    GRID_NEUTRALITY("energy-grid-neutrality-gauge"),
    SOLAR_CONSUMED("energy-solar-consumed-gauge"),
    CARBON_CONSUMED("energy-carbon-consumed-gauge"),
}

/** What a gauge card shows: a gauge, a message when it can't be computed, or nothing. */
sealed interface EnergyGaugeModel {
    /**
     * A gauge from [min] to [max] at [value], showing [text] under [name]. A [needle] gauge has coloured [levels]
     * (from each level to the next); another one fills to its value in its [severity] colour.
     */
    data class Gauge(
        val value: Double,
        val min: Double,
        val max: Double,
        val text: String,
        val name: String,
        val info: List<String>,
        val severity: Severity?,
        val needle: Boolean = false,
        val levels: List<Pair<Double, String>> = emptyList(),
    ) : EnergyGaugeModel

    /** Why the gauge can't be shown. */
    data class Message(val text: String) : EnergyGaugeModel

    /** The card shows nothing (the carbon gauge without a CO2 signal). */
    data object Hidden : EnergyGaugeModel
}

/** The frontend's severity colours (`severityMap`), by theme variable. */
enum class Severity(val variable: String) {
    RED("error-color"),
    GREEN("success-color"),
    YELLOW("warning-color"),
    NORMAL("info-color"),
}

/** What the gauge [type] shows for [data]. */
fun HassSnapshot.energyGauge(type: EnergyGaugeType, data: EnergyData): EnergyGaugeModel = when (type) {
    EnergyGaugeType.SELF_SUFFICIENCY -> selfSufficiency(data)
    EnergyGaugeType.GRID_NEUTRALITY -> gridNeutrality(data)
    EnergyGaugeType.SOLAR_CONSUMED -> solarConsumed(data)
    EnergyGaugeType.CARBON_CONSUMED -> carbonConsumed(data)
}

private fun HassSnapshot.selfSufficiency(data: EnergyData): EnergyGaugeModel {
    val sums = data.summed()
    val fromGrid = sums.total[EnergyFlow.FROM_GRID] ?: 0.0
    val home = max(0.0, sums.consumption().total.usedTotal)
    if (home <= 0) return EnergyGaugeModel.Message(text("self_sufficiency_gauge.self_sufficiency_could_not_calc"))
    val value = (1 - min(1.0, fromGrid / home)) * PERCENT
    return percentGauge(
        value = value,
        name = text("self_sufficiency_gauge.self_sufficiency_quota"),
        info = listOf(text("self_sufficiency_gauge.card_indicates_self_sufficiency_quota")),
        severity = quotaSeverity(value),
    )
}

private fun HassSnapshot.gridNeutrality(data: EnergyData): EnergyGaugeModel {
    val sums = data.summed()
    val consumed = sums.total[EnergyFlow.FROM_GRID] ?: return EnergyGaugeModel.Hidden
    val returned = sums.total[EnergyFlow.TO_GRID] ?: 0.0
    val value = when {
        returned > consumed -> (1 - consumed / returned) * -1
        returned < consumed -> 1 - returned / consumed
        else -> 0.0
    }
    return EnergyGaugeModel.Gauge(
        value = value,
        min = -1.0,
        max = 1.0,
        text = formats.number(BigDecimal.valueOf(abs(returned - consumed)), 0, 2) + " kWh",
        name = text(
            if (returned >=
                consumed
            ) {
                "grid_neutrality_gauge.net_returned_grid"
            } else {
                "grid_neutrality_gauge.net_consumed_grid"
            },
        ),
        info = listOf(text("grid_neutrality_gauge.energy_dependency"), text("grid_neutrality_gauge.color_explain")),
        severity = null,
        needle = true,
        levels = listOf(-1.0 to "energy-grid-return-color", 0.0 to "energy-grid-consumption-color"),
    )
}

private fun HassSnapshot.solarConsumed(data: EnergyData): EnergyGaugeModel {
    val sums = data.summed()
    val hasBattery = EnergyFlow.TO_BATTERY in sums.byStart || EnergyFlow.FROM_BATTERY in sums.byStart
    val value = if (EnergyFlow.TO_GRID in sums.total) solarConsumedGauge(hasBattery, sums) else null
    return when {
        EnergyFlow.SOLAR !in sums.total -> EnergyGaugeModel.Hidden
        EnergyFlow.TO_GRID !in sums.total ->
            EnergyGaugeModel.Message(text("solar_consumed_gauge.self_consumed_solar_could_not_calc"))
        value == null -> EnergyGaugeModel.Message(text("solar_consumed_gauge.not_produced_solar_energy"))
        else -> percentGauge(
            value = value,
            name = text("solar_consumed_gauge.self_consumed_solar_energy"),
            info = listOf(
                text("solar_consumed_gauge.card_indicates_solar_energy_used"),
                text("solar_consumed_gauge.card_indicates_solar_energy_used_charge_home_bat"),
            ),
            severity = quotaSeverity(value),
        )
    }
}

private fun HassSnapshot.carbonConsumed(data: EnergyData): EnergyGaugeModel {
    val co2 = data.co2SignalEntity
    if (co2 == null || co2 !in states) return EnergyGaugeModel.Hidden
    val value = lowCarbonShare(data)
    return if (value == null) {
        EnergyGaugeModel.Message(text("carbon_consumed_gauge.low_carbon_energy_not_calculated"))
    } else {
        percentGauge(
            value = value,
            name = text("carbon_consumed_gauge.low_carbon_energy_consumed"),
            info = listOf(text("carbon_consumed_gauge.card_indicates_energy_used")),
            severity = when {
                value < CARBON_RED -> Severity.RED
                value < CARBON_YELLOW -> Severity.YELLOW
                value > HIGH -> Severity.GREEN
                else -> Severity.NORMAL
            },
        )
    }
}

/** The low-carbon share of the energy used, in %, `null` without fossil fuel data or use. */
private fun lowCarbonShare(data: EnergyData): Double? {
    val fossil = data.fossilEnergyConsumption ?: return null
    val sums = data.summed()
    val fromGrid = sums.total[EnergyFlow.FROM_GRID] ?: 0.0
    val solar = sums.total[EnergyFlow.SOLAR] ?: 0.0
    val returned = sums.total[EnergyFlow.TO_GRID] ?: 0.0
    val consumed = fromGrid + max(0.0, solar - returned)
    return if (consumed != 0.0) jsRoundTo((1 - fossil.values.sum() / consumed) * PERCENT, 2) else null
}

/** A gauge from 0 to 100 %, its value without decimals. */
private fun HassSnapshot.percentGauge(value: Double, name: String, info: List<String>, severity: Severity) =
    EnergyGaugeModel.Gauge(
        value = value,
        min = 0.0,
        max = PERCENT,
        text = formats.number(BigDecimal.valueOf(value), 0, 0) + "%",
        name = name,
        info = info,
        severity = severity,
    )

/** Green above 75 %, yellow below 50 %. */
private fun quotaSeverity(value: Double) = when {
    value > HIGH -> Severity.GREEN
    value < LOW -> Severity.YELLOW
    else -> Severity.NORMAL
}

private fun HassSnapshot.text(key: String) = localize("ui.panel.lovelace.cards.energy.$key")

internal const val PERCENT = 100.0
private const val HIGH = 75
private const val LOW = 50
private const val CARBON_RED = 10
private const val CARBON_YELLOW = 30
