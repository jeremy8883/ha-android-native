package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.action.ElementActions
import io.homeassistant.companion.android.dashboard.action.cardActions
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Display-ready content of an empty state card: an icon, a title, a text and buttons.
 *
 * @property contentOnly drawn without the card background
 */
data class EmptyStateModel(
    val icon: String?,
    val iconColor: DisplayColor?,
    val title: String?,
    val content: String?,
    val contentOnly: Boolean,
    val buttons: List<EmptyStateButton>,
)

/** A button of an empty state card; [actions] holds its `tap_action`. */
data class EmptyStateButton(val icon: String?, val text: String?, val actions: ElementActions)

/** Port of `HuiEmptyStateCard.render` (frontend@20260624.6 src/panels/lovelace/cards/hui-empty-state-card.ts). */
fun emptyStateModel(card: CardConfig): EmptyStateModel = EmptyStateModel(
    icon = card.json.string("icon")?.ifEmpty { null },
    iconColor = card.json.string("icon_color")?.ifEmpty { null }?.let(::cssColor),
    title = card.json.string("title")?.ifEmpty { null },
    content = card.json.string("content")?.ifEmpty { null },
    contentOnly = card.json.boolean("content_only") == true,
    buttons = card.json.objects("buttons").map { button ->
        EmptyStateButton(
            icon = button.string("icon")?.ifEmpty { null },
            text = button.string("text"),
            // Buttons run their tap action only (no default)
            actions = cardActions(CardConfig(JsonObject(button + ("type" to JsonPrimitive("button"))))).card,
        )
    },
)
