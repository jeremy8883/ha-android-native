package io.homeassistant.companion.android.dashboard.ui.moreinfo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.data.Loadable
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.moreinfo.CleanArea
import io.homeassistant.companion.android.dashboard.moreinfo.VacuumCleanAreas
import io.homeassistant.companion.android.dashboard.moreinfo.vacuumCleanAreas
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.loadErrorText

/** Upstream's "Cleaning · By area" button, opening the areas to clean. */
@Composable
internal fun CleanAreasButton(
    label: Pair<String, String>,
    state: EntityState,
    hass: HassSnapshot,
    onAction: (CardAction) -> Unit,
) {
    val colors = LocalHAColorScheme.current
    var open by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .widthIn(max = BUTTON_MAX_WIDTH)
            .fillMaxWidth()
            .clip(RoundedCornerShape(HARadius.L))
            .background(colors.colorFillDisabledLoudResting.copy(alpha = TINT_ALPHA))
            .clickable(enabled = state.state != "unavailable", role = Role.Button) { open = true }
            .padding(HADimens.SPACE3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE3),
    ) {
        DashboardIcon("mdi:texture-box", colors.colorTextPrimary, Modifier.size(HASize.XL))
        Column(Modifier.weight(1f)) {
            Text(label.first, style = HATextStyle.BodyMedium, color = colors.colorTextSecondary)
            Text(label.second, style = HATextStyle.Body, color = colors.colorTextPrimary)
        }
        DashboardIcon("mdi:chevron-right", colors.colorTextSecondary, Modifier.size(HASize.XL))
    }
    if (open) CleanAreasDialog(state, hass, onAction) { open = false }
}

/**
 * Port of `ha-more-info-view-vacuum-clean-areas`: the areas mapped to the vacuum's map (from its registry entry,
 * loaded here), by floor; taps choose them in order (numbered), and start cleans them.
 */
@Composable
private fun CleanAreasDialog(
    state: EntityState,
    hass: HassSnapshot,
    onAction: (CardAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val entry by favoritesViewModel(state.entityId).entry.collectAsStateWithLifecycle()
    val view = (entry as? Loadable.Ready)?.let { hass.vacuumCleanAreas(state, it.value) }
    var chosen by remember { mutableStateOf(listOf<String>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = view?.let { { Text(it.title, style = HATextStyle.HeadlineMedium) } },
        text = {
            when (val loaded = entry) {
                Loadable.Loading -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { HALoading() }
                is Loadable.Failed -> Text(
                    LocalContext.current.loadErrorText(loaded.error),
                    style = HATextStyle.BodyMedium,
                    color = LocalHAColorScheme.current.colorOnDangerNormal,
                )
                is Loadable.Ready -> view?.let {
                    CleanAreasContent(it, chosen) { area ->
                        chosen =
                            if (area in chosen) chosen - area else chosen + area
                    }
                }
            }
        },
        confirmButton = {
            if (view != null && view.empty == null) {
                val count = if (chosen.isEmpty()) "" else " (${chosen.size})"
                HAPlainButton("${view.startLabel}$count", {
                    onAction(view.clean(chosen))
                    onDismiss()
                }, enabled = chosen.isNotEmpty())
            }
        },
        dismissButton = { HAPlainButton(stringResource(commonR.string.cancel), onDismiss) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CleanAreasContent(view: VacuumCleanAreas, chosen: List<String>, onToggle: (String) -> Unit) {
    val colors = LocalHAColorScheme.current
    view.empty?.let { empty ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
        ) {
            DashboardIcon("mdi:texture-box", colors.colorTextSecondary, Modifier.size(HASize.X5L))
            Text(empty.title, style = HATextStyle.Body, color = colors.colorTextPrimary)
            Text(
                empty.text,
                style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.Center),
                color = colors.colorTextSecondary,
            )
        }
        return
    }
    Column(
        Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
    ) {
        view.sections.forEach { section ->
            section.label?.let { label ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
                ) {
                    section.icon?.let { DashboardIcon(it, colors.colorTextSecondary, Modifier.size(HASize.XL)) }
                    Text(label, style = HATextStyle.BodyMedium, color = colors.colorTextSecondary)
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
                verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
            ) {
                section.areas.forEach { area -> AreaCard(area, chosen.indexOf(area.areaId)) { onToggle(area.areaId) } }
            }
        }
        Text(view.hint, style = HATextStyle.BodyMedium, color = colors.colorTextSecondary)
    }
}

/** An area to clean: its icon and name, outlined and numbered by when it was chosen. */
@Composable
private fun AreaCard(area: CleanArea, order: Int, onClick: () -> Unit) {
    val colors = LocalHAColorScheme.current
    val selected = order >= 0
    Box(
        modifier = Modifier
            .width(CARD_WIDTH)
            .clip(RoundedCornerShape(HARadius.L))
            .background(colors.colorFillDisabledLoudResting.copy(alpha = TINT_ALPHA))
            .border(
                BORDER,
                if (selected) {
                    colors.colorFillPrimaryLoudResting
                } else {
                    colors.colorFillDisabledLoudResting.copy(
                        alpha = 0f,
                    )
                },
                RoundedCornerShape(HARadius.L),
            )
            .clickable(role = Role.Checkbox, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(HADimens.SPACE3),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            DashboardIcon(
                area.icon,
                if (selected) colors.colorFillPrimaryLoudResting else colors.colorTextSecondary,
                Modifier.size(HASize.X2L),
            )
            Text(area.name, style = HATextStyle.BodyMedium, color = colors.colorTextPrimary, maxLines = 2)
        }
        if (selected) {
            Box(
                modifier = Modifier.align(
                    Alignment.TopEnd,
                ).size(BADGE).clip(CircleShape).background(colors.colorFillPrimaryLoudResting),
                contentAlignment = Alignment.Center,
            ) { Text("${order + 1}", style = HATextStyle.BodyMedium, color = colors.colorOnPrimaryLoud) }
        }
    }
}

private const val TINT_ALPHA = 0.2f
private val BUTTON_MAX_WIDTH = 400.dp
private val CARD_WIDTH = 104.dp
private val BADGE = 20.dp
private val BORDER = 2.dp
