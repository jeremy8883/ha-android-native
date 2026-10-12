package io.homeassistant.companion.android.dashboard.ui.lovelace.cardfeatures

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.data.Loadable
import io.homeassistant.companion.android.dashboard.data.valueOrNull
import io.homeassistant.companion.android.dashboard.feature.LightColorFavoritesFeature
import io.homeassistant.companion.android.dashboard.feature.PositionFavoritesFeature
import io.homeassistant.companion.android.dashboard.ui.theme.Css
import io.homeassistant.companion.android.dashboard.ui.theme.parseCssColor
import kotlinx.serialization.json.JsonObject

/**
 * The registry entry of [entityId] as the favourite features load it, [Loadable.Loading] until it answers.
 *
 * Upstream: part of `HuiNumericFavoriteCardFeatureBase`, `hui-light-color-favorites-card-feature`
 * (src/panels/lovelace/card-features/hui-numeric-favorite-card-feature-base.ts;
 * src/panels/lovelace/card-features/hui-light-color-favorites-card-feature.ts)
 */
@Composable
private fun rememberEntry(entityId: String): Loadable<JsonObject?> {
    val viewModel = hiltViewModel<FeatureEntryViewModel>(key = "feature-entry-$entityId")
    LaunchedEffect(entityId) { viewModel.show(entityId) }
    val entry by viewModel.entry.collectAsStateWithLifecycle()
    return entry
}

/**
 * The favourite positions as labelled segments, as many as fit 30px each; the defaults until the entry loads, or
 * when it can't, as upstream.
 *
 * Upstream: `HuiNumericFavoriteCardFeatureBase` (src/panels/lovelace/card-features/hui-numeric-favorite-card-feature-base.ts)
 */
@Composable
internal fun FeaturePositionFavorites(
    feature: PositionFavoritesFeature,
    color: Color,
    onAction: (CardAction) -> Unit,
) {
    val entry = rememberEntry(feature.entityId)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fit = maxOf(1, (maxWidth / OPTION_MIN_WIDTH).toInt())
        feature.select(entry.valueOrNull, fit)?.let { TileFeatureControl(it, feature.enabled, color, onAction) }
    }
}

/**
 * The favourite colours as pills sharing the width, as many as fit 32px each with their gap; none until the entry
 * loads, or when it can't, as upstream.
 *
 * Upstream: `hui-light-color-favorites-card-feature`, `ha-favorite-color-button`
 * (src/panels/lovelace/card-features/hui-light-color-favorites-card-feature.ts;
 * src/dialogs/more-info/components/lights/ha-favorite-color-button.ts)
 */
@Composable
internal fun FeatureLightColorFavorites(feature: LightColorFavoritesFeature, onAction: (CardAction) -> Unit) {
    val colors = LocalHAColorScheme.current
    val entry = rememberEntry(feature.entityId)
    val favorites = feature.favorites(entry.valueOrNull)
    BoxWithConstraints(Modifier.fillMaxWidth().height(FEATURE_HEIGHT)) {
        val fit = ((maxWidth + PILL_GAP) / (PILL_MIN_SIZE + PILL_GAP)).toInt()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PILL_GAP)) {
            favorites.take(fit).forEach { favorite ->
                val shape = RoundedCornerShape(FEATURE_RADIUS)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .alpha(if (feature.enabled) 1f else DISABLED_ALPHA)
                        .clip(shape)
                        .background(parseCssColor(favorite.swatch) ?: Color.White)
                        .border(
                            Css.BorderWidth.Sm,
                            if (favorite.outlined) colors.colorBorderNeutralQuiet else Color.Transparent,
                            shape,
                        )
                        .clickable(enabled = feature.enabled, role = Role.Button) { onAction(favorite.apply) }
                        .semantics { contentDescription = favorite.label },
                )
            }
        }
    }
}

/** `OPTION_MIN_WIDTH` of the numeric favourites. */
private val OPTION_MIN_WIDTH = 30.dp

/** `PILL_MIN_SIZE` and `PILL_GAP` of the colour favourites. */
private val PILL_MIN_SIZE = 32.dp
private val PILL_GAP = 8.dp
