package io.homeassistant.companion.android.dashboard.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.homeassistant.companion.android.common.compose.composable.HAHorizontalDivider
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.HeaderFooterButton
import io.homeassistant.companion.android.dashboard.derive.HeaderFooterModel
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.ui.theme.entityIconTint
import java.time.ZonedDateTime

/**
 * An entities card's `header` or `footer` element (a [footer] when true, which sets its dividers and padding): a
 * picture, a row of entity chips or a graph.
 *
 * Upstream: `createHeaderFooterElement` (src/panels/lovelace/create-element/create-header-footer-element.ts)
 */
@Composable
internal fun HeaderFooter(
    model: HeaderFooterModel,
    footer: Boolean,
    hass: State<HassSnapshot?>,
    now: State<ZonedDateTime?>,
    interactions: CardInteractions,
) {
    when (model) {
        is HeaderFooterModel.Picture -> PictureHeaderFooter(model, interactions)
        is HeaderFooterModel.Buttons -> ButtonsHeaderFooter(model, footer, interactions)
        is HeaderFooterModel.Graph -> SensorGraphView(
            model.graph,
            hass,
            now,
            Modifier
                .fillMaxWidth()
                .clickable { interactions.onAction(CardAction.MoreInfo(model.graph.entityId)) },
        )
        is HeaderFooterModel.Invalid -> CardWarning(model.message, Modifier.fillMaxWidth())
    }
}

/**
 * A picture as wide as the card, at its own height.
 *
 * Upstream: `hui-picture-header-footer` (src/panels/lovelace/header-footer/hui-picture-header-footer.ts)
 */
@Composable
private fun PictureHeaderFooter(model: HeaderFooterModel.Picture, interactions: CardInteractions) {
    val url = resolveServerUrl(model.image, LocalServerUrl.current, LocalBrandsToken.current) ?: return
    AsyncImage(
        model = url,
        contentDescription = model.altText,
        contentScale = ContentScale.FillWidth,
        modifier = Modifier.fillMaxWidth().elementGestures(model.actions, interactions),
    )
}

/**
 * A row of chips, one per entity, scrolling sideways on phones; a divider separates it from the rows.
 *
 * Upstream: `hui-buttons-header-footer`, `hui-buttons-base`
 * (src/panels/lovelace/header-footer/hui-buttons-header-footer.ts; src/panels/lovelace/components/hui-buttons-base.ts)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ButtonsHeaderFooter(model: HeaderFooterModel.Buttons, footer: Boolean, interactions: CardInteractions) {
    if (footer) HAHorizontalDivider(Modifier.padding(vertical = HADimens.SPACE4))
    val padding = Modifier
        .padding(horizontal = HADimens.SPACE3)
        .padding(
            top = if (!footer) HADimens.SPACE4 else HADimens.SPACE2,
            bottom = if (footer) HADimens.SPACE4 else HADimens.SPACE2,
        )
    val gap = Arrangement.spacedBy(HADimens.SPACE2)
    // Upstream wraps the chips, but keeps them on one scrolling line on phones (450px wide or less)
    if (LocalConfiguration.current.screenWidthDp > PHONE_MAX_WIDTH_DP) {
        FlowRow(Modifier.fillMaxWidth().then(padding), horizontalArrangement = gap, verticalArrangement = gap) {
            model.buttons.forEach { button -> EntityChip(button, interactions) }
        }
    } else {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).then(padding),
            horizontalArrangement = gap,
        ) {
            model.buttons.forEach { button -> EntityChip(button, interactions) }
        }
    }
    if (!footer) HAHorizontalDivider(Modifier.padding(bottom = HADimens.SPACE4))
}

/**
 * A filled assist chip with the entity's coloured badge and its name.
 *
 * Upstream: part of `hui-buttons-base`, with `ha-assist-chip` (src/panels/lovelace/components/hui-buttons-base.ts;
 * src/components/chips/ha-assist-chip.ts)
 */
@Composable
private fun EntityChip(button: HeaderFooterButton, interactions: CardInteractions) {
    val colors = LocalHAColorScheme.current
    val shape = RoundedCornerShape(CHIP_RADIUS)
    val badge = button.badge
    Row(
        modifier = Modifier
            .heightIn(min = CHIP_HEIGHT)
            .clip(shape)
            .background(colors.colorTextPrimary.copy(alpha = CHIP_FILL_ALPHA), shape)
            .elementGestures(button.actions, interactions)
            // The icon keeps its space before the (empty) label, like `ha-assist-chip`
            .padding(
                start = if (badge != null) HADimens.SPACE2 else HADimens.SPACE4,
                end = if (badge != null && button.name.isEmpty()) HADimens.SPACE6 else HADimens.SPACE4,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
    ) {
        badge?.let {
            val picture = it.picture
            if (picture != null) {
                ServerImage(picture, button.name, Modifier.size(CHIP_ICON).clip(CircleShape))
            } else {
                DashboardIcon(
                    it.icon,
                    entityIconTint(it.color, it.unavailable, it.brightness),
                    Modifier.size(CHIP_ICON),
                )
            }
        }
        if (button.name.isNotEmpty()) {
            Text(button.name, style = HATextStyle.BodyMedium, color = colors.colorTextPrimary, maxLines = 1)
        }
    }
}

private const val PHONE_MAX_WIDTH_DP = 450

/** `ha-assist-chip`'s 32dp tall chip with 16dp corners and an 18dp icon. */
private val CHIP_HEIGHT = 32.dp
private val CHIP_RADIUS = 16.dp
private val CHIP_ICON = 18.dp

/** `--ha-assist-chip-filled-container-color`: the primary text colour at 15 %. */
private const val CHIP_FILL_ALPHA = 0.15f
