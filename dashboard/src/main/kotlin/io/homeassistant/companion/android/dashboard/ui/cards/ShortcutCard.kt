package io.homeassistant.companion.android.dashboard.ui.cards

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.homeassistant.companion.android.dashboard.derive.TileModel
import io.homeassistant.companion.android.dashboard.derive.shortcutModel
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig

/** A shortcut, drawn like a tile as upstream does (`ha-tile-container`). */
@Composable
internal fun ShortcutCard(card: CardConfig, hass: State<HassSnapshot?>, modifier: Modifier = Modifier) {
    val shortcut by remember(card) { derivedStateOf { hass.value?.shortcutModel(card) } }
    val model = shortcut ?: return
    TileCardContent(
        tile = TileModel(
            entityId = card.json.toString(),
            name = model.label,
            state = model.description,
            icon = model.icon,
            active = false,
            available = true,
        ),
        modifier = modifier,
    )
}
