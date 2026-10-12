package io.homeassistant.companion.android.dashboard.ui.dialogs.quickbar

import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.inspector.WindowInspector
import androidx.core.view.ViewCompat

/**
 * Hands the keys nothing took in [root]'s window to [onKey], and those of every window its activity opens over it
 * (dialogs, sheets, menus), as the frontend's shortcuts work with a dialog open. A window opening takes the focus
 * from the one under it, which is when the new windows are looked for; Android lists them from 10 on, so before
 * that only [root]'s window is hooked. Used on the main thread only.
 */
internal class ShortcutWindows(private val root: View, onKey: (KeyEvent) -> Boolean) {
    private val hooked = mutableSetOf<View>()
    private val unhandled = ViewCompat.OnUnhandledKeyEventListenerCompat { _, event -> onKey(event) }
    private val focus = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
        if (!hasFocus) hookNewWindows()
    }

    /** Starts handing keys over. */
    fun start() {
        hook(root)
    }

    /** Stops handing keys over, in every window hooked. */
    fun stop() {
        hooked.toList().forEach(::unhook)
    }

    private fun hook(view: View) {
        ViewCompat.addOnUnhandledKeyEventListener(view, unhandled)
        view.viewTreeObserver.addOnWindowFocusChangeListener(focus)
        hooked += view
    }

    private fun unhook(view: View) {
        ViewCompat.removeOnUnhandledKeyEventListener(view, unhandled)
        if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnWindowFocusChangeListener(focus)
        hooked -= view
    }

    private fun hookNewWindows() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        hooked.filter { it !== root && !it.isAttachedToWindow }.forEach(::unhook)
        // A dialog's window belongs to the activity's token, a popup's to the window it pops over
        val owners = hooked.mapNotNull { it.windowToken } + listOfNotNull(root.windowParams()?.token)
        WindowInspector.getGlobalWindowViews()
            .filter { it !in hooked && it.isAttachedToWindow && it.windowParams()?.token in owners }
            .forEach(::hook)
    }
}

private fun View.windowParams(): WindowManager.LayoutParams? = layoutParams as? WindowManager.LayoutParams
