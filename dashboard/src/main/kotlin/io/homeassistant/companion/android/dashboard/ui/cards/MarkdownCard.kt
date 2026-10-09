package io.homeassistant.companion.android.dashboard.ui.cards

import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HAFontSize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.derive.MarkdownModel
import io.homeassistant.companion.android.dashboard.derive.markdownModel
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig

/**
 * A markdown card: the server-rendered template as markdown, in a card unless `text_only`. Nothing is shown until
 * the template has rendered, as upstream, but its room is kept so that the cards below don't move when it is.
 */
@Composable
internal fun MarkdownCard(card: CardConfig, hass: State<HassSnapshot?>, modifier: Modifier = Modifier) {
    val markdown by remember(card) { derivedStateOf { hass.value?.markdownModel(card) } }
    val model = markdown ?: return
    MarkdownCardContent(model, modifier)
}

@Composable
private fun MarkdownCardContent(model: MarkdownModel, modifier: Modifier = Modifier) {
    val colors = LocalHAColorScheme.current
    val body = HATextStyle.Body.copy(textAlign = TextAlign.Start, color = colors.colorTextPrimary)
    val heading = body.copy(fontWeight = FontWeight.Bold)
    val content = @Composable {
        Column(modifier = Modifier.fillMaxWidth().padding(if (model.textOnly) HADimens.SPACE0 else HADimens.SPACE4)) {
            model.error?.let { Text(it, style = HATextStyle.BodyMedium, color = colors.colorOnDangerNormal) }
            model.title?.let {
                Text(
                    it,
                    style = HATextStyle.HeadlineMedium.copy(textAlign = TextAlign.Start),
                    color = colors.colorTextPrimary,
                )
            }
            (model.content ?: model.placeholder)?.let { text ->
                Markdown(
                    content = text,
                    colors = markdownColor(text = colors.colorTextPrimary),
                    typography = markdownTypography(
                        text = body,
                        paragraph = body,
                        ordered = body,
                        bullet = body,
                        list = body,
                        table = body,
                        quote = body,
                        h1 = heading.copy(fontSize = HAFontSize.X2L, lineHeight = HAFontSize.X3L),
                        h2 = heading.copy(fontSize = HAFontSize.XL, lineHeight = HAFontSize.X2L),
                        h3 = heading.copy(fontSize = HAFontSize.L, lineHeight = HAFontSize.X2L),
                        h4 = heading.copy(fontSize = HAFontSize.L, lineHeight = HAFontSize.X2L),
                        h5 = heading.copy(fontSize = HAFontSize.M, lineHeight = HAFontSize.XL),
                        h6 = heading.copy(fontSize = HAFontSize.M, lineHeight = HAFontSize.XL),
                        textLink = HATextStyle.Link,
                    ),
                    modifier = Modifier.fillMaxWidth().alpha(if (model.content == null) 0f else 1f),
                )
            }
        }
    }
    if (model.textOnly) {
        Column(modifier = modifier) { content() }
    } else {
        DashboardCardSurface(modifier = modifier) { content() }
    }
}

@Preview
@Composable
private fun MarkdownCardPreview() {
    HAThemeForPreview(modifier = Modifier.padding(HADimens.SPACE4)) {
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
