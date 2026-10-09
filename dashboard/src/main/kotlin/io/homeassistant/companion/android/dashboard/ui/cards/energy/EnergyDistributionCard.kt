package io.homeassistant.companion.android.dashboard.ui.cards.energy

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.energy.EnergyDistributionModel
import io.homeassistant.companion.android.dashboard.energy.energyDistribution
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.ui.theme.resolveVariable
import java.time.Instant
import java.time.ZonedDateTime

/**
 * The energy distribution card: the grid, solar, battery, gas and water around the home, with the energy that
 * flowed between them. Layout of `hui-energy-distribution-card` (frontend@20260624.6).
 */
@Composable
internal fun EnergyDistributionCard(
    card: CardConfig,
    hass: State<HassSnapshot?>,
    now: State<ZonedDateTime?>,
    modifier: Modifier = Modifier,
) {
    EnergyCardFrame(card, hass, modifier) { data ->
        val snapshot = hass.value ?: return@EnergyCardFrame
        val at = now.value?.toInstant() ?: Instant.now()
        val model = remember(data, snapshot.states, snapshot.formats) { snapshot.energyDistribution(data, at) }
        val label = { key: String -> snapshot.localize("$LABELS$key") }
        Distribution(model, label)
    }
}

@Composable
private fun Distribution(model: EnergyDistributionModel, label: (String) -> String) {
    val colors = rememberDistributionColors()
    val high = model.battery != null || model.waterBelow
    BoxWithConstraints(
        Modifier.fillMaxWidth().padding(
            start = HADimens.SPACE4,
            end = HADimens.SPACE4,
            bottom = HADimens.SPACE4,
            top = HADimens.SPACE2,
        ),
    ) {
        // The lines between the circles, under them
        DistributionLines(
            model = model,
            colors = colors,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (high) HIGH_LINES_BOTTOM else 0.dp)
                .width((maxWidth - SIDE_CIRCLES).coerceIn(0.dp, MAX_LINES_WIDTH))
                .height(if (high) HIGH_LINES_HEIGHT else LINES_HEIGHT),
        )
        Column(Modifier.widthIn(max = MAX_ROW_WIDTH).align(Alignment.TopCenter)) {
            TopRow(model, colors, label)
            MiddleRow(model, colors, label)
            if (high) BottomRow(model, colors, label)
        }
    }
}

@Composable
private fun TopRow(model: EnergyDistributionModel, colors: DistributionColors, label: (String) -> String) {
    val solar = model.solar
    val gas = model.gas
    val water = model.water
    if (listOf(model.lowCarbon, solar, gas, water).all { it == null }) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        model.lowCarbon?.let { value ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(end = HADimens.SPACE1),
            ) {
                NodeLabel(label("low_carbon"))
                Node(colors.lowCarbon) {
                    NodeIcon("mdi:leaf", colors.lowCarbon)
                    NodeText(value)
                }
                FlowLine(colors.lowCarbon, animated = false)
            }
        } ?: Spacer(Modifier.width(SPACER))
        when {
            solar != null -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = HADimens.SPACE1).height(TOP_HEIGHT),
            ) {
                NodeLabel(label("solar"))
                Node(colors.solar) {
                    NodeIcon("mdi:solar-power")
                    NodeText(solar)
                }
            }
            gas != null || water != null -> Spacer(Modifier.width(SPACER))
        }
        when {
            gas != null -> UtilityNode(label("gas"), "mdi:fire", gas, colors.gas, model.gasFlows)
            water != null -> UtilityNode(label("water"), "mdi:water", water, colors.water, model.waterFlows)
            else -> Spacer(Modifier.width(SPACER))
        }
    }
}

/** Gas or water above the home, with the line down to it. */
@Composable
private fun UtilityNode(name: String, icon: String, value: String, color: Color, flows: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(start = HADimens.SPACE1).height(TOP_HEIGHT),
    ) {
        NodeLabel(name)
        Node(color) {
            NodeIcon(icon)
            NodeText(value)
        }
        FlowLine(color, animated = flows)
    }
}

@Composable
private fun MiddleRow(model: EnergyDistributionModel, colors: DistributionColors, label: (String) -> String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        model.grid?.let { grid ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Node(colors.gridIn) {
                    NodeIcon("mdi:transmission-tower")
                    grid.returned?.let { Amount("mdi:arrow-left", it, colors.gridOut) }
                    Amount(if (grid.returned != null) "mdi:arrow-right" else null, grid.fromGrid, colors.gridIn)
                }
                NodeLabel(label("grid"))
            }
        } ?: Spacer(Modifier.size(SPACER, GRID_SPACER_HEIGHT))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            HomeNode(model, colors)
            model.homeLabel?.let { NodeLabel(it) }
        }
    }
}

@Composable
private fun HomeNode(model: EnergyDistributionModel, colors: DistributionColors) {
    val ring = model.homeRing
    Box(contentAlignment = Alignment.Center) {
        if (ring == null) {
            Node(colors.primary) {
                NodeIcon("mdi:home")
                NodeText(model.home)
            }
        } else {
            HomeRing(ring, colors, Modifier.size(CIRCLE))
            Node(null) {
                NodeIcon("mdi:home")
                NodeText(model.home)
            }
        }
    }
}

@Composable
private fun BottomRow(model: EnergyDistributionModel, colors: DistributionColors, label: (String) -> String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Spacer(Modifier.width(SPACER))
        model.battery?.let { battery ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.height(BATTERY_HEIGHT),
            ) {
                Node(colors.batteryOut) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NodeIcon(battery.icon)
                        battery.stateOfCharge?.let { NodeText(it) }
                    }
                    Amount("mdi:arrow-down", battery.charged, colors.batteryIn)
                    Amount("mdi:arrow-up", battery.discharged, colors.batteryOut)
                }
                NodeLabel(label("battery"))
            }
        } ?: Spacer(Modifier.width(SPACER))
        val water = model.water
        if (model.waterBelow && water != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(start = HADimens.SPACE1).pullUp(WATER_BELOW_SHIFT),
            ) {
                FlowLine(colors.water, animated = model.waterFlows, upwards = true)
                Node(colors.water) {
                    NodeIcon("mdi:water")
                    NodeText(water)
                }
                NodeLabel(label("water"))
            }
        } else {
            Spacer(Modifier.width(SPACER))
        }
    }
}

/** The frontend's energy colours, light or dark like [dark]; [fallback] for any it can't resolve. */
internal class DistributionColors(private val dark: Boolean, private val fallback: Color) {
    val gridIn = color("energy-grid-consumption-color")
    val gridOut = color("energy-grid-return-color")
    val solar = color("energy-solar-color")
    val lowCarbon = color("energy-non-fossil-color")
    val batteryIn = color("energy-battery-in-color")
    val batteryOut = color("energy-battery-out-color")
    val gas = color("energy-gas-color")
    val water = color("energy-water-color")
    val primary = color("primary-color")

    /** Lines without a flow of their own. */
    val line = fallback

    private fun color(name: String) = resolveVariable(name, dark) ?: fallback
}

@Composable
private fun rememberDistributionColors(): DistributionColors {
    val dark = isSystemInDarkTheme()
    val text = LocalHAColorScheme.current.colorTextPrimary
    return remember(dark, text) { DistributionColors(dark, text) }
}

private const val LABELS = "ui.panel.lovelace.cards.energy.energy_distribution."
private val SPACER = 84.dp
private val GRID_SPACER_HEIGHT = 100.dp
private val TOP_HEIGHT = 130.dp
private val BATTERY_HEIGHT = 110.dp
private val WATER_BELOW_SHIFT = 20.dp
private val MAX_ROW_WIDTH = 500.dp
private val SIDE_CIRCLES = 160.dp
private val MAX_LINES_WIDTH = 340.dp
private val LINES_HEIGHT = 130.dp
private val HIGH_LINES_HEIGHT = 140.dp
private val HIGH_LINES_BOTTOM = 100.dp
