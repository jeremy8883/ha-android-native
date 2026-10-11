package io.homeassistant.companion.android.dashboard.ui.cards

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.mikepenz.markdown.compose.components.CurrentComponentsBridge
import com.mikepenz.markdown.compose.components.MarkdownComponent
import com.mikepenz.markdown.compose.components.MarkdownComponents
import com.mikepenz.markdown.compose.components.markdownComponents

/** `--card-text-align`: how a card's text is aligned where its container sets it (a centred view header). */
internal val LocalCardTextAlign = compositionLocalOf { TextAlign.Start }

/**
 * The markdown renderer's components with headings and paragraphs placed as [align] says: the renderer sizes them to
 * their text, so a centred text is also centred as a block.
 */
internal fun alignedMarkdownComponents(align: TextAlign): MarkdownComponents {
    if (align == TextAlign.Start) return markdownComponents()
    val alignment = if (align == TextAlign.Center) Alignment.TopCenter else Alignment.TopEnd
    fun aligned(component: MarkdownComponent): MarkdownComponent = { model ->
        Box(Modifier.fillMaxWidth(), contentAlignment = alignment) { component(model) }
    }
    return markdownComponents(
        heading1 = aligned(CurrentComponentsBridge.heading1),
        heading2 = aligned(CurrentComponentsBridge.heading2),
        heading3 = aligned(CurrentComponentsBridge.heading3),
        heading4 = aligned(CurrentComponentsBridge.heading4),
        heading5 = aligned(CurrentComponentsBridge.heading5),
        heading6 = aligned(CurrentComponentsBridge.heading6),
        paragraph = aligned(CurrentComponentsBridge.paragraph),
    )
}
