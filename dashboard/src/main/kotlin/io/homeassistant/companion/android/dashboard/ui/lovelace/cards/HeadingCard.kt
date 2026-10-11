package io.homeassistant.companion.android.dashboard.ui.lovelace.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HAFontSize
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.derive.DisplayColor
import io.homeassistant.companion.android.dashboard.derive.HeadingBadgeModel
import io.homeassistant.companion.android.dashboard.derive.headingModel
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.ui.components.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.lovelace.common.CardInteractions
import io.homeassistant.companion.android.dashboard.ui.lovelace.common.LocalConditionContext
import io.homeassistant.companion.android.dashboard.ui.lovelace.common.elementGestures
import io.homeassistant.companion.android.dashboard.ui.theme.toColor
import java.time.Instant
import java.time.ZonedDateTime

/**
 * A heading card: its icon and title (or subtitle), with a chevron when it has an action, and its badges on the right.
 *
 * Upstream: `hui-heading-card` (src/panels/lovelace/cards/hui-heading-card.ts)
 */
@Composable
internal fun HeadingCard(
    card: CardConfig,
    hass: State<HassSnapshot?>,
    now: State<ZonedDateTime?>,
    interactions: CardInteractions,
    modifier: Modifier = Modifier,
) {
    val context = LocalConditionContext.current
    val model by remember(card, context) {
        derivedStateOf {
            hass.value?.headingModel(card, context.copy(now = now.value), now.value?.toInstant() ?: Instant.EPOCH)
        }
    }
    val heading = model ?: return
    if (heading.text.isEmpty() && heading.badges.isEmpty()) return
    val colors = LocalHAColorScheme.current
    Row(
        modifier = Modifier.padding(
            start = HADimens.SPACE1,
            top = HADimens.SPACE4,
            end = HADimens.SPACE1,
            bottom = HADimens.SPACE1,
        ),
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The title takes the tap action; badges handle their own gestures
        Row(
            modifier = modifier.weight(1f, fill = false),
            horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DashboardIcon(
                name = heading.icon,
                tint = colors.colorTextSecondary,
                modifier = Modifier.size(if (heading.isSubtitle) HASize.L else HASize.XL),
            )
            if (heading.text.isNotEmpty()) {
                Text(
                    text = heading.text,
                    style = if (heading.isSubtitle) HATextStyle.Body else HATextStyle.HeadlineMedium,
                    color = colors.colorTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (heading.actionable) {
                DashboardIcon(name = CHEVRON, tint = colors.colorTextSecondary, modifier = Modifier.size(HASize.L))
            }
        }
        if (heading.badges.isNotEmpty()) {
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState(), reverseScrolling = true),
                horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                heading.badges.forEach { badge -> HeadingBadge(badge, interactions) }
            }
        }
    }
}

/**
 * A heading's badge: an entity's icon and state, or a button's icon and text.
 *
 * Upstream: `hui-entity-heading-badge`, `hui-button-heading-badge`
 * (src/panels/lovelace/heading-badges/hui-entity-heading-badge.ts;
 * src/panels/lovelace/heading-badges/hui-button-heading-badge.ts)
 */
@Composable
private fun HeadingBadge(badge: HeadingBadgeModel, interactions: CardInteractions) {
    when (badge) {
        is HeadingBadgeModel.Entity -> EntityHeadingBadge(badge, interactions)
        is HeadingBadgeModel.Button -> ButtonHeadingBadge(badge, interactions)
    }
}

/**
 * An entity's badge: its icon and state in the secondary text colour, without a background.
 *
 * Upstream: `hui-entity-heading-badge`, `ha-heading-badge`
 * (src/panels/lovelace/heading-badges/hui-entity-heading-badge.ts; src/components/ha-heading-badge.ts)
 */
@Composable
private fun EntityHeadingBadge(badge: HeadingBadgeModel.Entity, interactions: CardInteractions) {
    // `--secondary-text-color`, which an icon without a colour inherits
    val secondary =
        DisplayColor.State(listOf(SECONDARY_TEXT)).toColor() ?: LocalHAColorScheme.current.colorTextSecondary
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(HADimens.SPACE1))
            .elementGestures(badge.actions, interactions)
            .semantics { contentDescription = badge.name },
        horizontalArrangement = Arrangement.spacedBy(ENTITY_BADGE_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DashboardIcon(name = badge.icon, tint = badge.color?.toColor() ?: secondary, modifier = Modifier.size(HASize.L))
        badge.state?.let {
            Text(it, style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.Start), color = secondary, maxLines = 1)
        }
    }
}

/**
 * A button badge: a pill of its colour (or the disabled colour) at 0.2 opacity, with its icon and text.
 *
 * Upstream: `hui-button-heading-badge`, `ha-control-button`
 * (src/panels/lovelace/heading-badges/hui-button-heading-badge.ts; src/components/ha-control-button.ts)
 */
@Composable
private fun ButtonHeadingBadge(badge: HeadingBadgeModel.Button, interactions: CardInteractions) {
    val colors = LocalHAColorScheme.current
    val color = badge.color?.toColor()
    val background = color ?: DisplayColor.State(listOf(DISABLED)).toColor() ?: colors.colorFillNeutralQuietResting
    Row(
        modifier = Modifier
            .height(BUTTON_BADGE_SIZE)
            .widthIn(min = BUTTON_BADGE_SIZE)
            .clip(CircleShape)
            .background(background.copy(alpha = BUTTON_BACKGROUND_ALPHA))
            .elementGestures(badge.actions, interactions)
            .padding(horizontal = if (badge.text != null) HADimens.SPACE2 else HADimens.SPACE0),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        badge.icon?.let {
            DashboardIcon(name = it, tint = color ?: colors.colorTextPrimary, modifier = Modifier.size(HASize.L))
        }
        badge.text?.let {
            Text(
                it,
                style = HATextStyle.BodyMedium.copy(fontSize = HAFontSize.S, fontWeight = FontWeight.Medium),
                color = colors.colorTextPrimary,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = HADimens.SPACE1),
            )
        }
    }
}

private const val CHEVRON = "mdi:chevron-right"
private const val BUTTON_BACKGROUND_ALPHA = 0.2f
private const val SECONDARY_TEXT = "secondary-text-color"
private const val DISABLED = "disabled-color"

/** `ha-heading-badge`'s 3px gap between the icon and the state. */
private val ENTITY_BADGE_GAP = 3.dp

/** `--ha-heading-badge-size`. */
private val BUTTON_BADGE_SIZE = 26.dp
