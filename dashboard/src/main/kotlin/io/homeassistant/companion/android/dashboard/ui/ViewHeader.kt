package io.homeassistant.companion.android.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.derive.DisplayColor
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.layout.ViewBadgeModel
import io.homeassistant.companion.android.dashboard.layout.viewHeader
import io.homeassistant.companion.android.dashboard.model.ViewConfig
import io.homeassistant.companion.android.dashboard.ui.cards.CardInteractions
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardCard
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.cards.LocalConditionContext
import io.homeassistant.companion.android.dashboard.ui.cards.elementGestures
import io.homeassistant.companion.android.dashboard.ui.theme.toColor
import java.time.Instant
import java.time.ZonedDateTime

/** The header of a sections view: its card, then (or before, with `badges_position: top`) its badges. */
@Composable
internal fun ViewHeader(
    view: ViewConfig,
    hass: State<HassSnapshot?>,
    now: State<ZonedDateTime?>,
    interactions: CardInteractions,
) {
    val context = LocalConditionContext.current
    val model by remember(view, context) {
        derivedStateOf {
            hass.value?.viewHeader(view, context.copy(now = now.value), now.value?.toInstant() ?: Instant.EPOCH)
        }
    }
    val header = model ?: return
    val badges = @Composable { if (header.badges.isNotEmpty()) ViewBadges(header.badges, interactions) }
    Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4), modifier = Modifier.fillMaxWidth()) {
        if (header.badgesPosition == BADGES_TOP) badges()
        header.card?.let { card -> DashboardCard(card, hass, now, interactions, Modifier.fillMaxWidth()) }
        if (header.badgesPosition != BADGES_TOP) badges()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ViewBadges(badges: List<ViewBadgeModel>, interactions: CardInteractions) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
    ) {
        badges.forEach { ViewBadge(it, interactions) }
    }
}

@Composable
private fun ViewBadge(badge: ViewBadgeModel, interactions: CardInteractions) {
    val colors = LocalHAColorScheme.current
    Row(
        modifier = Modifier
            .heightIn(min = HASize.X4L)
            .clip(CircleShape)
            .background(colors.colorSurfaceLow)
            .elementGestures(badge.actions, interactions)
            .padding(horizontal = HADimens.SPACE3, vertical = HADimens.SPACE1),
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DashboardIcon(
            name = badge.icon,
            // `--badge-color`: the primary colour while active, else the inactive one
            tint = badge.color?.toColor()
                ?: DisplayColor.State(listOf(if (badge.active) "primary-color" else "state-inactive-color")).toColor()
                ?: colors.colorTextSecondary,
            modifier = Modifier.size(HASize.L),
        )
        Column {
            badge.label?.let {
                Text(it, style = HATextStyle.BodyMedium, color = colors.colorTextSecondary, maxLines = 1)
            }
            badge.content?.let {
                Text(it, style = HATextStyle.BodyMedium, color = colors.colorTextPrimary, maxLines = 1)
            }
        }
    }
}

private const val BADGES_TOP = "top"
