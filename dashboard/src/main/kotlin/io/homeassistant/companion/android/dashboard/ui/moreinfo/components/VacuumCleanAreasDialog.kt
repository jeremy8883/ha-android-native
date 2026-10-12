package io.homeassistant.companion.android.dashboard.ui.moreinfo.components

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
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.ButtonSize
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.data.Loadable
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.moreinfo.CleanArea
import io.homeassistant.companion.android.dashboard.moreinfo.VacuumCleanAreas
import io.homeassistant.companion.android.dashboard.moreinfo.vacuumCleanAreas
import io.homeassistant.companion.android.dashboard.ui.components.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.components.loadErrorText
import io.homeassistant.companion.android.dashboard.ui.moreinfo.favoritesViewModel
import io.homeassistant.companion.android.dashboard.ui.theme.Css
import io.homeassistant.companion.android.dashboard.ui.theme.cssFontPx

/**
 * Upstream's "Cleaning · By area" button, opening the areas to clean; [onConfigure] opens the full dialog, where an
 * admin maps the vacuum's segments to areas.
 *
 * Upstream: part of `more-info-vacuum` (src/dialogs/more-info/controls/more-info-vacuum.ts)
 */
@Composable
internal fun CleanAreasButton(
    label: Pair<String, String>,
    state: EntityState,
    hass: HassSnapshot,
    onAction: (CardAction) -> Unit,
    onConfigure: (() -> Unit)?,
) {
    val colors = LocalHAColorScheme.current
    var open by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .widthIn(max = BUTTON_MAX_WIDTH)
            .fillMaxWidth()
            .clip(RoundedCornerShape(Css.Radius.Lg))
            .background(colors.colorFillDisabledLoudResting.copy(alpha = TINT_ALPHA))
            .clickable(enabled = state.state != "unavailable", role = Role.Button) { open = true }
            .padding(Css.Space.S3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Css.Space.S3),
    ) {
        DashboardIcon("mdi:texture-box", colors.colorTextPrimary, Modifier.size(HASize.XL))
        Column(Modifier.weight(1f)) {
            // `.clean-areas-button`: `line-height: 1.4; letter-spacing: 0.25px`
            val buttonText = Css.Text.Body.copy(
                textAlign = TextAlign.Start,
                lineHeight = BUTTON_LINE_HEIGHT.em,
                letterSpacing = BUTTON_LETTER_SPACING.cssFontPx,
            )
            Text(label.first, style = buttonText, color = colors.colorTextSecondary)
            Text(label.second, style = buttonText, color = colors.colorTextPrimary)
        }
        DashboardIcon("mdi:chevron-right", colors.colorTextSecondary, Modifier.size(HASize.XL))
    }
    if (open) CleanAreasDialog(state, hass, onAction, onConfigure) { open = false }
}

/**
 * Port of `ha-more-info-view-vacuum-clean-areas`: the areas mapped to the vacuum's map (from its registry entry,
 * loaded here), by floor; taps choose them in order (numbered), and start cleans them. Without mapped areas, an
 * admin's "Configure" opens the full dialog ([onConfigure]), as the segment mapping isn't drawn natively.
 *
 * Upstream: `ha-more-info-view-vacuum-clean-areas`
 * (src/dialogs/more-info/components/vacuum/ha-more-info-view-vacuum-clean-areas.ts)
 */
@Composable
private fun CleanAreasDialog(
    state: EntityState,
    hass: HassSnapshot,
    onAction: (CardAction) -> Unit,
    onConfigure: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val entry by favoritesViewModel(state.entityId).entry.collectAsStateWithLifecycle()
    val view = (entry as? Loadable.Ready)?.let { hass.vacuumCleanAreas(state, it.value) }
    var chosen by remember { mutableStateOf(listOf<String>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = view?.let { { Text(it.title, style = Css.Text.DialogTitle) } },
        text = {
            when (val loaded = entry) {
                Loadable.Loading -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { HALoading() }
                is Loadable.Failed -> Text(
                    LocalContext.current.loadErrorText(loaded.error),
                    style = Css.Text.Body,
                    color = LocalHAColorScheme.current.colorOnDangerNormal,
                )
                is Loadable.Ready -> view?.let {
                    val configure = onConfigure?.let { open -> { open().also { onDismiss() } } }
                    CleanAreasContent(it, chosen, configure) { area ->
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

/**
 * The areas by floor, then the hint on the order; or, without mapped areas, why there are none and for an admin
 * the button configuring them ([onConfigure]).
 *
 * Upstream: part of `ha-more-info-view-vacuum-clean-areas`
 * (src/dialogs/more-info/components/vacuum/ha-more-info-view-vacuum-clean-areas.ts)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CleanAreasContent(
    view: VacuumCleanAreas,
    chosen: List<String>,
    onConfigure: (() -> Unit)?,
    onToggle: (String) -> Unit,
) {
    val colors = LocalHAColorScheme.current
    view.empty?.let { empty ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Css.Space.S2),
        ) {
            DashboardIcon("mdi:texture-box", colors.colorTextSecondary, Modifier.size(HASize.X5L))
            Text(empty.title, style = Css.Text.Body.copy(textAlign = TextAlign.Center), color = colors.colorTextPrimary)
            Text(
                empty.text,
                style = Css.Text.Body.copy(textAlign = TextAlign.Center),
                color = colors.colorTextSecondary,
            )
            val configureLabel = empty.configureLabel
            if (configureLabel != null && onConfigure != null) {
                HAPlainButton(
                    configureLabel,
                    onConfigure,
                    size = ButtonSize.SMALL,
                    prefix = { DashboardIcon(CONFIGURE_ICON, LocalContentColor.current, Modifier.size(HASize.XL)) },
                )
            }
        }
        return
    }
    Column(
        Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Css.Space.S4),
    ) {
        view.sections.forEach { section ->
            section.label?.let { label ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Css.Space.S2),
                ) {
                    section.icon?.let { DashboardIcon(it, colors.colorTextSecondary, Modifier.size(HASize.XL)) }
                    Text(label, style = Css.Text.Body, color = colors.colorTextSecondary)
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Css.Space.S2),
                verticalArrangement = Arrangement.spacedBy(Css.Space.S2),
            ) {
                section.areas.forEach { area -> AreaCard(area, chosen.indexOf(area.areaId)) { onToggle(area.areaId) } }
            }
        }
        Text(view.hint, style = Css.Text.Body.copy(textAlign = TextAlign.Center), color = colors.colorTextSecondary)
    }
}

/**
 * An area to clean: its icon and name, outlined and numbered by when it was chosen.
 *
 * Upstream: part of `ha-more-info-view-vacuum-clean-areas` (`_renderAreaCard`)
 * (src/dialogs/more-info/components/vacuum/ha-more-info-view-vacuum-clean-areas.ts)
 */
@Composable
private fun AreaCard(area: CleanArea, order: Int, onClick: () -> Unit) {
    val colors = LocalHAColorScheme.current
    val selected = order >= 0
    Box(
        modifier = Modifier
            .width(CARD_WIDTH)
            .clip(RoundedCornerShape(Css.Radius.Lg))
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
                RoundedCornerShape(Css.Radius.Lg),
            )
            .clickable(role = Role.Checkbox, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(Css.Space.S3),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            DashboardIcon(
                area.icon,
                if (selected) colors.colorFillPrimaryLoudResting else colors.colorTextSecondary,
                Modifier.size(HASize.X2L),
            )
            Text(
                area.name,
                style = Css.Text.Body.copy(textAlign = TextAlign.Center),
                color = colors.colorTextPrimary,
                maxLines = 2,
            )
        }
        if (selected) {
            Box(
                modifier = Modifier.align(
                    Alignment.TopEnd,
                ).size(BADGE).clip(CircleShape).background(colors.colorFillPrimaryLoudResting),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${order + 1}",
                    style = Css.Text.Body.copy(textAlign = TextAlign.Center),
                    color = colors.colorOnPrimaryLoud,
                )
            }
        }
    }
}

private const val TINT_ALPHA = 0.2f
private const val BUTTON_LINE_HEIGHT = 1.4f
private const val BUTTON_LETTER_SPACING = 0.25
private val BUTTON_MAX_WIDTH = 400.dp
private val CARD_WIDTH = 104.dp
private val BADGE = 20.dp
private val BORDER = 2.dp

private const val CONFIGURE_ICON = "mdi:cog-outline"
