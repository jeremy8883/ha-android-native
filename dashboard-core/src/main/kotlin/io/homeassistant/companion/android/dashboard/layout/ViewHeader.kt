package io.homeassistant.companion.android.dashboard.layout

import io.homeassistant.companion.android.dashboard.action.ElementActions
import io.homeassistant.companion.android.dashboard.action.cardActions
import io.homeassistant.companion.android.dashboard.condition.ConditionContext
import io.homeassistant.companion.android.dashboard.condition.conditionsMet
import io.homeassistant.companion.android.dashboard.derive.DisplayColor
import io.homeassistant.companion.android.dashboard.derive.cssColor
import io.homeassistant.companion.android.dashboard.derive.entityIcon
import io.homeassistant.companion.android.dashboard.derive.entityNameDisplay
import io.homeassistant.companion.android.dashboard.derive.isActive
import io.homeassistant.companion.android.dashboard.derive.lightColor
import io.homeassistant.companion.android.dashboard.derive.stateColor
import io.homeassistant.companion.android.dashboard.display.stateDisplay
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.ViewConfig
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The header of a sections view: an optional card (often a markdown welcome) and the view's badges.
 *
 * @property layout `center`, `start` or `responsive`
 * @property badgesPosition `bottom` or `top` of the card
 */
data class ViewHeaderModel(
    val card: CardConfig?,
    val layout: String,
    val badgesPosition: String,
    val badges: List<ViewBadgeModel>,
)

/**
 * A view badge (`hui-entity-badge`): an icon with the state or name, or a warning for a missing entity.
 *
 * @property label the name shown before the content when both name and state are shown
 * @property content the state (or the name when only the name is shown); `null` for an icon-only badge
 */
data class ViewBadgeModel(
    val entityId: String,
    val icon: String?,
    val label: String?,
    val content: String?,
    val color: DisplayColor?,
    val active: Boolean,
    val missing: Boolean,
    val actions: ElementActions,
)

/** The badge configs of a view: `badges`, where plain strings are entity ids (legacy config). */
fun viewBadges(view: ViewConfig): List<JsonObject> = (view.json["badges"] as? JsonArray).orEmpty().mapNotNull {
    when {
        it is JsonObject -> it
        it is JsonPrimitive && it.isString -> JsonObject(mapOf("type" to JsonPrimitive("entity"), "entity" to it))
        else -> null
    }
}

/** The card in a sections view's header, if any. */
fun viewHeaderCard(view: ViewConfig): CardConfig? = view.json.obj("header")?.obj("card")?.let(::CardConfig)

/**
 * The header of [view] for the current state: its card and the visible entity badges. Ports of
 * `hui-view-header` and `hui-entity-badge` (frontend@20260624.6 src/panels/lovelace/views/hui-view-header.ts,
 * src/panels/lovelace/badges/hui-entity-badge.ts). Only `entity` badges are ported.
 */
fun HassSnapshot.viewHeader(view: ViewConfig, context: ConditionContext, now: Instant): ViewHeaderModel? {
    val header = view.json.obj("header")
    val badges = viewBadges(view)
        .filter { badge ->
            val visibility = badge.objects("visibility")
            badge.boolean("disabled") != true &&
                (visibility.isEmpty() || conditionsMet(visibility, context.copy(entityId = badge.string("entity"))))
        }
        .mapNotNull { viewBadge(it, now) }
    val card = viewHeaderCard(view)
    if (card == null && badges.isEmpty()) return null
    return ViewHeaderModel(
        card = card,
        layout = header?.string("layout") ?: DEFAULT_LAYOUT,
        badgesPosition = header?.string("badges_position") ?: DEFAULT_BADGES_POSITION,
        badges = badges,
    )
}

private fun HassSnapshot.viewBadge(badge: JsonObject, now: Instant): ViewBadgeModel? {
    if ((badge.string("type") ?: ENTITY) != ENTITY) return null
    val entityId = badge.string("entity").orEmpty()
    // `tap_action` defaults to more-info, like the tile card
    val actions = cardActions(CardConfig(JsonObject(badge + ("type" to JsonPrimitive("tile"))))).card
    val state = states[entityId] ?: return ViewBadgeModel(
        entityId,
        "mdi:alert-circle",
        null,
        entityId,
        DisplayColor.Theme("red"),
        false,
        true,
        actions,
    )
    val legacyType = badge.string("display_type")
    val showName = badge.boolean("show_name") ?: (legacyType == "complete")
    val showState = badge.boolean("show_state") ?: (legacyType != "minimal")
    val showIcon = badge.boolean("show_icon") ?: true
    val name = entityNameDisplay(state, badge["name"])
    val stateText =
        stateDisplay(state, badge["state_content"], now, name = name, timeFormat = badge.string("time_format"))
    val color = badge.string("color")?.ifEmpty { null }
    return ViewBadgeModel(
        entityId = entityId,
        icon = if (showIcon) entityIcon(entityId, badge.string("icon")) else null,
        label = name.takeIf { showState && showName },
        content = if (showState) stateText else name.takeIf { showName },
        color = if (color !=
            null
        ) {
            cssColor(color).takeIf { state.isActive() }
        } else {
            lightColor(state) ?: stateColor(state)
        },
        active = state.isActive(),
        missing = false,
        actions = actions,
    )
}

private const val ENTITY = "entity"
private const val DEFAULT_LAYOUT = "center"
private const val DEFAULT_BADGES_POSITION = "bottom"
