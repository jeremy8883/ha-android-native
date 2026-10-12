package io.homeassistant.companion.android.dashboard.keyboard

import io.homeassistant.companion.android.dashboard.quickbar.QuickBarSection

// The frontend's global keyboard shortcuts. Port of `_registerShortcut` and its handlers in `quick-bar-mixin`
// (frontend@20260624.6 src/state/quick-bar-mixin.ts), matched as `tinykeys` (4.0.0, `matchKeybindingPress`) does
// through `ShortcutManager` (src/common/keyboard/shortcuts.ts).

/** A modifier key, named as `KeyboardEvent.getModifierState` names it (`KEYBINDING_MODIFIER_KEYS`). */
enum class ModifierKey(val keyName: String) {
    SHIFT("Shift"),
    META("Meta"),
    ALT("Alt"),
    CONTROL("Control"),
}

/**
 * A key pressed on a hardware keyboard: [key] what it types (`KeyboardEvent.key`: "e", "?", "E" with caps lock),
 * [code] the physical key (`KeyboardEvent.code`: "KeyE", whatever the layout types), the [modifiers] held, and
 * whether it is a [repeat] of a key held down.
 */
data class KeyPress(
    val key: String,
    val code: String?,
    val modifiers: Set<ModifierKey> = emptySet(),
    val repeat: Boolean = false,
)

/** What a keyboard shortcut does. */
sealed interface ShortcutCommand {
    /** Open the quick bar on [section] (`_showQuickBar`). */
    data class ShowQuickBar(val section: QuickBarSection) : ShortcutCommand

    /** Open the quick bar, or close it when open (`_toggleQuickBar`). */
    data object ToggleQuickBar : ShortcutCommand

    /** Open Assist (`_showVoiceCommandDialog`). */
    data object ShowAssist : ShortcutCommand

    /** Create a My Home Assistant link to the page shown (`_createMyLink`). */
    data object CreateMyLink : ShortcutCommand

    /** Show the keyboard shortcuts (`_showShortcutDialog`). */
    data object ShowShortcuts : ShortcutCommand
}

/**
 * What the shortcuts depend on: whether the user [enabled] them (the profile's "Keyboard shortcuts",
 * `enableShortcuts`), [isAdmin], whether the quick bar is open ([quickBarOpen]) and whether the `conversation`
 * integration is loaded ([conversationLoaded]).
 */
data class ShortcutState(
    val enabled: Boolean,
    val isAdmin: Boolean,
    val quickBarOpen: Boolean,
    val conversationLoaded: Boolean,
)

/**
 * The command [press] runs in [state], `null` when it is no shortcut or the shortcut can't run now: the first
 * binding matching it (the latin keys, then their physical keys for other layouts, as upstream registers them),
 * when its handler's conditions hold. Upstream also ignores keys typed into a field and while text is selected
 * (`canOverrideAlphanumericInput`); the caller only passes the keys nothing on screen took.
 */
fun keyboardShortcut(press: KeyPress, state: ShortcutState): ShortcutCommand? {
    val bindings = if (state.isAdmin) BINDINGS + ADMIN_BINDINGS else BINDINGS
    // `defaultKeybindingsHandlerIgnore`: a key held down doesn't run its shortcut again
    return bindings.firstOrNull { !press.repeat && it.matches(press) }?.command?.takeIf { it.canRun(state) }
}

/** Whether [this] command's handler goes on in [state] (its checks before `preventDefault`). */
private fun ShortcutCommand.canRun(state: ShortcutState): Boolean = when (this) {
    // `_canShowQuickBar`
    is ShortcutCommand.ShowQuickBar, ShortcutCommand.ShowShortcuts -> state.enabled && !state.quickBarOpen
    ShortcutCommand.ShowAssist -> state.enabled && state.conversationLoaded
    ShortcutCommand.ToggleQuickBar, ShortcutCommand.CreateMyLink -> state.enabled
}

/** A keybinding: [key] (a `KeyboardEvent.key` or `code`) pressed with exactly the [modifiers] runs [command]. */
private class Binding(val key: String, val command: ShortcutCommand, val modifiers: Set<ModifierKey> = emptySet()) {
    /**
     * Port of `matchKeybindingPress`: the key typed (in any case) or the physical key is [key], with every one of
     * [modifiers] held and no other modifier (unless [key] is that modifier).
     */
    fun matches(press: KeyPress): Boolean {
        val keyMatches = key.equals(press.key, ignoreCase = true) || key == press.code
        return keyMatches &&
            press.modifiers.containsAll(modifiers) &&
            press.modifiers.all { it in modifiers || it.keyName == key }
    }
}

/** `$mod`: Control, as on every platform but Apple's. */
private val MOD = setOf(ModifierKey.CONTROL)

private val ENTITIES = ShortcutCommand.ShowQuickBar(QuickBarSection.ENTITY)
private val COMMANDS = ShortcutCommand.ShowQuickBar(QuickBarSection.COMMAND)
private val DEVICES = ShortcutCommand.ShowQuickBar(QuickBarSection.DEVICE)

/** Everyone's shortcuts, in upstream's order. */
private val BINDINGS = listOf(
    // For latin keyboards that have e, m and a keys
    Binding("e", ENTITIES),
    Binding("m", ShortcutCommand.CreateMyLink),
    Binding("a", ShortcutCommand.ShowAssist),
    Binding("k", ShortcutCommand.ToggleQuickBar, MOD),
    Binding("?", ShortcutCommand.ShowShortcuts, setOf(ModifierKey.SHIFT)),
    // Fallbacks for other keyboards, by the physical key
    Binding("KeyE", ENTITIES),
    Binding("KeyM", ShortcutCommand.CreateMyLink),
    Binding("KeyA", ShortcutCommand.ShowAssist),
    Binding("KeyK", ShortcutCommand.ToggleQuickBar, MOD),
)

/** The admins' shortcuts, after everyone's. */
private val ADMIN_BINDINGS = listOf(
    Binding("c", COMMANDS),
    Binding("d", DEVICES),
    Binding("KeyC", COMMANDS),
    Binding("KeyD", DEVICES),
)
