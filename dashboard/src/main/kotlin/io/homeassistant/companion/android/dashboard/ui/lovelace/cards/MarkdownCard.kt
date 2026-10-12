package io.homeassistant.companion.android.dashboard.ui.lovelace.cards

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.derive.MarkdownModel
import io.homeassistant.companion.android.dashboard.derive.markdownModel
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.ui.components.DashboardCardSurface
import io.homeassistant.companion.android.dashboard.ui.lovelace.common.LocalCardTextAlign
import io.homeassistant.companion.android.dashboard.ui.lovelace.common.alignedMarkdownComponents
import io.homeassistant.companion.android.dashboard.ui.theme.CardHeaderPadding
import io.homeassistant.companion.android.dashboard.ui.theme.Css

/**
 * A markdown card: the server-rendered template as markdown, in a card unless `text_only`. Nothing is shown until
 * the template has rendered, as upstream, but its room is kept so that the cards below don't move when it is.
 *
 * Upstream: `hui-markdown-card` (src/panels/lovelace/cards/hui-markdown-card.ts)
 */
@Composable
internal fun MarkdownCard(card: CardConfig, hass: State<HassSnapshot?>, modifier: Modifier = Modifier) {
    val markdown by remember(card) { derivedStateOf { hass.value?.markdownModel(card) } }
    val model = markdown ?: return
    MarkdownCardContent(model, modifier)
}

/**
 * The markdown card's title and text, in a card unless `text_only`; an error in place of the text when the template
 * failed.
 *
 * Upstream: part of `hui-markdown-card` (src/panels/lovelace/cards/hui-markdown-card.ts)
 */
@Composable
private fun MarkdownCardContent(model: MarkdownModel, modifier: Modifier = Modifier) {
    val colors = LocalHAColorScheme.current
    // `ha-markdown`'s padding: 2px 4px text-only, none above under a header, else 16px
    val padding = when {
        model.textOnly -> PaddingValues(horizontal = Css.Space.S1, vertical = TEXT_ONLY_VERTICAL_PADDING)
        model.title != null -> PaddingValues(start = Css.Space.S4, end = Css.Space.S4, bottom = Css.Space.S4)
        else -> PaddingValues(Css.Space.S4)
    }
    val content = @Composable {
        Column(modifier = Modifier.fillMaxWidth()) {
            model.title?.let {
                Text(it, style = Css.Text.CardHeader, modifier = Modifier.padding(CardHeaderPadding))
            }
            Column(modifier = Modifier.fillMaxWidth().padding(padding)) {
                model.error?.let { Text(it, style = Css.Text.Body, color = colors.colorOnDangerNormal) }
                (model.content ?: model.placeholder)?.let { text ->
                    MarkdownText(text, Modifier.fillMaxWidth().alpha(if (model.content == null) 0f else 1f))
                }
            }
        }
    }
    if (model.textOnly) {
        Column(modifier = modifier) { content() }
    } else {
        DashboardCardSurface(modifier = modifier) { content() }
    }
}

/**
 * Markdown in the theme's text styles, as `ha-markdown` renders it: in [color] (the primary text colour by default)
 * at [fontSize] (the body's by default), as the element around it sets them.
 *
 * Upstream: `ha-markdown` (src/components/ha-markdown.ts)
 */
@Composable
internal fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
) {
    val colors = LocalHAColorScheme.current
    val align = LocalCardTextAlign.current
    val textColor = color.takeOrElse { colors.colorTextPrimary }
    val body = Css.Text.Body.copy(textAlign = align, color = textColor)
        .let { if (fontSize.isSpecified) it.copy(fontSize = fontSize) else it }
    val heading = body.copy(fontWeight = FontWeight.Bold)
    Markdown(
        content = text,
        colors = markdownColor(text = textColor),
        typography = markdownTypography(
            text = body,
            paragraph = body,
            ordered = body,
            bullet = body,
            list = body,
            table = body,
            quote = body,
            h1 = heading.copy(fontSize = Css.FontSize.X2l, lineHeight = Css.FontSize.X3l),
            h2 = heading.copy(fontSize = Css.FontSize.Xl, lineHeight = Css.FontSize.X2l),
            h3 = heading.copy(fontSize = Css.FontSize.L, lineHeight = Css.FontSize.X2l),
            h4 = heading.copy(fontSize = Css.FontSize.L, lineHeight = Css.FontSize.X2l),
            h5 = heading.copy(fontSize = Css.FontSize.M, lineHeight = Css.FontSize.Xl),
            h6 = heading.copy(fontSize = Css.FontSize.M, lineHeight = Css.FontSize.Xl),
            // `a { color: var(--markdown-link-color, var(--primary-color)) }`: the text's own size and weight
            textLink = TextLinkStyles(SpanStyle(color = colors.colorTextLink)),
        ),
        components = remember(align) { alignedMarkdownComponents(align) },
        modifier = modifier,
    )
}

@Preview
@Composable
private fun MarkdownCardPreview() {
    HAThemeForPreview(modifier = Modifier.padding(Css.Space.S4)) {
        MarkdownCardContent(
            MarkdownModel(
                title = "Daily briefing",
                textOnly = false,
                content = "## Welcome home\n" +
                    "Comfortable **inside**, with three lights on.\n\n" +
                    "- Kitchen: 21 °C\n" +
                    "- Patio: Clear",
                error = null,
            ),
        )
    }
}

/** `.text-only ha-markdown`'s 2px above and below. */
private val TEXT_ONLY_VERTICAL_PADDING = 2.dp
