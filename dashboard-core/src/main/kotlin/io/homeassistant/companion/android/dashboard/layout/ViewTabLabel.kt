package io.homeassistant.companion.android.dashboard.layout

/**
 * What a dashboard's view tab shows. Port of the tab content of `hui-root` (frontend@20260624.6
 * src/panels/lovelace/hui-root.ts).
 */
sealed interface ViewTabLabel {
    /** The icon alone; [title] (if any) names it for accessibility. */
    data class IconOnly(val icon: String, val title: String?) : ViewTabLabel

    /** The icon then the title (`show_icon_and_title`). */
    data class IconAndTitle(val icon: String, val title: String) : ViewTabLabel

    /** The title, or "Unnamed view" when [title] is `null`. */
    data class TitleOnly(val title: String?) : ViewTabLabel
}

/**
 * The label of a view's tab: its icon when it has one (with the title too with [showIconAndTitle] and a title),
 * otherwise its title.
 */
fun viewTabLabel(title: String?, icon: String?, showIconAndTitle: Boolean): ViewTabLabel {
    val shownTitle = title?.ifEmpty { null }
    val shownIcon = icon?.ifEmpty { null }
    return when {
        shownIcon != null && shownTitle != null && showIconAndTitle -> ViewTabLabel.IconAndTitle(shownIcon, shownTitle)
        shownIcon != null -> ViewTabLabel.IconOnly(shownIcon, shownTitle)
        else -> ViewTabLabel.TitleOnly(shownTitle)
    }
}
