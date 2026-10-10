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
 * Port of `ha-more-info-favorites` with `ha-favorite-color-button` (frontend@20260624.6
 * src/dialogs/more-info/components/): round swatches that set the light when tapped. An admin's long press starts
 * editing them: they shake, each with a delete badge, a tap edits one, dragging one moves it, and the add and done
 * buttons follow.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LightFavoritesRow(
    favorites: LightFavorites,
    editMode: Boolean,
    isAdmin: Boolean,
    callbacks: FavoritesRowCallbacks,
) {
    // Where each favourite sits, to find where a dragged one lands
    val bounds = remember { mutableStateMapOf<Int, Rect>() }
    FlowRow(
        modifier = Modifier.widthIn(max = ROW_MAX_WIDTH),
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE4, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
    ) {
        favorites.favorites.forEachIndexed { index, favorite ->
            FavoriteBubble(
                favorite = favorite,
                index = index,
                enabled = favorites.enabled,
                editMode = editMode,
                isAdmin = isAdmin,
                callbacks = callbacks,
                landing = { from, moved ->
                    val center = (bounds[from]?.center ?: Offset.Zero) + moved
                    bounds.entries.minByOrNull { (_, rect) -> (rect.center - center).getDistanceSquared() }?.key
                        ?.takeIf { it != from }
                },
                modifier = Modifier.onGloballyPositioned { bounds[index] = it.boundsInParent() },
            )
        }
        if (editMode) {
            RoundButton("mdi:plus", favorites.addLabel, callbacks.onAdd)
            RoundButton("mdi:check", favorites.doneLabel, callbacks.onDone)
        }
    }
}

/** One favourite: its swatch, shaking with a delete badge while editing, and draggable then. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteBubble(
    favorite: LightFavorite,
    index: Int,
    enabled: Boolean,
    editMode: Boolean,
    isAdmin: Boolean,
    callbacks: FavoritesRowCallbacks,
    landing: (from: Int, moved: Offset) -> Int?,
    modifier: Modifier,
) {
    var drag by remember { mutableStateOf<Offset?>(null) }
    val latest by rememberUpdatedState(callbacks)
    val rotation = if (editMode && drag == null) shakeRotation(index) else 0f
    Box(
        modifier = modifier
            .zIndex(if (drag != null) 1f else 0f)
            .offset { drag?.let { IntOffset(it.x.roundToInt(), it.y.roundToInt()) } ?: IntOffset.Zero }
            .graphicsLayer { rotationZ = rotation },
    ) {
        Swatch(
            favorite = favorite,
            enabled = enabled,
            modifier = Modifier
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
                    enabled = enabled,
                    role = Role.Button,
                    onLongClick = if (!editMode && isAdmin) latest.onStartEditing else null,
                    onClick = { if (editMode) latest.onEdit(index) else latest.onApply(index) },
                ),
        )
        if (editMode) {
            DeleteBadge(favorite.deleteLabel, Modifier.align(Alignment.TopEnd).offset(DELETE_OFFSET, -DELETE_OFFSET)) {
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
