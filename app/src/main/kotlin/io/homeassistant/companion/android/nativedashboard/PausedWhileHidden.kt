package io.homeassistant.companion.android.nativedashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Gives [content] a lifecycle that follows the screen's but stops while it isn't [visible], so what follows the
 * lifecycle pauses with it: the frontend's WebView stops (its JavaScript timers paused) and keeps its page loaded.
 */
@Composable
internal fun PausedWhileHidden(visible: Boolean, content: @Composable () -> Unit) {
    val parent = LocalLifecycleOwner.current
    val owner = remember(parent) { CappedLifecycleOwner(parent.lifecycle) }
    DisposableEffect(owner) {
        owner.start()
        onDispose { owner.stop() }
    }
    SideEffect { owner.maxState = if (visible) Lifecycle.State.RESUMED else Lifecycle.State.CREATED }
    CompositionLocalProvider(LocalLifecycleOwner provides owner, content = content)
}

/** A lifecycle at [parent]'s state, but never past [maxState]. */
private class CappedLifecycleOwner(private val parent: Lifecycle) :
    LifecycleOwner,
    LifecycleEventObserver {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry

    var maxState: Lifecycle.State = Lifecycle.State.RESUMED
        set(value) {
            field = value
            update()
        }

    fun start() = parent.addObserver(this)

    fun stop() {
        parent.removeObserver(this)
        if (registry.currentState.isAtLeast(Lifecycle.State.CREATED)) registry.currentState = Lifecycle.State.DESTROYED
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) = update()

    private fun update() {
        val state = minOf(parent.currentState, maxState)
        // A destroyed lifecycle can't come back; the parent going there ends this one too
        if (registry.currentState != Lifecycle.State.DESTROYED && state != Lifecycle.State.INITIALIZED) {
            registry.currentState = state
        }
    }
}
