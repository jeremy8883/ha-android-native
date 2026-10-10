package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.color.rgb2hex
import io.homeassistant.companion.android.dashboard.color.temperature2rgb
import io.homeassistant.companion.android.dashboard.energy.WsCommand
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.feature.available
import io.homeassistant.companion.android.dashboard.feature.entityData
import io.homeassistant.companion.android.dashboard.model.array
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Ports of `ha-more-info-light-favorite-colors`, `ha-favorite-color-button` (frontend@20260624.6
// src/dialogs/more-info/components/lights/), `computeDefaultFavoriteColors` (src/data/light.ts) and the light's
// favourites handler (src/dialogs/more-info/favorites.ts).

/**
 * The favourite colours a light's details show, saved in its entity registry entry.
 *
 * @property enabled whether they can be applied (the light is available)
 * @property custom whether they were saved, rather than the defaults; resetting them is offered only then
 */
data class LightFavorites(
    val favorites: List<LightFavorite>,
    val enabled: Boolean,
    val custom: Boolean,
    val addLabel: String,
    val doneLabel: String,
)

/**
 * One favourite colour.
 *
 * @property swatch the `#rrggbb` colour its button shows
 * @property outlined whether the swatch is light enough to need an outline
 * @property label what it does when tapped: "Set favorite color 1", or "Edit favorite color 1" while editing
 * @property apply the call that sets the light to it
 */
data class LightFavorite(
    val color: LightColor,
    val swatch: String,
    val outlined: Boolean,
    val label: String,
    val deleteLabel: String,
    val apply: CardAction.CallService,
)

/**
 * The favourites of [state] given its registry [entry] (`null` when it has none, which hides them), or `null` when
 * they're hidden: for lights without colours, or with none saved, unless [editMode].
 */
fun HassSnapshot.lightFavorites(state: EntityState, entry: JsonObject?, editMode: Boolean): LightFavorites? {
    val saved = entry?.obj("options")?.obj(LIGHT)?.get(FAVORITE_COLORS)?.takeIf { it !is JsonNull } as? JsonArray
    val supports = lightSupportsColor(state) || supportsMode(state, COLOR_TEMP)
    val shown = entry != null && (editMode || (supports && saved?.isEmpty() != true))
    if (!shown) return null
    val colors = saved?.mapNotNull { (it as? JsonObject)?.let(::parseLightColor) } ?: defaultFavoriteColors(state)
    return LightFavorites(
        favorites = colors.mapIndexed { index, color ->
            val rgb = color.displayRgb()
            val number = mapOf("number" to "${index + 1}")
            LightFavorite(
                color = color,
                swatch = rgb2hex(rgb),
                outlined = luminosity(rgb) > OUTLINE_LUMINOSITY,
                label = localize("$FAVORITE_STRINGS.${if (editMode) "edit" else "set"}", number),
                deleteLabel = localize("$FAVORITE_STRINGS.delete", number),
                apply = CardAction.CallService(LIGHT, "turn_on", JsonObject(entityData(state) + color.json()), null),
            )
        },
        enabled = state.available(),
        custom = saved != null,
        addLabel = localize("$FAVORITE_STRINGS.add"),
        doneLabel = localize("ui.dialogs.more_info_control.exit_edit_mode"),
    )
}

/** Port of `computeDefaultFavoriteColors`: four whites, then four colours, as the light takes them. */
fun defaultFavoriteColors(state: EntityState): List<LightColor> = buildList {
    val supportsColor = lightSupportsColor(state)
    if (supportsMode(state, COLOR_TEMP)) {
        val min = state.attributes.lightNumber("min_color_temp_kelvin") ?: Double.NaN
        val max = state.attributes.lightNumber("max_color_temp_kelvin") ?: Double.NaN
        val step = (max - min) / (DEFAULT_WHITES - 1)
        repeat(DEFAULT_WHITES) { add(LightColor.ColorTemp(Math.round(min + step * it).toDouble())) }
    } else if (supportsColor) {
        val step = (MAX_DEFAULT_KELVIN - MIN_DEFAULT_KELVIN) / (DEFAULT_WHITES - 1)
        repeat(DEFAULT_WHITES) {
            add(LightColor.Rgb(temperature2rgb(Math.round(MIN_DEFAULT_KELVIN + step * it).toDouble()).toList()))
        }
    }
    if (supportsColor) addAll(DEFAULT_COLORS)
}

/**
 * The colour [state] shows, to start a new favourite from; `null` when it has none. XY colours aren't kept as
 * favourites, so they start from the light's HS or RGB colour. Port of `_computeCurrentColor`.
 */
fun lightCurrentColor(state: EntityState): LightColor? {
    val mode = state.attributes.string("color_mode") ?: return null
    return when (mode) {
        "xy" -> parseLightColor(JsonObject(state.attributes.filterKeys { it == "hs_color" }))
            ?: parseLightColor(JsonObject(state.attributes.filterKeys { it == "rgb_color" }))
        COLOR_TEMP -> state.attributes.lightNumber("color_temp_kelvin")?.takeIf {
            it != 0.0
        }?.let { LightColor.ColorTemp(it) }
        else -> parseLightColor(JsonObject(state.attributes.filterKeys { it == "${mode}_color" }))
    }
}

/** The command that saves [colors] as [entityId]'s favourites, or resets them to the defaults when `null`. */
fun favoriteColorsUpdate(entityId: String, colors: List<LightColor>?): WsCommand = WsCommand(
    "config/entity_registry/update",
    JsonObject(
        mapOf(
            "entity_id" to JsonPrimitive(entityId),
            "options_domain" to JsonPrimitive(LIGHT),
            // Upstream resets with `favorite_colors: undefined`, which JSON leaves out
            "options" to
                JsonObject(colors?.let { mapOf(FAVORITE_COLORS to JsonArray(it.map(LightColor::json))) }.orEmpty()),
        ),
    ),
)

/**
 * The lights [colors] can be copied to: the others in the entity registry that take every kind of colour among
 * them. Port of the light favourites handler's `copy`.
 */
fun HassSnapshot.favoriteCopyTargets(entityId: String, colors: List<LightColor>): List<String> =
    states.values.filter { candidate ->
        candidate.entityId != entityId &&
            candidate.domain == LIGHT &&
            candidate.entityId in registries.entities &&
            colors.all { it.supportedBy(candidate) }
    }.map { it.entityId }

private fun LightColor.supportedBy(light: EntityState): Boolean = when (this) {
    is LightColor.ColorTemp -> supportsMode(light, COLOR_TEMP)
    is LightColor.Hs, is LightColor.Rgb -> lightSupportsColor(light)
    is LightColor.Rgbw -> supportsMode(light, "rgbw")
    is LightColor.Rgbww -> supportsMode(light, "rgbww")
}

/** Port of `lightSupportsColor`. */
internal fun lightSupportsColor(state: EntityState): Boolean =
    state.attributes.array("supported_color_modes")?.any { (it as? JsonPrimitive)?.content in COLOR_MODES } == true

private const val LIGHT = "light"
private const val COLOR_TEMP = "color_temp"
private const val FAVORITE_COLORS = "favorite_colors"
private const val FAVORITE_STRINGS = "ui.dialogs.more_info_control.light.favorite_color"
private const val DEFAULT_WHITES = 4
private const val MIN_DEFAULT_KELVIN = 2000.0
private const val MAX_DEFAULT_KELVIN = 6500.0
private const val OUTLINE_LUMINOSITY = 0.8
private val COLOR_MODES = setOf("hs", "xy", "rgb", "rgbw", "rgbww")

/** `DEFAULT_COLORED_COLORS`: blue, purple, pink and red. */
private val DEFAULT_COLORS = listOf(
    LightColor.Rgb(listOf(127.0, 172.0, 255.0)),
    LightColor.Rgb(listOf(215.0, 150.0, 255.0)),
    LightColor.Rgb(listOf(255.0, 158.0, 243.0)),
    LightColor.Rgb(listOf(255.0, 110.0, 84.0)),
)
