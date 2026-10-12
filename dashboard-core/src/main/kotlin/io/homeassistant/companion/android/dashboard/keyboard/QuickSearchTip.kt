package io.homeassistant.companion.android.dashboard.keyboard

import io.homeassistant.companion.android.dashboard.entity.Localize

// The quick bar's footer tip. Port of the `ha-tip` `ha-quick-bar` renders in its footer
// (frontend@20260624.6 src/dialogs/quick-bar/ha-quick-bar.ts; src/components/ha-tip.ts).

/**
 * The tip saying Ctrl+K opens the quick bar: [prefix] ("Tip!"), then [before], the [link] ("keyboard shortcut")
 * opening the shortcuts dialog, and [after].
 */
data class QuickSearchTip(val prefix: String, val before: String, val link: String, val after: String)

/** The quick bar's tip in [localize]'s words, with "Ctrl" as the modifier, as off Apple's devices. */
fun quickSearchTip(localize: Localize): QuickSearchTip {
    val text = localize(QUICK_SEARCH_TIP, mapOf("keyboard_shortcut" to LINK_MARKER, "modifier" to CTRL))
    val parts = text.split(LINK_MARKER, limit = 2)
    return QuickSearchTip(
        prefix = localize(TIP_PREFIX),
        before = parts.first(),
        link = localize(TIP_LINK),
        after = parts.getOrElse(1) { "" },
    )
}

private const val QUICK_SEARCH_TIP = "ui.tips.key_shortcut_quick_search"
private const val TIP_LINK = "ui.tips.keyboard_shortcut"
private const val TIP_PREFIX = "ui.panel.config.tips.tip"
private const val CTRL = "Ctrl"
private const val LINK_MARKER = "\u0000"
