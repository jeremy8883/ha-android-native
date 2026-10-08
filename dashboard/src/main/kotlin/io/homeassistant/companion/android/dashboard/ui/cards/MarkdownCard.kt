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
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.dashboard.derive.markdownModel
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.model.CardConfig

/**
 * A markdown card: the server-rendered template as markdown, in a card unless `text_only`. Nothing is shown until
 * the template has rendered, as upstream.
 */
@Composable
internal fun MarkdownCard(card: CardConfig, hass: State<HassSnapshot?>, modifier: Modifier = Modifier) {
    val markdown by remember(card) { derivedStateOf { hass.value?.markdownModel(card) } }
    val model = markdown ?: return
    val colors = LocalHAColorScheme.current
    val content = @Composable {
        Column(modifier = Modifier.fillMaxWidth().padding(if (model.textOnly) HADimens.SPACE0 else HADimens.SPACE4)) {
            model.error?.let { Text(it, style = HATextStyle.BodyMedium, color = colors.colorOnDangerNormal) }
            model.title?.let { Text(it, style = HATextStyle.HeadlineMedium, color = colors.colorTextPrimary) }
            model.content?.let { text ->
                Markdown(
                    content = text,
                    colors = markdownColor(text = colors.colorTextPrimary),
                    modifier = Modifier.fillMaxWidth(),
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
