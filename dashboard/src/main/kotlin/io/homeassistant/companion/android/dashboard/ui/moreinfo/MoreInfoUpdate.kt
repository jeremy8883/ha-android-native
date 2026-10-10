package io.homeassistant.companion.android.dashboard.ui.moreinfo

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAFilledButton
import io.homeassistant.companion.android.common.compose.composable.HAHorizontalDivider
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.composable.HASwitch
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.data.Loadable
import io.homeassistant.companion.android.dashboard.data.valueOrNull
import io.homeassistant.companion.android.dashboard.entity.EntityState
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.moreinfo.UpdateBackupOption
import io.homeassistant.companion.android.dashboard.moreinfo.UpdateBackupSettings
import io.homeassistant.companion.android.dashboard.moreinfo.UpdateMoreInfo
import io.homeassistant.companion.android.dashboard.moreinfo.UpdateProgress
import io.homeassistant.companion.android.dashboard.moreinfo.UpdateType
import io.homeassistant.companion.android.dashboard.moreinfo.updateBackupOption
import io.homeassistant.companion.android.dashboard.ui.cards.MarkdownText
import io.homeassistant.companion.android.dashboard.ui.loadErrorText
import java.time.Instant

/**
 * The controls of an update's details, port of `more-info-update` (frontend@20260624.6
 * src/dialogs/more-info/controls/more-info-update.ts): its progress, title, versions and release announcement,
 * the release notes, the backup switch, and skip and update.
 */
@Composable
internal fun MoreInfoUpdate(
    info: UpdateMoreInfo,
    state: EntityState,
    hass: HassSnapshot,
    now: Instant,
    onAction: (CardAction) -> Unit,
) {
    val entityId = state.entityId
    val colors = LocalHAColorScheme.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(HADimens.SPACE3)) {
        when (val progress = info.progress) {
            is UpdateProgress.Percent -> LinearProgressIndicator(
                progress = { (progress.percent / PERCENT).toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
            UpdateProgress.Indeterminate -> LinearProgressIndicator(Modifier.fillMaxWidth())
            null -> Unit
        }
        info.title?.let {
            Text(it, style = HATextStyle.Body.copy(fontWeight = FontWeight.Bold), color = colors.colorTextPrimary)
        }
        info.versions.forEach { (key, value) -> VersionRow(key, value) }
        info.releaseUrl?.let { url -> ReleaseLink(info.releaseLabel, url) }
        ReleaseNotes(info, entityId)
        val backup = info.install?.backupType?.let { type -> rememberBackupOption(type, state, hass, now) }
        UpdateFooter(info, backup, onAction)
    }
}

@Composable
private fun VersionRow(key: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, style = HATextStyle.Body, color = LocalHAColorScheme.current.colorTextPrimary)
        Text(value, style = HATextStyle.Body, color = LocalHAColorScheme.current.colorTextPrimary)
    }
}

@Composable
private fun ReleaseLink(label: String, url: String) {
    val context = LocalContext.current
    HAPlainButton(label, { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) })
}

/** The fetched notes (a spinner while loading, the error if it fails), else the summary, after a rule. */
@Composable
private fun ReleaseNotes(info: UpdateMoreInfo, entityId: String) {
    if (!info.fetchNotes) {
        info.summary?.let {
            HAHorizontalDivider()
            MarkdownText(it, Modifier.fillMaxWidth())
        }
        return
    }
    val viewModel = hiltViewModel<MoreInfoUpdateViewModel>(key = "update-$entityId")
    LaunchedEffect(entityId) { viewModel.show(entityId) }
    val notes by viewModel.releaseNotes.collectAsStateWithLifecycle()
    when (val loaded = notes) {
        Loadable.Loading -> {
            HAHorizontalDivider()
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(HASize.X3L))
            }
        }
        is Loadable.Failed -> Text(
            LocalContext.current.loadErrorText(loaded.error),
            style = HATextStyle.BodyMedium,
            color = LocalHAColorScheme.current.colorOnDangerNormal,
        )
        is Loadable.Ready -> loaded.value?.let {
            HAHorizontalDivider()
            MarkdownText(it, Modifier.fillMaxWidth())
        }
    }
}

/**
 * The backup switch of [type]'s update, from the server's backup settings once read; before (or when they can't
 * be read) it starts off and offers a manual backup, as upstream's.
 */
@Composable
private fun rememberBackupOption(
    type: UpdateType,
    state: EntityState,
    hass: HassSnapshot,
    now: Instant,
): UpdateBackupOption {
    val viewModel = hiltViewModel<UpdateBackupViewModel>(key = "update-backup-${state.entityId}")
    LaunchedEffect(type) { viewModel.show(type, hass.config.components) }
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val loaded = settings.valueOrNull ?: UpdateBackupSettings()
    return remember(loaded, state, hass, now) { hass.updateBackupOption(state, type, loaded, now) }
}

/** The backup switch, then skip (or clear skipped) and update; skipping an update that installs itself asks first. */
@Composable
private fun UpdateFooter(info: UpdateMoreInfo, option: UpdateBackupOption?, onAction: (CardAction) -> Unit) {
    // Starts as the settings say, once they are read; then as the user sets it
    var backup by remember(option?.defaultOn) { mutableStateOf(option?.defaultOn == true) }
    var explainSkip by remember { mutableStateOf(false) }
    val install = info.install
    if (install != null && option != null) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    option.title,
                    style = HATextStyle.Body.copy(textAlign = TextAlign.Start),
                    color = LocalHAColorScheme.current.colorTextPrimary,
                )
                option.description?.let {
                    Text(
                        it,
                        style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.Start),
                        color = LocalHAColorScheme.current.colorTextSecondary,
                    )
                }
            }
            HASwitch(checked = backup, onCheckedChange = { backup = it }, enabled = !install.installing)
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2, Alignment.End)) {
        val skip = info.skip
        HAPlainButton(skip.label, {
            if (skip.autoUpdateTitle !=
                null
            ) {
                explainSkip = true
            } else {
                onAction(skip.action)
            }
        }, enabled = skip.enabled)
        install?.let {
            HAFilledButton(it.label, { onAction(it.call(backup)) }, enabled = it.enabled && !it.installing)
        }
    }
    if (explainSkip) {
        AlertDialog(
            onDismissRequest = { explainSkip = false },
            title = { Text(info.skip.autoUpdateTitle.orEmpty(), style = HATextStyle.HeadlineMedium) },
            text = { Text(info.skip.autoUpdateText.orEmpty(), style = HATextStyle.Body) },
            confirmButton = { HAPlainButton(stringResource(commonR.string.ok), { explainSkip = false }) },
        )
    }
}

private const val PERCENT = 100.0
