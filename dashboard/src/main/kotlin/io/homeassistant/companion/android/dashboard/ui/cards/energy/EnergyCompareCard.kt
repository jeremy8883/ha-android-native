package io.homeassistant.companion.android.dashboard.ui.cards.energy

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.composable.HABanner
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.R
import io.homeassistant.companion.android.dashboard.energy.energyCompare
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.ui.EnergyChange
import io.homeassistant.companion.android.dashboard.ui.cards.CardInteractions
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardIcon

/**
 * While comparing, which periods are compared, with a link to compare with the other kind of period and a button
 * to stop comparing; nothing otherwise. Port of `hui-energy-compare-card` (frontend@20260624.6).
 */
@Composable
internal fun EnergyCompareCard(
    card: CardConfig,
    hass: State<HassSnapshot?>,
    interactions: CardInteractions,
    modifier: Modifier = Modifier,
) {
    val key = card.collectionKey
    val model by remember(key) {
        derivedStateOf { hass.value?.let { h -> h.energy[key]?.let { h.formats.energyCompare(it) } } }
    }
    val shown = model ?: return
    val localize = hass.value?.localize ?: return
    val colors = LocalHAColorScheme.current
    val prefix = "ui.panel.lovelace.cards.energy.energy_compare"
    // The translation places the two periods; they're shown bold
    val text = localize("$prefix.info", mapOf("start" to START, "end" to END))
    HABanner(modifier.fillMaxWidth()) {
        DashboardIcon(INFO_ICON, colors.colorOnPrimaryNormal, Modifier.size(ICON))
        Column(
            Modifier.weight(1f).padding(horizontal = HADimens.SPACE2),
            verticalArrangement = Arrangement.spacedBy(HADimens.SPACE1),
        ) {
            Text(
                buildAnnotatedString {
                    val before = text.substringBefore(START)
                    val between = text.substringAfter(START).substringBefore(END)
                    val after = text.substringAfter(END)
                    append(before)
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(shown.shown) }
                    append(between)
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(shown.compared) }
                    append(after)
                },
                style = HATextStyle.Body,
                color = colors.colorTextPrimary,
                textAlign = TextAlign.Start,
            )
            Text(
                "(${localize("$prefix.${shown.switchKey}")})",
                style = HATextStyle.Body,
                color = colors.colorOnPrimaryNormal,
                textAlign = TextAlign.Start,
                modifier = Modifier.clickable(role = Role.Button) {
                    interactions.onEnergyChange(key, EnergyChange.Compare(shown.switchTo))
                },
            )
        }
        val close = stringResource(R.string.native_dashboard_energy_stop_compare)
        IconButton(
            onClick = { interactions.onEnergyChange(key, EnergyChange.Compare(null)) },
            modifier = Modifier.semantics { contentDescription = close }.align(Alignment.Top),
        ) {
            DashboardIcon(CLOSE_ICON, colors.colorTextSecondary, Modifier.size(ICON))
        }
    }
}

// Placeholders the periods are put in, then shown bold
private const val START = "\u0001"
private const val END = "\u0002"
private const val INFO_ICON = "mdi:information-outline"
private const val CLOSE_ICON = "mdi:close"
private val ICON = 24.dp
