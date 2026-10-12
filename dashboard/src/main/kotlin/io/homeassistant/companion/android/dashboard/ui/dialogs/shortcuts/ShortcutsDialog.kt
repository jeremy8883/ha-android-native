package io.homeassistant.companion.android.dashboard.ui.dialogs.shortcuts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.entity.Localize
import io.homeassistant.companion.android.dashboard.keyboard.SHORTCUTS_HINT
import io.homeassistant.companion.android.dashboard.keyboard.SHORTCUTS_HINT_LINK
import io.homeassistant.companion.android.dashboard.keyboard.SHORTCUTS_TITLE
import io.homeassistant.companion.android.dashboard.keyboard.ShortcutsItem
import io.homeassistant.companion.android.dashboard.keyboard.ShortcutsSection
import io.homeassistant.companion.android.dashboard.keyboard.shortcutsSections
import io.homeassistant.companion.android.dashboard.ui.components.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.theme.Css
import io.homeassistant.companion.android.dashboard.ui.theme.cssPx
import io.homeassistant.companion.android.dashboard.ui.theme.resolveVariable

/**
 * Port of `dialog-shortcuts` on a phone (a full-screen `ha-dialog`): each section's title over its shortcuts, each key
 * drawn as a key before what it does, and the hint about turning shortcuts on, its link opening where that is
 * ([onOpenSettings], the text alone without it).
 *
 * Upstream: `dialog-shortcuts` (src/dialogs/shortcuts/dialog-shortcuts.ts)
 */
@Composable
internal fun ShortcutsDialog(localize: Localize, onOpenSettings: (() -> Unit)?, onDismiss: () -> Unit) {
    val sections = remember(localize) { shortcutsSections(localize) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val surface = LocalHAColorScheme.current.colorSurfaceDefault
        DialogSystemBars(surface)
        Column(Modifier.fillMaxSize().background(surface).systemBarsPadding()) {
            DialogHeader(localize(SHORTCUTS_TITLE), localize("ui.common.close"), onDismiss)
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(start = Css.Space.S6, end = Css.Space.S6, bottom = Css.Space.S6),
            ) {
                sections.forEach { Section(it) }
            }
            HintFooter(localize, onOpenSettings?.let { open -> { onDismiss().also { open() } } })
        }
    }
}

/**
 * `ha-dialog-header`: the close button and the title.
 *
 * Upstream: part of `ha-dialog` (src/components/ha-dialog.ts)
 */
@Composable
private fun DialogHeader(title: String, closeLabel: String, onClose: () -> Unit) {
    val colors = LocalHAColorScheme.current
    Row(Modifier.fillMaxWidth().padding(Css.Space.S1), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose, modifier = Modifier.semantics { contentDescription = closeLabel }) {
            DashboardIcon("mdi:close", colors.colorTextPrimary, Modifier.size(HASize.X2L))
        }
        Text(
            title,
            style = Css.Text.DialogTitle,
            color = colors.colorTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A section: its title (an `h3`, unstyled by the dialog, so the browser's: bold, 1.17em) over its lines.
 *
 * Upstream: part of `dialog-shortcuts` (src/dialogs/shortcuts/dialog-shortcuts.ts)
 */
@Composable
private fun Section(section: ShortcutsSection) {
    val colors = LocalHAColorScheme.current
    Text(
        section.title,
        style = Css.Text.Body.copy(fontSize = Css.FontSize.M * H3_SCALE, textAlign = TextAlign.Start),
        fontWeight = FontWeight.Bold,
        color = colors.colorTextPrimary,
        modifier = Modifier.padding(top = H3_MARGIN, bottom = Css.Space.S2),
    )
    section.items.forEach { item ->
        when (item) {
            is ShortcutsItem.Text -> Text(
                item.text,
                style = Css.Text.Body.copy(textAlign = TextAlign.Start),
                color = colors.colorTextPrimary,
                modifier = Modifier.padding(bottom = Css.Space.S2),
            )
            is ShortcutsItem.Shortcut -> ShortcutRow(item)
        }
    }
}

/**
 * `.shortcut`: each key in an outlined box, then what the keys do.
 *
 * Upstream: part of `dialog-shortcuts` (src/dialogs/shortcuts/dialog-shortcuts.ts)
 */
@Composable
private fun ShortcutRow(shortcut: ShortcutsItem.Shortcut) {
    val colors = LocalHAColorScheme.current
    val outline = resolveVariable("outline-color", isSystemInDarkTheme())
        ?: colors.colorBorderNeutralNormal
    FlowRow(
        Modifier.fillMaxWidth().padding(vertical = Css.Space.S1),
        horizontalArrangement = Arrangement.spacedBy(Css.Space.S2),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        shortcut.keys.forEach { key ->
            Text(
                key,
                style = Css.Text.Body,
                color = colors.colorTextPrimary,
                modifier = Modifier
                    .border(KEY_BORDER, outline, RoundedCornerShape(Css.Radius.Md))
                    .padding(Css.Space.S2),
            )
        }
        Text(
            shortcut.description,
            style = Css.Text.Body.copy(textAlign = TextAlign.Start),
            color = colors.colorTextPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The footer's `ha-alert`: shortcuts work once turned on, with [onOpenSettings] behind "user profile".
 *
 * Upstream: part of `dialog-shortcuts`, `ha-alert` (src/dialogs/shortcuts/dialog-shortcuts.ts;
 * src/components/ha-alert.ts)
 */
@Composable
private fun HintFooter(localize: Localize, onOpenSettings: (() -> Unit)?) {
    val colors = LocalHAColorScheme.current
    val dark = isSystemInDarkTheme()
    val info = resolveVariable("info-color", dark) ?: colors.colorTextSecondary
    val link = resolveVariable("primary-color", dark) ?: colors.colorTextLink
    val text = remember(localize, onOpenSettings != null) {
        val (before, after) = localize(SHORTCUTS_HINT, mapOf("user_profile" to LINK_MARKER))
            .split(LINK_MARKER, limit = 2).let { it.first() to it.getOrElse(1) { "" } }
        buildAnnotatedString {
            append(before)
            val label = localize(SHORTCUTS_HINT_LINK)
            // UPSTREAM-DIFF: upstream's "user profile" link opens the profile page, where shortcuts are turned on;
            // here it opens the app's Settings, where that switch is.
            if (onOpenSettings == null) {
                append(label)
            } else {
                val style = TextLinkStyles(SpanStyle(color = link, textDecoration = TextDecoration.Underline))
                withLink(LinkAnnotation.Clickable(LINK_TAG, style) { onOpenSettings() }) { append(label) }
            }
            append(after)
        }
    }
    Row(
        Modifier
            .padding(start = Css.Space.S4, top = Css.Space.S3, end = Css.Space.S4, bottom = Css.Space.S4)
            .fillMaxWidth()
            .background(info.copy(alpha = ALERT_ALPHA), RoundedCornerShape(Css.Radius.Sm))
            .padding(Css.Space.S2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DashboardIcon("mdi:information-outline", info, Modifier.size(HASize.X2L))
        Text(
            text,
            style = Css.Text.Body.copy(textAlign = TextAlign.Start),
            color = colors.colorTextPrimary,
            modifier = Modifier.padding(horizontal = Css.Space.S2),
        )
    }
}

/**
 * The full-screen dialog's system bar icons, dark over a light [surface].
 *
 * Upstream: none (native: Android system bar colours)
 */
@Composable
private fun DialogSystemBars(surface: Color) {
    val view = LocalView.current
    val light = surface.luminance() > LIGHT_LUMINANCE
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }
}

/** The browser's `h3` font size, 1.17em. */
private const val H3_SCALE = 1.17f

/** The browser's `h3` top margin, 1em of its font size. */
private val H3_MARGIN = 16.cssPx
private val KEY_BORDER = 1.cssPx

/** `ha-alert`'s background: its colour at 12%. */
private const val ALERT_ALPHA = 0.12f
private const val LIGHT_LUMINANCE = 0.5f
private const val LINK_MARKER = "\u0000"
private const val LINK_TAG = "user_profile"
