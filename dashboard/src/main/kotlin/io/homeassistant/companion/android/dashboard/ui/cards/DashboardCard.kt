package io.homeassistant.companion.android.dashboard.ui.cards

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import io.homeassistant.companion.android.dashboard.derive.discoveredDevicesModel
import io.homeassistant.companion.android.dashboard.derive.homeSummaryModel
import io.homeassistant.companion.android.dashboard.derive.repairsModel
import io.homeassistant.companion.android.dashboard.derive.tapNavigationPath
import io.homeassistant.companion.android.dashboard.derive.updatesModel
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import java.time.ZonedDateTime

/** Renders [card] with its native renderer, or a placeholder when the type is not supported yet. */
@Composable
internal fun DashboardCard(
    card: CardConfig,
    hass: State<HassSnapshot?>,
    now: State<ZonedDateTime?>,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // A navigate tap action works on any card, so subviews are reachable before every card type is native.
    val clickModifier = card.tapNavigationPath
        ?.let { path -> modifier.clickable(role = Role.Button) { onNavigate(path) } }
        ?: modifier
    when (card.type) {
        CARD_TILE -> TileCard(card, hass, now, clickModifier)
        CARD_HEADING -> HeadingCard(card, clickModifier)
        CARD_AREA -> AreaCard(card, hass, clickModifier)
        CARD_HOME_SUMMARY -> InfoTileCard(card, hass, clickModifier) { homeSummaryModel(it) }
        CARD_REPAIRS -> InfoTileCard(card, hass, clickModifier) { repairsModel(it) }
        CARD_UPDATES -> InfoTileCard(card, hass, clickModifier) { updatesModel(it) }
        CARD_DISCOVERED_DEVICES -> InfoTileCard(card, hass, clickModifier) { discoveredDevicesModel(it) }
        else -> UnsupportedCard(card.type.orEmpty(), clickModifier)
    }
}

private const val CARD_TILE = "tile"
private const val CARD_HEADING = "heading"
private const val CARD_AREA = "area"
private const val CARD_HOME_SUMMARY = "home-summary"
private const val CARD_REPAIRS = "repairs"
private const val CARD_UPDATES = "updates"
private const val CARD_DISCOVERED_DEVICES = "discovered-devices"
