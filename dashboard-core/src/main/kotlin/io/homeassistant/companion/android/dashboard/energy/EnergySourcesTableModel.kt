package io.homeassistant.companion.android.dashboard.energy

import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import java.math.BigDecimal
import java.util.Currency

/**
 * The energy sources table: a row per source with its energy (and cost), a total per kind of source, and the total
 * cost. Port of `HuiEnergySourcesTableCard.render` (frontend@20260624.6
 * src/panels/lovelace/cards/energy/hui-energy-sources-table-card.ts).
 *
 * @property compare whether the table has the compared period's columns
 * @property showCosts whether the table has cost columns
 */
data class EnergySourcesTableModel(val compare: Boolean, val showCosts: Boolean, val rows: List<SourceRow>)

/**
 * A row of the table: a source, or a total ([total], drawn bold). Amounts are formatted, "" where the table leaves
 * the cell empty.
 *
 * @property bullet the kind's colour (`grid_consumption`, `solar`...) and the source's position among those of its
 * kind, `null` for the totals that have none
 * @property statisticId the source's statistic, which opens its more-info when it's an entity's
 */
data class SourceRow(
    val label: String,
    val bullet: Pair<String, Int>?,
    val total: Boolean,
    val energy: String,
    val cost: String,
    val compareEnergy: String,
    val compareCost: String,
    val statisticId: String? = null,
)

/** The table of [data] for the card [types] (all when `null`), with only the totals when [onlyTotals]. */
fun HassSnapshot.energySourcesTable(
    data: EnergyData,
    types: List<String>?,
    onlyTotals: Boolean,
): EnergySourcesTableModel {
    val sources = data.prefs.energySources.filter { types == null || it.typeKey in types }
    val table = TableBuilder(this, data, onlyTotals, showCosts(sources))
    table.simpleCategory(SOLAR, sources)
    table.batteries(sources.filterIsInstance<EnergySource.Battery>())
    table.grids(sources.filterIsInstance<EnergySource.Grid>())
    table.simpleCategory(GAS, sources)
    table.simpleCategory(WATER, sources)
    table.totalCosts()
    return EnergySourcesTableModel(compare = data.statsCompare != null, showCosts = table.showCosts, rows = table.rows)
}

/** Whether any of [sources] has a cost. */
private fun showCosts(sources: List<EnergySource>): Boolean = sources.any { source ->
    when (source) {
        is EnergySource.Grid -> listOf(
            source.statCost,
            source.entityEnergyPrice,
            source.statCompensation,
            source.entityEnergyPriceExport,
        )
            .any { !it.isNullOrEmpty() } ||
            listOf(source.numberEnergyPrice, source.numberEnergyPriceExport).any { it != null && it != 0.0 }
        is EnergySource.Utility -> !source.statCost.isNullOrEmpty() ||
            !source.entityEnergyPrice.isNullOrEmpty() ||
            source.numberEnergyPrice.let { it != null && it != 0.0 }
        else -> false
    }
}

/** The kind upstream groups the source by (`energySourcesByType`). */
private val EnergySource.typeKey: String
    get() = when (this) {
        is EnergySource.Grid -> GRID
        is EnergySource.Solar -> SOLAR
        is EnergySource.Battery -> BATTERY
        is EnergySource.Utility -> type.key
    }

/** A source's energy and cost in the period and the compared one. Port of `_extractStatData`. */
private class StatData(
    val hasData: Boolean,
    val energy: Double,
    val energyCompare: Double,
    val cost: Double,
    val costCompare: Double,
)

/** A source's row: its name ("" for the statistic's), statistic and colour. */
private class RowSource(val name: String, val statId: String, val bullet: Pair<String, Int>)

/** A running total of a kind of source. */
private class Total(
    var energy: Double = 0.0,
    var energyCompare: Double = 0.0,
    var cost: Double = 0.0,
    var costCompare: Double = 0.0,
) {
    var hasCost = false

    fun add(data: StatData, sign: Int = 1, withCost: Boolean) {
        energy += sign * data.energy
        energyCompare += sign * data.energyCompare
        if (withCost) {
            hasCost = true
            cost += sign * data.cost
            costCompare += sign * data.costCompare
        }
    }
}

private class TableBuilder(
    val hass: HassSnapshot,
    val data: EnergyData,
    val onlyTotals: Boolean,
    val showCosts: Boolean,
) {
    val rows = mutableListOf<SourceRow>()
    private val compare = data.statsCompare != null
    private val totals = mutableMapOf<String, Total>()
    private val formats = TableFormats(hass)

    fun extract(statId: String, costStatId: String?): StatData {
        val energy = statisticsSumGrowth(data.stats, listOf(statId))
        val energyCompare = if (compare) statisticsSumGrowth(data.statsCompare.orEmpty(), listOf(statId)) else null
        val cost = costStatId?.let { statisticsSumGrowth(data.stats, listOf(it)) } ?: 0.0
        val costCompare =
            costStatId?.takeIf { compare }?.let { statisticsSumGrowth(data.statsCompare.orEmpty(), listOf(it)) } ?: 0.0
        return StatData(
            hasData = energy != null || compare && energyCompare != null,
            energy = energy ?: 0.0,
            energyCompare = energyCompare ?: 0.0,
            cost = cost,
            costCompare = costCompare,
        )
    }

    fun simpleCategory(kind: String, sources: List<EnergySource>) {
        val ofKind = sources.filter { it.typeKey == kind }
        if (ofKind.isEmpty()) return
        val total = totals.getOrPut(kind) { Total() }
        val unit = when (kind) {
            GAS -> data.gasUnit
            WATER -> data.waterUnit
            else -> KWH
        }
        ofKind.forEachIndexed { index, source ->
            val from = source.statEnergyFrom ?: return@forEachIndexed
            // Solar has no cost
            val costStat = if (kind ==
                SOLAR
            ) {
                null
            } else {
                ((source as? EnergySource.Utility)?.statCost ?: data.info.costSensors[from])
            }
            val stat = extract(from, costStat)
            if (!stat.hasData && stat.cost == 0.0 && stat.costCompare == 0.0) return@forEachIndexed
            total.add(stat, withCost = costStat != null)
            if (!onlyTotals) {
                rows += row(RowSource(source.name.orEmpty(), from, kind to index), stat, unit, costStat != null)
            }
        }
        rows += totalRow("${kind}_total", total, unit, kind)
    }

    fun batteries(batteries: List<EnergySource.Battery>) {
        if (batteries.isEmpty()) return
        val total = Total()
        batteries.forEachIndexed { index, source ->
            val from = extract(source.statEnergyFrom, null)
            val to = extract(source.statEnergyTo, null)
            if (!from.hasData && !to.hasData) return@forEachIndexed
            total.energy += from.energy - to.energy
            total.energyCompare += from.energyCompare - to.energyCompare
            if (onlyTotals) return@forEachIndexed
            val name = { key: String -> source.name?.let { formats.named(key, it) }.orEmpty() }
            val discharged = RowSource(name("named_battery_discharged"), source.statEnergyFrom, BATTERY_OUT to index)
            rows += row(discharged, from, KWH, false)
            val charged = RowSource(name("named_battery_charged"), source.statEnergyTo, BATTERY_IN to index)
            rows += row(charged, to.negated(), KWH, false)
        }
        rows += totalRow("battery_total", total, KWH, BATTERY_OUT)
    }

    fun grids(grids: List<EnergySource.Grid>) {
        val total = totals.getOrPut(GRID) { Total() }
        grids.forEachIndexed { index, source ->
            source.statEnergyFrom?.takeIf { it.isNotEmpty() }?.let { from -> gridImport(source, from, index, total) }
            source.statEnergyTo?.takeIf { it.isNotEmpty() }?.let { to -> gridExport(source, to, index, total) }
        }
        if (grids.any { !it.statEnergyFrom.isNullOrEmpty() || !it.statEnergyTo.isNullOrEmpty() }) {
            rows += totalRow("grid_total", total, KWH, GRID_CONSUMPTION)
        }
    }

    private fun gridImport(source: EnergySource.Grid, from: String, index: Int, total: Total) {
        val costStat = source.statCost ?: data.info.costSensors[from]
        val stat = extract(from, costStat)
        if (!stat.hasData && stat.cost == 0.0 && stat.costCompare == 0.0) return
        total.add(stat, withCost = costStat != null)
        if (onlyTotals) return
        val name = source.name?.let {
            if (source.statEnergyTo !=
                null
            ) {
                formats.named("named_grid_imported", it)
            } else {
                it
            }
        }.orEmpty()
        // Grid rows always show their cost, 0 without a cost statistic
        rows += row(RowSource(name, from, GRID_CONSUMPTION to index), stat, KWH, withCost = true)
    }

    private fun gridExport(source: EnergySource.Grid, to: String, index: Int, total: Total) {
        val costStat = source.statCompensation ?: data.info.costSensors[to]
        val stat = extract(to, costStat)
        if (!stat.hasData && stat.cost == 0.0 && stat.costCompare == 0.0) return
        total.add(stat, sign = -1, withCost = costStat != null)
        if (onlyTotals) return
        val name =
            source.name?.let {
                if (source.statEnergyFrom !=
                    null
                ) {
                    formats.named("named_grid_exported", it)
                } else {
                    it
                }
            }.orEmpty()
        rows += row(RowSource(name, to, GRID_RETURN to index), stat.negated(), KWH, withCost = true)
    }

    /** The total of every cost, when more than one kind of source has costs. */
    fun totalCosts() {
        val withCosts = listOf(GAS, WATER, GRID).mapNotNull { totals[it]?.takeIf(Total::hasCost) }
        if (withCosts.size <= 1) return
        rows += SourceRow(
            label = hass.localize("$TABLE.total_costs"),
            bullet = null,
            total = true,
            energy = "",
            cost = formats.money(withCosts.sumOf { it.cost }),
            compareEnergy = "",
            compareCost = formats.money(withCosts.sumOf { it.costCompare }),
        )
    }

    private fun row(source: RowSource, stat: StatData, unit: String, withCost: Boolean) = SourceRow(
        label = source.name.ifEmpty { hass.statisticLabel(source.statId, data.statsMetadata[source.statId]) },
        bullet = source.bullet,
        total = false,
        energy = formats.energy(stat.energy, unit),
        cost = if (withCost) formats.money(stat.cost) else "",
        compareEnergy = formats.energy(stat.energyCompare, unit),
        compareCost = if (withCost) formats.money(stat.costCompare) else "",
        statisticId = source.statId,
    )

    /** A kind's total; with only totals, it shows the kind's colour like a source row. */
    private fun totalRow(key: String, total: Total, unit: String, kind: String) = SourceRow(
        label = hass.localize("$TABLE.$key"),
        bullet = (kind to 0).takeIf { onlyTotals },
        total = !onlyTotals,
        energy = formats.energy(total.energy, unit),
        cost = if (total.hasCost) formats.money(total.cost) else "",
        compareEnergy = formats.energy(total.energyCompare, unit),
        compareCost = if (total.hasCost) formats.money(total.costCompare) else "",
    )
}

private fun StatData.negated() = StatData(hasData, -energy, -energyCompare, -cost, -costCompare)

/** How the table writes amounts and names. */
private class TableFormats(private val hass: HassSnapshot) {
    private val currency = hass.config.currency?.let { runCatching { Currency.getInstance(it) }.getOrNull() }

    fun named(key: String, name: String) = hass.localize("$TABLE.$key", mapOf("name" to name))

    fun energy(value: Double, unit: String) =
        "${negativeZero(value)}${hass.formats.number(BigDecimal.valueOf(value), 0, 2)} $unit"

    fun money(value: Double): String = negativeZero(value) + (
        currency?.let {
            hass.formats.currency(BigDecimal.valueOf(value), it, it.defaultFractionDigits, it.defaultFractionDigits)
        } ?: hass.formats.number(BigDecimal.valueOf(value), 0, 2)
        )

    /** The "-" `Intl` writes for -0 (a negated 0, such as nothing exported), which [BigDecimal] drops. */
    fun negativeZero(value: Double) = if (value == 0.0 && 1 / value < 0) "-" else ""
}

private const val TABLE = "ui.panel.lovelace.cards.energy.energy_sources_table"
private const val KWH = "kWh"
private const val GRID_CONSUMPTION = "grid_consumption"
private const val GRID_RETURN = "grid_return"
private const val BATTERY_IN = "battery_in"
private const val BATTERY_OUT = "battery_out"
