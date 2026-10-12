package io.homeassistant.companion.android.dashboard.feature

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.color.rgb2hex
import io.homeassistant.companion.android.dashboard.derive.DisplayColor
import io.homeassistant.companion.android.dashboard.derive.cssColor
import io.homeassistant.companion.android.dashboard.derive.stateColor
import io.homeassistant.companion.android.dashboard.derive.supportsFeature
import io.homeassistant.companion.android.dashboard.display.jsNumberString
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.jsNumber
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.moreinfo.LightColor
import io.homeassistant.companion.android.dashboard.moreinfo.LightFavorite
import io.homeassistant.companion.android.dashboard.moreinfo.defaultFavoriteColors
import io.homeassistant.companion.android.dashboard.moreinfo.displayRgb
import io.homeassistant.companion.android.dashboard.moreinfo.json
import io.homeassistant.companion.android.dashboard.moreinfo.lightTakesFavorites
import io.homeassistant.companion.android.dashboard.moreinfo.luminosity
import io.homeassistant.companion.android.dashboard.moreinfo.normalizeFavoritePositions
import io.homeassistant.companion.android.dashboard.moreinfo.parseLightColor
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Ports of the favourite card features (frontend@20260624.6 src/panels/lovelace/card-features/): the cover and
// valve positions of `HuiNumericFavoriteCardFeatureBase`, and `hui-light-color-favorites-card-feature`. Both read
// the favourites from the entity's registry entry, which the tile loads.

/**
 * The favourite positions of [entityId], picked from the registry entry's `options.[optionsDomain].[optionKey]`
 * (else the defaults) once it loads.
 */
data class PositionFavoritesFeature(
    val entityId: String,
    val optionsDomain: String,
    val optionKey: String,
    val label: String,
    val current: Double?,
    val enabled: Boolean,
    val color: DisplayColor?,
    val service: ValueService,
) : TileFeature {
    /**
     * The favourites as labelled segments, given the [entry] (`null` without one, or when it couldn't load, where
     * upstream shows the defaults) and how many fit ([maxVisible]); `null` when there are none.
     */
    fun select(entry: JsonObject?, maxVisible: Int?): TileFeature.Select? {
        val stored = (
            entry?.obj("options")?.obj(optionsDomain)?.get(optionKey)?.takeIf {
                it !is JsonNull
            } as? JsonArray
            )
            ?.map(::jsNumber)
        val positions = normalizeFavoritePositions(stored ?: DEFAULT_POSITIONS)
        if (positions.isEmpty()) return null
        val visible = maxVisible?.let { positions.take(it) } ?: positions
        return TileFeature.Select(
            label = label,
            options = visible.map { position ->
                val text = "${jsNumberString(position)}%"
                TileFeature.Option(
                    value = jsNumberString(position),
                    label = text,
                    icon = null,
                    action = service.withValue(position),
                )
            },
            selected = current?.let(::jsNumberString),
            enabled = enabled,
            color = color,
            showLabels = true,
        )
    }
}

/**
 * The favourite colours of the light [entityId], from its registry entry once it loads. [setLabel] names a
 * favourite, with `{number}` for its index.
 */
data class LightColorFavoritesFeature(
    val entityId: String,
    val enabled: Boolean,
    val defaults: List<LightColor>,
    val setLabel: String,
) : TileFeature {
    /** The favourites the [entry] saved, else the defaults; none without an entry (or when it couldn't load). */
    fun favorites(entry: JsonObject?): List<LightFavorite> {
        if (entry == null) return emptyList()
        val saved = entry.obj("options")?.obj("light")?.get("favorite_colors")?.takeIf { it !is JsonNull } as? JsonArray
        val colors = saved?.mapNotNull { (it as? JsonObject)?.let(::parseLightColor) } ?: defaults
        return colors.mapIndexed { index, color ->
            val rgb = color.displayRgb()
            LightFavorite(
                color = color,
                swatch = rgb2hex(rgb),
                outlined = luminosity(rgb) > OUTLINE_LUMINOSITY,
                // Upstream numbers them from 0 here
                label = setLabel.replace("{number}", "$index"),
                deleteLabel = "",
                apply = CardAction.CallService(
                    "light",
                    "turn_on",
                    JsonObject(mapOf("entity_id" to JsonPrimitive(entityId)) + color.json()),
                    target = null,
                ),
            )
        }
    }
}

/**
 * Ports of `hui-cover-position-favorite-card-feature`, `-cover-tilt-favorite-` and `-valve-position-favorite-`:
 * in the tile's configured colour, else the state's.
 */
internal fun HassSnapshot.positionFavorites(state: EntityState, type: String, configColor: String?): TileFeature? {
    val tilt = type == "cover-tilt-favorite"
    val domain = if (type == "valve-position-favorite") "valve" else "cover"
    if (state.domain != domain || !state.supportsFeature(if (tilt) SET_TILT_POSITION else SET_POSITION)) return null
    val attribute = if (tilt) "current_tilt_position" else "current_position"
    return PositionFavoritesFeature(
        entityId = state.entityId,
        optionsDomain = domain,
        optionKey = if (tilt) "favorite_tilt_positions" else "favorite_positions",
        label = localize("ui.panel.lovelace.editor.features.types.$type.label"),
        current = number(state.attributes[attribute]),
        enabled = state.available(),
        color = configColor?.let(::cssColor) ?: stateColor(state),
        service = ValueService(
            domain,
            when {
                tilt -> "set_cover_tilt_position"
                domain == "valve" -> "set_valve_position"
                else -> "set_cover_position"
            },
            entityData(state),
            if (tilt) "tilt_position" else "position",
        ),
    )
}

/** Port of `hui-light-color-favorites-card-feature`'s support check, for lights with colours or whites. */
internal fun HassSnapshot.lightColorFavorites(state: EntityState): TileFeature? {
    if (state.domain != "light" || !lightTakesFavorites(state)) return null
    return LightColorFavoritesFeature(
        entityId = state.entityId,
        enabled = state.available(),
        defaults = defaultFavoriteColors(state),
        setLabel = localize("ui.dialogs.more_info_control.light.favorite_color.set"),
    )
}

private const val SET_POSITION = 4
private const val SET_TILT_POSITION = 128
private const val OUTLINE_LUMINOSITY = 0.8

/** `DEFAULT_COVER_FAVORITE_POSITIONS` and `DEFAULT_VALVE_FAVORITE_POSITIONS`. */
private val DEFAULT_POSITIONS = listOf(0.0, 25.0, 75.0, 100.0)
