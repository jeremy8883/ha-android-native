package io.homeassistant.companion.android.dashboard.ui.moreinfo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAFilledButton
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.common.compose.util.isLight
import io.homeassistant.companion.android.dashboard.action.CardAction
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.moreinfo.MediaBrowseChild
import io.homeassistant.companion.android.dashboard.moreinfo.MediaBrowseId
import io.homeassistant.companion.android.dashboard.moreinfo.MediaBrowseLayout
import io.homeassistant.companion.android.dashboard.moreinfo.MediaBrowsePage
import io.homeassistant.companion.android.dashboard.moreinfo.mediaBrowsePage
import io.homeassistant.companion.android.dashboard.ui.cards.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.cards.LocalServerUrl
import io.homeassistant.companion.android.dashboard.ui.loadErrorText

/**
 * Port of `dialog-media-player-browse` with `ha-media-player-browse`: [entityId]'s media, page by page (back goes
 * up a page, then closes), its children as cards or rows; a tap opens a folder or plays the item, which closes the
 * browser as upstream does.
 */
@Composable
internal fun MediaBrowserDialog(
    entityId: String,
    title: String,
    hass: HassSnapshot,
    onAction: (CardAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val viewModel = hiltViewModel<MediaBrowserViewModel>(key = "browse-$entityId")
    val server = LocalServerUrl.current
    LaunchedEffect(entityId) { viewModel.start(entityId, server) }
    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val thumbnails by viewModel.thumbnails.collectAsStateWithLifecycle()
    val back = { if (!viewModel.back()) onDismiss() }
    val play = { call: CardAction.CallService ->
        onAction(call)
        onDismiss()
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        BackHandler(onBack = back)
        val surface = LocalHAColorScheme.current.colorSurfaceDefault
        DialogSystemBars(surface)
        Column(Modifier.fillMaxSize().background(surface).systemBarsPadding()) {
            val shown = pages.lastOrNull()
            val loaded = (shown as? BrowsePage.Loaded)?.let { page ->
                remember(page, hass) { hass.mediaBrowsePage(entityId, page.result) }
            }
            BrowserTopBar(loaded?.title ?: title, canGoBack = pages.size > 1, onBack = back, onClose = onDismiss)
            when (shown) {
                null, is BrowsePage.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    HALoading()
                }
                is BrowsePage.Failed -> Column(
                    Modifier.padding(HADimens.SPACE4),
                    verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
                ) {
                    Text(
                        LocalContext.current.loadErrorText(shown.error),
                        style = HATextStyle.Body,
                        color = LocalHAColorScheme.current.colorOnDangerNormal,
                    )
                    HAPlainButton(stringResource(commonR.string.retry), viewModel::retry)
                }
                is BrowsePage.Loaded -> loaded?.let { BrowserPage(it, thumbnails, viewModel::open, play) }
            }
        }
    }
}

@Composable
private fun BrowserTopBar(title: String, canGoBack: Boolean, onBack: () -> Unit, onClose: () -> Unit) {
    val colors = LocalHAColorScheme.current
    Row(Modifier.fillMaxWidth().padding(HADimens.SPACE1), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = if (canGoBack) onBack else onClose) {
            DashboardIcon(
                if (canGoBack) "mdi:arrow-left" else "mdi:close",
                colors.colorTextPrimary,
                Modifier.size(HASize.X2L),
            )
        }
        Text(
            title,
            style = HATextStyle.HeadlineMedium,
            color = colors.colorTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A page: a header with its play button when it plays as a whole, then its children, or why there are none. */
@Composable
private fun BrowserPage(
    page: MediaBrowsePage,
    thumbnails: ThumbnailAuth,
    onOpen: (MediaBrowseId) -> Unit,
    onPlay: (CardAction.CallService) -> Unit,
) {
    val tap = { child: MediaBrowseChild -> child.open?.let(onOpen) ?: child.play?.let(onPlay) ?: Unit }
    val footer = listOfNotNull(page.emptyText, page.hiddenText)
    when (val layout = page.layout) {
        is MediaBrowseLayout.Grid -> LazyVerticalGrid(
            columns = GridCells.Adaptive(CARD_WIDTH),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(HADimens.SPACE4),
            horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE3),
            verticalArrangement = Arrangement.spacedBy(HADimens.SPACE3),
        ) {
            page.play?.let { call ->
                item(span = { GridItemSpan(maxLineSpan) }) { PageHeader(page, call, thumbnails, onPlay) }
            }
            items(page.children) { child ->
                GridCard(child, layout.portrait, thumbnails, {
                    tap(child)
                }) { call -> PlayButton(call, page.playLabel, onPlay) }
            }
            footer.forEach { text -> item(span = { GridItemSpan(maxLineSpan) }) { FooterText(text) } }
        }
        is MediaBrowseLayout.List -> LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(HADimens.SPACE2),
        ) {
            page.play?.let { call -> item { PageHeader(page, call, thumbnails, onPlay) } }
            items(page.children) { child ->
                ListRow(child, layout.images, thumbnails, {
                    tap(child)
                }) { call -> PlayButton(call, page.playLabel, onPlay) }
            }
            footer.forEach { text -> item { FooterText(text) } }
        }
    }
}

@Composable
private fun PageHeader(
    page: MediaBrowsePage,
    play: CardAction.CallService,
    thumbnails: ThumbnailAuth,
    onPlay: (CardAction.CallService) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = HADimens.SPACE3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
    ) {
        page.thumbnail?.let { url ->
            MediaBrowserThumbnail(
                url,
                thumbnails,
                Modifier.size(HEADER_IMAGE).clip(RoundedCornerShape(HARadius.L)),
                ContentScale.Crop,
            )
        }
        HAFilledButton(page.playLabel, {
            onPlay(play)
        }, prefix = {
            DashboardIcon("mdi:play", LocalHAColorScheme.current.colorOnPrimaryLoud, Modifier.size(HASize.XL))
        })
    }
}

/** A child as a card: its thumbnail (or its class's icon) with a play button when it plays, and its title. */
@Composable
private fun GridCard(
    child: MediaBrowseChild,
    portrait: Boolean,
    thumbnails: ThumbnailAuth,
    onTap: () -> Unit,
    playButton: @Composable (CardAction.CallService) -> Unit,
) {
    val colors = LocalHAColorScheme.current
    Column(
        Modifier
            .clip(RoundedCornerShape(HARadius.L))
            .background(colors.colorFillDisabledLoudResting.copy(alpha = TINT_ALPHA))
            .clickable(role = Role.Button, onClick = onTap),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(if (portrait) PORTRAIT else 1f), contentAlignment = Alignment.Center) {
            DashboardIcon(child.icon, colors.colorTextSecondary, Modifier.size(ICON_SIZE))
            child.thumbnail?.let { url ->
                val fit = if (child.centered) Modifier.size(LOGO_SIZE) else Modifier.fillMaxSize()
                MediaBrowserThumbnail(url, thumbnails, fit, if (child.centered) ContentScale.Fit else ContentScale.Crop)
            }
            child.play?.let { call -> Box(Modifier.align(Alignment.BottomEnd)) { playButton(call) } }
        }
        Text(
            child.title,
            style = HATextStyle.BodyMedium,
            color = colors.colorTextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(HADimens.SPACE2),
        )
    }
}

/** A child as a row: its thumbnail or icon, its title, and a play button when it plays. */
@Composable
private fun ListRow(
    child: MediaBrowseChild,
    images: Boolean,
    thumbnails: ThumbnailAuth,
    onTap: () -> Unit,
    playButton: @Composable (CardAction.CallService) -> Unit,
) {
    val colors = LocalHAColorScheme.current
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onTap).padding(HADimens.SPACE2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE3),
    ) {
        Box(Modifier.size(ROW_IMAGE), contentAlignment = Alignment.Center) {
            DashboardIcon(child.icon, colors.colorTextSecondary, Modifier.size(HASize.X2L))
            child.thumbnail?.takeIf {
                images
            }?.let {
                MediaBrowserThumbnail(
                    it,
                    thumbnails,
                    Modifier.fillMaxSize().clip(RoundedCornerShape(HARadius.M)),
                    ContentScale.Crop,
                )
            }
        }
        Text(
            child.title,
            style = HATextStyle.Body,
            color = colors.colorTextPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        child.play?.let { playButton(it) }
    }
}

@Composable
private fun PlayButton(call: CardAction.CallService, label: String, onPlay: (CardAction.CallService) -> Unit) {
    IconButton(onClick = { onPlay(call) }) {
        DashboardIcon(
            "mdi:play",
            LocalHAColorScheme.current.colorFillPrimaryLoudResting,
            Modifier.size(HASize.X2L).semantics {
                contentDescription =
                    label
            },
        )
    }
}

@Composable
private fun FooterText(text: String) {
    Text(
        text,
        style = HATextStyle.BodyMedium,
        color = LocalHAColorScheme.current.colorTextSecondary,
        modifier = Modifier.padding(HADimens.SPACE4),
    )
}

/** The full-screen dialog's system bar icons, dark over a light [surface] as the bottom sheets' are. */
@Composable
private fun DialogSystemBars(surface: Color) {
    val view = LocalView.current
    val light = surface.isLight()
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }
}

private const val TINT_ALPHA = 0.2f
private const val PORTRAIT = 2f / 3f
private val CARD_WIDTH = 140.dp
private val ICON_SIZE = 48.dp
private val LOGO_SIZE = 72.dp
private val HEADER_IMAGE = 96.dp
private val ROW_IMAGE = 48.dp
