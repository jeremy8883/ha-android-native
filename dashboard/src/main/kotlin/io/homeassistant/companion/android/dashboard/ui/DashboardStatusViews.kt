package io.homeassistant.companion.android.dashboard.ui

import android.text.format.DateUtils
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.R
import io.homeassistant.companion.android.dashboard.data.LoadError
import io.homeassistant.companion.android.dashboard.ui.components.loadErrorText
import io.homeassistant.companion.android.dashboard.ui.theme.Css
import java.time.ZonedDateTime
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * Shown in place of the dashboard when nothing could be loaded for it: why, and a way to try again.
 *
 * Upstream: `hass-error-screen` (src/layouts/hass-error-screen.ts)
 */
@Composable
internal fun LoadErrorScreen(error: LoadError, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Css.Space.S6),
        verticalArrangement = Arrangement.spacedBy(Css.Space.S4, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.native_dashboard_error, LocalContext.current.loadErrorText(error)),
            style = Css.Text.Body,
            color = LocalHAColorScheme.current.colorTextSecondary,
            textAlign = TextAlign.Center,
        )
        HAAccentButton(text = stringResource(R.string.native_dashboard_retry), onClick = onRetry)
    }
}

/**
 * A thin bar at the top of the content while shown data is loaded again, drawn over it so nothing moves.
 *
 * Upstream: none (native: cached data shown while it loads again)
 */
@Composable
internal fun RefreshIndicator(visible: Boolean, modifier: Modifier = Modifier) {
    if (visible) {
        IndeterminateBar(
            color = LocalHAColorScheme.current.colorFillPrimaryLoudResting,
            trackColor = LocalHAColorScheme.current.colorFillPrimaryQuietResting,
            modifier = modifier.fillMaxWidth().height(BAR_HEIGHT),
        )
    }
}

/**
 * Material's indeterminate linear progress (two lines chasing across the track), timed by the frame clock so it
 * keeps moving when the phone's animations are turned off. Material's own bar follows the animation scale and, at
 * 0, stops as an empty track.
 *
 * Upstream: none (native: the refresh indicator)
 */
@Composable
private fun IndeterminateBar(color: Color, trackColor: Color, modifier: Modifier) {
    val cycleMillis = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (true) withFrameMillis { cycleMillis.longValue = (it - start) % BAR_CYCLE_MILLIS }
    }
    Canvas(modifier) {
        drawRect(trackColor)
        val time = cycleMillis.longValue
        BAR_LINES.forEach { line ->
            val head = line.head.fraction(time)
            val tail = line.tail.fraction(time)
            if (head >
                tail
            ) {
                drawRect(color, Offset(tail * size.width, 0f), Size((head - tail) * size.width, size.height))
            }
        }
    }
}

/** One edge of a moving line: from [delayMillis], it eases across the track in [durationMillis]. */
private class BarEdge(val delayMillis: Int, val durationMillis: Int, val easing: Easing) {
    fun fraction(time: Long): Float =
        easing.transform(((time - delayMillis).toFloat() / durationMillis).coerceIn(0f, 1f))
}

private class BarLine(val head: BarEdge, val tail: BarEdge)

/** Material 3's indeterminate linear timings. */
private const val BAR_CYCLE_MILLIS = 1800L
private val BAR_LINES = listOf(
    BarLine(
        head = BarEdge(delayMillis = 0, durationMillis = 750, easing = CubicBezierEasing(0.2f, 0f, 0.8f, 1f)),
        tail = BarEdge(delayMillis = 333, durationMillis = 850, easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)),
    ),
    BarLine(
        head = BarEdge(delayMillis = 1000, durationMillis = 567, easing = CubicBezierEasing(0f, 0f, 0.65f, 1f)),
        tail = BarEdge(delayMillis = 1267, durationMillis = 533, easing = CubicBezierEasing(0.1f, 0f, 0.45f, 1f)),
    ),
)

/** Material's linear indicator height. */
private val BAR_HEIGHT = 4.dp

/**
 * The bottom of the screen, over the content: messages for the user, then while [offline] a bar saying so, and under
 * them [updatedAt] when the toolbar has no room for it (it shows the view tabs).
 *
 * Upstream: part of `notification-manager`, the disconnect toast mixin (src/managers/notification-manager.ts;
 * src/state/disconnect-toast-mixin.ts)
 */
@Composable
internal fun DashboardBottomBars(
    snackbar: SnackbarHostState,
    offline: Boolean,
    updatedAt: Instant?,
    now: State<ZonedDateTime?>,
) {
    Column(Modifier.fillMaxWidth()) {
        SnackbarHost(snackbar)
        if (offline) {
            Snackbar(modifier = Modifier.padding(Css.Space.S3)) {
                Text(stringResource(R.string.native_dashboard_offline))
            }
        }
        if (updatedAt != null) {
            Snackbar(modifier = Modifier.padding(Css.Space.S3)) { Text(updatedText(updatedAt, now.value)) }
        }
    }
}

/**
 * Under the toolbar's title, small and muted: when the data shown was last current, such as "Updated 5 minutes ago".
 *
 * Upstream: none (native: the age of cached data)
 */
@Composable
internal fun UpdatedSubtitle(updatedAt: Instant, now: State<ZonedDateTime?>) {
    Text(
        updatedText(updatedAt, now.value),
        fontSize = Css.FontSize.S,
        color = LocalHAColorScheme.current.colorTextSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Show [refreshErrors] as a message each time a new one comes, with a way to try again; the data shown stays.
 *
 * Upstream: none (native: refreshing cached data that failed)
 */
@Composable
internal fun RefreshErrorMessages(refreshErrors: Flow<LoadError?>, snackbar: SnackbarHostState, onRetry: () -> Unit) {
    val context = LocalContext.current
    val currentOnRetry by rememberUpdatedState(onRetry)
    LaunchedEffect(refreshErrors) {
        refreshErrors.collect { error ->
            if (error == null) return@collect
            val result = snackbar.showSnackbar(
                message = context.getString(R.string.native_dashboard_refresh_failed, context.loadErrorText(error)),
                actionLabel = context.getString(R.string.native_dashboard_retry),
            )
            if (result == SnackbarResult.ActionPerformed) currentOnRetry()
        }
    }
}

/**
 * When the data shown was last current, such as "Updated 5 minutes ago".
 *
 * Upstream: none (native: the age of cached data)
 */
@Composable
private fun updatedText(time: Instant, now: ZonedDateTime?): String {
    val timeMillis = time.toEpochMilliseconds()
    val nowMillis = now?.toInstant()?.toEpochMilli() ?: timeMillis
    if (nowMillis - timeMillis < DateUtils.MINUTE_IN_MILLIS) {
        return stringResource(R.string.native_dashboard_updated_just_now)
    }
    val relative = DateUtils.getRelativeTimeSpanString(timeMillis, nowMillis, DateUtils.MINUTE_IN_MILLIS)
    return stringResource(R.string.native_dashboard_updated, relative)
}
