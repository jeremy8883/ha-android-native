package io.homeassistant.companion.android.dashboard.energy

import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.abs
import kotlin.math.max

/**
 * The grid balance card: imported − exported = net, and a bar with the export to the left of its centre and the
 * import to the right, the net drawn over the larger. Port of `HuiEnergyGridBalanceCard.render` (frontend@20260624.6
 * src/panels/lovelace/cards/energy/hui-energy-grid-balance-card.ts).
 *
 * @property consumption whether more was imported than exported
 * @property exportedShare the export's share of the bar's left half, 0 to 1 (the larger of the two fills its half)
 * @property netShare the net's share of its half
 */
data class EnergyGridBalanceModel(
    val title: String,
    val imported: String,
    val exported: String,
    val net: String,
    val consumption: Boolean,
    val exportedShare: Double,
    val importedShare: Double,
    val netShare: Double,
    val tooltips: GridBalanceTooltips,
)

/** The explanations of the amounts, which the frontend shows in tooltips. */
data class GridBalanceTooltips(val imported: String, val exported: String, val net: String)

/** The grid balance of [data], titled [title] or with the card's default title. */
fun HassSnapshot.energyGridBalance(data: EnergyData, title: String?): EnergyGridBalanceModel {
    val sums = data.summed()
    val imported = sums.total[EnergyFlow.FROM_GRID] ?: 0.0
    val exported = sums.total[EnergyFlow.TO_GRID] ?: 0.0
    val net = imported - exported
    val consumption = net >= 0
    val max = max(imported, exported)
    fun share(value: Double) = if (max > 0) value / max else 0.0
    fun text(key: String, value: Double) = localize("$BALANCE.$key", mapOf("value" to amount(value)))
    return EnergyGridBalanceModel(
        title = title ?: localize("$BALANCE.title"),
        imported = "${amount(imported)} kWh",
        exported = "${amount(exported)} kWh",
        net = "${amount(net)} kWh",
        consumption = consumption,
        exportedShare = share(exported),
        importedShare = share(imported),
        netShare = share(abs(net)),
        tooltips = GridBalanceTooltips(
            imported = text("imported", imported),
            exported = text("exported", exported),
            net = text(if (consumption) "net_import" else "net_export", abs(net)),
        ),
    )
}

/** Two significant digits for tiny amounts, else at most two fraction digits. */
private fun HassSnapshot.amount(value: Double): String {
    if (abs(value) >= TINY || value == 0.0) return formats.number(BigDecimal.valueOf(value), 0, 2)
    val rounded = BigDecimal.valueOf(value).round(MathContext(SIGNIFICANT)).stripTrailingZeros()
    return formats.number(rounded, 0, rounded.scale().coerceAtLeast(0))
}

private const val BALANCE = "ui.panel.lovelace.cards.energy.grid_balance"
private const val TINY = 0.01
private const val SIGNIFICANT = 2
