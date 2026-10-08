package io.homeassistant.companion.android.dashboard.ui

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.R
import io.homeassistant.companion.android.dashboard.data.LoadError
import java.time.ZonedDateTime
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow

/** Shown in place of the dashboard when nothing could be loaded for it: why, and a way to try again. */
@Composable
internal fun LoadErrorScreen(error: LoadError, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(HADimens.SPACE6),
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.native_dashboard_error, LocalContext.current.loadErrorText(error)),
            style = HATextStyle.Body,
            textAlign = TextAlign.Center,
        )
        HAAccentButton(text = stringResource(R.string.native_dashboard_retry), onClick = onRetry)
    }
}

/** A thin bar at the top of the content while shown data is loaded again, drawn over it so nothing moves. */
@Composable
internal fun RefreshIndicator(visible: Boolean, modifier: Modifier = Modifier) {
    if (visible) {
        LinearProgressIndicator(
            modifier = modifier.fillMaxWidth(),
            color = LocalHAColorScheme.current.colorFillPrimaryLoudResting,
            trackColor = LocalHAColorScheme.current.colorFillPrimaryQuietResting,
        )
    }
}

/**
 * The bottom of the screen, over the content: messages for the user, and below them while the connection is lost,
 * a bar saying so and, when [dataShown], how old the data shown is.
 */
@Composable
internal fun DashboardBottomBars(
    snackbar: SnackbarHostState,
    offlineSince: Instant?,
    dataShown: Boolean,
    now: State<ZonedDateTime?>,
) {
    Column(Modifier.fillMaxWidth()) {
        SnackbarHost(snackbar)
        if (offlineSince != null) {
            Snackbar(modifier = Modifier.padding(HADimens.SPACE3)) {
                val offline = stringResource(R.string.native_dashboard_offline)
                Text(if (dataShown) "$offline · ${updatedText(offlineSince, now.value)}" else offline)
            }
        }
    }
}

/**
 * Show [refreshErrors] as a message each time a new one comes, with a way to try again; the data shown stays.
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

/** When the data shown was last current, such as "Updated 5 minutes ago". */
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
