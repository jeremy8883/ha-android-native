package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.action.ElementActions
import io.homeassistant.companion.android.dashboard.action.elementActions
import io.homeassistant.companion.android.dashboard.display.formatEntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.feature.ValueService
import io.homeassistant.companion.android.dashboard.feature.entityData
import io.homeassistant.companion.android.dashboard.feature.number
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.string
import kotlin.math.roundToInt
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A light card: a round brightness slider around the light's icon (which toggles it), its name under them, and a
 * button opening its details. Port of `hui-light-card` (frontend@20260624.6 src/panels/lovelace/cards/).
 */
sealed interface LightCardModel {
    /** A warning in its place: the light doesn't exist, or the card's entity isn't a light. */
    data class Warning(val text: String) : LightCardModel

    /**
     * The light, at [brightness] percent (1-100 on the slider, shown only when it [supportsBrightness]).
     *
     * @property stateText the state shown above the name while unavailable or unknown, else the brightness shows
     * there while it's dragged
     * @property brightnessCall sets the brightness (`light.turn_on` with `brightness_pct`)
     */
    data class Shown(
        val entityId: String,
        val name: String,
        val icon: String?,
        val color: DisplayColor,
        val brightness: Int,
        val supportsBrightness: Boolean,
        val disabled: Boolean,
        val stateText: String?,
        val actions: ElementActions,
        val brightnessCall: ValueService,
        val moreInfoLabel: String,
    ) : LightCardModel
}

/** The light card of [card]. */
fun HassSnapshot.lightCardModel(card: CardConfig): LightCardModel {
    val json = card.json
    val entityId = json.string("entity")
    val state = entityId?.let(states::get)
    return when {
        entityId?.substringBefore(
            '.',
        ) != LIGHT -> LightCardModel.Warning(localize("ui.errors.config.configuration_error"))
        state == null -> LightCardModel.Warning(localize("ui.card.common.entity_not_found"))
        else -> LightCardModel.Shown(
            entityId = entityId,
            name = entityNameDisplay(state, json["name"]),
            icon = entityIcon(entityId, configIcon = json.string("icon")),
            color = lightButtonColor(state.state, state.attributes),
            brightness = ((number(state.attributes["brightness"]) ?: 0.0) / BRIGHTNESS_MAX * PERCENT).roundToInt(),
            supportsBrightness = state.lightSupportsBrightness(),
            disabled = state.state == STATE_UNAVAILABLE,
            stateText = formatEntityState(state).takeIf {
                state.state == STATE_UNAVAILABLE ||
                    state.state == STATE_UNKNOWN
            },
            actions = elementActions(JsonObject(DEFAULT_ACTIONS + json)),
            brightnessCall = ValueService(LIGHT, "turn_on", entityData(state), "brightness_pct"),
            moreInfoLabel = localize("ui.panel.lovelace.cards.show_more_info"),
        )
    }
}

/** The icon's colour: the light's own while on (else the active colour), the unavailable or the icon colour. */
private fun lightButtonColor(state: String, attributes: JsonObject): DisplayColor {
    val rgb = (attributes["rgb_color"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }
    return when {
        state == "on" && rgb != null -> DisplayColor.Literal("rgb(${rgb.joinToString(",")})")
        state == "on" -> DisplayColor.State(listOf("state-light-active-color"))
        state == STATE_UNAVAILABLE -> DisplayColor.State(listOf("state-unavailable-color"))
        else -> DisplayColor.State(listOf("state-icon-color"))
    }
}

/** `setConfig`'s defaults: a tap toggles, a hold opens the details. */
private val DEFAULT_ACTIONS = buildJsonObject {
    put("tap_action", buildJsonObject { put("action", "toggle") })
    put("hold_action", buildJsonObject { put("action", "more-info") })
}

private const val LIGHT = "light"
private const val BRIGHTNESS_MAX = 255.0
private const val PERCENT = 100
