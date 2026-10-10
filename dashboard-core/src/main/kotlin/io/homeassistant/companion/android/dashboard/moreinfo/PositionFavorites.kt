package io.homeassistant.companion.android.dashboard.moreinfo

import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.derive.supportsFeature
import io.homeassistant.companion.android.dashboard.display.jsNumberString
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.feature.available
import io.homeassistant.companion.android.dashboard.model.jsNumber
import io.homeassistant.companion.android.dashboard.model.obj
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

// Ports of `ha-more-info-cover-favorite-positions` and `-valve-favorite-positions` (frontend@20260624.6
// src/dialogs/more-info/components/), `normalizeFavoritePositions` (src/data/favorite_positions.ts) and the numeric
// favourites handler (src/dialogs/more-info/favorites.ts).

/** Which positions a favourite sets. */
sealed interface PositionKind {
    /** The position (`favorite_positions`). */
    data object Position : PositionKind

    /** A cover's tilt (`favorite_tilt_positions`). */
    data object Tilt : PositionKind
}

/**
 * A cover's or valve's favourite positions, saved in its registry entry or upstream's defaults.
 *
 * @property custom whether some were saved, which resetting them restores
 */
data class PositionFavorites(
    val sections: List<PositionFavoriteSection>,
    val enabled: Boolean,
    val custom: Boolean,
    val doneLabel: String,
)

/**
 * The favourites of one [kind], under their [label] when both kinds show.
 *
 * @property showDone whether the done button ends this section while editing (the last one)
 */
data class PositionFavoriteSection(
    val kind: PositionKind,
    val label: String?,
    val favorites: List<PositionFavorite>,
    val values: List<Double>,
    val addLabel: String,
    val showDone: Boolean,
)

/**
 * One favourite: its [text] ("40%"), what tapping does ([label]), and whether the entity is there now ([active]).
 */
data class PositionFavorite(
    val value: Double,
    val text: String,
    val label: String,
    val deleteLabel: String,
    val active: Boolean,
    val apply: CardAction.CallService,
)

/**
 * The favourites of [state] given its registry [entry] (`null` without one, which hides them); `null` when hidden:
 * without positions, or with none saved, unless [editMode].
 */
fun HassSnapshot.positionFavorites(state: EntityState, entry: JsonObject?, editMode: Boolean): PositionFavorites? {
    val kinds = favoriteKinds(state)
    val positions = state.domain == COVER || state.domain == VALVE
    val shown = editMode || kinds.any { kind -> entry?.let { stored(state, it, kind) }?.isEmpty() != true }
    if (entry == null || !positions || !shown) return null
    // A valve's favourites show while editing even without a position, as upstream lists them regardless
    val sectionKinds = if (state.domain == VALVE) listOf(PositionKind.Position) else kinds
    val values = sectionKinds.associateWith { favoriteValues(state, entry, it) }
    val visible = sectionKinds.filter { editMode || values.getValue(it).isNotEmpty() }
    return PositionFavorites(
        sections = visible.map { kind ->
            section(
                state,
                kind,
                values.getValue(kind),
                SectionShape(visible.size > 1, editMode, kind == visible.last()),
            )
        },
        enabled = state.available(),
        custom = kinds.any { stored(state, entry, it) != null },
        doneLabel = localize("ui.dialogs.more_info_control.exit_edit_mode"),
    )
}

/** How a section shows: under a [label] (when both kinds show), while editing, ending with the done button. */
private data class SectionShape(val label: Boolean, val editMode: Boolean, val showDone: Boolean)

private fun HassSnapshot.section(
    state: EntityState,
    kind: PositionKind,
    values: List<Double>,
    shape: SectionShape,
): PositionFavoriteSection {
    val (label, editMode, showDone) = shape
    val strings = favoriteStrings(state, kind)
    val current = state.attributes.numberOrNull(
        if (kind ==
            PositionKind.Tilt
        ) {
            CURRENT_TILT_POSITION
        } else {
            CURRENT_POSITION
        },
    )
    return PositionFavoriteSection(
        kind = kind,
        label = if (label) {
            localize(
                if (kind ==
                    PositionKind.Tilt
                ) {
                    "ui.card.cover.tilt_position"
                } else {
                    "ui.card.cover.position"
                },
            )
        } else {
            null
        },
        favorites = values.mapIndexed { index, value ->
            val text = "${jsNumberString(value)}%"
            PositionFavorite(
                value = value,
                text = text,
                label = localize("$strings.${if (editMode) "edit" else "set"}", mapOf("value" to text)),
                deleteLabel = localize("$strings.delete", mapOf("number" to "${index + 1}")),
                active = current == value,
                apply = positionCall(state, kind, value),
            )
        },
        values = values,
        addLabel = localize("$strings.add"),
        showDone = showDone,
    )
}

/** The position kinds [state] has favourites for. */
internal fun favoriteKinds(state: EntityState): List<PositionKind> = listOfNotNull(
    PositionKind.Position.takeIf { state.supportsFeature(FEATURE_SET_POSITION) },
    PositionKind.Tilt.takeIf { state.domain == COVER && state.supportsFeature(FEATURE_SET_TILT_POSITION) },
)

/** The favourites of [kind] as shown: the saved ones, else the defaults, normalised. */
internal fun favoriteValues(state: EntityState, entry: JsonObject, kind: PositionKind): List<Double> =
    normalizeFavoritePositions(stored(state, entry, kind) ?: DEFAULT_POSITIONS)

/** The saved favourites of [kind], `null` when none were saved. */
internal fun stored(state: EntityState, entry: JsonObject, kind: PositionKind): List<Double>? =
    (entry.obj("options")?.obj(state.domain)?.get(optionKey(kind))?.takeIf { it !is JsonNull } as? JsonArray)
        ?.map(::jsNumber)

/** Port of `normalizeFavoritePositions`: numbers only, within 0–100, each once. */
fun normalizeFavoritePositions(positions: List<Double>): List<Double> =
    positions.filterNot { it.isNaN() }.map { it.coerceIn(0.0, FULL) }.distinct()

internal fun optionKey(kind: PositionKind) = if (kind ==
    PositionKind.Tilt
) {
    "favorite_tilt_positions"
} else {
    "favorite_positions"
}

private fun favoriteStrings(state: EntityState, kind: PositionKind): String =
    "ui.dialogs.more_info_control.${state.domain}." +
        if (kind == PositionKind.Tilt) "favorite_tilt_position" else "favorite_position"

/** `DEFAULT_COVER_FAVORITE_POSITIONS` and `DEFAULT_VALVE_FAVORITE_POSITIONS`. */
private val DEFAULT_POSITIONS = listOf(0.0, 25.0, 75.0, FULL)
