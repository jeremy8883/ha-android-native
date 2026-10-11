package io.homeassistant.companion.android.dashboard.derive

import io.homeassistant.companion.android.dashboard.model.CardConfig
import io.homeassistant.companion.android.dashboard.model.obj
import io.homeassistant.companion.android.dashboard.model.string

/** The `navigation_path` of the card's tap action when it is a `navigate` action. */
val CardConfig.tapNavigationPath: String?
    get() = json.obj("tap_action")?.takeIf { it.string("action") == "navigate" }?.string("navigation_path")
