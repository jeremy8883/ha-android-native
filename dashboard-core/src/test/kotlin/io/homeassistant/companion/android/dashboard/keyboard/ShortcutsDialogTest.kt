package io.homeassistant.companion.android.dashboard.keyboard

import io.homeassistant.companion.android.dashboard.entity.JsonTranslations
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ShortcutsDialogTest {
    @Test
    fun `Given the English translations when listing the shortcuts then they are the frontend dialog's`() {
        val sections = shortcutsSections(JsonTranslations.bundled()!!)
        assertEquals(
            listOf("Searching", "Assist", "Automations / Scripts", "Charts", "Other"),
            sections.map { it.title },
        )
        assertEquals(
            listOf(
                ShortcutsItem.Text("On any page"),
                ShortcutsItem.Shortcut(listOf("Ctrl", "K"), "Quick search"),
                ShortcutsItem.Shortcut(listOf("C"), "search commands"),
                ShortcutsItem.Shortcut(listOf("E"), "search entities"),
                ShortcutsItem.Shortcut(listOf("D"), "search devices"),
                ShortcutsItem.Text("On pages with tables"),
                ShortcutsItem.Shortcut(listOf("Ctrl", "F"), "to search in tables"),
            ),
            sections.first().items,
        )
        assertEquals(
            ShortcutsItem.Shortcut(listOf("Ctrl", "Del"), "to delete the selected item"),
            sections[2].items[2],
        )
        assertEquals(
            ShortcutsItem.Shortcut(listOf("Shift", "/"), "show keyboard shortcuts"),
            sections.last().items.last(),
        )
    }
}
