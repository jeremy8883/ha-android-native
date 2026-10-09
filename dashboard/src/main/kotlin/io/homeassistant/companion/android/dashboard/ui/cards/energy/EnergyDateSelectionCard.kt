package io.homeassistant.companion.android.dashboard.ui.cards.energy

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.R
import io.homeassistant.companion.android.dashboard.energy.CompareMode
import io.homeassistant.companion.android.dashboard.energy.EnergyPeriod
import io.homeassistant.companion.android.dashboard.energy.EnergyRangePreset
import io.homeassistant.companion.android.dashboard.energy.energyPeriodTitle
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.string
import io.homeassistant.companion.android.dashboard.strategy.energy.DEFAULT_ENERGY_COLLECTION_KEY
import io.homeassistant.companion.android.dashboard.ui.EnergyChange
import io.homeassistant.companion.android.dashboard.ui.cards.CardInteractions
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardCardSurface
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardIcon
import java.time.DayOfWeek
import java.time.ZonedDateTime

/**
 * The energy date selection: the shown period, with the previous and next ones, today's, and the ranges and
 * comparison of the frontend's period selector (`hui-energy-date-selection-card`, frontend@20260624.6
 * src/panels/lovelace/components/hui-energy-period-selector.ts). The frontend's free date range picker is not
 * offered yet.
 */
@Composable
internal fun EnergyDateSelectionCard(
    card: CardConfig,
    hass: State<HassSnapshot?>,
    now: State<ZonedDateTime?>,
    interactions: CardInteractions,
    modifier: Modifier = Modifier,
) {
    val key = card.json.string("collection_key") ?: DEFAULT_ENERGY_COLLECTION_KEY
    val collection by remember(key) { derivedStateOf { hass.value?.energy?.get(key) } }
    val shown = collection
    val snapshot = hass.value
    val today = now.value?.toLocalDate()
    if (shown == null || snapshot == null || today == null) return
    val firstDay = DayOfWeek.of(snapshot.formats.firstWeekday)
    val title = remember(shown.period, today) { snapshot.formats.energyPeriodTitle(shown.period, today) }
    val localize = snapshot.localize
    fun choose(period: EnergyPeriod) = interactions.onEnergyChange(key, EnergyChange.Period(period))
    DashboardCardSurface(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = HADimens.SPACE2, vertical = HADimens.SPACE1),
        ) {
            RangeMenu(localize = { localize(it) }, onPick = { choose(it.period(today, firstDay)) })
            Column(Modifier.weight(1f).padding(horizontal = HADimens.SPACE2)) {
                Text(title.title, style = HATextStyle.BodyMedium, color = LocalHAColorScheme.current.colorTextPrimary)
                title.subtitle?.let {
                    Text(it, style = HATextStyle.Body, color = LocalHAColorScheme.current.colorTextSecondary)
                }
            }
            if (shown.loading) HALoading(modifier = Modifier.size(HADimens.SPACE5))
            SelectorButton(MDI_PREVIOUS, localize(SELECTOR + "previous")) {
                choose(shown.period.shift(forward = false))
            }
            SelectorButton(MDI_NEXT, localize(SELECTOR + "next")) { choose(shown.period.shift(forward = true)) }
            OverflowMenu(
                nowLabel = localize(SELECTOR + "now"),
                compareLabel = localize(SELECTOR + "compare"),
                comparing = shown.compareMode != null,
                onNow = { choose(shown.period.current(today, firstDay)) },
                onCompare = {
                    val mode = if (shown.compareMode == null) CompareMode.PREVIOUS else null
                    interactions.onEnergyChange(key, EnergyChange.Compare(mode))
                },
            )
        }
    }
}

@Composable
private fun SelectorButton(icon: String, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = label }) {
        DashboardIcon(icon, tint = LocalHAColorScheme.current.colorTextPrimary)
    }
}

/** The ranges the frontend's date picker offers, from a calendar button. */
@Composable
private fun RangeMenu(localize: (String) -> String, onPick: (EnergyRangePreset) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        SelectorButton(MDI_CALENDAR, localize(PICKER + "select_date_range")) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            EnergyRangePreset.entries.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(localize(PICKER + "ranges." + preset.key), style = HATextStyle.Body) },
                    onClick = {
                        open = false
                        onPick(preset)
                    },
                )
            }
        }
    }
}

/** Going to now and comparing, which the frontend collapses in a menu on narrow screens. */
@Composable
private fun OverflowMenu(
    nowLabel: String,
    compareLabel: String,
    comparing: Boolean,
    onNow: () -> Unit,
    onCompare: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val colors = LocalHAColorScheme.current
    Box {
        SelectorButton(MDI_MORE, stringResource(R.string.native_dashboard_more_options)) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(nowLabel, style = HATextStyle.Body) },
                leadingIcon = { DashboardIcon(MDI_NOW, tint = colors.colorTextPrimary) },
                onClick = {
                    open = false
                    onNow()
                },
            )
            DropdownMenuItem(
                text = { Text(compareLabel, style = HATextStyle.Body) },
                leadingIcon = {
                    DashboardIcon(if (comparing) MDI_CHECKED else MDI_UNCHECKED, tint = colors.colorTextPrimary)
                },
                onClick = {
                    open = false
                    onCompare()
                },
            )
        }
    }
}

private const val SELECTOR = "ui.panel.lovelace.components.energy_period_selector."
private const val PICKER = "ui.components.date-range-picker."
private const val MDI_PREVIOUS = "mdi:chevron-left"
private const val MDI_NEXT = "mdi:chevron-right"
private const val MDI_CALENDAR = "mdi:calendar"
private const val MDI_MORE = "mdi:dots-vertical"
private const val MDI_NOW = "mdi:home-clock"
private const val MDI_CHECKED = "mdi:checkbox-outline"
private const val MDI_UNCHECKED = "mdi:checkbox-blank-outline"
