package io.homeassistant.companion.android.dashboard.keyboard

import io.homeassistant.companion.android.dashboard.quickbar.QuickBarSection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class KeyboardShortcutsTest {
    private val user = ShortcutState(enabled = true, isAdmin = false, quickBarOpen = false, conversationLoaded = true)
    private val admin = user.copy(isAdmin = true)

    private fun press(key: String, code: String? = null, vararg modifiers: ModifierKey) = KeyPress(key, code, modifiers.toSet())

    @Test
    fun `Given a letter when pressed then it runs its shortcut`() {
        assertEquals(ShortcutCommand.ShowQuickBar(QuickBarSection.ENTITY), keyboardShortcut(press("e", "KeyE"), user))
        assertEquals(ShortcutCommand.ShowAssist, keyboardShortcut(press("a", "KeyA"), user))
        assertEquals(ShortcutCommand.CreateMyLink, keyboardShortcut(press("m", "KeyM"), user))
        // Caps lock types a capital, still the same key
        assertEquals(ShortcutCommand.ShowQuickBar(QuickBarSection.ENTITY), keyboardShortcut(press("E", "KeyE"), user))
    }

    @Test
    fun `Given a non-latin layout when its physical key is pressed then the latin key's shortcut runs`() {
        assertEquals(ShortcutCommand.ShowQuickBar(QuickBarSection.ENTITY), keyboardShortcut(press("у", "KeyE"), user))
        assertEquals(ShortcutCommand.ToggleQuickBar, keyboardShortcut(press("л", "KeyK", ModifierKey.CONTROL), user))
    }

    @Test
    fun `Given commands and devices when pressed then they open for admins only`() {
        assertEquals(ShortcutCommand.ShowQuickBar(QuickBarSection.COMMAND), keyboardShortcut(press("c", "KeyC"), admin))
        assertEquals(ShortcutCommand.ShowQuickBar(QuickBarSection.DEVICE), keyboardShortcut(press("d", "KeyD"), admin))
        assertNull(keyboardShortcut(press("c", "KeyC"), user))
        assertNull(keyboardShortcut(press("d", "KeyD"), user))
    }

    @Test
    fun `Given modifiers when pressed then only the exact combination matches`() {
        assertEquals(ShortcutCommand.ToggleQuickBar, keyboardShortcut(press("k", "KeyK", ModifierKey.CONTROL), user))
        assertNull(keyboardShortcut(press("k", "KeyK"), user))
        assertNull(keyboardShortcut(press("e", "KeyE", ModifierKey.CONTROL), user))
        assertNull(keyboardShortcut(press("E", "KeyE", ModifierKey.SHIFT), user))
        assertNull(keyboardShortcut(press("k", "KeyK", ModifierKey.META), user))
        assertEquals(ShortcutCommand.ShowShortcuts, keyboardShortcut(press("?", null, ModifierKey.SHIFT), user))
        assertNull(keyboardShortcut(press("?", null, ModifierKey.SHIFT, ModifierKey.CONTROL), user))
    }

    @Test
    fun `Given the quick bar is open when pressing a shortcut then only Ctrl+K works, closing it`() {
        val open = user.copy(quickBarOpen = true)
        assertNull(keyboardShortcut(press("e", "KeyE"), open))
        assertNull(keyboardShortcut(press("?", null, ModifierKey.SHIFT), open))
        assertEquals(ShortcutCommand.ToggleQuickBar, keyboardShortcut(press("k", "KeyK", ModifierKey.CONTROL), open))
        assertEquals(ShortcutCommand.CreateMyLink, keyboardShortcut(press("m", "KeyM"), open))
    }

    @Test
    fun `Given shortcuts are off or Assist isn't loaded when pressing then nothing runs`() {
        val off = admin.copy(enabled = false)
        listOf(press("e", "KeyE"), press("c", "KeyC"), press("k", "KeyK", ModifierKey.CONTROL), press("m", "KeyM"))
            .forEach { assertNull(keyboardShortcut(it, off)) }
        assertNull(keyboardShortcut(press("a", "KeyA"), user.copy(conversationLoaded = false)))
    }

    @Test
    fun `Given a key held down when it repeats then it doesn't run again`() {
        assertNull(keyboardShortcut(KeyPress("e", "KeyE", repeat = true), user))
    }

    @Test
    fun `Given another key when pressed then it is no shortcut`() {
        assertNull(keyboardShortcut(press("x", "KeyX"), admin))
    }
}
