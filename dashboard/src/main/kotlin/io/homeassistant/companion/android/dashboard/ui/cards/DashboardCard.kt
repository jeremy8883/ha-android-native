package io.homeassistant.companion.android.dashboard.ui.cards

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.dashboard.action.cardActions
import io.homeassistant.companion.android.dashboard.derive.discoveredDevicesModel
import io.homeassistant.companion.android.dashboard.derive.homeSummaryModel
import io.homeassistant.companion.android.dashboard.derive.repairsModel
import io.homeassistant.companion.android.dashboard.derive.updatesModel
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.layout.conditionalInnerCard
import io.homeassistant.companion.android.dashboard.model.CardConfig
import java.time.ZonedDateTime

/** Renders [card] with its native renderer, or a placeholder when the type is not supported yet. */
@Composable
internal fun DashboardCard(
    card: CardConfig,
    hass: State<HassSnapshot?>,
    now: State<ZonedDateTime?>,
    interactions: CardInteractions,
    modifier: Modifier = Modifier,
) {
    // A conditional card is only laid out while its conditions pass, so it shows its card
    card.conditionalInnerCard()?.let { inner -> return DashboardCard(inner, hass, now, interactions, modifier) }
    val actions = remember(card) { cardActions(card) }
    // Gestures work on every card, so actions are available before every card type is native
    val cardModifier = modifier.clip(RoundedCornerShape(HARadius.XL)).elementGestures(actions.card, interactions)
    when (card.type) {
        CARD_TILE -> TileCard(
            card = card,
            hass = hass,
            now = now,
            modifier = cardModifier,
            iconModifier =
            actions.icon?.let { Modifier.clip(CircleShape).elementGestures(it, interactions) } ?: Modifier,
            onAction = interactions.onAction,
        )
        CARD_HEADING -> HeadingCard(card, hass, now, interactions, cardModifier)
        CARD_AREA -> AreaCard(card, hass, cardModifier)
        CARD_MARKDOWN -> MarkdownCard(card, hass, cardModifier)
        CARD_ENTITIES -> EntitiesCard(card, hass, now, interactions, modifier)
        CARD_MEDIA_CONTROL -> MediaControlCard(card, hass, interactions, cardModifier)
        CARD_EMPTY_STATE -> EmptyStateCard(card, interactions, modifier)
        CARD_SHORTCUT -> ShortcutCard(card, hass, cardModifier)
        CARD_PICTURE_ENTITY -> PictureEntityCard(card, hass, interactions, modifier)
        CARD_HOME_SUMMARY -> InfoTileCard(card, hass, cardModifier) { homeSummaryModel(it) }
        CARD_REPAIRS -> InfoTileCard(card, hass, cardModifier) { repairsModel(it) }
        CARD_UPDATES -> InfoTileCard(card, hass, cardModifier) { updatesModel(it) }
        CARD_DISCOVERED_DEVICES -> InfoTileCard(card, hass, cardModifier) { discoveredDevicesModel(it) }
        else -> UnsupportedCard(card.type.orEmpty(), cardModifier)
    }
}
private const val CARD_TILE = "tile"
private const val CARD_HEADING = "heading"
private const val CARD_AREA = "area"
private const val CARD_HOME_SUMMARY = "home-summary"
private const val CARD_MARKDOWN = "markdown"
private const val CARD_ENTITIES = "entities"
private const val CARD_MEDIA_CONTROL = "media-control"
private const val CARD_EMPTY_STATE = "empty-state"
private const val CARD_SHORTCUT = "shortcut"
private const val CARD_PICTURE_ENTITY = "picture-entity"
private const val CARD_REPAIRS = "repairs"
private const val CARD_UPDATES = "updates"
private const val CARD_DISCOVERED_DEVICES = "discovered-devices"
