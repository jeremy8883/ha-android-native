package io.homeassistant.companion.android.dashboard.ui.cards.energy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.energy.EnergyGridBalanceModel
import io.homeassistant.companion.android.dashboard.energy.energyGridBalance
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.theme.resolveVariable
import kotlinx.serialization.json.JsonObject

/**
 * The grid balance: imported − exported = net, and a bar with the export left of the centre line and the import
 * right of it. A tap shows what each amount means, which the frontend shows in tooltips. Port of the rendering of
 * `hui-energy-grid-balance-card` (frontend@20260624.6).
 */
@Composable
internal fun EnergyGridBalanceCard(card: CardConfig, hass: State<HassSnapshot?>, modifier: Modifier = Modifier) {
    val snapshot = hass.value ?: return
    // Its title is in the tile, not the card's header
    val untitled = remember(card) { CardConfig(JsonObject(card.json - "title")) }
    EnergyCardFrame(untitled, hass, modifier) { data ->
        val model = remember(data, snapshot.formats) { snapshot.energyGridBalance(data, card.json.string("title")) }
        GridBalance(model)
    }
}

@Composable
private fun GridBalance(model: EnergyGridBalanceModel) {
    val colors = LocalHAColorScheme.current
    val dark = isSystemInDarkTheme()
    val import = remember(dark) { resolveVariable("energy-grid-consumption-color", dark) ?: colors.colorTextPrimary }
    val export = remember(dark) { resolveVariable("energy-grid-return-color", dark) ?: colors.colorTextPrimary }
    var explained by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { explained = !explained }) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE3),
            modifier = Modifier.padding(HADimens.SPACE3),
        ) {
            Box(
                Modifier.size(TILE_ICON).background(colors.colorFillNeutralQuietResting, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                DashboardIcon("mdi:transmission-tower", colors.colorTextSecondary, Modifier.size(ICON))
            }
            Column {
                Text(
                    model.title,
                    style = HATextStyle.BodyMedium,
                    color = colors.colorTextPrimary,
                    textAlign = TextAlign.Start,
                )
                FlowRow {
                    val secondary = colors.colorTextSecondary
                    Text(model.imported, style = HATextStyle.Body, color = import)
                    Text(" - ", style = HATextStyle.Body, color = secondary)
                    Text(model.exported, style = HATextStyle.Body, color = export)
                    Text(" = ", style = HATextStyle.Body, color = secondary)
                    Text(model.net, style = HATextStyle.Body, color = if (model.consumption) import else export)
                }
            }
        }
        BalanceBar(model, import, export)
        if (explained) {
            listOf(model.tooltips.imported, model.tooltips.exported, model.tooltips.net).forEach {
                Text(
                    it,
                    style = HATextStyle.Body,
                    color = colors.colorTextSecondary,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.padding(
                        start = HADimens.SPACE3,
                        end = HADimens.SPACE3,
                        bottom = HADimens.SPACE2,
                    ),
                )
            }
        }
    }
}

/** The two halves: the export grows left from the centre, the import right; the net is solid over the larger. */
@Composable
private fun BalanceBar(model: EnergyGridBalanceModel, import: Color, export: Color) {
    val line = LocalHAColorScheme.current.colorTextPrimary
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(start = HADimens.SPACE3, end = HADimens.SPACE3, bottom = HADimens.SPACE4, top = CENTER_OVERHANG)
            .height(BAR_HEIGHT),
    ) {
        val half = maxWidth / 2
        Row(Modifier.fillMaxHeight()) {
            Half(half, export, model.exportedShare, model.netShare.takeIf { !model.consumption }, left = true)
            Half(half, import, model.importedShare, model.netShare.takeIf { model.consumption }, left = false)
        }
        Box(
            Modifier
                .align(Alignment.Center)
                .width(CENTER_WIDTH)
                .height(BAR_HEIGHT + CENTER_OVERHANG * 2)
                .background(line),
        )
    }
}

@Composable
private fun Half(width: Dp, color: Color, share: Double, net: Double?, left: Boolean) {
    val radius = HARadius.L
    val shape = if (left) {
        RoundedCornerShape(
            topStart = radius,
            bottomStart = radius,
        )
    } else {
        RoundedCornerShape(topEnd = radius, bottomEnd = radius)
    }
    Box(
        Modifier
            .width(width)
            .fillMaxHeight()
            .border(1.dp, color.copy(alpha = BORDER_ALPHA), shape),
        contentAlignment = if (left) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        // A full share reaches the half's rounded end, so it takes its shape
        fun fillShape(of: Double) = if (of >= 1.0) shape else RectangleShape
        Box(
            Modifier.width(
                width * share.toFloat(),
            ).fillMaxHeight().background(color.copy(alpha = FILL_ALPHA), fillShape(share)),
        )
        net?.let { netShare ->
            Box(Modifier.width(width * netShare.toFloat()).fillMaxHeight().background(color, fillShape(netShare)))
        }
    }
}

private val TILE_ICON = 36.dp
private val ICON = 24.dp
private val BAR_HEIGHT = 42.dp
private val CENTER_WIDTH = 2.dp
private val CENTER_OVERHANG = 6.dp
private const val BORDER_ALPHA = 0.3f
private const val FILL_ALPHA = 0.3f
