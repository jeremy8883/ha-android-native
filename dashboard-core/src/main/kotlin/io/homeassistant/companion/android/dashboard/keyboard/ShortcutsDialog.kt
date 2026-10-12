package io.homeassistant.companion.android.dashboard.keyboard

import io.homeassistant.companion.android.dashboard.entity.Localize

// The keyboard shortcuts dialog's content. Port of `_SHORTCUTS` and `_renderShortcut` of `dialog-shortcuts`
// (frontend@20260624.6 src/dialogs/shortcuts/dialog-shortcuts.ts).

/** A section of the shortcuts dialog: its [title] over its [items]. */
data class ShortcutsSection(val title: String, val items: List<ShortcutsItem>)

/** A line of a section. */
sealed interface ShortcutsItem {
    /** A paragraph, saying where the shortcuts after it work. */
    data class Text(val text: String) : ShortcutsItem

    /** The [keys] to press together, each drawn as a key, then what they do ([description]). */
    data class Shortcut(val keys: List<String>, val description: String) : ShortcutsItem
}

/** The shortcuts dialog's sections, in [localize]'s words; "Ctrl" stands for `CTRL_CMD`, as off Apple's devices. */
fun shortcutsSections(localize: Localize): List<ShortcutsSection> {
    fun key(name: String) = localize("$SHORTCUTS.$name")
    val ctrl = key("keys.ctrl")
    fun shortcut(keys: List<String>, description: String) = ShortcutsItem.Shortcut(keys, key(description))
    return listOf(
        ShortcutsSection(
            key("searching.title"),
            listOf(
                ShortcutsItem.Text(key("searching.on_any_page")),
                shortcut(listOf(ctrl, "K"), "searching.search"),
                shortcut(listOf("C"), "searching.search_command"),
                shortcut(listOf("E"), "searching.search_entities"),
                shortcut(listOf("D"), "searching.search_devices"),
                ShortcutsItem.Text(key("searching.on_pages_with_tables")),
                shortcut(listOf(ctrl, "F"), "searching.search_in_table"),
            ),
        ),
        ShortcutsSection(key("assist.title"), listOf(shortcut(listOf("A"), "assist.open_assist"))),
        ShortcutsSection(
            key("automation_script.title"),
            listOf(
                shortcut(listOf(ctrl, "C"), "automation_script.copy"),
                shortcut(listOf(ctrl, "X"), "automation_script.cut"),
                shortcut(listOf(ctrl, key("keys.del")), "automation_script.delete"),
                shortcut(listOf(ctrl, "V"), "automation_script.paste"),
                shortcut(listOf(ctrl, "S"), "automation_script.save"),
                shortcut(listOf(ctrl, "Z"), "automation_script.undo"),
                shortcut(listOf(ctrl, "Y"), "automation_script.redo"),
            ),
        ),
        ShortcutsSection(
            key("charts.title"),
            listOf(
                shortcut(listOf(ctrl, key("shortcuts.drag")), "charts.drag_to_zoom"),
                shortcut(listOf(ctrl, key("shortcuts.scroll_wheel")), "charts.scroll_to_zoom"),
                shortcut(listOf(key("shortcuts.double_click")), "charts.double_click"),
                shortcut(listOf(ctrl, key("shortcuts.click")), "charts.click_legend_solo"),
            ),
        ),
        ShortcutsSection(
            key("other.title"),
            listOf(
                shortcut(listOf("M"), "other.my_link"),
                shortcut(listOf("Shift", "/"), "other.show_shortcuts"),
            ),
        ),
    )
}

/** The dialog's title. */
const val SHORTCUTS_TITLE = "ui.dialogs.shortcuts.title"

/** The footer's hint, with `{user_profile}` linking to where shortcuts are turned on. */
const val SHORTCUTS_HINT = "ui.dialogs.shortcuts.enable_shortcuts_hint"

/** The link's text in [SHORTCUTS_HINT]. */
const val SHORTCUTS_HINT_LINK = "ui.dialogs.shortcuts.enable_shortcuts_hint_user_profile"

private const val SHORTCUTS = "ui.dialogs.shortcuts"
