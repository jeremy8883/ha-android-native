package io.homeassistant.companion.android.dashboard.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner

/**
 * One of the screens stacked in the same place (the native dashboards, the web frontend), laid out whether [visible]
 * or not so it keeps its state and views, but only drawn and touched while [visible].
 *
 * @param backEnabled whether its back handlers get back presses; a hidden layer never does
 */
@Composable
internal fun ScreenLayer(visible: Boolean, backEnabled: Boolean = visible, content: @Composable () -> Unit) {
    val parent = LocalNavigationEventDispatcherOwner.current
    // The layer's back handlers go to its own dispatcher, turned off while it shouldn't get back presses
    val backOwner = remember(parent) {
        parent?.let { LayerBackOwner(NavigationEventDispatcher(it.navigationEventDispatcher)) }
    }
    DisposableEffect(backOwner) { onDispose { backOwner?.navigationEventDispatcher?.dispose() } }
    SideEffect { backOwner?.navigationEventDispatcher?.isEnabled = visible && backEnabled }
    Box(Modifier.fillMaxSize().then(if (visible) Modifier else NotPlaced)) {
        if (backOwner != null) {
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides backOwner, content = content)
        } else {
            content()
        }
    }
}

/** Measured, so it keeps its size, but not placed, so it is neither drawn nor touched. */
private val NotPlaced = Modifier.layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {}
}

private class LayerBackOwner(override val navigationEventDispatcher: NavigationEventDispatcher) :
    NavigationEventDispatcherOwner
