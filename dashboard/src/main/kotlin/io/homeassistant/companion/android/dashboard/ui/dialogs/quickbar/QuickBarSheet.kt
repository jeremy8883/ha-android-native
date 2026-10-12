package io.homeassistant.companion.android.dashboard.ui.dialogs.quickbar

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import io.homeassistant.companion.android.common.compose.composable.HAModalBottomSheet
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.composable.consumeSheetScrollFling
import io.homeassistant.companion.android.common.compose.composable.rememberHAModalBottomSheetState
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.entity.Localize
import io.homeassistant.companion.android.dashboard.quickbar.QuickBarAction
import io.homeassistant.companion.android.dashboard.quickbar.QuickBarIcon
import io.homeassistant.companion.android.dashboard.quickbar.QuickBarItem
import io.homeassistant.companion.android.dashboard.quickbar.QuickBarItems
import io.homeassistant.companion.android.dashboard.quickbar.QuickBarRow
import io.homeassistant.companion.android.dashboard.quickbar.QuickBarSection
import io.homeassistant.companion.android.dashboard.quickbar.QuickBarSource
import io.homeassistant.companion.android.dashboard.quickbar.isSvgPath
import io.homeassistant.companion.android.dashboard.target.TargetIcon
import io.homeassistant.companion.android.dashboard.ui.components.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.components.targetpicker.EmptyRow
import io.homeassistant.companion.android.dashboard.ui.components.targetpicker.SectionTitle
import io.homeassistant.companion.android.dashboard.ui.components.targetpicker.TargetIconView
import io.homeassistant.companion.android.dashboard.ui.theme.Css
import io.homeassistant.companion.android.dashboard.ui.theme.cssPx
import io.homeassistant.companion.android.dashboard.ui.theme.parseCssColor
import io.homeassistant.companion.android.dashboard.ui.theme.resolveVariable

/**
 * Port of the quick bar ("Search Home Assistant") on a phone: the search, the section chips (Navigate, Commands,
 * then Entities, Devices and Areas; the admin's sections only for admins) and the list: each section's items under
 * its title, or one section's, the best matches first when searching. Choosing an item hands its [QuickBarAction]
 * to [onChoose] and closes the sheet; restarting or stopping the server asks first; a reload is handed to [onRun]
 * with a spinner on its row, and the sheet closes once it is done.
 *
 * Upstream: `ha-quick-bar`, `ha-picker-combo-box` (src/dialogs/quick-bar/ha-quick-bar.ts;
 * src/components/ha-picker-combo-box.ts)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuickBarSheet(
    source: QuickBarSource,
    onDismiss: () -> Unit,
    onChoose: (QuickBarAction) -> Unit,
    onRun: (QuickBarAction.CallService, onDone: () -> Unit) -> Unit,
) {
    val localize = source.hass.localize
    var search by rememberSaveable { mutableStateOf("") }
    var section by rememberSaveable { mutableStateOf<QuickBarSection?>(null) }
    var confirming by remember { mutableStateOf<QuickBarAction.CallService?>(null) }
    // The command running, until it is done; nothing else can be chosen meanwhile (`_itemSelected`)
    var running by remember { mutableStateOf<String?>(null) }
    val items = remember(source) { QuickBarItems(source) }
    val rows = remember(items, search, section) { items.rows(search.trim(), section) }
    val focus = remember { FocusRequester() }
    val choose = { item: QuickBarItem ->
        val action = item.action
        when {
            running != null -> Unit
            action is QuickBarAction.CallService && action.confirm -> confirming = action
            action is QuickBarAction.CallService -> {
                running = item.id
                onRun(action, onDismiss)
            }
            else -> {
                onDismiss()
                onChoose(action)
            }
        }
    }
    HAModalBottomSheet(
        bottomSheetState = rememberHAModalBottomSheetState(skipPartiallyExpanded = true),
        onDismissRequest = onDismiss,
    ) {
        SearchField(search, { search = it }, localize, Modifier.focusRequester(focus))
        SectionChips(items, section) { section = if (section == it) null else it }
        LazyColumn(Modifier.fillMaxWidth().consumeSheetScrollFling()) {
            items(rows, key = ::rowKey) { row ->
                when (row) {
                    is QuickBarRow.Title -> SectionTitle(row.text)
                    is QuickBarRow.Item -> ItemRow(row.item, source.hass, row.item.id == running) { choose(row.item) }
                    is QuickBarRow.Empty -> EmptyRow(row.text, searching = search.isNotBlank())
                }
            }
        }
    }
    // Upstream focuses the search as the dialog opens
    LaunchedEffect(Unit) { focus.requestFocus() }
    confirming?.let { action ->
        ConfirmServerControl(action, localize, onDismiss = { confirming = null }) {
            confirming = null
            onDismiss()
            onChoose(action)
        }
    }
}

private fun rowKey(row: QuickBarRow): String = when (row) {
    is QuickBarRow.Title -> "title-${row.text}"
    is QuickBarRow.Item -> row.item.id
    is QuickBarRow.Empty -> "empty"
}

/**
 * The search, labelled "Search Home Assistant", with a button that clears it.
 *
 * Upstream: part of `ha-picker-combo-box`, `ha-input-search` (src/components/ha-picker-combo-box.ts;
 * src/components/input/ha-input-search.ts)
 */
@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, localize: Localize, modifier: Modifier) {
    val colors = LocalHAColorScheme.current
    HATextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth().padding(horizontal = Css.Space.S4),
        placeholder = { Text(localize(TITLE), style = Css.Text.Body) },
        leadingIcon = { DashboardIcon("mdi:magnify", colors.colorTextPrimary, Modifier.size(HASize.X2L)) },
        trailingIcon = if (value.isEmpty()) {
            null
        } else {
            {
                IconButton(
                    onClick = { onChange("") },
                    modifier = Modifier.semantics { contentDescription = localize(CLEAR) },
                ) {
                    DashboardIcon("mdi:close", colors.colorOnNeutralNormal)
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        singleLine = true,
    )
}

/**
 * The section chips with a separator after Navigate and after Commands: the chosen one filled; a tap chooses a section
 * or shows all again.
 *
 * Upstream: part of `ha-picker-combo-box` (src/components/ha-picker-combo-box.ts)
 */
@Composable
private fun SectionChips(items: QuickBarItems, selected: QuickBarSection?, onToggle: (QuickBarSection) -> Unit) {
    val colors = LocalHAColorScheme.current
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(start = Css.Space.S4, end = Css.Space.S4, top = Css.Space.S3, bottom = Css.Space.S3),
        horizontalArrangement = Arrangement.spacedBy(Css.Space.S2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.sections.forEach { section ->
            if (section == QuickBarSection.COMMAND || section == QuickBarSection.ENTITY) {
                Box(
                    Modifier.width(SEPARATOR_WIDTH).heightIn(min = Css.Space.S8)
                        .background(colors.colorBorderNeutralQuiet),
                )
            }
            val chosen = section == selected
            val shape = RoundedCornerShape(Css.Space.S2)
            Row(
                modifier = Modifier
                    .clip(shape)
                    .then(
                        if (chosen) {
                            Modifier.background(
                                resolveVariable("ha-color-fill-primary-normal-hover", isSystemInDarkTheme())
                                    ?: colors.colorFillPrimaryQuietResting,
                            )
                        } else {
                            Modifier.border(SECTION_BORDER, colors.colorBorderNeutralNormal, shape)
                        },
                    )
                    .clickable(role = Role.Checkbox) { onToggle(section) }
                    .padding(horizontal = Css.Space.S4, vertical = Css.Space.S2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Css.Space.S2),
            ) {
                if (chosen) DashboardIcon("mdi:check", colors.colorTextPrimary, Modifier.size(HASize.L))
                Text(items.sectionTitle(section), style = Css.Text.Body, color = colors.colorTextPrimary)
            }
        }
    }
}

/**
 * Port of `_renderRow`: the item's icon (an entity's state badge, an integration's icon or logo, a page's icon on its
 * colour), its name over its place or kind, and its integration's name at the end.
 *
 * Upstream: part of `ha-quick-bar`, `ha-combo-box-item` (src/dialogs/quick-bar/ha-quick-bar.ts;
 * src/components/ha-combo-box-item.ts)
 */
@Composable
private fun ItemRow(item: QuickBarItem, hass: HassSnapshot, running: Boolean, onClick: () -> Unit) {
    val colors = LocalHAColorScheme.current
    val runningColor = resolveVariable("ha-color-fill-primary-normal-resting", isSystemInDarkTheme())
        ?: colors.colorFillPrimaryNormalResting
    Row(
        modifier = Modifier.fillMaxWidth()
            .then(if (running) Modifier.background(runningColor) else Modifier)
            .heightIn(min = if (item.secondary != null) TWO_LINE_HEIGHT else ONE_LINE_HEIGHT)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Css.Space.S4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(ICON_BOX), contentAlignment = Alignment.Center) { ItemIcon(item.icon, hass) }
        Column(Modifier.weight(1f).padding(start = Css.Space.S4, top = Css.Space.S2, bottom = Css.Space.S2)) {
            Text(
                item.primary,
                style = Css.Text.Body.copy(textAlign = TextAlign.Start),
                color = colors.colorTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.secondary?.let {
                Text(
                    it,
                    style = Css.Text.Body.copy(textAlign = TextAlign.Start),
                    color = colors.colorTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        item.domainName?.let {
            Text(
                it,
                style = Css.Text.Body.copy(textAlign = TextAlign.End),
                color = colors.colorTextSecondary,
                maxLines = 1,
                modifier = Modifier.padding(start = Css.Space.S2),
            )
        }
        // A small `ha-spinner` at the row's end while its command runs
        if (running) CircularProgressIndicator(Modifier.size(SPINNER_SIZE))
    }
}

/**
 * An item's icon: a state badge or integration logo as the target picker draws them, a plain icon, or a white icon
 * on a circle of the page's colour.
 *
 * Upstream: part of `ha-quick-bar` (src/dialogs/quick-bar/ha-quick-bar.ts)
 */
@Composable
private fun ItemIcon(icon: QuickBarIcon, hass: HassSnapshot) {
    val tint = LocalHAColorScheme.current.colorTextSecondary
    val size = Modifier.size(HASize.X2L)
    when (icon) {
        is QuickBarIcon.Entity -> TargetIconView(TargetIcon.Entity(icon.entityId), hass, size, tint)
        is QuickBarIcon.Brand -> TargetIconView(TargetIcon.Brand(icon.domain), hass, size, tint)
        is QuickBarIcon.Plain -> Glyph(icon.icon, tint, Modifier.size(HASize.X2L))
        is QuickBarIcon.Colored -> Box(
            Modifier.size(ICON_BOX).background(parseCssColor(icon.color) ?: tint, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Glyph(icon.icon, Color.White, Modifier.size(HASize.X2L), icon.viewBoxTop)
        }
    }
}

/**
 * An `mdi:` icon, or a logo given as its SVG path on MDI's 24 × 24 viewport, starting [viewBoxTop] units down.
 *
 * Upstream: `ha-icon`, `ha-svg-icon` (src/components/ha-icon.ts; src/components/ha-svg-icon.ts)
 */
@Composable
private fun Glyph(icon: String, tint: Color, modifier: Modifier, viewBoxTop: Int = 0) {
    if (!isSvgPath(icon)) {
        DashboardIcon(icon, tint, modifier)
        return
    }
    val vector = remember(icon, viewBoxTop) {
        ImageVector.Builder(
            defaultWidth = VIEWPORT.cssPx,
            defaultHeight = VIEWPORT.cssPx,
            viewportWidth = VIEWPORT.toFloat(),
            viewportHeight = VIEWPORT.toFloat(),
        ).addGroup(translationY = -viewBoxTop.toFloat())
            .addPath(PathParser().parsePathString(icon).toNodes(), fill = SolidColor(Color.Black))
            .clearGroup()
            .build()
    }
    Image(vector, contentDescription = null, colorFilter = ColorFilter.tint(tint), modifier = modifier)
}

/**
 * Asks before restarting or stopping the server, with the frontend's texts.
 *
 * Upstream: `showConfirmationDialog` as `ha-quick-bar` calls it (src/dialogs/quick-bar/ha-quick-bar.ts;
 * src/dialogs/generic/show-dialog-box.ts)
 */
@Composable
private fun ConfirmServerControl(
    action: QuickBarAction.CallService,
    localize: Localize,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val keys = "ui.dialogs.restart.${action.service}"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(localize("$keys.confirm_title"), style = Css.Text.DialogTitle) },
        text = { Text(localize("$keys.confirm_description"), style = Css.Text.Body) },
        confirmButton = { HAPlainButton(localize("$keys.confirm_action"), onConfirm) },
        dismissButton = { HAPlainButton(localize(CANCEL), onDismiss) },
    )
}

private const val TITLE = "ui.dialogs.quick-bar.title"
private const val CLEAR = "ui.common.clear"
private const val CANCEL = "ui.common.cancel"
private const val VIEWPORT = 24
private val SEPARATOR_WIDTH = 2.cssPx
private val SECTION_BORDER = 1.cssPx
private val ONE_LINE_HEIGHT = 48.cssPx
private val TWO_LINE_HEIGHT = 64.cssPx
private val ICON_BOX = 32.cssPx

/** `ha-spinner`'s small size. */
private val SPINNER_SIZE = 28.cssPx
