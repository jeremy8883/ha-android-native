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
import io.homeassistant.companion.android.dashboard.derive.InfoTileModel
import io.homeassistant.companion.android.dashboard.derive.discoveredDevicesModel
import io.homeassistant.companion.android.dashboard.derive.homeSummaryModel
import io.homeassistant.companion.android.dashboard.derive.repairsModel
import io.homeassistant.companion.android.dashboard.derive.updatesModel
import io.homeassistant.companion.android.dashboard.energy.EnergyGaugeType
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.layout.conditionalInnerCard
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.ui.cards.energy.EnergyDateSelectionCard
import io.homeassistant.companion.android.dashboard.ui.cards.energy.EnergyDistributionCard
import io.homeassistant.companion.android.dashboard.ui.cards.energy.EnergyGaugeCard
import io.homeassistant.companion.android.dashboard.ui.cards.energy.EnergySourcesTableCard
import io.homeassistant.companion.android.dashboard.ui.cards.energy.EnergyUsageGraphCard
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
        CARD_ENTITIES -> EntitiesCard(card, hass, now, interactions, modifier)
        CARD_ENERGY_DATE_SELECTION -> EnergyDateSelectionCard(card, hass, now, interactions, modifier)
        CARD_ENERGY_DISTRIBUTION -> EnergyDistributionCard(card, hass, now, modifier)
        CARD_ENERGY_USAGE_GRAPH -> EnergyUsageGraphCard(card, hass, now, modifier)
        CARD_ENERGY_SOURCES_TABLE -> EnergySourcesTableCard(card, hass, interactions, modifier)
        in ENERGY_GAUGES -> EnergyGaugeCard(card, hass, ENERGY_GAUGES.getValue(card.type.orEmpty()), modifier)
        else -> OtherCard(card, hass, interactions, modifier, cardModifier)
    }
}

/** The card types that don't need the current time; [cardModifier] carries the card's own gestures. */
@Composable
private fun OtherCard(
    card: CardConfig,
    hass: State<HassSnapshot?>,
    interactions: CardInteractions,
    modifier: Modifier,
    cardModifier: Modifier,
) {
    val infoTile = INFO_TILES[card.type]
    when {
        infoTile != null -> InfoTileCard(card, hass, cardModifier, infoTile)
        card.type == CARD_AREA -> AreaCard(card, hass, cardModifier)
        card.type == CARD_MARKDOWN -> MarkdownCard(card, hass, cardModifier)
        card.type == CARD_MEDIA_CONTROL -> MediaControlCard(card, hass, interactions, cardModifier)
        card.type == CARD_EMPTY_STATE -> EmptyStateCard(card, interactions, modifier)
        card.type == CARD_SHORTCUT -> ShortcutCard(card, hass, cardModifier)
        card.type == CARD_PICTURE_ENTITY -> PictureEntityCard(card, hass, interactions, modifier)
        else -> UnsupportedCard(card.type.orEmpty(), cardModifier)
    }
}

/** The cards shown as an info tile, with how each derives it. */
private val INFO_TILES: Map<String, HassSnapshot.(CardConfig) -> InfoTileModel?> = mapOf(
    CARD_HOME_SUMMARY to { homeSummaryModel(it) },
    CARD_REPAIRS to { repairsModel(it) },
    CARD_UPDATES to { updatesModel(it) },
    CARD_DISCOVERED_DEVICES to { discoveredDevicesModel(it) },
)

private const val CARD_TILE = "tile"
private const val CARD_ENERGY_DATE_SELECTION = "energy-date-selection"
private const val CARD_ENERGY_DISTRIBUTION = "energy-distribution"
private const val CARD_ENERGY_USAGE_GRAPH = "energy-usage-graph"
private const val CARD_ENERGY_SOURCES_TABLE = "energy-sources-table"
private val ENERGY_GAUGES = EnergyGaugeType.entries.associateBy { it.cardType }
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
