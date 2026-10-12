package io.homeassistant.companion.android.dashboard.ui.dialogs.quickbar

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.dashboard.data.ShortcutPreferences
import io.homeassistant.companion.android.dashboard.data.valueOrNull
import io.homeassistant.companion.android.dashboard.entity.HassSnapshot
import io.homeassistant.companion.android.dashboard.entity.Localize
import io.homeassistant.companion.android.dashboard.keyboard.KeyPress
import io.homeassistant.companion.android.dashboard.keyboard.ModifierKey
import io.homeassistant.companion.android.dashboard.keyboard.MyLink
import io.homeassistant.companion.android.dashboard.keyboard.SHORTCUTS_TITLE
import io.homeassistant.companion.android.dashboard.keyboard.ShortcutCommand
import io.homeassistant.companion.android.dashboard.keyboard.ShortcutState
import io.homeassistant.companion.android.dashboard.keyboard.createMyLink
import io.homeassistant.companion.android.dashboard.keyboard.keyboardShortcut
import io.homeassistant.companion.android.dashboard.layout.DashboardMenuAction
import io.homeassistant.companion.android.dashboard.ui.DashboardUiState
import io.homeassistant.companion.android.dashboard.ui.DashboardViewModel
import java.net.URLDecoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber

/**
 * The keys pressed on a hardware keyboard that nothing on the screen took (a focused text field takes what is typed
 * into it), handed to [onKey], which tells whether it used the key. The keys pressed in the dialogs and sheets
 * opened over the screen, windows of their own, come here too (see [ShortcutWindows]).
 *
 * Upstream: part of `quick-bar-mixin` (src/state/quick-bar-mixin.ts)
 */
@Composable
internal fun UnhandledKeys(onKey: (KeyPress) -> Boolean) {
    val view = LocalView.current
    val handler by rememberUpdatedState(onKey)
    DisposableEffect(view) {
        val windows = ShortcutWindows(view.rootView) { event -> event.toKeyPress()?.let(handler) == true }
        windows.start()
        onDispose { windows.stop() }
    }
}

/**
 * The key pressed as the frontend sees it (a `keydown`'s `key`, `code` and modifiers), `null` when it isn't a press.
 * What the key types is read without Ctrl, Alt and Meta, as a browser's `key` is ("k" for Ctrl+K).
 */
internal fun KeyEvent.toKeyPress(): KeyPress? {
    if (action != KeyEvent.ACTION_DOWN) return null
    val typed = getUnicodeChar(metaState and COMMAND_META.inv())
    val key = if (typed > 0 && typed and KeyCharacterMap.COMBINING_ACCENT == 0) {
        String(Character.toChars(typed))
    } else {
        KeyEvent.keyCodeToString(keyCode)
    }
    val modifiers = buildSet {
        if (isShiftPressed) add(ModifierKey.SHIFT)
        if (isCtrlPressed) add(ModifierKey.CONTROL)
        if (isAltPressed) add(ModifierKey.ALT)
        if (isMetaPressed) add(ModifierKey.META)
    }
    return KeyPress(key = key, code = physicalCode(keyCode), modifiers = modifiers, repeat = repeatCount > 0)
}

/** `KeyboardEvent.code` of a letter key ("KeyE"), whatever the layout types with it. */
private fun physicalCode(keyCode: Int): String? = if (keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) {
    "Key${'A' + (keyCode - KeyEvent.KEYCODE_A)}"
} else {
    null
}

private const val COMMAND_META = KeyEvent.META_CTRL_MASK or KeyEvent.META_ALT_MASK or KeyEvent.META_META_MASK

/**
 * The keyboard shortcuts that work on the screen shown, for Android's list of shortcuts (Meta + /), which the
 * activity reads in `onProvideKeyboardShortcuts`; empty while no dashboard shows.
 */
@Singleton
class KeyboardShortcutHelp @Inject constructor() {
    @Volatile
    private var shown: Pair<Localize, Boolean>? = null

    /** The groups to list. */
    @RequiresApi(Build.VERSION_CODES.N)
    fun groups(): List<KeyboardShortcutGroup> =
        shown?.let { (localize, isAdmin) -> listOf(shortcutGroup(localize, isAdmin)) }.orEmpty()

    /** Lists the shortcuts of a dashboard named with [localize] for an admin ([isAdmin]), or none (`null`). */
    internal fun show(localize: Localize?, isAdmin: Boolean) {
        shown = localize?.let { it to isAdmin }
    }
}

/**
 * The shortcuts the dashboards answer, named as the shortcuts dialog names them.
 *
 * Upstream: none (native: Android's list of keyboard shortcuts)
 */
@RequiresApi(Build.VERSION_CODES.N)
private fun shortcutGroup(localize: Localize, isAdmin: Boolean): KeyboardShortcutGroup {
    fun info(key: String, keyCode: Int, meta: Int = 0) =
        KeyboardShortcutInfo(localize("ui.dialogs.shortcuts.$key"), keyCode, meta)
    val items = listOfNotNull(
        info("searching.search", KeyEvent.KEYCODE_K, KeyEvent.META_CTRL_ON),
        info("searching.search_command", KeyEvent.KEYCODE_C).takeIf { isAdmin },
        info("searching.search_entities", KeyEvent.KEYCODE_E),
        info("searching.search_devices", KeyEvent.KEYCODE_D).takeIf { isAdmin },
        info("assist.open_assist", KeyEvent.KEYCODE_A),
        info("other.my_link", KeyEvent.KEYCODE_M),
        info("other.show_shortcuts", KeyEvent.KEYCODE_SLASH, KeyEvent.META_SHIFT_ON),
    )
    return KeyboardShortcutGroup(localize(SHORTCUTS_TITLE), items)
}

/** Whether keyboard shortcuts are on, and the list Android shows of them. */
@HiltViewModel
internal class KeyboardShortcutsViewModel @Inject constructor(
    preferences: ShortcutPreferences,
    val help: KeyboardShortcutHelp,
) : ViewModel() {
    /** Whether the user turned keyboard shortcuts on (`enableShortcuts`). */
    val enabled: StateFlow<Boolean> = preferences.enabled
}

/**
 * Port of `quick-bar-mixin`'s shortcut handlers: a key press, with whether the quick bar is open, runs its command
 * (see [keyboardShortcut]) and tells whether it did. The quick bar and the shortcuts dialog are left to [onCommand];
 * Assist opens as the toolbar's does, when [offered]; "m" opens the page creating a My link to the page shown, or
 * says in a toast (`showToast`) that there is none. Lists the shortcuts for Android while shown.
 *
 * Upstream: part of `quick-bar-mixin` (src/state/quick-bar-mixin.ts)
 */
@Composable
internal fun rememberShortcutHandler(
    viewModel: DashboardViewModel,
    hass: State<HassSnapshot?>,
    offered: Set<DashboardMenuAction>,
    onCommand: (ShortcutCommand) -> Unit,
): (KeyPress, Boolean) -> Boolean {
    val shortcuts: KeyboardShortcutsViewModel = hiltViewModel()
    val enabled by shortcuts.enabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val command by rememberUpdatedState(onCommand)
    val snapshot = hass.value
    DisposableEffect(snapshot?.localize, snapshot?.user?.isAdmin) {
        shortcuts.help.show(snapshot?.localize, snapshot?.user?.isAdmin == true)
        onDispose { shortcuts.help.show(null, isAdmin = false) }
    }
    return { press, quickBarOpen ->
        val current = hass.value
        val state = ShortcutState(
            enabled = enabled,
            isAdmin = current?.user?.isAdmin == true,
            quickBarOpen = quickBarOpen,
            conversationLoaded = CONVERSATION in current?.config?.components.orEmpty(),
        )
        when (val shortcut = current?.let { keyboardShortcut(press, state) }) {
            null -> false
            ShortcutCommand.ShowAssist -> (DashboardMenuAction.Assist in offered).also { open ->
                if (open) viewModel.onOpenShownInWeb(flag = DashboardMenuAction.Assist.webParam)
            }
            ShortcutCommand.CreateMyLink -> {
                openMyLink(context, viewModel.shownPage(), current.localize)
                true
            }
            else -> {
                command(shortcut)
                true
            }
        }
    }
}

/** The page shown, as the frontend's address would be: its path and query. */
private fun DashboardViewModel.shownPage(): Pair<String, Map<String, String>> {
    val urlPath = selectedDashboardUrlPath.value ?: sidebar.value.valueOrNull?.defaultPanel.orEmpty()
    return when (val shown = uiState.value) {
        is DashboardUiState.Content -> "/$urlPath/${shown.viewPath}" to emptyMap()
        is DashboardUiState.Panel -> "/$urlPath" to shown.query.orEmpty().split('&').mapNotNull { pair ->
            pair.split('=', limit = 2).takeIf { it.size == 2 }?.let { (key, value) ->
                URLDecoder.decode(key, Charsets.UTF_8) to URLDecoder.decode(value, Charsets.UTF_8)
            }
        }.toMap()
        else -> "/$urlPath" to emptyMap()
    }
}

/** Opens the page creating a My link to [page] in the browser, or says there is none. */
private fun openMyLink(context: Context, page: Pair<String, Map<String, String>>, localize: Localize) {
    when (val link = createMyLink(page.first, page.second)) {
        // UPSTREAM-DIFF: upstream says so in the frontend's toast bar (`hass-notification`); here, an Android toast.
        is MyLink.NotFound -> Toast.makeText(
            context,
            localize(NO_MY_LINK, mapOf("path" to link.path)),
            Toast.LENGTH_LONG,
        ).show()
        is MyLink.Create -> try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url)))
        } catch (e: ActivityNotFoundException) {
            Timber.w(e, "No browser to create a My link in")
        }
    }
}

private const val CONVERSATION = "conversation"
private const val NO_MY_LINK = "ui.notification_toast.no_matching_link_found"
