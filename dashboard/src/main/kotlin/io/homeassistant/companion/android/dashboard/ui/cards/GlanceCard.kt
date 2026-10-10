package io.homeassistant.companion.android.dashboard.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.derive.GlanceEntity
import io.homeassistant.companion.android.dashboard.derive.glanceCardModel
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.ui.theme.toColor
import java.time.Instant
import java.time.ZonedDateTime

/**
 * A glance card, port of `hui-glance-card` (frontend@20260624.6 src/panels/lovelace/cards/hui-glance-card.ts): an
 * optional title, then the entities in columns, each its name, icon (or picture) and state, opening its details.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GlanceCard(
    card: CardConfig,
    hass: State<HassSnapshot?>,
    now: State<ZonedDateTime?>,
    interactions: CardInteractions,
    modifier: Modifier = Modifier,
) {
    val glance by remember(card) {
        derivedStateOf {
            hass.value?.glanceCardModel(
                card,
                now.value?.toInstant() ?: Instant.EPOCH,
            )
        }
    }
    val model = glance ?: return
    val colors = LocalHAColorScheme.current
    DashboardCardSurface(modifier = modifier) {
        Column(
            Modifier.fillMaxWidth().padding(HADimens.SPACE4),
            verticalArrangement = Arrangement.spacedBy(HADimens.SPACE3),
        ) {
            model.title?.let {
                Text(
                    it,
                    style = HATextStyle.HeadlineMedium.copy(textAlign = TextAlign.Start),
                    color = colors.colorTextPrimary,
                )
            }
            FlowRow(Modifier.fillMaxWidth(), maxItemsInEachRow = model.columns) {
                model.entities.forEach { entity ->
                    GlanceEntityView(entity, Modifier.weight(1f).elementGestures(entity.actions, interactions))
                }
                // Keep the last row's columns as wide as the others
                repeat((model.columns - model.entities.size % model.columns) % model.columns) {
                    Box(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun GlanceEntityView(entity: GlanceEntity, modifier: Modifier) {
    val colors = LocalHAColorScheme.current
    val textColor = if (entity.missing) colors.colorOnWarningNormal else colors.colorTextPrimary
    Column(modifier.padding(HADimens.SPACE1), horizontalAlignment = Alignment.CenterHorizontally) {
        entity.name?.let {
            Text(it, style = HATextStyle.BodyMedium, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val picture = entity.picture
        when {
            picture != null -> ServerImage(
                picture,
                entity.name,
                Modifier.padding(HADimens.SPACE2).size(HASize.X5L).clip(CircleShape),
            )
            entity.missing -> DashboardIcon(
                "mdi:alert",
                colors.colorOnWarningNormal,
                Modifier.padding(HADimens.SPACE2).size(HASize.X2L),
            )
            entity.icon != null -> DashboardIcon(
                entity.icon,
                entity.color?.toColor() ?: colors.colorTextSecondary,
                Modifier.padding(HADimens.SPACE2).size(HASize.X2L),
            )
        }
        entity.state?.let {
            Text(
                it,
                style = HATextStyle.BodyMedium,
                color = colors.colorTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
