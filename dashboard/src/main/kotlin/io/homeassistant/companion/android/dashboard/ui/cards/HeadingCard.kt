package io.homeassistant.companion.android.dashboard.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.derive.HeadingBadgeModel
import io.homeassistant.companion.android.dashboard.derive.headingModel
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.ui.theme.toColor
import java.time.Instant
import java.time.ZonedDateTime

@Composable
internal fun HeadingCard(
    card: CardConfig,
    hass: State<HassSnapshot?>,
    now: State<ZonedDateTime?>,
    onGesture: OnGesture,
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
                heading.badges.forEach { badge -> HeadingBadge(badge, onGesture) }
            }
        }
    }
}

@Composable
private fun HeadingBadge(badge: HeadingBadgeModel, onGesture: OnGesture) {
    val colors = LocalHAColorScheme.current
    val tint = when (badge) {
        is HeadingBadgeModel.Entity -> badge.color?.toColor()
        is HeadingBadgeModel.Button -> badge.color?.toColor()
    }
    val background = when (badge) {
        // Coloured buttons get a tinted background, like upstream's 0.2 background opacity
        is HeadingBadgeModel.Button -> tint?.copy(alpha = BUTTON_BACKGROUND_ALPHA)
            ?: colors.colorFillNeutralQuietResting
        is HeadingBadgeModel.Entity -> colors.colorSurfaceLow
    }
    Row(
        modifier = Modifier
            .heightIn(min = HASize.X2L)
            .clip(CircleShape)
            .background(background)
            .elementGestures(badge.actions, onGesture)
            .semantics { if (badge is HeadingBadgeModel.Entity) contentDescription = badge.name }
            .padding(horizontal = HADimens.SPACE2, vertical = HADimens.SPACE1),
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = when (badge) {
            is HeadingBadgeModel.Entity -> badge.icon
            is HeadingBadgeModel.Button -> badge.icon
        }
        DashboardIcon(name = icon, tint = tint ?: colors.colorTextSecondary, modifier = Modifier.size(HASize.L))
        val text = when (badge) {
            is HeadingBadgeModel.Entity -> badge.state
            is HeadingBadgeModel.Button -> badge.text
        }
        text?.let { Text(it, style = HATextStyle.BodyMedium, color = colors.colorTextPrimary, maxLines = 1) }
    }
}

private const val CHEVRON = "mdi:chevron-right"
private const val BUTTON_BACKGROUND_ALPHA = 0.2f
