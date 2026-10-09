package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.action.ElementActions
import io.homeassistant.companion.android.dashboard.action.Gesture
import io.homeassistant.companion.android.dashboard.action.elementActions
import io.homeassistant.companion.android.dashboard.action.hasAction
import io.homeassistant.companion.android.dashboard.condition.ConditionContext
import io.homeassistant.companion.android.dashboard.condition.conditionsMet
import io.homeassistant.companion.android.dashboard.display.StateDisplayOptions
import io.homeassistant.companion.android.dashboard.display.stateDisplay
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.boolean
import io.homeassistant.companion.android.dashboard.model.objects
import io.homeassistant.companion.android.dashboard.model.string
import java.time.Instant
import kotlinx.serialization.json.JsonObject

/**
 * Display-ready content of a heading card.
 *
 * @property actionable whether the heading has a tap action, which upstream marks with a chevron
 * @property badges the visible badges, in order
 */
data class HeadingModel(
    val text: String,
    val icon: String?,
    val isSubtitle: Boolean,
    val actionable: Boolean,
    val badges: List<HeadingBadgeModel>,
)

/** A badge of a heading card. */
sealed interface HeadingBadgeModel {
    /** The gestures the badge responds to; badges do nothing unless configured. */
    val actions: ElementActions

    /**
     * An entity badge: its icon and state (`hui-entity-heading-badge`), or a warning when the entity is missing.
     *
     * @property name the accessible name of the badge (`title` upstream)
     * @property icon `null` when `show_icon: false`
     * @property state `null` when `show_state: false`
     * @property color the icon colour, `null` for the default
     */
    data class Entity(
        val entityId: String,
        val name: String,
        val icon: String?,
        val state: String?,
        val color: DisplayColor?,
        val missing: Boolean,
        override val actions: ElementActions,
    ) : HeadingBadgeModel

    /** A button badge: an icon and/or text on a pill (`hui-button-heading-badge`). */
    data class Button(
        val icon: String?,
        val text: String?,
        val color: DisplayColor?,
        override val actions: ElementActions,
    ) : HeadingBadgeModel
}

/**
 * Derive a heading card with its badges, keeping only the badges whose visibility conditions pass.
 * Ports of `hui-heading-card`, `hui-heading-badge` and the entity and button badges (frontend@20260624.6
 * src/panels/lovelace/cards/hui-heading-card.ts, src/panels/lovelace/heading-badges/).
 *
 * @param now the time relative states in badges are shown against
 */
fun HassSnapshot.headingModel(card: CardConfig, context: ConditionContext, now: Instant): HeadingModel = HeadingModel(
    text = card.json.string("heading").orEmpty(),
    icon = card.json.string("icon")?.ifEmpty { null },
    isSubtitle = card.json.string("heading_style") == "subtitle",
    actionable = hasAction(card.json, Gesture.TAP),
    badges = card.json.objects("badges").filter { badgeVisible(it, context) }.mapNotNull { headingBadge(it, now) },
)

/** Port of `HuiHeadingBadge._updateVisibility`: disabled badges and failing conditions hide the badge. */
private fun HassSnapshot.badgeVisible(badge: JsonObject, context: ConditionContext): Boolean {
    if (badge.boolean("disabled") == true) return false
    val visibility = badge.objects("visibility")
    return visibility.isEmpty() || conditionsMet(visibility, context.copy(entityId = badge.string("entity")))
}

private fun HassSnapshot.headingBadge(badge: JsonObject, now: Instant): HeadingBadgeModel? =
    // `entity` is the default badge type
    when (badge.string("type") ?: BADGE_ENTITY) {
        BADGE_ENTITY -> entityBadge(badge, now)
        BADGE_BUTTON -> HeadingBadgeModel.Button(
            icon = badge.string("icon")?.ifEmpty { null },
            text = badge.string("text")?.ifEmpty { null },
            color = badge.string("color")?.ifEmpty { null }?.let(::cssColor),
            actions = elementActions(badge),
        )
        else -> null
    }

private fun HassSnapshot.entityBadge(badge: JsonObject, now: Instant): HeadingBadgeModel.Entity {
    val entityId = badge.string("entity").orEmpty()
    val state = states[entityId]
        ?: return HeadingBadgeModel.Entity(
            entityId = entityId,
            name = entityId,
            icon = MISSING_ICON,
            state = MISSING_STATE,
            color = DisplayColor.Theme(MISSING_COLOR),
            missing = true,
            actions = elementActions(JsonObject(emptyMap())),
        )
    val name = entityNameDisplay(state, badge["name"])
    return HeadingBadgeModel.Entity(
        entityId = entityId,
        name = name,
        icon = if (badge.boolean("show_icon") != false) entityIcon(entityId, badge.string("icon")) else null,
        state = if (badge.boolean("show_state") != false) {
            stateDisplay(state, badge["state_content"], now, StateDisplayOptions(name = name, dashUnavailable = true))
        } else {
            null
        },
        color = badgeColor(state, badge.string("color")),
        missing = false,
        actions = elementActions(badge),
    )
}

/**
 * Port of `_computeStateColor` of the entity badge: `state` uses the state colour (or the light's own colour),
 * another colour applies only while the entity is active.
 */
private fun badgeColor(state: EntityState, color: String?): DisplayColor? = when {
    color.isNullOrEmpty() || color == "none" -> null
    color == "state" -> lightColor(state) ?: stateColor(state)
    state.isActive() -> cssColor(color)
    else -> null
}

private const val BADGE_ENTITY = "entity"
private const val BADGE_BUTTON = "button"
private const val MISSING_ICON = "mdi:alert-circle"
private const val MISSING_STATE = "-"
private const val MISSING_COLOR = "red"
