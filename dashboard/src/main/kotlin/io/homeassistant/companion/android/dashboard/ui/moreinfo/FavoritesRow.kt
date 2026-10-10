package io.homeassistant.companion.android.dashboard.ui.moreinfo

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.homeassistant.companion.android.common.compose.theme.HABorderWidth
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.moreinfo.LightFavorite
import io.homeassistant.companion.android.dashboard.moreinfo.LightFavorites
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.theme.parseCssColor
import kotlin.math.roundToInt

/**
 * Port of `ha-more-info-favorites` (frontend@20260624.6 src/dialogs/more-info/components/): favourites that act
 * when tapped. An admin's long press starts editing them: they shake, each with a delete badge (labelled
 * [deleteLabels]), a tap edits one, dragging one moves it, and the add (and, given [doneLabel], done) buttons
 * follow. [item] draws each, with the modifier carrying its gestures.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FavoritesRow(
    deleteLabels: List<String>,
    state: FavoritesRowState,
    callbacks: FavoritesRowCallbacks,
    item: @Composable (index: Int, modifier: Modifier) -> Unit,
) {
    // Where each favourite sits, to find where a dragged one lands
    val bounds = remember { mutableStateMapOf<Int, Rect>() }
    FlowRow(
        modifier = Modifier.widthIn(max = state.maxWidth),
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE4, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
    ) {
        deleteLabels.forEachIndexed { index, deleteLabel ->
            FavoriteBubble(
                index = index,
                deleteLabel = deleteLabel,
                state = state,
                callbacks = callbacks,
                landing = { from, moved ->
                    val center = (bounds[from]?.center ?: Offset.Zero) + moved
                    bounds.entries.minByOrNull { (_, rect) -> (rect.center - center).getDistanceSquared() }?.key
                        ?.takeIf { it != from }
                },
                modifier = Modifier.onGloballyPositioned { bounds[index] = it.boundsInParent() },
                item = item,
            )
        }
        if (state.editMode) {
            RoundButton("mdi:plus", state.addLabel, callbacks.onAdd)
            state.doneLabel?.let { RoundButton("mdi:check", it, callbacks.onDone) }
        }
    }
}

/**
 * How a [FavoritesRow] shows: [enabled] (the entity is available), [editMode], whether the user [isAdmin] (who may
 * edit), its buttons' labels, and its [maxWidth].
 */
internal data class FavoritesRowState(
    val enabled: Boolean,
    val editMode: Boolean,
    val isAdmin: Boolean,
    val addLabel: String,
    val doneLabel: String?,
    val maxWidth: Dp = ROW_MAX_WIDTH,
)

/** A light's favourite colours as a [FavoritesRow] of swatches. */
@Composable
internal fun LightFavoritesRow(
    favorites: LightFavorites,
    editMode: Boolean,
    isAdmin: Boolean,
    callbacks: FavoritesRowCallbacks,
) {
    FavoritesRow(
        deleteLabels = favorites.favorites.map { it.deleteLabel },
        state = FavoritesRowState(favorites.enabled, editMode, isAdmin, favorites.addLabel, favorites.doneLabel),
        callbacks = callbacks,
    ) { index, modifier -> Swatch(favorites.favorites[index], favorites.enabled, modifier) }
}

/** One favourite: shaking with a delete badge while editing, and draggable then. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteBubble(
    index: Int,
    deleteLabel: String,
    state: FavoritesRowState,
    callbacks: FavoritesRowCallbacks,
    landing: (from: Int, moved: Offset) -> Int?,
    modifier: Modifier,
    item: @Composable (index: Int, modifier: Modifier) -> Unit,
) {
    var drag by remember { mutableStateOf<Offset?>(null) }
    val latest by rememberUpdatedState(callbacks)
    val editMode = state.editMode
    val rotation = if (editMode && drag == null) shakeRotation(index) else 0f
    Box(
        modifier = modifier
            .zIndex(if (drag != null) 1f else 0f)
            .offset { drag?.let { IntOffset(it.x.roundToInt(), it.y.roundToInt()) } ?: IntOffset.Zero }
            .graphicsLayer { rotationZ = rotation },
    ) {
        item(
            index,
            Modifier
                .then(
                    if (editMode) {
                        Modifier.pointerInput(index) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { drag = Offset.Zero },
                                onDragEnd = {
                                    val landed = drag?.let { landing(index, it) }
                                    drag = null
                                    landed?.let { latest.onMove(index, it) }
                                },
                                onDragCancel = { drag = null },
                            ) { change, amount ->
                                change.consume()
                                drag = (drag ?: Offset.Zero) + amount
                            }
                        }
                    } else {
                        Modifier
                    },
                )
                .combinedClickable(
                    enabled = state.enabled,
                    role = Role.Button,
                    onLongClick = if (!editMode && state.isAdmin) latest.onStartEditing else null,
                    onClick = { if (editMode) latest.onEdit(index) else latest.onApply(index) },
                ),
        )
        if (editMode) {
            DeleteBadge(deleteLabel, Modifier.align(Alignment.TopEnd).offset(DELETE_OFFSET, -DELETE_OFFSET)) {
                latest.onDelete(index)
            }
        }
    }
}

/** The colour swatch, outlined when it's very light, greyed while the light is unavailable. */
@Composable
private fun Swatch(favorite: LightFavorite, enabled: Boolean, modifier: Modifier) {
    val colors = LocalHAColorScheme.current
    val color = parseCssColor(favorite.swatch) ?: Color.White
    Box(
        modifier = modifier
            .size(SWATCH_SIZE)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(CircleShape)
            .background(if (enabled) color else colors.colorFillDisabledLoudResting)
            .border(
                HABorderWidth.S,
                if (favorite.outlined) colors.colorBorderNeutralQuiet else Color.Transparent,
                CircleShape,
            )
            .semantics { contentDescription = favorite.label },
    )
}

/** The small "−" badge that deletes a favourite while editing. */
@Composable
private fun DeleteBadge(label: String, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalHAColorScheme.current
    Box(
        modifier = modifier
            .size(HASize.XL)
            .clip(RoundedCornerShape(DELETE_RADIUS))
            .background(colors.colorSurfaceLow)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        DashboardIcon("mdi:minus", colors.colorTextPrimary, Modifier.size(HASize.S))
    }
}

/** Port of `ha-outlined-icon-button`: an outlined round icon button, the size of a swatch. */
@Composable
private fun RoundButton(icon: String, label: String, onClick: () -> Unit) {
    val colors = LocalHAColorScheme.current
    Box(
        modifier = Modifier
            .size(SWATCH_SIZE)
            .clip(CircleShape)
            .border(HABorderWidth.S, colors.colorBorderNeutralNormal, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        DashboardIcon(icon, colors.colorTextPrimary, Modifier.size(HASize.X2L))
    }
}

/** Upstream's `shake` keyframes: a small wobble, offset for every third favourite so they don't move together. */
@Composable
private fun shakeRotation(index: Int): Float {
    val transition = rememberInfiniteTransition(label = "shake")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = SHAKE_MS
                0f at 0 using LinearEasing
                -SHAKE_DEGREES at SHAKE_MS / 5 using LinearEasing
                0f at SHAKE_MS * 2 / 5 using LinearEasing
                SHAKE_DEGREES at SHAKE_MS * 3 / 5 using LinearEasing
            },
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(SHAKE_STAGGER_MS * (index % 3)),
        ),
        label = "rotation",
    )
    return rotation
}

private const val DISABLED_ALPHA = 0.5f
private const val SHAKE_MS = 450
private const val SHAKE_STAGGER_MS = 150
private const val SHAKE_DEGREES = 3f
private val SWATCH_SIZE = HADimens.SPACE10
private val ROW_MAX_WIDTH = 250.dp
private val DELETE_OFFSET = 6.dp
private val DELETE_RADIUS = HADimens.SPACE2
