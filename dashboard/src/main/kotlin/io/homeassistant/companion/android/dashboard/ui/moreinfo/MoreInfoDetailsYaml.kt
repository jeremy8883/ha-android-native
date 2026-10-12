package io.homeassistant.companion.android.dashboard.ui.moreinfo

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.derive.moreInfoDetailsYaml
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.entity.Localize
import io.homeassistant.companion.android.dashboard.model.YamlToken
import io.homeassistant.companion.android.dashboard.model.YamlTokenKind
import io.homeassistant.companion.android.dashboard.model.yamlHighlight
import io.homeassistant.companion.android.dashboard.ui.components.DashboardIcon
import io.homeassistant.companion.android.dashboard.ui.theme.Css
import io.homeassistant.companion.android.dashboard.ui.theme.cssFontPx
import io.homeassistant.companion.android.dashboard.ui.theme.cssPx
import io.homeassistant.companion.android.dashboard.ui.theme.resolveVariable
import kotlinx.coroutines.launch

// Port of the details' YAML mode (frontend@20260624.6 src/dialogs/more-info/ha-more-info-details.ts): a read-only
// `ha-yaml-editor`, which is `ha-code-editor` (src/components/ha-code-editor.ts, src/resources/codemirror.ts).

/**
 * Port of the details' YAML mode: the state and attributes as the read-only `ha-yaml-editor` shows them, coloured,
 * with line numbers, its toolbar's copy button, and selectable.
 *
 * Upstream: part of `ha-more-info-details`, `ha-yaml-editor`, `ha-code-editor`
 * (src/dialogs/more-info/ha-more-info-details.ts; src/components/ha-yaml-editor.ts;
 * src/components/ha-code-editor.ts)
 */
@Composable
internal fun ColumnScope.MoreInfoDetailsYamlView(entityId: String, hass: HassSnapshot) {
    val yaml = remember(hass.states[entityId], hass.localize) { hass.moreInfoDetailsYaml(entityId) } ?: return
    val dark = isSystemInDarkTheme()
    val colors = LocalHAColorScheme.current
    val background = resolveVariable(CODE_BACKGROUND, dark) ?: colors.colorSurfaceLow
    val gutter = resolveVariable(GUTTER_BACKGROUND, dark) ?: Color.Transparent
    val lines = remember(yaml) { yamlHighlight(yaml) }
    val code = remember(lines, dark) { highlighted(lines, dark) }
    // UPSTREAM-DIFF: the code editor's fold markers and its undo, redo, find and replace and full-screen buttons
    // are left out: the view is read-only, and only its colours and copy button are ported.
    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(Css.Space.S6)) {
        Column(Modifier.fillMaxWidth().background(background, EDITOR_SHAPE)) {
            CodeEditorToolbar(yaml, gutter, hass.localize)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                LineNumbers(lines.size, gutter)
                CodeLines(code)
            }
        }
    }
}

/**
 * The code editor's toolbar above the code: its copy button, which copies the YAML and confirms it.
 *
 * Upstream: `ha-icon-button-toolbar`, part of `ha-code-editor` (src/components/ha-icon-button-toolbar.ts;
 * src/components/ha-code-editor.ts)
 */
@Composable
private fun CodeEditorToolbar(yaml: String, background: Color, localize: Localize) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val label = localize("ui.components.yaml-editor.copy_to_clipboard")
    Row(
        Modifier
            .fillMaxWidth()
            .height(TOOLBAR_HEIGHT)
            .background(background, TOOLBAR_SHAPE)
            .padding(end = TOOLBAR_END_PADDING),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = {
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, yaml)))
                    Toast.makeText(context, localize("ui.common.copied_clipboard"), Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.size(TOOLBAR_BUTTON_SIZE).semantics { contentDescription = label },
        ) {
            DashboardIcon(COPY_ICON, LocalHAColorScheme.current.colorTextSecondary, Modifier.size(TOOLBAR_ICON_SIZE))
        }
    }
}

/**
 * The gutter's line numbers, right-aligned, against the code's lines.
 *
 * Upstream: part of `ha-code-editor`, its line numbers gutter (src/resources/codemirror.ts)
 */
@Composable
private fun LineNumbers(count: Int, background: Color) {
    val colors = LocalHAColorScheme.current
    Box(Modifier.fillMaxHeight().background(background)) {
        // One text like the code's, so the numbers keep to its lines
        Text(
            (1..count).joinToString("\n"),
            style = CODE_STYLE.copy(textAlign = TextAlign.End),
            color = colors.colorTextSecondary,
            modifier = Modifier
                .padding(top = CONTENT_PADDING + BORDER_WIDTH, bottom = CONTENT_PADDING)
                .padding(end = GUTTERS_END_PADDING + BORDER_WIDTH)
                .padding(start = GUTTER_START_PADDING, end = GUTTER_END_PADDING)
                .widthIn(min = GUTTER_MIN_WIDTH),
        )
        // `.cm-gutters { border-right: 1px solid var(--secondary-text-color) }`
        Box(
            Modifier.align(Alignment.TopEnd).fillMaxHeight().width(BORDER_WIDTH)
                .background(colors.colorTextSecondary),
        )
    }
}

/**
 * The code, under the toolbar's line, scrolling sideways, selectable to copy part of it.
 *
 * Upstream: part of `ha-code-editor`, its content (src/resources/codemirror.ts)
 */
@Composable
private fun RowScope.CodeLines(code: AnnotatedString) {
    val colors = LocalHAColorScheme.current
    Column(Modifier.weight(1f)) {
        // `:host(.hasToolbar) .cm-content { border-top: 1px solid var(--secondary-text-color) }`
        Box(Modifier.fillMaxWidth().height(BORDER_WIDTH).background(colors.colorTextSecondary))
        SelectionContainer(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Text(
                code,
                style = CODE_STYLE,
                color = colors.colorTextPrimary,
                softWrap = false,
                modifier = Modifier
                    .padding(vertical = CONTENT_PADDING)
                    .padding(start = LINE_START_PADDING, end = LINE_END_PADDING),
            )
        }
    }
}

/** The lines of [lines] as one text, each token in its colour (the text's for plain ones). */
private fun highlighted(lines: List<List<YamlToken>>, dark: Boolean): AnnotatedString = buildAnnotatedString {
    lines.forEachIndexed { index, tokens ->
        if (index > 0) append('\n')
        tokens.forEach { token ->
            val color = tokenColor(token.kind, dark)
            if (color == null) append(token.text) else withStyle(SpanStyle(color = color)) { append(token.text) }
        }
    }
}

private fun tokenColor(kind: YamlTokenKind, dark: Boolean): Color? =
    (if (dark) kind.darkArgb else kind.lightArgb)?.let { Color(it) }

private const val CODE_BACKGROUND = "card-background-color"
private const val GUTTER_BACKGROUND = "secondary-background-color"
private const val COPY_ICON = "mdi:content-copy"

/** CodeMirror's `.cm-scroller { line-height: 1.4 }`. */
private const val CODE_LINE_HEIGHT = 1.4f
private val CODE_FONT_SIZE = 14.cssFontPx
private val CODE_STYLE = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = CODE_FONT_SIZE,
    lineHeight = CODE_FONT_SIZE * CODE_LINE_HEIGHT,
)

/** `--code-editor-toolbar-height`, and the buttons and icons it sizes (`- 4px`, `- 10px`). */
private val TOOLBAR_HEIGHT = 28.cssPx
private val TOOLBAR_BUTTON_SIZE = 24.cssPx
private val TOOLBAR_ICON_SIZE = 18.cssPx
private val TOOLBAR_END_PADDING = 4.cssPx

/** `border-radius: var(--mdc-shape-small, 4px) var(--mdc-shape-small, 4px) 0px 0px`. */
private val EDITOR_SHAPE = RoundedCornerShape(topStart = Css.Radius.Sm, topEnd = Css.Radius.Sm)
private val TOOLBAR_SHAPE = EDITOR_SHAPE

/** `.cm-content { padding-top: 16px; padding-bottom: 16px }`. */
private val CONTENT_PADDING = 16.cssPx
private val BORDER_WIDTH = 1.cssPx

/** `.cm-line { padding: 0 2px 0 6px }`. */
private val LINE_START_PADDING = 6.cssPx
private val LINE_END_PADDING = 2.cssPx

/** `.cm-gutterElement { padding: 0 3px 0 5px; min-width: 20px }`. */
private val GUTTER_START_PADDING = 5.cssPx
private val GUTTER_END_PADDING = 3.cssPx
private val GUTTER_MIN_WIDTH = 20.cssPx

/** `.cm-gutters { padding-right: 1px }`, before its border. */
private val GUTTERS_END_PADDING = 1.cssPx
